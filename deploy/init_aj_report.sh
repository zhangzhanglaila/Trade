#!/usr/bin/env bash
# ================================================================
# AJ-Report 大屏（独立服务，默认端口 9095）初始化 / 修复
#
# 为什么需要它：
#   AJ-Report 是「独立部署的第三方服务」——它的元数据库（报表定义、
#   数据源、分享码）不在主库 foreign_trade_qa_db 里，也不由后端代码管理。
#   一旦换机器 / 重装 MySQL，9095 即使起来了也是空壳：分享链接报
#   「分享链接已失效」，大屏打开一片空白。本脚本把这段「手工摸索出来
#   的部署知识」固化下来，可反复执行。
#
# 它做四件事（全部幂等，不会破坏已有数据）：
#   1. 建库 aj_report / data_board（不存在才建）
#   2. 首次导入 src/dashboard-ajreport/aj_report.sql（报表+大屏+组件的定义）
#   3. 首次导入 src/dashboard-ajreport/data_board.sql（大屏取数用的数据表）
#   4. 校准数据源指向，并**重建两张分享码记录**
#
# 关于第 4 步（重点）：
#   AJ-Report 的分享码 share_code 不是随机串，而是 JWT 的载荷字段，
#   由后端用**硬编码在 jar 里的 HMAC 密钥 `aj-report`** 以 HS256 签名，
#   校验时拿 share_code 反查数据库、并比对签名。所以手工往
#   gaea_report_share 里塞一个随机 UUID 是不行的 —— 会报
#   「The token was expected to have 3 parts」。本脚本按真实结构重签。
#
# 用法：
#   bash deploy/init_aj_report.sh
#   AJ_PUBLIC_HOST=10.0.0.5 bash deploy/init_aj_report.sh   # 指定对外地址
# ================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQLDIR="$ROOT/src/dashboard-ajreport"

