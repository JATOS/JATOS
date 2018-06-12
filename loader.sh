#!/bin/bash
# JATOS loader for Linux and MacOS X

# Change IP address and port here
# Alternatively you can use command-line arguments -Dhttp.address and -Dhttp.port
address="127.0.0.1"
port="9000"

# Don't change after here unless you know what you're doing
###########################################################

# Get JATOS directory
dir="$( cd "$( dirname "$0" )" && pwd )"
pidfile="$dir/RUNNING_PID"
args=${@:2}

function start() {
    getJavaArgs
    checkAlreadyRunning
    checkJava

    echo -n "Starting JATOS"

    # Generate application secret for the Play framework
    secret="$(LC_ALL=C tr -cd '[:alnum:]' < /dev/urandom 2>/dev/null | dd bs=64 count=1 2>/dev/null)"

    if [ ! -f "$dir/bin/jatos" ]
    then
        echo -e "\n$dir/bin/jatos doesn't exist!"
        exit 1
    fi

    # In case './bin/jatos' isn't executable set the x bit
    chmod u+x "$dir/bin/jatos"

    # Start JATOS with configuration file and application secret
    "$dir/bin/jatos" -Dconfig.file="$dir/conf/production.conf" -Dplay.crypto.secret=$secret -Dhttp.port=$port -Dhttp.address=$address -J-server $args > /dev/null &

    echo "...started"
    echo "To use JATOS type $address:$port in your browser's address bar"
}

function stop() {
    if [ ! -f "$pidfile" ] || ! kill -0 $(cat "$pidfile") 2>&1 >/dev/null
    then
        echo "This JATOS was not running"
        exit 1
    fi
    echo -n "Stopping JATOS"
    kill -SIGTERM $(cat "$pidfile")
    rm -f "$pidfile"
    echo "...stopped"
}

function getJavaArgs() {
    for i in $args ; do
        case $i in
        -Dhttp.address=*)
            address=${i#*=}
            ;;
        -Dhttp.port=*)
            port=${i#*=}
            ;;
        esac
    done
}

function checkAlreadyRunning() {
    if [ -f "$pidfile" ] && kill -0 $(cat "$pidfile") 2>&1 >/dev/null
    then
        echo "There seems to be a running JATOS."
        exit 1
    fi
    # Delete old PID file (just to be sure)
    rm -f "$pidfile"

    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null
    then
        echo "Some other program is using port $port already. Maybe another JATOS?"
        exit 1
    fi
}

function checkJava() {
    # Get Java version if installed
    if type -p java
    then
        echo "Found Java"
        java_version=$(java -version 2>&1 | sed 's/.*version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
    elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]]
    then
        echo "Found Java in JAVA_HOME"
        java_version=$($JAVA_HOME/bin/java -version 2>&1 | sed 's/.*version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
    fi

    # If we don't have a version or if the version is not a number or if the version is smaller 18 (Java 8) try to find a local Java installation
    if [[ -z "$java_version" ]] || [[ ! "$java_version" =~ ^[0-9]+$ ]] || [[ "$java_version" -lt 18 ]]
    then
        if [[ -n "$dir/jre/linux_x64_jre" ]] && [[ -x "$dir/jre/linux_x64_jre/bin/java" ]]
        then
            echo "Jatos uses local JRE"
            export JAVA_HOME="$dir/jre/linux_x64_jre"
        elif [[ -n "$dir/jre/mac_x64_jre" ]] && [[ -x "$dir/jre/mac_x64_jre/bin/java" ]]
        then
            echo "Jatos uses local JRE"
            export JAVA_HOME="$dir/jre/mac_x64_jre"
        else
            echo "No Java found"
            exit 1
        fi
    fi
}

case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        stop
        # Check that JATOS' port is free
        while lsof -Pi :$port -sTCP:LISTEN -t >/dev/null
        do
            sleep 1
        done
        start
        ;;
    *)
        echo "Usage: loader.sh start|stop|restart"
        exit 1
        ;;
esac
