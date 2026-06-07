#!/bin/bash
# ============================================================
# start.sh - 部署并启动 game-log-service
# 用途：在阿里云 Linux 服务器上，编译项目并将 WAR 包部署到 Tomcat
# 使用方式：bash scripts/start.sh 或 ./scripts/start.sh
# ============================================================

# ---------- 变量定义 ----------
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOMCAT_HOME="/usr/local/tomcat"
WAR_NAME="game-log-service"

echo "=========================================="
echo " 开始部署 ${WAR_NAME}"
echo "=========================================="

# ---------- 1. 编译项目 ----------
echo "[1/5] 进入项目目录: ${PROJECT_DIR}"
cd "${PROJECT_DIR}" || { echo "错误：无法进入项目目录 ${PROJECT_DIR}"; exit 1; }

echo "[2/5] 执行 Maven 打包（跳过测试）..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "错误：Maven 打包失败，部署终止"
    exit 1
fi

# ---------- 2. 停止已有的 Tomcat ----------
echo "[3/5] 停止已有的 Tomcat..."
if [ -f "${TOMCAT_HOME}/bin/shutdown.sh" ]; then
    ${TOMCAT_HOME}/bin/shutdown.sh
    sleep 3

    # 检查是否仍在运行
    TOMCAT_PID=$(ps -ef | grep "${TOMCAT_HOME}" | grep -v grep | awk '{print $2}')
    if [ -n "${TOMCAT_PID}" ]; then
        echo "Tomcat 仍在运行（PID: ${TOMCAT_PID}），强制终止..."
        kill -9 ${TOMCAT_PID}
        sleep 2
    fi
else
    echo "未找到 Tomcat shutdown 脚本，跳过停止步骤"
fi

# ---------- 3. 复制 WAR 包到 Tomcat ----------
echo "[4/5] 复制 WAR 包到 Tomcat webapps 目录..."
WAR_FILE="${PROJECT_DIR}/target/${WAR_NAME}.war"
if [ ! -f "${WAR_FILE}" ]; then
    # 尝试匹配 target 下任意 war 文件
    WAR_FILE=$(ls "${PROJECT_DIR}/target/"*.war 2>/dev/null | head -1)
fi

if [ -z "${WAR_FILE}" ] || [ ! -f "${WAR_FILE}" ]; then
    echo "错误：未找到 WAR 包，部署终止"
    exit 1
fi

cp "${WAR_FILE}" "${TOMCAT_HOME}/webapps/"
if [ $? -ne 0 ]; then
    echo "错误：WAR 包复制失败，部署终止"
    exit 1
fi
echo "已复制: $(basename ${WAR_FILE}) -> ${TOMCAT_HOME}/webapps/"

# ---------- 4. 启动 Tomcat ----------
echo "[5/5] 启动 Tomcat..."
if [ -f "${TOMCAT_HOME}/bin/startup.sh" ]; then
    ${TOMCAT_HOME}/bin/startup.sh
    if [ $? -ne 0 ]; then
        echo "错误：Tomcat 启动失败"
        exit 1
    fi
else
    echo "错误：未找到 Tomcat startup 脚本: ${TOMCAT_HOME}/bin/startup.sh"
    exit 1
fi

# ---------- 5. 输出部署状态 ----------
echo ""
echo "=========================================="
echo " 部署完成"
echo "=========================================="
echo " 项目目录 : ${PROJECT_DIR}"
echo " WAR 包   : $(basename ${WAR_FILE})"
echo " Tomcat   : ${TOMCAT_HOME}"
echo " 日志路径 : ${TOMCAT_HOME}/logs/catalina.out"
echo ""
echo " 查看启动日志命令："
echo "   tail -f ${TOMCAT_HOME}/logs/catalina.out"
echo "=========================================="