# 与 deploy/start-all.sh 同一套凭据来源：项目根 config/.env（不入库）。
# 已显式传入的环境变量优先，不会被文件覆盖。
load_env_file() {
  local file="$1" line key val
  [ -f "$file" ] || return 1
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line#"${line%%[![:space:]]*}"}"
    case "$line" in ''|'#'*) continue ;; esac
    case "$line" in *=*) ;; *) continue ;; esac
    key="${line%%=*}"; val="${line#*=}"
    key="${key%"${key##*[![:space:]]}"}"
    case "$val" in
      \"*\") val="${val#\"}"; val="${val%\"}" ;;
      \'*\') val="${val#\'}"; val="${val%\'}" ;;
    esac
    [ -n "$key" ] || continue
    [ -z "${!key+x}" ] && export "$key=$val"
  done < "$file"
}
load_env_file "$ROOT/config/.env" || true

AJ_DB_HOST="${AJ_DB_HOST:-127.0.0.1}"
AJ_DB_PORT="${AJ_DB_PORT:-3306}"
AJ_DB_USERNAME="${AJ_DB_USERNAME:-root}"
AJ_DB_PASSWORD="${AJ_DB_PASSWORD:-}"
AJ_DB_NAME="${AJ_DB_NAME:-aj_report}"
BOARD_DB_NAME="${AJ_BOARD_DB_NAME:-data_board}"
AJ_PORT="${AJ_PORT:-9095}"
AJ_PUBLIC_HOST="${AJ_PUBLIC_HOST:-192.168.1.100}"

# 两张对外分享码：<分享码> -> <报表编码>
SHARE_CODES="KhTradeA1=data_board_1714888676953 KhTradeB2=ss"

echo "AJ-Report 初始化"
echo "  元数据库 : $AJ_DB_NAME@$AJ_DB_HOST:$AJ_DB_PORT"
echo "  数据源库 : $BOARD_DB_NAME"
echo "  对外地址 : http://$AJ_PUBLIC_HOST:$AJ_PORT"
echo

for f in "$SQLDIR/aj_report.sql" "$SQLDIR/data_board.sql"; do
  [ -f "$f" ] || { echo "错误：缺少 $f"; exit 1; }
done

# 用环境变量把连接参数传给内嵌的 Python，避免密码出现在命令行里
export AJ_DB_HOST AJ_DB_PORT AJ_DB_USERNAME AJ_DB_PASSWORD
export AJ_DB_NAME BOARD_DB_NAME AJ_PORT AJ_PUBLIC_HOST SHARE_CODES SQLDIR

python3 - <<'PY'
import base64
import hashlib
import hmac
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time

HOST = os.environ["AJ_DB_HOST"]
PORT = os.environ["AJ_DB_PORT"]
USER = os.environ["AJ_DB_USERNAME"]
PWD = os.environ["AJ_DB_PASSWORD"]
AJ_DB = os.environ["AJ_DB_NAME"]
BOARD_DB = os.environ["BOARD_DB_NAME"]
AJ_PORT = os.environ["AJ_PORT"]
PUBLIC_HOST = os.environ["AJ_PUBLIC_HOST"]
SQLDIR = os.environ["SQLDIR"]

# AJ-Report 1.3.0 里 JwtUtil 硬编码的 HMAC 密钥（反编译 jar 得到）
JWT_SECRET = b"aj-report"
JWT_EXP = 4070908800  # 2099-01-01


def mysql_base():
    args = ["mysql", "-h", HOST, "-P", PORT, "-u", USER,
            "--default-character-set=utf8mb4"]
    if PWD:
        args.append("-p" + PWD)
    return args


def mysql_exec(sql, db=None, use_file=None):
    """执行 SQL（字符串或文件），返回 (rc, stdout, stderr)"""
    args = mysql_base()
    if db:
        args.append(db)
    if use_file:
        with open(use_file, "rb") as fin:
            p = subprocess.run(args, stdin=fin, capture_output=True)
        return p.returncode, p.stdout.decode("utf-8", "replace"), p.stderr.decode("utf-8", "replace")
    p = subprocess.run(args + ["-e", sql], capture_output=True, text=True)
    err = "\n".join(l for l in p.stderr.splitlines() if "insecure" not in l.lower())
    return p.returncode, p.stdout, err


def query(sql):
    p = subprocess.run(mysql_base() + ["-N", "-B", "-e", sql],
                       capture_output=True, text=True)
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip())
    return p.stdout.strip()


def b64u(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def make_token(report_code, share_code):
    header = b64u(json.dumps({"alg": "HS256", "typ": "JWT"},
                             separators=(",", ":")).encode())
    payload = b64u(json.dumps({
        "iat": int(time.time()),
        "exp": JWT_EXP,
        "reportCode": report_code,
        "shareCode": share_code,
        "sharePassword": None,
    }, separators=(",", ":")).encode())
    signing_input = (header + "." + payload).encode()
    sig = b64u(hmac.new(JWT_SECRET, signing_input, hashlib.sha256).digest())
    return header + "." + payload + "." + sig


def table_count(db):
    out = query("select count(*) from information_schema.tables "
                "where table_schema='%s';" % db)
    return int(out or "0")


# ---------- 1) 建库 ----------
for db in (AJ_DB, BOARD_DB):
    rc, _, err = mysql_exec("create database if not exists `%s` "
                            "default character set utf8mb4;" % db)
    if rc != 0:
        print("建库失败 %s: %s" % (db, err), file=sys.stderr)
        sys.exit(1)
print("[1/4] 数据库已就绪：%s / %s" % (AJ_DB, BOARD_DB))

# ---------- 2) 导入 aj_report.sql ----------
n = table_count(AJ_DB)
if n == 0:
    print("[2/4] aj_report 为空，导入 aj_report.sql（含报表/大屏/组件定义）…")
    rc, out, err = mysql_exec(None, db=AJ_DB, use_file=os.path.join(SQLDIR, "aj_report.sql"))
    if rc != 0:
        print("导入失败：%s" % err, file=sys.stderr)
        sys.exit(1)
    print("     导入完成，共 %d 张表" % table_count(AJ_DB))
else:
    print("[2/4] aj_report 已有 %d 张表，跳过导入" % n)

# ---------- 3) 导入 data_board.sql ----------
n = table_count(BOARD_DB)
if n == 0:
    print("[3/4] %s 为空，导入 data_board.sql（大屏取数的数据表）…" % BOARD_DB)
    # 上游文件里写的是 `use aj_report;`（导出工具的问题），
    # 直接导入会把示例数据塞进 AJ-Report 自己的元数据库，导致大屏取数全空。
    # 这里不改第三方文件，只在导入前把库名改对。
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False,
                                     encoding="utf-8") as tmp:
        fixed = tmp.name
        with open(os.path.join(SQLDIR, "data_board.sql"), encoding="utf-8",
                  errors="replace") as src:
            for line in src:
                if line.strip().lower() == "use aj_report;":
                    tmp.write("use `%s`;\n" % BOARD_DB)
                else:
                    tmp.write(line)
    rc, out, err = mysql_exec(None, db=BOARD_DB, use_file=fixed)
    os.unlink(fixed)
    if rc != 0:
        print("导入失败：%s" % err, file=sys.stderr)
        sys.exit(1)
    print("     导入完成，共 %d 张表" % table_count(BOARD_DB))
