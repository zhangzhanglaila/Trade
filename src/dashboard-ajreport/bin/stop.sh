#!/bin/bash
# AJ-Report 停止脚本：优先用 pid 文件，找不到再按进程名兜底；
# 先 SIGTERM 优雅退出，最多等 20s，仍不死再 SIGKILL。
cd `dirname $0`
cd ../
DEPLOY_DIR=`pwd`
PIDF=$DEPLOY_DIR/logs/aj-report.pid

if [ -f "$PIDF" ] && kill -0 "$(cat "$PIDF" 2>/dev/null)" 2>/dev/null; then
  pid=$(cat "$PIDF")
else
  pid=`ps ax | grep -i 'aj-report' | grep java | grep -v grep | awk '{print $1}'`
fi

if [ -z "$pid" ] ; then
  echo "No AJ-Report Server running."
  rm -f "$PIDF"
  exit 0
fi

kill "$pid" 2>/dev/null
for i in $(seq 1 20); do
  kill -0 "$pid" 2>/dev/null || break
  sleep 1
done
if kill -0 "$pid" 2>/dev/null; then
  kill -9 "$pid" 2>/dev/null
fi
rm -f "$PIDF"
echo "AJ-Report(${pid}) stopped."
