#!/bin/bash
# =====================================================================
# AJ-Report 1.3.0 启动脚本（Linux）
#
# 【为什么重写发行版原始脚本】
#   原始 bin/start.sh 在本机直接跑不起来，有三处硬伤：
#     1) `java ... -jar -Dspring.config.location=xxx yyy.jar`
#        —— `-jar` 的下一个参数会被当作 jar 文件名，于是 JVM 去找一个
#        名字叫 `-Dspring.config.location=...` 的文件，报
#        "Unable to access jarfile"。`-D` 必须写在 `-jar` 之前。
#     2) `-XX:PermSize=128m` 在 JDK 8 之后已被移除，JDK 11/17 上
#        JVM 直接拒绝启动。改用 `-XX:MetaspaceSize`。
#     3) AJ-Report 1.3.0 是 Spring Boot 2.3.5，**只兼容 JDK 8~11**；
#        本机默认 java 是 17，必须显式挑一个 JDK 11。
#   另外把日志从 /dev/null 改成落文件，并写 pid 文件，便于排错与停止。
# =====================================================================

cd `dirname $0`
BIN_DIR=`pwd`
cd ../
DEPLOY_DIR=`pwd`
LIB_DIR=$DEPLOY_DIR/lib
CONF_DIR=$DEPLOY_DIR/conf
LOGS_DIR=$DEPLOY_DIR/logs

# ---- 挑一个 JDK 8~11（Spring Boot 2.3.5 不支持 17）----
find_java() {
  for c in "${JAVA_HOME:+$JAVA_HOME/bin/java}" \
           /usr/lib/jvm/java-11-openjdk-amd64/bin/java \
           /usr/lib/jvm/java-1.11.0-openjdk-amd64/bin/java \
           /usr/lib/jvm/java-8-openjdk-amd64/bin/java \
           java; do
    [ -n "$c" ] || continue
    if [ -x "$c" ] || command -v "$c" >/dev/null 2>&1; then
      major=$("$c" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
      case "$major" in
        1|8|9|10|11) echo "$c"; return 0 ;;
      esac
    fi
  done
  return 1
}

JAVA_BIN=$(find_java) || {
  echo >&2 "ERROR: 未找到 JDK 8~11。AJ-Report 1.3.0 (Spring Boot 2.3.5) 无法运行在 JDK 17 上。"
  echo >&2 "       请安装：sudo apt-get install -y openjdk-11-jre-headless"
  exit 1
}

PIDS=`ps -f | grep java | grep "aj-report" | grep -v grep | awk '{print $2}'`
if [ -n "$PIDS" ]; then
  echo "ERROR: The AJ-Report already started! PID: $PIDS"
  exit 1
fi

mkdir -p "$LOGS_DIR"
mkdir -p "$DEPLOY_DIR/../runtime/aj-upload" 2>/dev/null || true

LIB_JARS=`ls $LIB_DIR | grep -v aj-report | awk '{print "'$LIB_DIR'/"$0}' | tr "\n" ":"`

JAVA_OPTS=" -server -Xms1g -Xmx2g -Xmn256m -XX:MetaspaceSize=128m -Xss256k "

nohup "$JAVA_BIN" $JAVA_OPTS \
  -Dspring.config.location=$CONF_DIR/bootstrap.yml \
  -Dfile.encoding=UTF-8 \
  -Xbootclasspath/a:$LIB_JARS \
  -jar $LIB_DIR/aj-report-*.jar \
  > "$LOGS_DIR/aj-report.log" 2>&1 &

echo $! > "$LOGS_DIR/aj-report.pid"
echo "AJ-Report 启动中：java=$JAVA_BIN  pid=$(cat $LOGS_DIR/aj-report.pid)"
echo "日志：$LOGS_DIR/aj-report.log"
