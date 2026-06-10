@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set SERVER_IP=124.70.32.184
set SERVER_PORT=8080
set URL=http://%SERVER_IP%:%SERVER_PORT%/api/game-logs

echo ========================================
echo   Game Log 远程压测脚本 (本地 → 云服务器)
echo ========================================
echo   目标服务器: %SERVER_IP%:%SERVER_PORT%
echo   默认 TPS: 300 (云模式)
echo ========================================
echo.

:: 先编译
echo [1/2] 编译 StressTest...
call mvn compile -q -f "%~dp0..\pom.xml"
if %ERRORLEVEL% neq 0 (
    echo 编译失败，请检查代码错误
    pause
    exit /b 1
)

echo [2/2] 启动压测...
echo.

mvn exec:java -q -f "%~dp0..\pom.xml" ^
    -Dexec.mainClass="com.gamelog.StressTest" ^
    -Dexec.args="--url=%URL% --cloud %*"

echo.
echo 压测结束
pause
