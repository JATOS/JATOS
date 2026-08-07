#!/bin/bash
# JATOS loader for Linux and macOS

# Get JATOS' path
dir="$(cd "$(dirname "$0")" && pwd -P)"

base_args=("${@:2}")

log_dir="$dir/logs"
mkdir -p "$log_dir" || {
    printf 'Could not create log directory: %s\n' "$log_dir" >&2
    exit 1
}
loader_log="$log_dir/loader.log"
update_log="$log_dir/update.log"

pid_file="$dir/RUNNING_PID"
jatos_pid=""
update_msg=""
java_major=""

# Exit code of JATOS when it wants to update and restart
UPDATE_RESTART_EXIT_CODE=46

start() {
    local secret exit_code
    local -a start_args=("${base_args[@]}")


    # Check if JATOS is already running
    if isRunning; then
        die "$loader_log" "There seems to be a running JATOS."
    fi

    if [[ -f "$pid_file" ]]; then
        log "$loader_log" both "Removing stale RUNNING_PID file"
        rm -f -- "$pid_file"
    fi

    check_java

    # Generate an fallback secret. This is only used if jatos.conf/jatos.secret or env var JATOS_SECRET aren't used.
    secret="$(LC_ALL=C tr -cd '[:alnum:]' < /dev/urandom 2>/dev/null | dd bs=64 count=1 2>/dev/null)"
    (( ${#secret} == 64 )) ||
            die "$loader_log" "Could not generate a fallback application secret."

    [[ -f "$dir/bin/jatos" ]] ||
            die "$loader_log" "$dir/bin/jatos does not exist."

    chmod u+x "$dir/bin/jatos" ||
            die "$loader_log" "Could not make $dir/bin/jatos executable."

    # If an update passed a update message add it to the start args
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

    printf "Starting JATOS... "

    # Start JATOS with configuration file, application secret and pass on other arguments
    env GENERATED_SECRET="$secret" \
            "$dir/bin/jatos" "${start_args[@]}" -J-server \
            2> >(tee -a "$loader_log" >&2) &

    jatos_pid=$!
    wait "$jatos_pid"
    exit_code=$?
    jatos_pid=""

    return "$exit_code"
}

update() {
    touch "$update_log" || {
        printf 'Could not create update log: %s\n' "$update_log" >&2
        exit 1
    }

    wait_until_stopped || die "$update_log" "JATOS didn't shut down. Cannot update. Exit."

    local -a update_dirs=()
    local candidate

    for candidate in "$dir"/update-*; do
        [[ -d "$candidate" ]] && update_dirs+=("$candidate")
    done

    # Check that we have exactly one update folder
    case "${#update_dirs[@]}" in
            0)
                update_msg="update_folder_not_found"
                fail_update "No update folder found. Start JATOS without update."
                ;;
            1)
                ;;
            *)
                update_msg="more_than_one_update_folder"
                fail_update "There is more than one folder with a JATOS update. Remove the undesirable ones and start JATOS again."
                ;;
    esac

    local update_dir="${update_dirs[0]}"

    validate_update_allowed "$update_dir" || fail_update "Update validation failed."

    log "$update_log" both "Start update of JATOS from folder ${update_dir}."

    # If the update folder has Java bundled, remove the current Java
    local old_jre_backup=""
    if [[ -d "$dir/jre" && -d "$update_dir/jre" ]]; then
        old_jre_backup="$dir/jre.before-update"
        rm -rf -- "$old_jre_backup"
        mv -- "$dir/jre" "$old_jre_backup" ||
            fail_update "Could not temporarily move the old Java runtime."
    fi

    # Backup conf/production.conf or conf/jatos.conf -> jatos.bkp
    if [[ -f "${dir}/conf/jatos.conf" ]]; then
        mv -f -- "${dir}/conf/jatos.conf" "${dir}/conf/jatos.bkp" || die "$update_log" "Could not back up conf/jatos.conf."
    elif [[ -f "${dir}/conf/production.conf" ]]; then
        mv -f -- "${dir}/conf/production.conf" "${dir}/conf/jatos.bkp" || die "$update_log" "Could not back up conf/production.conf."
    fi

    # Move everything from the update folder into the current JATOS folder
    if ! cp -a -- "$update_dir/." "$dir/" >> "$update_log" 2>&1; then
        if [[ -n "$old_jre_backup" && ! -d "$dir/jre" ]]; then
            mv -- "$old_jre_backup" "$dir/jre" || true
        fi
        fail_update "Could not copy update files."
    fi


    # Remove update dir jre backup
    rm -rf -- "$old_jre_backup"
    rm -rf -- "${update_dir}"

    # Compare new and old jatos.conf and recover it. All new releases must contain jatos.conf.
    if cmp -s "${dir}/conf/jatos.conf" "${dir}/conf/jatos.bkp"; then
        log "$update_log" both "New and old conf/jatos.conf are identical."
        rm -- "${dir}/conf/jatos.bkp"
    else
        mv -- "${dir}/conf/jatos.conf" "${dir}/conf/jatos.new"  || die "$update_log" "Could not move the configuration."
        mv -- "${dir}/conf/jatos.bkp" "${dir}/conf/jatos.conf" || die "$update_log" "Could not restore the configuration."
        log "$update_log" both "Recovered old conf/jatos.conf but there is a newer version stored in conf/jatos.new."
    fi

    # Remove unused production.new from earlier updates
    [[ -f "${dir}/conf/production.new" ]] && rm -- "${dir}/conf/production.new"

    log "$update_log" both "Update successfully finished."
    update_msg="success"
}

