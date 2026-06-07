@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM stop.bat - 停止本地运行的 game-log-service（Windows 开发环境）
REM 用途：查找并终止 SpringBoot 应用进程
REM 使用方式：双击运行 或 scripts\stop.bat
REM ============================================================

echo ==========================================
echo  停止 game-log-service (Windows 本地模式)
echo ==========================================

REM ---------- 查找 Java 进程 ----------
echo 正在查找 SpringBoot 应用进程...

REM 查找包含 game-log-service 或 GameLogApplication 的 Java 进程
for /f "tokens=1,5" %%a in ('jps -l 2^>nul ^| findstr /i "GameLogApplication game-log-service"') do (
    set "PID=%%a"
    set "CLASS=%%b"
)

if not defined PID (
    echo 未找到运行中的 game-log-service 进程
    echo.
    echo 提示：如果应用是通过 mvn spring-boot:run 启动的，
    echo       请在启动窗口按 Ctrl+C 停止
    pause
    exit /b 0
)

echo 找到进程: PID=%PID%  Class=%CLASS%

REM ---------- 终止进程 ----------
echo 正在终止进程 PID=%PID%...
taskkill /PID %PID% /F >nul 2>&1

if %ERRORLEVEL% equ 0 (
    echo 进程已终止
) else (
    echo 警告：进程终止失败，请手动在任务管理器中结束
)

echo.
echo ==========================================
echo  停止完成
echo ==========================================
pause
