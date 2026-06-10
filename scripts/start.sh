#!/bin/bash
# ============================================================
# start.sh - 云服务器启动 game-log-service (Linux)
#
#  云模式（硬编码）：
#    - 无 profile（使用 application.yml 云默认值）
#    - JVM: -Xmx512m（限制内存）
#    - 日志输出到 logs/app.log
#
#  用法：
#    ./scripts/start.sh             启动（后台运行）
#    ./scripts/start.sh foreground  前台运行（Ctrl+C 停止）
# ============================================================

set -e

APP_NAME="game-log-service"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${PROJECT_DIR}/logs"
APP_LOG="${LOG_DIR}/app.log"
PID_FILE="${LOG_DIR}/app.pid"

# 创建日志目录
mkdir -p "$LOG_DIR"

echo "=========================================="
echo "  Starting ${APP_NAME} (CLOUD mode)"
echo "  Project: ${PROJECT_DIR}"
echo "=========================================="

cd "$PROJECT_DIR"

# 编译（跳过测试）
echo "Compiling..."
mvn clean package -DskipTests -q
echo "Compile done."

# JVM 参数（云服务器 2C2G，保守配置）
JVM_OPTS="-Xmx512m -Xms256m"
JVM_OPTS="${JVM_OPTS} -Dfile.encoding=UTF-8"
JVM_OPTS="${JVM_OPTS} -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
JVM_OPTS="${JVM_OPTS} -XX:+ExitOnOutOfMemoryError"

# 查找打包产物（支持 jar 和 war）
JAR_FILE=$(ls target/*.jar 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
    JAR_FILE=$(ls target/*.war 2>/dev/null | head -1)
fi

if [ -z "$JAR_FILE" ]; then
    echo "Error: No jar/war file found in target/"
    exit 1
fi

if [ "$1" = "foreground" ]; then
    # 前台运行（用于测试）
    echo "Starting in foreground..."
    echo "URL: http://localhost:8080"
    echo "Log: ${APP_LOG}"
    echo "Press Ctrl+C to stop"
    echo ""
    exec java ${JVM_OPTS} -jar "${JAR_FILE}" 2>&1 | tee -a "$APP_LOG"
else
    # 后台运行
    echo "Starting in background..."
    nohup java ${JVM_OPTS} -jar "${JAR_FILE}" >> "$APP_LOG" 2>&1 &
    APP_PID=$!
    echo $APP_PID > "$PID_FILE"
    echo "PID: ${APP_PID}"
    echo "URL: http://localhost:8080"
    echo "Log: ${APP_LOG}"
    echo "Started."
fi
