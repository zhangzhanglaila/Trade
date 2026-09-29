#!/bin/bash
# reverse_tunnel.sh — 内网 → 阿里云 反向隧道守护（持久版）
#
# 2026-09-29 更新: 原脚本放在 /tmp，服务器重启后丢失且无 crontab 守护。
# 现持久化到 ~/Trade/deploy/，并由 crontab 每 5 分钟拉起（脚本自带 pgrep 去重）。
#
# 端口映射:
#   内网 nginx 80        -> 阿里云 123.56.246.31:8080  (主系统前端+后端, 安全组已放行)
#   内网 AJ-Report 9095  -> 阿里云 123.56.246.31:80    (大屏, 安全组已放行且空闲)
#
# 断线自动重连（无限循环 + 每 10s 重试）

ALIYUN_HOST="root@123.56.246.31"
LOG="/home/wust_1/Trade/logs/reverse_tunnel.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG"
}

# 已存在则退出，避免重复启动（匹配任一 -R 转发 + 目标主机）
if pgrep -f "ssh .*-R 0\.0\.0\.0:.*localhost.*${ALIYUN_HOST}" >/dev/null 2>&1; then
    exit 0
fi

log "启动反向隧道守护: 本地:80 -> ${ALIYUN_HOST}:8080, 本地:9095 -> ${ALIYUN_HOST}:80"

while true; do
    log "建立隧道..."
    ssh -N -T \
        -o ExitOnForwardFailure=yes \
        -o ServerAliveInterval=30 \
        -o ServerAliveCountMax=3 \
        -o ConnectTimeout=20 \
        -o StrictHostKeyChecking=accept-new \
        -R "0.0.0.0:8080:localhost:80" \
        -R "0.0.0.0:80:localhost:9095" \
        "${ALIYUN_HOST}" >> "$LOG" 2>&1

    log "隧道断开，10 秒后重连..."
    sleep 10
done
