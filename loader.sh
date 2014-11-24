#!/bin/bash

function start() {
	echo -n "Starting JATOS"

	# Generate application secret for the Play framework
	# If it's the first start, create a new secret, otherwise load it from the file.
	secretfile="application.secret"
	if [ -f "$secretfile" ]
	then
		secret=$(<$secretfile)
	else
		random="$(LC_CTYPE=C tr -cd '[:alnum:]' < /dev/urandom | fold -w128 | head -n1)"
		echo "$random" > "$secretfile"
		secret=$random
	fi
	
	# In case './bin/jatos' isn't executable set the x bit
	chmod u+x ./bin/jatos
	
	# Start JATOS with configuration file and application secret
	./bin/jatos -Dconfig.file="conf/production.conf" -Dapplication.secret=$secret > /dev/null &

	echo "...started"
}

function stop() {
	[ -f ./RUNNING_PID ] || return
	echo -n "Stopping JATOS"
	kill `cat RUNNING_PID`
	echo "...stopped"
}

[ -f ./bin/jatos ] || [ exit 1 ]
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