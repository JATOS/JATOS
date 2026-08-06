#!/bin/bash
# JATOS loader for Linux and MacOS

# Get JATOS' path
dir="$(cd "$(dirname "$0")" && pwd)"

base_args=("${@:2}")

mkdir -p "$dir/logs" || {
    printf 'Could not create log directory: %s\n' "$dir/logs" >&2
    exit 1
}
loaderLog="$dir/logs/loader.log"
updateLog="$dir/logs/update.log"

pidfile="$dir/RUNNING_PID"
jatos_pid=""
update_msg=""
java_major=""

# Exit code of JATOS when it wants to update and restart
UPDATE_RESTART_EXIT_CODE=46

function start() {

      # Create log directory if not exist
      mkdir -p "$dir/logs"

    # Check if JATOS is already running
    if isRunning; then
        die "$loaderLog" "There seems to be a running JATOS."
    fi
    if [[ -f "$pidfile" ]]; then
        log "$loaderLog" both "Removing old RUNNING_PID file"
        rm -f "$pidfile"
    fi

    checkJava

    # Generate an fallback secret. This is only used if jatos.conf/jatos.secret or env var JATOS_SECRET aren't used.
    secret="$(LC_ALL=C tr -cd '[:alnum:]' < /dev/urandom 2>/dev/null | dd bs=64 count=1 2>/dev/null)"

    if [[ -z "$secret" ]]; then
        die "$loaderLog" "Could not generate a fallback application secret."
    fi

    if [[ ! -f "$dir/bin/jatos" ]]; then
        die "$loaderLog" "$dir/bin/jatos doesn't exist!"
    fi

    # In case './bin/jatos' isn't executable set the x bit
    chmod u+x "$dir/bin/jatos" || die "$loaderLog" "Could not make $dir/bin/jatos executable."

    # If an update passed a update message add it to the start args
    local start_args=("${base_args[@]}")
    if [[ -n "$update_msg" ]]; then
        start_args+=("-DJATOS_UPDATE_MSG=$update_msg")
        update_msg=""
    fi

    # Hide Guice warnings
    if (( java_major >= 24 && java_major <= 25 )); then
        start_args+=("-J--sun-misc-unsafe-memory-access=allow")
    fi

    # Add config file, either jatos.conf or production.conf (jatos.conf has precedence)
    if [[ -f "$dir/conf/jatos.conf" ]]; then
        start_args+=("-Dconfig.file=$dir/conf/jatos.conf")
    elif [[ -f "$dir/conf/production.conf" ]]; then
        start_args+=("-Dconfig.file=$dir/conf/production.conf")
    fi

    # Start JATOS with configuration file, application secret and pass on other arguments
    printf "Starting JATOS... "
    env GENERATED_SECRET="$secret" "$dir/bin/jatos" "${start_args[@]}" -J-server 2> >(tee -a "$loaderLog") &
    jatos_pid=$!

    wait "$jatos_pid"
    local exit_code=$?

    jatos_pid=""
    return "$exit_code"
}

