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
# ============================================================

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

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
echo "=========================================="

java -cp "$CP" com.gamelog.StressTest $ARGS

echo ""
echo "Stress test finished."
