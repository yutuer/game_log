@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM stop.bat - Stop game-log-service (Windows)
REM ============================================================

echo ==========================================
echo  Stopping game-log-service (Windows)
echo ==========================================

echo Finding process...

for /f "tokens=1,5" %%a in ('jps -l 2^>nul ^| findstr /i "GameLogApplication game-log-service"') do (
    set "PID=%%a"
    set "CLASS=%%b"
)

if not defined PID (
    echo Process not found
    echo If started via mvn spring-boot:run, press Ctrl+C in the running window
    pause
    exit /b 0
)

echo Found: PID=%PID%  Class=%CLASS%
echo Stopping PID=%PID%...

taskkill /PID %PID% /F >nul 2>&1

if %ERRORLEVEL% equ 0 (
    echo Process stopped
) else (
    echo Warning: Failed to stop process, please stop manually in Task Manager
)

echo.
echo ==========================================
echo  Done
echo ==========================================
pause