function update() {
    local i=0
    # Wait max 10s for JATOS to be fully stopped
    while isRunning; do
        if [[ $i -gt 10 ]]; then
            die "$updateLog" "JATOS didn't shut down. Cannot update. Exit."
        fi
        sleep 1
        ((i++))
    done

    local -a update_dirs=()
    local candidate

    shopt -s nullglob

    for candidate in "$dir"/update-*; do
        if [[ -d "$candidate" ]]; then
            update_dirs+=("$candidate")
        fi
    done

    shopt -u nullglob

    # Check that we have exactly one update folder
    case "${#update_dirs[@]}" in
        0)
            log "$updateLog" both \
                "No update folder found. Start JATOS without update."
            update_msg="update_folder_not_found"
            return
            ;;
        1)
            ;;
        *)
            log "$updateLog" both \
                "There is more than one folder with a JATOS update. Remove the undesirable ones and start JATOS again. Start JATOS without update."
            update_msg="more_than_one_update_folder"
            return
            ;;
    esac

    local updateDir="${update_dirs[0]}"

    log "$updateLog" both "Start update of JATOS from folder ${updateDir}."

    # If the update folder has Java bundled, remove the current Java
    if [[ -d "${dir}/jre" ]] && [[ -d "${updateDir}/jre" ]]; then
      log "$updateLog" both "New Java found. Remove old Java."
      rm -rf "${dir}/jre"
    fi

    # Backup conf/production.conf or conf/jatos.conf -> jatos.bkp
    if [[ -f "${dir}/conf/jatos.conf" ]]; then
        mv -f "${dir}/conf/jatos.conf" "${dir}/conf/jatos.bkp" || die "$updateLog" "Could not back up conf/jatos.conf."
    elif [[ -f "${dir}/conf/production.conf" ]]; then
        mv -f "${dir}/conf/production.conf" "${dir}/conf/jatos.bkp" || die "$updateLog" "Could not back up conf/production.conf."
    fi

    # Move everything from the update folder into the current JATOS folder
    cp -a -v "$updateDir/." "$dir/" >> "$updateLog" || die "$updateLog" "Could not copy update files."

    # Remove update dir
    rm -rf -- "${updateDir}"

    # Compare new and old jatos.conf and recover it
    if cmp -s "${dir}/conf/jatos.conf" "${dir}/conf/jatos.bkp"; then
        log "$updateLog" both "New and old conf/jatos.conf are identical."
        rm "${dir}/conf/jatos.bkp"
    else
        mv "${dir}/conf/jatos.conf" "${dir}/conf/jatos.new"  || die "$updateLog" "Could not move the configuration."
        mv "${dir}/conf/jatos.bkp" "${dir}/conf/jatos.conf" || die "$updateLog" "Could not restore the configuration."
        log "$updateLog" both "Recovered old conf/jatos.conf but there is a newer version stored in conf/jatos.new."
    fi

    # Remove unused production.new from earlier updates
    [[ -f "${dir}/conf/production.new" ]] && rm "${dir}/conf/production.new"

    log "$updateLog" both "Update successfully finished."
    update_msg="success"
}

function isRunning() {
    local pid

    [[ -f "$pidfile" ]] || return 1
    pid="$(cat "$pidfile")"

    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    kill -0 "$pid" >/dev/null 2>&1
}

log() {
    local log_file="$1"
    local destination="$2"
    shift 2

    local msg="$*"
    local msgWithDate
    msgWithDate="$(date) $msg"

    case "$destination" in
        file)
            printf '%s\n' "$msgWithDate" >> "$log_file"
            ;;
        stdout)
            printf '%s\n' "$msg"
            ;;
        stderr)
            printf '%s\n' "$msg" >&2
            ;;
        both)
            printf '%s\n' "$msg"
            printf '%s\n' "$msgWithDate" >> "$log_file"
            ;;
        both-stderr)
            printf '%s\n' "$msg" >&2
            printf '%s\n' "$msgWithDate" >> "$log_file"
            ;;
        *)
            printf 'Invalid log destination: %s\n' "$destination" >&2
            return 2
            ;;
    esac
}

die() {
    local log_file="$1"
    shift

    log "$log_file" both-stderr "$*"
    exit 1
}

