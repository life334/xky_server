#!/bin/sh
# ./run.sh start 启动 stop 停止 restart 重启 status 状态
AppName="zj-admin.jar"
APP_DIR=$(cd "$(dirname "$0")" && pwd)
JAR_FILE="$APP_DIR/$AppName"
LOG_DIR="$APP_DIR/logs"
PID_FILE="$LOG_DIR/$APP_NAME.pid"
JAVA_HOME="/usr/local/java/jdk-17.0.10+7"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# JVM参数
JVM_OPTS="-Dname=$AppName  \
          -Duser.timezone=Asia/Shanghai \
          -DLOG_PATH=$LOG_DIR \
          -Xms512m -Xmx1024m \
          -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m \
          -XX:+HeapDumpOnOutOfMemoryError \
          -XX:NewRatio=1  \
          -XX:SurvivorRatio=30 \
          -XX:+UseParallelGC"

# 配置文件路径（基于脚本目录，明确指向同级的config文件夹）
CONFIG_YML="$APP_DIR/config/application.yml"
CONFIG_DRUID_YML="$APP_DIR/config/application-druid.yml"
CONFIG_PROD_YML="$APP_DIR/config/application-prod.yml"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

if [ "$1" = "" ];
then
    echo -e "\033[0;31m 未输入操作名 \033[0m  \033[0;34m {start|stop|restart|status} \033[0m"
    exit 1
fi

if [ "$AppName" = "" ];
then
    echo -e "\033[0;31m 未输入应用名 \033[0m"
    exit 1
fi

function check_config() {
    # 检查application.yml
    if [ ! -f "$CONFIG_YML" ]; then
        echo -e "\033[0;31m 错误：配置文件不存在！路径：$CONFIG_YML \033[0m"
        exit 1
    fi
    # 检查application-druid.yml
    if [ ! -f "$CONFIG_DRUID_YML" ]; then
        echo -e "\033[0;31m 错误：配置文件不存在！路径：$CONFIG_DRUID_YML \033[0m"
        exit 1
    fi
    # 检查application-prod.yml
    if [ ! -f "$CONFIG_PROD_YML" ]; then
        echo -e "\033[0;31m 错误：配置文件不存在！路径：$CONFIG_PROD_YML \033[0m"
        exit 1
    fi
}

function create_log_dirs() {
    if [ ! -d "$LOG_DIR" ]; then
        echo -e "${BLUE}创建日志目录：$LOG_DIR${NC}"
        mkdir -p "$LOG_DIR"
    fi
}

function check_jar() {
    if [ ! -f "$JAR_FILE" ]; then
        echo -e "${RED}错误：JAR文件不存在！${NC}"
        echo -e "路径：${YELLOW}$JAR_FILE${NC}"
        echo -e "请先执行：${BLUE}mvn clean package -DskipTests${NC}"
        exit 1
    fi
}

# 获取进程PID
function get_pid() {
    if [ -f "$PID_FILE" ]; then
        cat "$PID_FILE"
    else
        echo ""
    fi
}

is_running() {
    PID=$(get_pid)
    if [ -n "$PID" ] && ps -p "$PID" > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

function start()
{
    # 先检查配置文件
    check_config
    check_jar
    
#    PID=`ps -ef |grep java|grep $AppName|grep -v grep|awk '{print $2}'`
    if is_running; then
        echo -e "${YELLOW}$APP_NAME 已在运行中 (PID: $(get_pid))${NC}"
        exit 0
    fi

    create_log_dirs

    echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')] 启动 $APP_NAME...${NC}"

    START_LOG="$LOG_DIR/startup_$(date +%Y%m%d_%H%M%S).log"

    nohup java $JVM_OPTS -jar $AppName \
          --spring.config.location="$CONFIG_YML,$CONFIG_DRUID_YML,$CONFIG_PROD_YML" \
          > "$START_LOG" 2>&1 &
    echo $! > "$PID_FILE"
    sleep 1
    if is_running; then
        echo -e "${GREEN}$APP_NAME 启动成功 (PID: $(get_pid))${NC}"
        echo -e "${BLUE}日志目录：$LOG_DIR${NC}"
    else
        echo -e "${RED}$APP_NAME 启动失败！${NC}"
        echo -e "${YELLOW}请查看启动日志：$START_LOG${NC}"
        cat "$START_LOG"
        exit 1
    fi

}

function stop()
{
    echo "Stop $AppName"

    PID=""
    query(){
      PID=`ps -ef |grep java|grep $AppName|grep -v grep|awk '{print $2}'`
    }

    query

    PID=$(get_pid)
    if [ x"$PID" != x"" ]; then
      kill -TERM $PID
      echo "$AppName (pid:$PID) exiting..."
      while [ x"$PID" != x"" ]
      do
        sleep 1
        query
      done
      echo "$AppName exited."
    else
      echo "$AppName already stopped."
    fi
}

function restart()
{
    stop
    sleep 2
    start
}

function status()
{
    PID=`ps -ef |grep java|grep $AppName|grep -v grep|wc -l`
    if [ $PID != 0 ];then
        echo "$AppName is running..."
    else
        echo "$AppName is not running..."
    fi
}

case $1 in
    start)
    start;;
    stop)
    stop;;
    restart)
    restart;;
    status)
    status;;
    *)

esac
