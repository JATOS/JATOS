#!/bin/bash
# JATOS loader for Linux and MacOS X

# IP address and port (DEPRECATED - use 'conf/production.conf' instead)
#address="1.2.3.4"
#port="80"

# Don't change after here - unless you know what you're doing
###########################################################

# Get JATOS' path
dir="$( cd "$( dirname "$0" )" && pwd )"
pidfile="$dir/RUNNING_PID"
args=("${@:2}")

function start() {

    # Check if JATOS is already running
    if [[ -f "$pidfile" ]] && kill -0 $(cat "$pidfile") 2>&1 >/dev/null; then
        echo "There seems to be a running JATOS."
        exit 1
    fi
    if [[ -f "$pidfile" ]]; then
        echo "Removing old RUNNING_PID file"
        rm -f $pidfile
    fi

    checkJava

    echo -n "Starting JATOS... "

    # Generate application secret for the Play framework
    secret="$(LC_ALL=C tr -cd '[:alnum:]' < /dev/urandom 2>/dev/null | dd bs=64 count=1 2>/dev/null)"

    if [[ ! -f "$dir/bin/jatos" ]]; then
        echo -e "\n$dir/bin/jatos doesn't exist!"
        exit 1
    fi

    # In case './bin/jatos' isn't executable set the x bit
    chmod u+x "$dir/bin/jatos"

    # Create log directory if not exist
    mkdir -p "$dir/logs"

    # Add address and port to arguments if set
    [[ -z ${address+x} ]] || args+=(-Dhttp.address=$address)
    [[ -z ${port+x} ]] || args+=(-Dhttp.port=$port)

    args+=(-Dconfig.file="$dir/conf/production.conf")
    args+=(-Dplay.http.secret.key=$secret)

    # Start JATOS with configuration file, application secret, address, port, and pass on other arguments
    "$dir/bin/jatos" "${args[@]}" -J-server 2>>"$dir/logs/loader.log"

    # Let Docker not exit in case of update restart: sleep infinity
    sleep infinity
}

function update() {
    updateLog=${dir}/logs/update.log

    # Wait max 10s for JATOS to be fully stopped
    while [[ -f "$pidfile" ]] && kill -0 $(cat "$pidfile") 2>&1 >/dev/null; do
        if [[ $i -gt 10 ]]; then
            echo "`date` JATOS didn't shut down. Cannot update. Exit." | tee ${updateLog}
            exit 1
        fi
        sleep 1
        let "i++"
    done

    # Check that we have exactly one update folder
    updateDirsCount=$(find ${dir} -maxdepth 1 -type d -name "update-*" | wc -l)
    if [[ $updateDirsCount -lt 1 ]]; then
        echo "`date` No update folder found. Start JATOS without update." | tee $updateLog
        args+=('-DJATOS_UPDATE_MSG="update_folder_not_found"')
        return
    fi
    if [[ $updateDirsCount -gt 1 ]]; then
        echo "`date` There is more than one folder with a JATOS update. Remove the undesirable ones and start JATOS again. Start JATOS without update." | tee $updateLog
        args+=('-DJATOS_UPDATE_MSG="more_than_one_update_folder"')
        return
    fi
    updateDir=(${dir}/update-*)
    echo "`date` Start update of JATOS from folder ${updateDir}." | tee $updateLog

    if [[ -d "${dir}/jre" ]] && [[ -d "${updateDir}/jre" ]]; then
      echo "`date` Remove old Java version" | tee $updateLog
      rm -rf ${dir}/jre
    fi

    # Backup conf/production.conf
    mv -f ${dir}/conf/production.conf ${dir}/conf/production.bkp

    # Move everything from the update folder into the current JATOS folder
    cp -a -v ${updateDir}/* ${dir} >> $updateLog

    # Remove update dir
    rm -rf ${updateDir}

    # Compare new and old production.conf and recover it
    if cmp -s ${dir}/conf/production.conf ${dir}/conf/production.bkp; then
        echo "`date` New and old conf/production.conf are identical." | tee -a $updateLog
        rm ${dir}/conf/production.bkp
    else
        mv ${dir}/conf/production.conf ${dir}/conf/production.new
        mv ${dir}/conf/production.bkp ${dir}/conf/production.conf
        echo "`date` Recovered old conf/production.conf but there is a newer version stored in conf/production.new." | tee -a $updateLog
    fi

    echo "`date` Update successfully finished." | tee -a $updateLog
    args+=('-DJATOS_UPDATE_MSG=success')
}

function checkJava() {
    # Check local Java
    if [[ -n "$dir/jre/linux_x64_jre" ]] && [[ -e "$dir/jre/linux_x64_jre/bin/java" ]]; then
        echo "JATOS uses local Java"
        chmod u+x "$dir/jre/linux_x64_jre/bin/java" # Packing might remove execution permission
        export JAVA_HOME="$dir/jre/linux_x64_jre"
        return
    elif [[ -n "$dir/jre/mac_x64_jre" ]] && [[ -e "$dir/jre/mac_x64_jre/bin/java" ]]; then
        echo "JATOS uses local Java"
        chmod u+x "$dir/jre/mac_x64_jre/bin/java" # Packing might remove execution permission
        export JAVA_HOME="$dir/jre/mac_x64_jre"
        return
    fi
}

# Necessary to stop with ctrl-c running in Docker
trap exit INT

function stop() {
    # Check if JATOS is running
    if [[ ! -f "$pidfile" ]] || ! kill -0 $(cat "$pidfile") 2>&1 >/dev/null; then
        echo "This JATOS was not running"
        if [[ -f "$pidfile" ]]; then
            echo "Removing old RUNNING_PID file"
            rm -f $pidfile
        fi
        exit 1
    fi

    # Kill JATOS
    local pid=$(cat "$pidfile")
    kill -SIGTERM $pid
    wait $pid 2>/dev/null # suppress output of kill

    # Wait max 10s for JATOS to be fully stopped
    while $(kill -0 $pid 2>/dev/null); do
        if [[ $i -gt 10 ]]; then
            echo "`date` Could not stop JATOS. Exit."
            exit 1
        fi
        sleep 1
        let "i++"
    done
}

case "$1" in
    start)
        start
        ;;
    update)
        # 'update' is supposed to be run from within JATOS
        update
        start
        ;;
    stop)
        stop
        ;;
    restart)
        stop
        start
        ;;
    *)
        echo "Usage: loader.sh start|stop|restart"
        exit 1
        ;;
esac
