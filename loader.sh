#!/bin/bash
# JATOS loader for Linux and MacOS X

# Change IP address and port here
address="127.0.0.1"
port="9000"

# Don't change after here unless you know what you're doing
#####################################

# Get JATOS directory and add it to PATH
dir="$( cd "$( dirname "$0" )" && pwd )"
export PATH="$dir:$dir/bin:$PATH"

function start() {
	[ -f $dir/RUNNING_PID ] && return
	
	checkJava
	
	echo -n "Starting JATOS"

	# Generate application secret for the Play framework
	# If it's the first start, create a new secret, otherwise load it from the file.
	secretfile="$dir/play.crypto.secret"
	if [ ! -f "$secretfile" ]
	then
		random="$(LC_CTYPE=C tr -cd '[:alnum:]' < /dev/urandom | fold -w128 | head -n1)"
		echo "$random" > "$secretfile"
	fi
	secret=$(<$secretfile)

	if [ ! -f $dir/bin/jatos ]
	then
		echo -e "\n$dir/bin/jatos doesn't exist!"
		exit 1
	fi
	
	# In case './bin/jatos' isn't executable set the x bit
	chmod u+x $dir/bin/jatos
	
	# Start JATOS with configuration file and application secret
	jatos -Dconfig.resource="production.conf" -Dplay.crypto.secret=$secret -Dhttp.port=$port -Dhttp.address=$address > /dev/null &
	
	echo "...started"
	echo "To use JATOS type $address:$port in your browser's address bar"
}

function stop() {
	[ -f $dir/RUNNING_PID ] || return
	echo -n "Stopping JATOS"
	kill -SIGTERM $(cat $dir/RUNNING_PID)
	while [ -f $dir/RUNNING_PID ]
	do
		sleep 0.5
	done

	echo "...stopped"
}

function checkJava() {
        if type -p java
        then
                JAVA_VER=$(java -version 2>&1 | sed 's/java version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
        fi
        if [[ -z "$JAVA_VER" ]] || [[ "$JAVA_VER" -lt 18 ]]
        then
                if [[ -n $dir/jre/linux_x64_jre ]] && [[ -x "$dir/jre/linux_x64_jre/bin/java" ]]
                then
                        echo "Jatos uses local JRE"
                        export JAVA_HOME="$dir/jre/linux_x64_jre"
                elif [[ -n $dir/jre/mac_x64_jre ]] && [[ -x "$dir/jre/mac_x64_jre/bin/java" ]]
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
		start
		;;
	*)
		echo "Usage: loader.sh start|stop|restart"
		exit 1
		;;
esac
