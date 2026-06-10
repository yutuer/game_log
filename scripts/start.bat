@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM start.bat - 本地启动 game-log-service (Windows)
REM
REM  启动模式（通过第一个参数指定）：
REM    start              - 本地开发模式（默认，使用 local profile）
REM    start cloud        - 云服务器模式（使用默认 profile，保守配置）
REM    start <profile>    - 指定任意 profile
REM
REM  示例：
REM    start               → mvn spring-boot:run (local profile)
REM    start cloud         → java -jar (cloud mode, 省内存)
REM    start prod          → 自定义 prod profile
REM ============================================================

set "MODE=%~1"
if "%MODE%"=="" set "MODE=local"

REM JVM 编码参数（解决 Windows console 乱码）
set "JVM_ARGS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

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

REM 编译
echo Compiling project...
call mvn clean compile -DskipTests
if %ERRORLEVEL% neq 0 (
    echo Error: Maven compile failed
    pause
    exit /b 1
)

echo.

if /i "%MODE%"=="cloud" (
    REM 云服务器模式：保守配置，限制内存
    echo ==========================================
    echo  Starting in CLOUD mode
    echo  Profile: (default / none)
    echo  JVM: -Xmx512m (limited)
    echo  URL: http://localhost:8080
    echo  Press Ctrl+C to stop
    echo ==========================================
    echo.
    call mvn spring-boot:run ^
        -Dspring-boot.run.jvmArguments="-Xmx512m %JVM_ARGS%"
) else if /i "%MODE%"=="local" (
    REM 本地开发模式：高性能配置
    echo ==========================================
    echo  Starting in LOCAL mode
    echo  Profile: local
    echo  JVM: unlimited heap
    echo  URL: http://localhost:8080
    echo  Press Ctrl+C to stop
    echo ==========================================
    echo.
    call mvn spring-boot:run ^
        -Dspring-boot.run.profiles=local ^
        -Dspring-boot.run.jvmArguments="%JVM_ARGS%"
) else (
    REM 自定义 profile
    echo ==========================================
    echo  Starting with custom profile: %MODE%
    echo  URL: http://localhost:8080
    echo  Press Ctrl+C to stop
    echo ==========================================
    echo.
    call mvn spring-boot:run ^
        -Dspring-boot.run.profiles=%MODE% ^
        -Dspring-boot.run.jvmArguments="%JVM_ARGS%"
)
