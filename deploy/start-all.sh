#!/usr/bin/env bash
# ================================================================
# 中哈贸易系统 · 一键启动（Linux / macOS）
#
# 启动顺序：
#   MySQL(需已运行) -> 主后端 8080 -> 训练后端 8081(自动拉起 Flask 5000)
#   前端由 Nginx 提供静态资源（本脚本不启动 Nginx）
#
# 关键约定：脚本会把工作目录切到项目根再启动 Java 进程，
#          这样配置里的 ${trade.home}（默认取 ${user.dir}）就等于项目根，
#          无需在任何配置文件里写死绝对路径。
#
# 用法：
#   bash deploy/start-all.sh
#   TRADE_HOME=/opt/trade bash deploy/start-all.sh    # 显式指定
# ================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p runtime logs

# ================================================================
# 统一凭据：载入项目根 config/.env（该文件不入库，详见 config/README.md）
# 逐行解析 key=value；外部已显式传入的环境变量优先，不会被文件覆盖。
# ================================================================
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

if load_env_file "$ROOT/config/.env"; then
  echo "配置文件   : config/.env"
else
  echo "配置文件   : 未找到 config/.env（将使用代码内默认值）"
  echo "             执行 cp config/.env.example config/.env 后按需填写"
fi

# 运行期环境变量（可按需覆盖）
export TRADE_HOME="${TRADE_HOME:-$ROOT}"
# Python 解释器：默认走 PATH；本机有多个版本时显式指定
# export PYTHON_EXE="/usr/bin/python3"
# Qwen 大模型接口（智能问答用；留空则问答的 LLM 兜底不可用）
# export QWEN_BASE_URL="http://127.0.0.1:8000/v1"
# export QWEN_CHAT_ENDPOINT="/chat/completions"
# export QWEN_EMBEDDING_ENDPOINT="/embeddings"

echo "项目根     : $ROOT"
echo "TRADE_HOME : $TRADE_HOME"

MAIN_JAR=$(ls src/backend-main/target/*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)
TRAIN_JAR=$(ls src/backend-training/target/*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)

if [ -z "$MAIN_JAR" ]; then
  echo "错误：未找到主后端 jar，请先执行 bash deploy/build.sh"; exit 1
fi
if [ -z "$TRAIN_JAR" ]; then
  echo "警告：未找到训练后端 jar（预测/训练功能将不可用）"
fi

port_busy() { (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }

start_one() {
  local name="$1" jar="$2" port="$3"
  if port_busy "$port"; then
    echo "[跳过] $name 端口 $port 已被占用"
    return
  fi
  echo "启动 $name (端口 $port) …"
  nohup java -jar "$jar" --server.port="$port" \
        > "logs/${name}.log" 2>&1 &
  echo $! > "logs/${name}.pid"
  echo "       PID $(cat "logs/${name}.pid")  日志 logs/${name}.log"
}

# 训练后端要先起（它会在启动后自动部署 Flask 5000）
if [ -n "$TRAIN_JAR" ]; then
  start_one backend-training "$TRAIN_JAR" 8081
fi
sleep 3
start_one backend-main "$MAIN_JAR" 8080

echo
echo "等待服务就绪…"
for i in $(seq 1 40); do
  if curl -sf "http://127.0.0.1:8080/api-docs" >/dev/null 2>&1 \
     || curl -sf "http://127.0.0.1:8080/swagger-ui.html" >/dev/null 2>&1; then
    echo "主后端已就绪 (8080)"
    break
  fi
  sleep 2
done

cat <<'EOF'

----------------------------------------------------------------
服务地址：
  主后端    http://<服务器IP>:8080
  接口文档  http://<服务器IP>:8080/swagger-ui.html
  训练后端  http://<服务器IP>:8081/api
  Flask     http://<服务器IP>:5000/health
  前端      http://<服务器IP>/            （由 Nginx 提供）

查看日志： tail -f logs/backend-main.log
停止服务： bash deploy/stop-all.sh
----------------------------------------------------------------
EOF