function checkJava() {
    local os arch jre_path="" java_cmd
    local java_version_output java_version
    local min_java_version=21

    os="$(uname -s)"
    arch="$(uname -m)"

    case "$os:$arch" in
        Linux:x86_64|Linux:amd64)
            jre_path="$dir/jre/linux_x64_jre"
            ;;
        Darwin:arm64|Darwin:aarch64)
            jre_path="$dir/jre/mac_aarch64_jre"
            ;;
        Darwin:x86_64|Darwin:amd64)
            jre_path="$dir/jre/mac_x64_jre"
            ;;
    esac

    if [[ -n "$jre_path" && -f "$jre_path/bin/java" ]]; then
        chmod u+x "$jre_path/bin/java" || die "$loaderLog" "Could not make bundled Java executable."
        export JAVA_HOME="$jre_path"
        export PATH="$JAVA_HOME/bin:$PATH"
        log "$loaderLog" both "JATOS uses bundled Java"
        java_cmd="$JAVA_HOME/bin/java"
    elif [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        log "$loaderLog" both "JATOS uses Java from JAVA_HOME"
        java_cmd="$JAVA_HOME/bin/java"
    elif command -v java >/dev/null 2>&1; then
        log "$loaderLog" both "JATOS uses Java from PATH"
        java_cmd="$(command -v java)"
    else
        die "$loaderLog" "Java was not found. Install Java $min_java_version or newer, or provide a bundled JRE."
    fi

    java_version_output="$("$java_cmd" -version 2>&1)" || die "$loaderLog" "Could not execute Java at $java_cmd."
    # Extracts versions such as 21.0.8, 25.0.1, or old-style 1.8.0_452.
    java_version="$(
        printf '%s\n' "$java_version_output" |
            sed -nE 's/.*version "([^"]+)".*/\1/p' |
            head -n 1
    )"
    if [[ -z "$java_version" ]]; then
        die "$loaderLog" "Could not determine the Java version: $java_version_output"
    fi

    if [[ "$java_version" == 1.* ]]; then
        java_major="${java_version#1.}"
        java_major="${java_major%%.*}"
    else
        java_major="${java_version%%.*}"
    fi

    if [[ ! "$java_major" =~ ^[0-9]+$ ]]; then
        die "$loaderLog" "Could not parse Java version '$java_version'."
    fi

    if (( java_major < min_java_version )); then
        die "$loaderLog" "JATOS requires Java $min_java_version or newer. Found Java $java_version at $java_cmd."
    fi

    log "$loaderLog" both "Using Java $java_version from $java_cmd"
}

terminate() {
    local received_signal="$1"
    local exit_code

    trap - INT TERM

    printf 'Stopping JATOS... '

    if [[ -n "${jatos_pid:-}" ]] &&
       kill -0 "$jatos_pid" 2>/dev/null; then
        # Always use TERM even if INT was received
        kill -TERM "$jatos_pid" 2>/dev/null || true
        wait "$jatos_pid" 2>/dev/null || true
    fi

    case "$received_signal" in
        INT)  exit_code=130 ;;
        TERM) exit_code=143 ;;
        *)    exit_code=1 ;;
    esac

    exit "$exit_code"
}

# Start JATOS and keep control of the process so the loader can finish
# an update when JATOS exits with the update-restart exit code.
function supervise() {
    local exit_code

    while true; do
        start
        exit_code=$?

        if [[ "$exit_code" -ne "$UPDATE_RESTART_EXIT_CODE" ]]; then
            return "$exit_code"
        fi

        update
    done
}

# Necessary to stop with ctrl-c running in Docker
trap 'terminate INT' INT
trap 'terminate TERM' TERM

function stop() {
    # Check if JATOS is running
    if ! isRunning; then
        # This JATOS was not running
        if [[ -f "$pidfile" ]]; then
            # Removing old RUNNING_PID file
            rm -f "$pidfile"
        fi
        exit 1
    fi

    # Kill JATOS
    local pid
    pid=$(cat "$pidfile")
    kill -SIGTERM "$pid"

    local i=0
    # Wait max 10s for JATOS to be fully stopped
    for ((i = 0; i < 10; i++)); do
        if ! kill -0 "$pid" >/dev/null 2>&1; then
            rm -f "$pidfile"
            return
        fi
        sleep 1
    done

    die "$loaderLog" "Could not stop JATOS."
}

case "$1" in
    start)
        supervise
        exit $?
        ;;
    update)
        # Backward compatibility with older JATOS versions:
        # old JATOS starts the new loader directly with "loader.sh update".
        update
        supervise
        exit $?
        ;;
    stop)
        stop
        ;;
    restart)
        stop
        supervise
        exit $?
        ;;
    *)
        printf '%s\n' "Usage: loader.sh start|stop|restart"
        exit 1
        ;;
esac