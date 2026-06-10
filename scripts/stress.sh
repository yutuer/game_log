#!/bin/bash
# ============================================================
# stress.sh - 云服务器压测脚本 (Linux)
#
#  用法：
#    ./scripts/stress.sh                      云模式 300 TPS，10 分钟
#    ./scripts/stress.sh --tps=500            自定义 500 TPS
#    ./scripts/stress.sh --tps=200 --duration=3  200 TPS × 3 分钟
#    ./scripts/stress.sh --url=http://localhost:8080/api/game-logs --cloud
#
#  所有参数透传给 StressTest，参见 StressTest --help
#
#  日志输出：
#    终端实时显示 + 同时保存到 logs/stress-{时间戳}.log
# ============================================================

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

# 固定日志文件（每次覆盖，方便 tail -f 追踪）
LOG_DIR="${PROJECT_DIR}/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="${LOG_DIR}/stress.log"

echo "=========================================="
echo "  Building project for stress test..."
echo "=========================================="
mvn compile -q -DskipTests
echo "  Build done."
echo ""

# 构建 classpath（编译输出 + Maven 依赖）
CP="target/classes"
CP="$CP:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout 2>/dev/null)"

# 运行压测（默认透传所有参数，无参数时默认 --cloud）
if [ $# -eq 0 ]; then
    ARGS="--cloud"
else
    ARGS="$*"
fi

echo "=========================================="
echo "  Starting stress test"
echo "  Args: $ARGS"
echo "  Log: ${LOG_FILE}"
echo "=========================================="

# 终端实时显示 + 同时写入日志文件
java -cp "$CP" com.gamelog.StressTest $ARGS 2>&1 | tee "$LOG_FILE"

echo ""
echo "Stress test finished."
echo "Log saved to: ${LOG_FILE}"

# 显示最后几行（主要看 Final Report）
echo ""
echo "===== Final Report (last 10 lines) ====="
tail -10 "$LOG_FILE"
echo "========================================"
