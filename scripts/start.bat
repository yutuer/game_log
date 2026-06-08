@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM start.bat - 本地启动 game-log-service (Windows)
REM ============================================================

REM 强制 JVM 使用 UTF-8 编码（解决 Windows console 乱码问题）
set "JVM_ARGS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

set "PROJECT_DIR=%~dp0.."

echo ==========================================
echo  Starting game-log-service (Windows)
echo ==========================================

cd /d "%PROJECT_DIR%" || (
    echo Error: Cannot access project directory
    pause
    exit /b 1
)

echo Compiling project...
call mvn clean compile -DskipTests
if %ERRORLEVEL% neq 0 (
    echo Error: Maven compile failed
    pause
    exit /b 1
)

echo.
echo ==========================================
echo  Starting SpringBoot (dev profile)...
echo  URL: http://localhost:8080
echo  Press Ctrl+C to stop
echo ==========================================
echo.

call mvn spring-boot:run -Dspring-boot.run.profiles=dev