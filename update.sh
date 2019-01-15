#!/bin/bash

# USE WITH CARE!
# This Shell script handles JATOS updates.
#
# Use it carefully. Do backups.
#
# To update JATOS use this script without any parameter:
# It downloads the latest JATOS version, installs it into JATOS current working directory and then
# restarts JATOS.
#
# If you want to do the update step by step, the script can be called with parameters:
#     download)    Download only - no installation
#                  It accepts a second parameter, the version to download
#                  e.g. 'update.sh download' - downloads the latest JATOS version into the tmp directory
#                  e.g. 'update.sh download 3.3.3' - downloads version 3.3.3
#     updateFiles) Moving JATOS update files from the tmp directory into JATOS' working directory
#     restart)     Stops JATOS and starts it again with the same command as used before

echoerr() { echo "$@" 1>&2; }

jatosUpdateDir="/tmp/jatosupdate"
dir="$( cd "$( dirname "$0" )" && pwd )"

if ! ls ${dir}/lib/org.jatos* 1> /dev/null 2>&1; then
    echoerr "Not a JATOS directory. Exit."
    sleep 2
    exit 1
fi

getLatestRelease() {
  curl --silent "https://api.github.com/repos/JATOS/JATOS/releases/latest" | # Get latest release from GitHub api
    grep '"tag_name":' |                                                     # Get tag line
    sed -E 's/.*"([^"]+)".*/\1/'                                             # Pluck JSON value
}

# Downloads new JATOS and unzips it. Optionally takes the version as a parameter and if the version
# is not given it uses the latest release version.
download() {
    local version = $1

    # If version is not given use the latest release version
    if [[ ! -e "$version" ]]; then
        local release=$(getLatestRelease)
        version=$(echo $release | sed 's/v//g')
    fi

    local jatosZip="/tmp/jatos-$version.zip"
    local downloadUrl="https://github.com/JATOS/JATOS/releases/download/v$version/jatos-$version.zip"

    # Use wget and curl as a fallback to download JATOS
    echo "Downloading JATOS $version from $downloadUrl"
#    rm -rf "$jatosZip"
#    if ! wget -q -O "$jatosZip" "${downloadUrl}"; then
#        curl -s -L "${downloadUrl}" > "$jatosZip"
#    fi
    if [[ ! -e "$jatosZip" ]]; then
        echo "Couldn't download JATOS. Exit."
        exit 1
    fi

    # Remove old JATOS directories in tmp
    rm -rf "/tmp/jatos-$version"
    rm -rf "$jatosUpdateDir"

    # Unzip into $jatosUpdateDir
    unzip -qq "$jatosZip" -d /tmp
    mv "/tmp/jatos-$version" "$jatosUpdateDir"
}

# Stops current JATOS if running and stores the command that was used to run JATOS
stop() {
    # Get PID of JATOS if it was started from this working directory
    local pid=$(pgrep -f "${dir}/lib/org.jatos")

    if [[ -z "$pid" ]]; then
        echo "No JATOS running currently"
        return
    fi

    # Keep the start command (with parameters) that started current JATOS
    jatosCmd=$(ps -p $pid -o args --no-headers)

    # Stop JATOS if it was started from the working directory (leaves other JATOS instances running)
    echo "Stopping JATOS with PID $pid"
    if ! kill $pid > /dev/null 2>&1; then
        echo "Couldn't stop JATOS. Exit."
        exit 1
    fi
}

# Moves JATOS update files into current folder
updateFiles() {
    if [[ ! -d "$jatosUpdateDir" ]]; then
        echo "JATOS update directory doesn't exist. Exit."
        exit 1
    fi

    # Make backups of all files that will be overwritten
    # todo how to overwrite update.sh https://stackoverflow.com/questions/8595751/is-this-a-valid-self-update-approach-for-a-bash-script
    echo "Making backup of current JATOS"
    mkdir -p bkp
    mv bin lib conf loader.sh loader.bat update.sh bkp/. 2>/dev/null

    # Remove old dir from earlier updates
    rm -rf "$jatosUpdateDir"

    # Copy update files to JATOS location
    echo "Copy JATOS update files into current location"
    cp -r "$jatosUpdateDir"/. .

    # Restore config files
    echo "Restore config files"
    yes | cp -rf bkp/conf/* conf/.

    #rm -rf "$jatosUpdateDir"
    # todo leave bkp?
}

# Starts new JATOS with the stored command that run the old JATOS
start() {
    # Run JATOS with the same parameters as before in background
    if [[ -e "$jatosCmd" ]]; then
        echo "Starting JATOS"
        eval "nohup ${jatosCmd} </dev/null >/dev/null 2>&1 &"
    fi
}

case "$1" in
    download)
        download $2
        ;;
    updateFiles)
        updateFiles
        ;;
    restart)
        read -p "Are you sure you want to restart JATOS? " -n 1 -r
        if [[ $REPLY =~ ^[Yy]$ ]]
        then
            stop
            start
        fi
        ;;
    *)
        read -p "Are you sure you want to update JATOS? " -n 1 -r
        if [[ $REPLY =~ ^[Yy]$ ]]
        then
            download
            stop
            updateFiles
            start
        fi
        ;;
esac