fail_update() {
    if [[ -z "$update_msg" ]]; then
        update_msg="update_failed"
    fi

    log "$update_log" both-stderr "JATOS update failed: $*"

    restore_loader_from_backup || die "$update_log" "Could not restore old loader.sh. Stop JATOS."

    log "$update_log" both "Restarting JATOS with restored loader.sh."

    # Restart the restored loader.sh
    # shellcheck disable=SC2093
    exec "$dir/loader.sh" start "${base_args[@]}" "-DJATOS_UPDATE_MSG=$update_msg"

    die "$update_log" "Could not execute restored loader.sh. Stop JATOS."
}

restore_loader_from_backup() {
    local -a backup_dirs=()
    local backup_dir
    local old_loader=""

    shopt -s nullglob

    for backup_dir in "$dir"/backup_*; do
        if [[ -f "$backup_dir/loader.sh" ]]; then
            backup_dirs+=("$backup_dir")
        fi
    done

    shopt -u nullglob

    if [[ "${#backup_dirs[@]}" -eq 0 ]]; then
        log "$update_log" both-stderr "Could not restore old loader.sh: no backup loader found."
        return 1
    fi

    old_loader="${backup_dirs[${#backup_dirs[@]} - 1]}/loader.sh"

    cp -f -- "$old_loader" "$dir/loader.sh" || {
        log "$update_log" both-stderr "Could not restore old loader.sh from $old_loader."
        return 1
    }

    chmod u+x "$dir/loader.sh" || {
        log "$update_log" both-stderr "Could not make restored loader.sh executable."
        return 1
    }

    log "$update_log" both "Restored old loader.sh from $old_loader."
}

isRunning() {
    local pid command

    [[ -r "$pid_file" ]] || return 1
    IFS= read -r pid < "$pid_file"

    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    kill -0 "$pid" 2>/dev/null || return 1

    command="$(ps -p "$pid" -o command= 2>/dev/null)" || return 1

    [[ "$command" == *"$dir/bin/jatos"* ]] ||
        [[ "$command" == *java* && "$command" == *"$dir"* ]]
}

wait_until_stopped() {
    local i

    for ((i = 0; i < 10; i++)); do
        if ! isRunning; then
            return 0
        fi
        sleep 1
    done

    return 1
}

validate_update_allowed() {
    local update_dir="$1"
    local current_version
    local update_version

    current_version="$(read_jatos_version "$dir/VERSION")" || {
        log "$update_log" both-stderr "Could not determine the current JATOS version."
        update_msg="invalid_current_version"
        return 1
    }

    update_version="$(read_jatos_version "$update_dir/VERSION")" || {
        log "$update_log" both-stderr "Could not determine the update JATOS version."
        update_msg="invalid_update_version"
        return 1
    }

    if is_version_less_than "$update_version" "$current_version"; then
        log "$update_log" both-stderr \
            "Cannot update from JATOS $current_version to JATOS $update_version: downgrading is not allowed."
        update_msg="downgrade_not_allowed"
        return 1
    fi
}

