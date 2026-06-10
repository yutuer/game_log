@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM start.bat - Start game-log-service (Windows)
REM
REM Usage: start [mode]
REM   start              - Local dev mode (default, local profile)
REM   start cloud        - Cloud server mode (default profile)
REM   start <profile>    - Custom profile
REM
REM Examples:
REM   start            -> mvn spring-boot:run (local)
REM   start cloud      -> java -jar (cloud, 512m heap)
REM   start prod       -> custom prod profile
REM ============================================================

set "MODE=%~1"
if "%MODE%"=="" set "MODE=local"

set "PROJECT_DIR=%~dp0.."

echo ==========================================
echo  Starting game-log-service (Windows)
echo  Mode: %MODE%
echo ==========================================

cd /d "%PROJECT_DIR%" || (
    echo Error: Cannot access project directory
    pause
    exit /b 1
)

REM Compile
echo Compiling project...
call mvn clean compile -DskipTests
if %ERRORLEVEL% neq 0 (
    echo Error: Maven compile failed
    pause
    exit /b 1
)

echo.

if /i "%MODE%"=="cloud" (
    REM Cloud mode: conservative config, limited memory
    echo ==========================================
    echo  Starting in CLOUD mode
    echo  Profile: (default)
    echo  JVM: -Xmx512m
    echo  URL: http://localhost:8080
    echo  Press Ctrl+C to stop
    echo ==========================================
    echo.
    call mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx512m -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
) else if /i "%MODE%"=="local" (
    REM Local dev mode: high performance
    echo ==========================================
    echo  Starting in LOCAL mode
    echo  Profile: local
    echo  JVM: unlimited heap
    echo  URL: http://localhost:8080
    echo  Press Ctrl+C to stop
    echo ==========================================
    echo.
    setlocal
    REM Set config via env vars (more reliable than -Dspring-boot.run.profiles=)
    set "SPRING_PROFILES_ACTIVE=local"
    set "ASYNC_BATCH_SIZE=3000"
    set "ASYNC_FLUSH_INTERVAL_MS=500"
    set "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10"
    set "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=2"
    call mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
    endlocal
) else (
    REM Custom profile
    echo ==========================================
    echo  Starting with custom profile: %MODE%
    echo  URL: http://localhost:8080
    echo  Press Ctrl+C to stop
    echo ==========================================
    echo.
    setlocal
    set "SPRING_PROFILES_ACTIVE=%MODE%"
    call mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
    endlocal
)

echo.
echo Script finished. Press any key to exit.
pause >nul
