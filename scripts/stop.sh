#!/bin/bash
# ============================================================
# stop.sh - 云服务器停止 game-log-service (Linux)
#
#  先尝试 actuator 优雅关停，失败则 kill
#  用法：
#    ./scripts/stop.sh
# ============================================================

APP_NAME="game-log-service"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${PROJECT_DIR}/logs"
PID_FILE="${LOG_DIR}/app.pid"

echo "=========================================="
echo "  Stopping ${APP_NAME}"
echo "=========================================="

# 方式 1：通过 actuator 优雅关停
echo "Sending shutdown request to actuator..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/actuator/shutdown 2>/dev/null || true)

if [ "$HTTP_CODE" = "200" ]; then
    echo "Shutdown request sent successfully."
    echo "Waiting 5 seconds for graceful shutdown..."
    sleep 5
else
    echo "Actuator shutdown failed (HTTP ${HTTP_CODE}), trying PID-based kill..."
fi

# 方式 2：通过 PID 文件 kill
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "Stopping process PID=${PID}..."
        kill "$PID" 2>/dev/null || true
        sleep 3
        # 如果还没停，强制 kill
        if kill -0 "$PID" 2>/dev/null; then
            echo "Force killing PID=${PID}..."
            kill -9 "$PID" 2>/dev/null || true
        fi
    else
        echo "Process ${PID} not running, cleaning PID file."
    fi
    rm -f "$PID_FILE"
fi

# 方式 3：通过 jps 搜索进程
JAVA_PID=$(jps -l 2>/dev/null | grep -i "gamelog\|GameLogApplication" | awk '{print $1}' || true)
if [ -n "$JAVA_PID" ]; then
    echo "Stopping Java process PID=${JAVA_PID} (from jps)..."
    kill "$JAVA_PID" 2>/dev/null || true
    sleep 2
    if kill -0 "$JAVA_PID" 2>/dev/null; then
        echo "Force killing PID=${JAVA_PID}..."
        kill -9 "$JAVA_PID" 2>/dev/null || true
    fi
fi

echo ""
echo "Done."
