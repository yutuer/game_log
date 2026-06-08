@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM start.bat - 本地启动 game-log-service (Windows)
REM ============================================================

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