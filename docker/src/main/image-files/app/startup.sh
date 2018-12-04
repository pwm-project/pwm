#!/usr/bin/env bash
#Docker Container startup script

JAVA_OPTS="$(sed 's/./&/' java.vmoptions | tr '\n' ' ')"
export JAVA_OPTS

java -jar /app/libs/pwm-onejar*.jar $JAVA_OPTS -applicationPath /config
