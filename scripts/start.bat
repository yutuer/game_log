@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM start.bat - 本地启动 game-log-service（Windows 开发环境）
REM 用途：编译项目并使用 SpringBoot 内嵌 Tomcat 启动
REM 使用方式：双击运行 或 scripts\start.bat
REM ============================================================

setlocal

REM ---------- 变量定义 ----------
set "PROJECT_DIR=%~dp0.."
set "WAR_NAME=game-log-service"

echo ==========================================
echo  开始启动 %WAR_NAME% (Windows 本地模式)
echo ==========================================

REM ---------- 1. 进入项目目录 ----------
echo [1/3] 进入项目目录: %PROJECT_DIR%
cd /d "%PROJECT_DIR%" || (
    echo 错误：无法进入项目目录 %PROJECT_DIR%
    pause
    exit /b 1
)

REM ---------- 2. 编译项目 ----------
echo [2/3] 执行 Maven 编译...
call mvn compile -DskipTests
if %ERRORLEVEL% neq 0 (
    echo 错误：Maven 编译失败
    pause
    exit /b 1
)

REM ---------- 3. 启动 SpringBoot ----------
echo [3/3] 启动 SpringBoot 应用 (dev profile)...
echo.
echo ==========================================
echo  应用启动中...
echo  访问地址: http://localhost:8080
echo  按 Ctrl+C 停止应用
echo ==========================================
echo.

call mvn spring-boot:run -Dspring-boot.run.profiles=dev

endlocal
pause
