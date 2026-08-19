D:\xky-project\run.bat
@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set APP_NAME=xky-admin.jar
set APP_DIR=%~dp0
set JAR_FILE=%APP_DIR%%APP_NAME%
set LOG_DIR=%APP_DIR%logs
set PID_FILE=%LOG_DIR%\app.pid

set JAVA_HOME=C:\Program Files\Java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%

set JVM_OPTS=-Dname=%APP_NAME% -Duser.timezone=Asia/Shanghai -DLOG_PATH=%LOG_DIR% -Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:NewRatio=1 -XX:SurvivorRatio=30 -XX:+UseParallelGC

set CONFIG_YML=%APP_DIR%config\application.yml
set CONFIG_DRUID=%APP_DIR%config\application-druid.yml
set CONFIG_PROD=%APP_DIR%config\application-prod.yml

if "%1"=="" (
    echo [错误] 未输入操作名，用法: run.bat {start^|stop^|restart^|status}
    exit /b 1
)

if "%1"=="start"   goto :do_start
if "%1"=="stop"    goto :do_stop
if "%1"=="restart" goto :do_restart
if "%1"=="status"  goto :do_status
echo [错误] 未知操作: %1，用法: run.bat {start^|stop^|restart^|status}
exit /b 1

:check_config
if not exist "%CONFIG_YML%"   (echo [错误] 配置文件不存在: %CONFIG_YML%   & exit /b 1)
if not exist "%CONFIG_DRUID%" (echo [错误] 配置文件不存在: %CONFIG_DRUID% & exit /b 1)
if not exist "%CONFIG_PROD%"  (echo [错误] 配置文件不存在: %CONFIG_PROD%  & exit /b 1)
goto :eof

:check_jar
if not exist "%JAR_FILE%" (
    echo [错误] JAR文件不存在: %JAR_FILE%
    echo 请先执行: mvn clean package -DskipTests
    exit /b 1
)
goto :eof

:create_log_dir
if not exist "%LOG_DIR%" (
    echo [信息] 创建日志目录: %LOG_DIR%
    mkdir "%LOG_DIR%"
)
goto :eof

:get_pid
set FOUND_PID=
if exist "%PID_FILE%" (
    set /p FOUND_PID=<"%PID_FILE%"
)
goto :eof

:is_running
call :get_pid
if not defined FOUND_PID (
    set RUNNING=0
    goto :eof
)
tasklist /FI "PID eq !FOUND_PID!" /NH 2>nul | findstr /I "java" >nul
if !errorlevel!==0 (
    set RUNNING=1
) else (
    set RUNNING=0
)
goto :eof

:do_start
call :check_config
if errorlevel 1 exit /b 1
call :check_jar
if errorlevel 1 exit /b 1

call :is_running
if !RUNNING!==1 (
    call :get_pid
    echo [警告] %APP_NAME% 已在运行中 ^(PID: !FOUND_PID!^)
    exit /b 0
)

call :create_log_dir

for /f "tokens=2 delims==" %%a in ('wmic os get localdatetime /value') do set DT=%%a
set START_LOG=%LOG_DIR%\startup_%DT%.log

echo [信息] [%date% %time%] 启动 %APP_NAME% ...

start "xky-admin" /min java %JVM_OPTS% -jar "%JAR_FILE%" --spring.config.location="%CONFIG_YML%,%CONFIG_DRUID%,%CONFIG_PROD%"

timeout /t 2 /nobreak >nul

for /f "tokens=2" %%p in ('tasklist /FI "IMAGENAME eq java.exe" /FI "WINDOWTITLE eq xky-admin*" /NH 2^>nul ^| findstr /R "[0-9]"') do (
    set NEW_PID=%%p
)

if defined NEW_PID (
    echo !NEW_PID!>"%PID_FILE%"
    echo [成功] %APP_NAME% 启动成功 ^(PID: !NEW_PID!^)
    echo [信息] 日志目录: %LOG_DIR%
) else (
    echo [失败] %APP_NAME% 启动失败！请查看日志目录下的启动日志
)

exit /b 0

:do_stop
echo [信息] 停止 %APP_NAME% ...

call :is_running
if !RUNNING!==0 (
    echo [信息] %APP_NAME% 未在运行
    if exist "%PID_FILE%" del "%PID_FILE%"
    exit /b 0
)

call :get_pid
echo [信息] %APP_NAME% ^(PID: !FOUND_PID!^) 正在停止...
taskkill /PID !FOUND_PID! /T /F >nul 2>&1

set WAIT_COUNT=0
:wait_stop
call :is_running
if !RUNNING!==0 goto :stopped
set /a WAIT_COUNT+=1
if !WAIT_COUNT! geq 30 (
    echo [警告] 等待超时，尝试强制终止
    taskkill /PID !FOUND_PID! /T /F >nul 2>&1
    goto :stopped
)
timeout /t 1 /nobreak >nul
goto :wait_stop

:stopped
if exist "%PID_FILE%" del "%PID_FILE%"
echo [成功] %APP_NAME% 已停止
exit /b 0

:do_restart
call :do_stop
timeout /t 2 /nobreak >nul
call :do_start
exit /b 0

:do_status
call :is_running
if !RUNNING!==1 (
    call :get_pid
    echo [信息] %APP_NAME% 正在运行 ^(PID: !FOUND_PID!^)
) else (
    echo [信息] %APP_NAME% 未在运行
)
exit /b 0