#!/usr/bin/env bash
# ================================================================
# 中哈贸易系统 · 一键构建（Linux / macOS）
#
# 产物：
#   src/backend-main/target/*.jar        主后端    (8080)
#   src/backend-training/target/*.jar    训练后端  (8081)
#   src/frontend-vue/dist/               前端静态资源
#
# 前置要求：JDK 17、Maven 3.8+、Node 18/20（Node 22 未经充分验证）
# ================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
echo "项目根目录: $ROOT"

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "缺少命令：$1，请先安装"; exit 1; }
}
need java
need mvn
need npm

echo
echo "==> Java 版本（需 17）"
java -version
echo
echo "==> Node 版本（建议 18 或 20）"
node -v

# 后端构建时如果服务器无法访问 Maven 中央仓库，
# 可先在能联网的机器执行：mvn dependency:go-offline
# 再拷贝 ~/.m2/repository 过来。
MVN_OPTS=(-DskipTests -Dmaven.test.skip=true)

echo
echo "==> [1/3] 构建主后端 backend-main (8080)"
mvn -f src/backend-main/pom.xml clean package "${MVN_OPTS[@]}"

echo
echo "==> [2/3] 构建训练后端 backend-training (8081)"
mvn -f src/backend-training/pom.xml clean package "${MVN_OPTS[@]}"

echo
echo "==> [3/3] 构建前端 frontend-vue"
cd src/frontend-vue
if [ -f package-lock.json ]; then
  npm ci
else
  npm install
fi
# 生产构建：接口以相对路径发出，由 Nginx 反向代理
npm run build

cd "$ROOT"
echo
echo "================================================================"
echo "构建完成，产物清单："
ls -lh src/backend-main/target/*.jar 2>/dev/null || echo "  [缺失] backend-main jar"
ls -lh src/backend-training/target/*.jar 2>/dev/null || echo "  [缺失] backend-training jar"
echo "  前端 dist: $(du -sh src/frontend-vue/dist 2>/dev/null | cut -f1)"
echo
echo "下一步："
echo "  1) 初始化数据库：python3 deploy/init_mysql.py --password <你的MySQL密码>"
echo "  2) 导入图谱数据：cd src/backend-main && mvn test -Dgraph.import=true -Dtest=RdfBulkImportTest"
echo "  3) 启动服务：    bash deploy/start-all.sh"
echo "================================================================"
