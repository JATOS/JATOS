#!/bin/bash
# JATOS loader for Linux and MacOS X

# Get JATOS' path
dir="$( cd "$( dirname "$0" )" && pwd )"
pidfile="$dir/RUNNING_PID"
args=("${@:2}")

# shellcheck disable=SC2120
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

    echo "Starting JATOS... "

    # Generate an application secret
    secret="$(LC_ALL=C tr -cd '[:alnum:]' < /dev/urandom 2>/dev/null | dd bs=64 count=1 2>/dev/null)"

    if [[ ! -f "$dir/bin/jatos" ]]; then
        echo -e "\n$dir/bin/jatos doesn't exist!"
        exit 1
    fi

    # In case './bin/jatos' isn't executable set the x bit
    chmod u+x "$dir/bin/jatos"

    # Create log directory if not exist
    mkdir -p "$dir/logs"

    # Add config file, either jatos.conf or production.conf (jatos.conf has precedence)
    [[ -e "$dir/conf/production.conf" ]] && args+=(-Dconfig.file="$dir/conf/production.conf")
    [[ -e "$dir/conf/jatos.conf" ]] && args+=(-Dconfig.file="$dir/conf/jatos.conf")

    # Hide Guice warning (illegal reflective access)
    args+=(-J--add-opens=java.base/java.lang=ALL-UNNAMED)

    # Start JATOS with configuration file, application secret and pass on other arguments
    env GENERATED_SECRET="$secret" "$dir/bin/jatos" "${args[@]}" -J-server 2> >(tee -a "$dir/logs/loader.log")

    # Let Docker not exit in case of update restart: sleep infinity
    sleep infinity
}

function update() {
    updateLog=${dir}/logs/update.log

    # Wait max 10s for JATOS to be fully stopped
    while [[ -f "$pidfile" ]] && kill -0 $(cat "$pidfile") 2>&1 >/dev/null; do
        if [[ $i -gt 10 ]]; then
            echo "`date` JATOS didn't shut down. Cannot update. Exit." 2>&1 | tee "${updateLog}"
            exit 1
        fi
        sleep 1
        let "i++"
    done

    # Check that we have exactly one update folder
    updateDirsCount=$(find ${dir} -maxdepth 1 -type d -name "update-*" | wc -l)
    if [[ $updateDirsCount -lt 1 ]]; then
        echo "`date` No update folder found. Start JATOS without update." 2>&1 | tee "$updateLog"
        args+=('-DJATOS_UPDATE_MSG="update_folder_not_found"')
        return
    fi
    if [[ $updateDirsCount -gt 1 ]]; then
        echo "`date` There is more than one folder with a JATOS update. Remove the undesirable ones and start JATOS again. Start JATOS without update." | tee $updateLog
        args+=('-DJATOS_UPDATE_MSG="more_than_one_update_folder"')
        return
    fi
    updateDir=(${dir}/update-*)
    echo "`date` Start update of JATOS from folder ${updateDir}." 2>&1 | tee "$updateLog"

    if [[ -d "${dir}/jre" ]] && [[ -d "${updateDir}/jre" ]]; then
      echo "`date` Remove old Java version" 2>&1 | tee "$updateLog"
      rm -rf ${dir}/jre
    fi

    # Backup conf/production.conf or conf/jatos.conf -> jatos.bkp
    [[ -e ${dir}/conf/production.conf ]] && mv -f ${dir}/conf/production.conf ${dir}/conf/jatos.bkp
    [[ -e ${dir}/conf/jatos.conf ]] && mv -f ${dir}/conf/jatos.conf ${dir}/conf/jatos.bkp

    # Move everything from the update folder into the current JATOS folder
    cp -a -v ${updateDir}/* ${dir} >> "$updateLog"

    # Remove update dir
    rm -rf ${updateDir}

    # Compare new and old jatos.conf and recover it
    if cmp -s ${dir}/conf/jatos.conf ${dir}/conf/jatos.bkp; then
        echo "`date` New and old conf/jatos.conf are identical." 2>&1 | tee -a "$updateLog"
        rm ${dir}/conf/jatos.bkp
    else
        mv ${dir}/conf/jatos.conf ${dir}/conf/jatos.new
        mv ${dir}/conf/jatos.bkp ${dir}/conf/jatos.conf
        echo "`date` Recovered old conf/jatos.conf but there is a newer version stored in conf/jatos.new." 2>&1 | tee -a "$updateLog"
    fi

    # Remove unused production.new from earlier updates
    [[ -e ${dir}/conf/production.new ]] && rm ${dir}/conf/production.new

    echo "`date` Update successfully finished." 2>&1 | tee -a "$updateLog"
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
        # This JATOS was not running
        if [[ -f "$pidfile" ]]; then
            # Removing old RUNNING_PID file
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
