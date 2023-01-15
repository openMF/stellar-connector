#!/bin/sh
set -e

while ! nc -zvw3 messaging-server 4222 ; do
    >&2 echo "Messaging is unavailable - sleeping"
    sleep 5
done
>&2 echo "Messaging is up - executing command"

java -Duser.country=MX -Duser.language=es -Djava.security.egd=file:/dev/./urandom -jar /tmp/phee-service-entrypoint-0.0.1.jar
