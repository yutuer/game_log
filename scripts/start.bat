@echo off
chcp 65001 >nul 2>&1

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
echo ==========================================

REM Route to correct mode
if /i "%MODE%"=="cloud" goto CLOUD_MODE
if /i "%MODE%"=="local" goto LOCAL_MODE
goto CUSTOM_MODE

:CLOUD_MODE
echo  Starting in CLOUD mode
echo  Profile: (default)
echo  JVM: -Xmx512m
echo  URL: http://localhost:8080
echo  Press Ctrl+C to stop
echo ==========================================
echo.
call mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Xmx512m -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
goto END

:LOCAL_MODE
echo  Starting in LOCAL mode
echo  Profile: local
echo  JVM: unlimited heap
echo  URL: http://localhost:8080
echo  Press Ctrl+C to stop
echo ==========================================
echo.
call mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dspring.profiles.active=local -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
goto END

:CUSTOM_MODE
echo  Starting with custom profile: %MODE%
echo  URL: http://localhost:8080
echo  Press Ctrl+C to stop
echo ==========================================
echo.
call mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dspring.profiles.active=%MODE% -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
goto END

:END
echo.
echo Maven exited. Press any key to exit.
pause >nul
