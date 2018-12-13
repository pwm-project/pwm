#!/usr/bin/env bash
#Docker Container startup script

JAVA_OPTS="$(sed 's/./&/' /app/java.vmoptions | tr '\n' ' ')"
export JAVA_OPTS

mkdir -p /config/logs

java $JAVA_OPTS -jar /app/libs/*onejar*.jar -applicationPath /config
