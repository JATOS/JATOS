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
	checkAlreadyRunning
	checkJava
	
	echo -n "Starting JATOS"

	# Generate application secret for the Play framework
	secret="$(LC_ALL=C tr -cd '[:alnum:]' < /dev/urandom | fold -w128 | head -n1)"

	if [ ! -f $dir/bin/jatos ]
	then
		echo -e "\n$dir/bin/jatos doesn't exist!"
		exit 1
	fi
	
	# In case './bin/jatos' isn't executable set the x bit
	chmod u+x $dir/bin/jatos
	
	# Start JATOS with configuration file and application secret
	jatos -Dconfig.file="conf/production.conf" -Dplay.crypto.secret=$secret -Dhttp.port=$port -Dhttp.address=$address > /dev/null &
	
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

function checkAlreadyRunning() {
	if [ -f $dir/RUNNING_PID ]
	then
		echo "There seems to be a running JATOS. If you're sure there is no running JATOS delete the file RUNNING_PID in JATOS' root folder."
		exit 1
	fi
	if nc -z $address $port > /dev/null 2>&1
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
		JAVA_VER=$(java -version 2>&1 | sed 's/.*version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
	elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]]
	then
		echo "Found Java in JAVA_HOME"
		JAVA_VER=$($JAVA_HOME/bin/java -version 2>&1 | sed 's/.*version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
	fi
	
	# If we don't have a version or if the version is not a number or if the version is smaller 18 (Java 8) try to find a local Java installation
	if [[ -z "$JAVA_VER" ]] || [[ ! "$JAVA_VER" =~ ^[0-9]+$ ]] || [[ "$JAVA_VER" -lt 18 ]]
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