else:
    print("[3/4] %s 已有 %d 张表，跳过导入" % (BOARD_DB, n))

# ---------- 4) 数据源 + 分享码 ----------
jdbc = ("jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8"
        "&serverTimezone=GMT%%2B8" % (HOST, PORT, BOARD_DB))
cfg = json.dumps({
    "driverName": "com.mysql.cj.jdbc.Driver",
    "jdbcUrl": jdbc,
    "username": USER,
    "password": PWD,
}, separators=(",", ":")).replace("'", "''")

sql_lines = [
    "update `%s`.`gaea_report_data_source` "
    "set source_config='%s', enable_flag=1, delete_flag=0 "
    "where source_code='trade_data_board';" % (AJ_DB, cfg),
    "insert into `%s`.`gaea_report_data_source` "
    "(source_code, source_name, source_desc, source_type, source_config, "
    " enable_flag, delete_flag, create_by, create_time, version) "
    "select 'trade_data_board', '数据大屏', '大屏取数数据源', 'mysql', '%s', "
    "1, 0, 'admin', now(), 1 from dual "
    "where not exists (select 1 from `%s`.`gaea_report_data_source` "
    "where source_code='trade_data_board');" % (AJ_DB, cfg, AJ_DB),
]

created = []
for pair in os.environ["SHARE_CODES"].split():
    code, report_code = pair.split("=", 1)
    token = make_token(report_code, code)
    share_url = "http://%s:%s/index.html#/aj/%s" % (PUBLIC_HOST, AJ_PORT, code)
    created.append((code, report_code))
    sql_lines.append(
        "delete from `%s`.`gaea_report_share` where share_code='%s';" % (AJ_DB, code))
    sql_lines.append(
        "insert into `%s`.`gaea_report_share` "
        "(share_code, share_valid_type, share_valid_time, share_token, share_url, "
        " share_password, report_code, enable_flag, delete_flag, create_by, "
        " create_time, version) values "
        "('%s', 0, '2099-01-01 00:00:00', '%s', '%s', null, '%s', 1, 0, "
        "'admin', now(), 1);" % (AJ_DB, code, token, share_url, report_code))

with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False,
                                 encoding="utf-8") as tmp:
    fix_sql = tmp.name
    tmp.write("\n".join(sql_lines) + "\n")

rc, out, err = mysql_exec(None, db=AJ_DB, use_file=fix_sql)
os.unlink(fix_sql)
if rc != 0:
    print("校准失败：%s" % err, file=sys.stderr)
    sys.exit(1)
print("[4/4] 数据源与分享码已校准")
for code, report_code in created:
    print("      http://%s:%s/index.html#/aj/%s  ->  %s"
          % (PUBLIC_HOST, AJ_PORT, code, report_code))

print("\n完成。请确认 AJ-Report 服务已启动（bash src/dashboard-ajreport/bin/start.sh）。")
PY