read_jatos_version() {
    local version_file="$1"
    local version

    [[ -f "$version_file" ]] || return 1

    version="$(tr -d '[:space:]' < "$version_file")"
    version="${version#v}"
    version="${version%%[-+]*}"

    [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || return 1

    printf '%s\n' "$version"
}

version_key() {
    local version="$1"
    local -a parts

    IFS=. read -r -a parts <<< "$version"

    printf '%03d%03d%03d\n' "$((10#${parts[0]}))" "$((10#${parts[1]}))" "$((10#${parts[2]}))"
}

is_version_less_than() {
    [[ "$(version_key "$1")" < "$(version_key "$2")" ]]
}

log() {
    local log_file="$1"
    local destination="$2"
    shift 2

    local msg="$*"
    local msg_with_date
    msg_with_date="$(date) $msg"

    case "$destination" in
        file)
            printf '%s\n' "$msg_with_date" >> "$log_file"
            ;;
        stdout)
            printf '%s\n' "$msg"
            ;;
        stderr)
            printf '%s\n' "$msg" >&2
            ;;
        both)
            printf '%s\n' "$msg"
            printf '%s\n' "$msg_with_date" >> "$log_file"
            ;;
        both-stderr)
            printf '%s\n' "$msg" >&2
            printf '%s\n' "$msg_with_date" >> "$log_file"
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

check_java() {
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
        chmod u+x "$jre_path/bin/java" || die "$loader_log" "Could not make bundled Java executable."
        export JAVA_HOME="$jre_path"
        log "$loader_log" both "JATOS uses bundled Java"
        java_cmd="$JAVA_HOME/bin/java"
    elif [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        log "$loader_log" both "JATOS uses Java from JAVA_HOME"
        java_cmd="$JAVA_HOME/bin/java"
    elif command -v java >/dev/null 2>&1; then
        log "$loader_log" both "JATOS uses Java from PATH"
        java_cmd="$(command -v java)"
    else
        die "$loader_log" "Java was not found. Install Java $min_java_version or newer, or provide a bundled JRE."
    fi

    java_version_output="$("$java_cmd" -version 2>&1)" || die "$loader_log" "Could not execute Java at $java_cmd."
    # Extracts versions such as 21.0.8, 25.0.1, or old-style 1.8.0_452.
    java_version="$(
        printf '%s\n' "$java_version_output" |
            sed -nE 's/.*version "([^"]+)".*/\1/p' |
            head -n 1
    )"
    if [[ -z "$java_version" ]]; then
        die "$loader_log" "Could not determine the Java version: $java_version_output"
    fi

    if [[ "$java_version" == 1.* ]]; then
        java_major="${java_version#1.}"
        java_major="${java_major%%.*}"
    else
        java_major="${java_version%%.*}"
    fi

    if [[ ! "$java_major" =~ ^[0-9]+$ ]]; then
        die "$loader_log" "Could not parse Java version '$java_version'."
    fi

    if (( java_major < min_java_version )); then
        die "$loader_log" "JATOS requires Java $min_java_version or newer. Found Java $java_version at $java_cmd."
    fi

    log "$loader_log" both "Using Java $java_version from $java_cmd"
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
supervise() {
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

stop() {
    local pid i

    if ! isRunning; then
        rm -f -- "$pid_file"
        log "$loader_log" stderr "JATOS is not running."
        return 1
    fi

    IFS= read -r pid < "$pid_file"

    kill -TERM "$pid" 2>/dev/null ||
        die "$loader_log" "Could not send TERM signal to JATOS process $pid."

    if wait_until_stopped; then
        rm -f -- "$pid_file"
        return 0
    fi

    die "$loader_log" "Could not stop JATOS process $pid."
}

# Necessary to stop with ctrl-c running in Docker
trap 'terminate INT' INT
trap 'terminate TERM' TERM

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
        # "update" is intentionally omitted because it is an internal command.
        printf '%s\n' "Usage: loader.sh start|stop|restart"
        exit 1
        ;;
esac