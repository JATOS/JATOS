#!/bin/bash

### Setup parameters ###
# Path to your study's HTML, JavaScript, and CSS files
studiespath="~/mecharg_studies"
# Username to access your database
dbuser="mecharguser"
# Password to access your database
dbpassword="mypassword"
# Port MechArg should listen on (e.g. 80 or 8080)
port="1024"
# IP address MechArg should listen on
address="127.0.0.1"

# Get application secret for the Play framework
# If it's the first start, create a new secret, otherwise load it from the file.
secretfile="application.secret"
if [ -f "$secretfile" ]
then
	secret=$(<$secretfile)
else
	random="$(tr -cd '[:alnum:]' < /dev/urandom | fold -w128 | head -n1)"
	echo "$random" > "$secretfile"
	secret=$random
fi

# Start the MechArg with the configuration file, the application secret,
# and the setup parameters
./bin/mecharg -Dconfig.file="conf/production.conf" -Dstudiespath=$studiespath -Ddb.default.user=$dbuser -Ddb.default.password=$dbpassword -Dhttp.port=$port -Dhttp.address=$address -Dapplication.secret=$secret > /dev/null &


