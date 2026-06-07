#!/bin/bash
# ============================================================
# stop.sh - 停止 Tomcat 服务
# 用途：在阿里云 Linux 服务器上，优雅关闭 Tomcat，若失败则强制终止
# 使用方式：bash scripts/stop.sh 或 ./scripts/stop.sh
# ============================================================

# ---------- 变量定义 ----------
TOMCAT_HOME="/usr/local/tomcat"

echo "=========================================="
echo " 停止 Tomcat"
echo "=========================================="

# ---------- 1. 检查 Tomcat 是否在运行 ----------
TOMCAT_PID=$(ps -ef | grep "${TOMCAT_HOME}" | grep -v grep | awk '{print $2}')

if [ -z "${TOMCAT_PID}" ]; then
    echo "Tomcat 当前未运行，无需停止"
    echo "=========================================="
    exit 0
fi

echo "检测到 Tomcat 进程，PID: ${TOMCAT_PID}"

# ---------- 2. 优雅关闭 ----------
echo "[1/2] 执行优雅关闭（shutdown.sh）..."
if [ -f "${TOMCAT_HOME}/bin/shutdown.sh" ]; then
    ${TOMCAT_HOME}/bin/shutdown.sh
else
    echo "警告：未找到 ${TOMCAT_HOME}/bin/shutdown.sh，将直接强制终止"
fi

# ---------- 3. 等待并检查关闭结果 ----------
echo "[2/2] 等待 5 秒检查关闭状态..."
sleep 5

TOMCAT_PID=$(ps -ef | grep "${TOMCAT_HOME}" | grep -v grep | awk '{print $2}')

if [ -n "${TOMCAT_PID}" ]; then
    echo "Tomcat 未能优雅关闭（PID: ${TOMCAT_PID}），执行强制终止..."
    kill -9 ${TOMCAT_PID}

    sleep 1
    TOMCAT_PID=$(ps -ef | grep "${TOMCAT_HOME}" | grep -v grep | awk '{print $2}')
    if [ -n "${TOMCAT_PID}" ]; then
        echo "错误：强制终止失败，Tomcat 仍在运行（PID: ${TOMCAT_PID}）"
        echo "=========================================="
        exit 1
    fi

    echo "Tomcat 已被强制终止"
else
    echo "Tomcat 已优雅关闭"
fi

# ---------- 4. 输出关闭状态 ----------
echo ""
echo "=========================================="
echo " Tomcat 已停止"
echo "=========================================="
echo " Tomcat 目录: ${TOMCAT_HOME}"
echo "=========================================="
