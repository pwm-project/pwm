#!/usr/bin/env bash
#Docker startup script

#start onejar
cd /appliance

JAVA_OPTS="$(sed 's/./&/' java.vmoptions | tr '\n' ' ')"

java $JAVA_OPTS -jar pwm-onejar.jar -applicationPath /config
