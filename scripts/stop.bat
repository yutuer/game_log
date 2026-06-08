@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM stop.bat - Stop game-log-service (Windows)
REM ============================================================

echo ==========================================
echo  Stopping game-log-service (Windows)
echo ==========================================

REM Use actuator shutdown endpoint for graceful shutdown
echo Sending shutdown request to actuator...

curl -X POST http://localhost:8080/actuator/shutdown -s -o nul
if %ERRORLEVEL% equ 0 (
    echo Shutdown request sent successfully
    echo Waiting for process to stop...
    timeout /t 5 /nobreak >nul
) else (
    echo Actuator shutdown failed, trying to kill process...
    for /f "tokens=1" %%a in ('jps -l 2^>nul ^| findstr /i "GameLogApplication gamelog"') do (
        echo Stopping PID=%%a...
        taskkill /PID %%a /F >nul 2>&1
    )
)

echo.
echo ==========================================
echo  Done
echo ==========================================