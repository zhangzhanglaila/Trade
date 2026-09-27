#!/usr/bin/env bash
# ================================================================
# 中哈贸易系统 · 部署后健康检查
#
# 逐项检查服务存活与数据就绪，输出 PASS / FAIL / WARN。
# 用法： bash deploy/check-health.sh [数据库密码]
#        不传密码时自动读取项目根 config/.env（详见 config/README.md）
# ================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ---- 统一凭据：载入项目根 config/.env（该文件不入库，详见 config/README.md）----
# 逐行解析 key=value；外部已显式传入的环境变量优先，不会被文件覆盖。
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
load_env_file "$ROOT/config/.env"

DB_USER="${DB_USERNAME:-root}"
DB_PASS="${1:-${DB_PASSWORD:-}}"
ADMIN_USER="${ADMIN_USERNAME:-admin}"
ADMIN_PASS="${ADMIN_PASSWORD:-}"
PASS=0; FAIL=0; WARN=0

ok()   { echo "  [PASS] $1"; PASS=$((PASS+1)); }
bad()  { echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }
warn() { echo "  [WARN] $1"; WARN=$((WARN+1)); }

http_code() { curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$1" 2>/dev/null || echo 000; }
port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }

echo "================================================================"
echo " 中哈贸易系统 · 健康检查   ($(date '+%Y-%m-%d %H:%M:%S'))"
echo " 项目根: $ROOT"
echo "================================================================"

echo
echo "[1] 端口监听"
for p in 3306 8080 8081 5000; do
  if port_open "$p"; then ok "端口 $p 监听中"; else bad "端口 $p 未监听"; fi
done
for p in 9095 19530; do
  if port_open "$p"; then ok "端口 $p 监听中（可选组件）"; else warn "端口 $p 未监听（可选，不影响核心功能）"; fi
done

echo
echo "[2] HTTP 接口"
c=$(http_code http://127.0.0.1:8080/swagger-ui.html)
[ "$c" = "200" ] || [ "$c" = "302" ] && ok "主后端 8080 ($c)" || bad "主后端 8080 返回 $c"

c=$(http_code http://127.0.0.1:8081/api/health)
if [ "$c" = "200" ] || [ "$c" = "404" ]; then ok "训练后端 8081 可达 ($c)"; else bad "训练后端 8081 返回 $c"; fi

c=$(http_code http://127.0.0.1:5000/health)
[ "$c" = "200" ] && ok "Flask 预测服务 5000" || bad "Flask 5000 返回 $c（检查 python 依赖与 PYTHON_EXE）"

c=$(http_code http://127.0.0.1/)
[ "$c" = "200" ] && ok "前端 (Nginx) 80" || warn "前端 80 返回 $c（未配 Nginx 时正常）"

echo
echo "[3] 登录接口"
if [ -z "$ADMIN_PASS" ]; then
  warn "未配置 ADMIN_PASSWORD，跳过登录检查（见 config/README.md）"
else
  resp=$(curl -s --max-time 10 -X POST http://127.0.0.1:8080/auth/login \
          -H 'Content-Type: application/json' \
          -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" 2>/dev/null)
  if echo "$resp" | grep -q -E '"code"\s*:\s*(200|0)' || echo "$resp" | grep -q 'token'; then
    ok "$ADMIN_USER 登录成功"
  else
    bad "登录失败，返回：$(echo "$resp" | head -c 160)"
  fi
fi

echo
echo "[4] 数据库数据就绪"
if ! command -v mysql >/dev/null 2>&1; then
  warn "未找到 mysql 客户端，跳过数据库检查"
elif [ -z "$DB_PASS" ]; then
  warn "未配置数据库密码，跳过数据库检查（cp config/.env.example config/.env 后填写）"
else
  q() { mysql -u "$DB_USER" -p"$DB_PASS" -N -B -e "$1" 2>/dev/null; }
  cnt() { q "SELECT COUNT(*) FROM foreign_trade_qa_db.$1;"; }

  for t in news_articles trade_records country_monthly_trade comprehensive_data corpus_entries; do
    n=$(cnt "$t")
    if [ -z "$n" ]; then bad "$t 查询失败（库/表不存在或密码错）"
    elif [ "$n" -gt 0 ]; then ok "$t = $n 行"
    else warn "$t = 0 行（未初始化，执行 deploy/init_mysql.py）"; fi
  done

  # 关键表阈值提示
  n=$(cnt trade_records)
  if [ -n "$n" ] && [ "$n" -gt 0 ] && [ "$n" -lt 100000 ]; then
    warn "trade_records 仅 $n 行 —— 可能用了 --skip-trade-records 或少导了数据"
  fi
fi

echo
echo "[5] 图谱数据（Jena TDB）"
TDB="${TRADE_HOME:-$ROOT}/runtime/ontology-graph-store"
if [ -d "$TDB" ]; then
  sz=$(du -sm "$TDB" 2>/dev/null | cut -f1)
  if [ "${sz:-0}" -gt 50 ]; then
    ok "TDB 目录存在，约 ${sz} MB"
  else
    warn "TDB 目录仅 ${sz} MB，疑似未导入数据（执行 RdfBulkImportTest）"
  fi
else
  bad "TDB 目录不存在：$TDB（执行 mvn test -Dgraph.import=true -Dtest=RdfBulkImportTest）"
fi

echo
echo "[6] 运行期目录"
for d in runtime runtime/uploadFiles logs; do
  [ -d "$ROOT/$d" ] && ok "$d 存在" || warn "$d 不存在（应用启动时会自动创建）"
done

echo
echo "================================================================"
echo " 结果: PASS=$PASS  FAIL=$FAIL  WARN=$WARN"
if [ "$FAIL" -gt 0 ]; then
  echo " 存在失败项，请对照 docs/服务器部署指南.md 的【故障排查】处理。"
else
  echo " 核心检查全部通过。"
fi
echo "================================================================"
exit $([ "$FAIL" -gt 0 ] && echo 1 || echo 0)
