#!/bin/bash

studiespath="~/mecharg_studies"
dbuser="mecharguser"
dbpassword="zaq12wsx"
port="9000"
address="127.0.0.1"

# Get application secret
secretfile="application.secret"
if [ -f "$secretfile" ]
then
	secret=$(<$secretfile)
else
	random="$(tr -cd '[:alnum:]' < /dev/urandom | fold -w128 | head -n1)"
	echo "$random" > "$secretfile"
	secret=$random
fi

./bin/mecharg -Dconfig.file="conf/production.conf" -Dstudiespath=$studiespath -Ddb.default.user=$dbuser -Ddb.default.password=$dbpassword -Dhttp.port=$port -Dhttp.address=$address -Dapplication.secret=$secret > /dev/null &


