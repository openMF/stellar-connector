#!/bin/sh
set -e

while ! nc -zvw3 database-server 3306 ; do
    >&2 echo "Database is unavailable - sleeping"
    sleep 5
done
>&2 echo "Database is up - executing command"

java -Duser.country=MX -Duser.language=es -Djava.security.egd=file:/dev/./urandom -jar /tmp/stellar-connector-0.0.1.jar
