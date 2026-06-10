@echo off
chcp 65001 >nul 2>&1

set "MODE=%~1"
if "%MODE%"=="" set "MODE=local"

set "PROJECT_DIR=%~dp0.."

echo ==========================================
echo  Starting game-log-service (Windows)
echo  Mode: [%MODE%]
echo ==========================================

cd /d "%PROJECT_DIR%" || (
    echo Error: Cannot access project directory
    pause
    exit /b 1
)

REM Build
echo Building project...
call mvn clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo Error: Maven build FAILED
    pause
    exit /b 1
)
echo Build OK.
echo.

set "JAR_PATH=%PROJECT_DIR%\target\game-log-service-1.0.0.war"
if not exist "%JAR_PATH%" (
    echo Error: WAR not found at %JAR_PATH%
    pause
    exit /b 1
)

REM Route to correct mode
if /i "%MODE%"=="cloud" goto CLOUD_MODE
if /i "%MODE%"=="local" goto LOCAL_MODE
goto CUSTOM_MODE

:CLOUD_MODE
echo ==========================================
echo  Starting in CLOUD mode
echo  Profile: (default)
echo  JVM: -Xmx512m
echo  URL: http://localhost:8080
echo  Press Ctrl+C to stop
echo ==========================================
echo.
java "-Dfile.encoding=UTF-8" "-Dconsole.encoding=UTF-8" "-Dsun.stdout.encoding=UTF-8" "-Dsun.stderr.encoding=UTF-8" -Xmx512m -jar "%JAR_PATH%"
goto END

:LOCAL_MODE
echo ==========================================
echo  Starting in LOCAL mode
echo  Profile: local
echo  JVM: unlimited heap
echo  URL: http://localhost:8080
echo  Press Ctrl+C to stop
echo ==========================================
echo.
java "-Dspring.profiles.active=local" "-Dfile.encoding=UTF-8" "-Dconsole.encoding=UTF-8" "-Dsun.stdout.encoding=UTF-8" "-Dsun.stderr.encoding=UTF-8" -jar "%JAR_PATH%"
goto END

:CUSTOM_MODE
echo ==========================================
echo  Starting with custom profile: %MODE%
echo  URL: http://localhost:8080
echo  Press Ctrl+C to stop
echo ==========================================
echo.
java "-Dspring.profiles.active=%MODE%" "-Dfile.encoding=UTF-8" "-Dconsole.encoding=UTF-8" "-Dsun.stdout.encoding=UTF-8" "-Dsun.stderr.encoding=UTF-8" -jar "%JAR_PATH%"
goto END

:END
echo.
echo Java exited with code %ERRORLEVEL%
pause
