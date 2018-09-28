#!/usr/bin/env bash
#Docker Container startup script

JAVA_OPTS="$(sed 's/./&/' java.vmoptions | tr '\n' ' ')"
export JAVA_OPTS

java -jar /app/libs/pwm-onejar-${project.version}.jar $JAVA_OPTS
