#!/bin/sh
# command.sh
#
# This script can be used to execute the command line tool.
# It must be run from within the WEB-INF directory.
#
# Krowten's fault

if [ -z "$JAVA_HOME" ]; then
echo "JAVA_HOME variable must be set to a valid Java JDK or JRE"
exit 1
fi

JAVA_OPTS=-Xmx1024m
for i in lib/*.jar ; do
 if [ "$CLASSPATH" == "" ]; then
   CLASSPATH=$i
 else
   CLASSPATH=$CLASSPATH:$i
 fi
done
CLASSPATH=$CLASSPATH:classes

$JAVA_HOME/bin/java $JAVA_OPTS -cp $CLASSPATH password.pwm.util.cli.MainClass $1 $2 $3 $4 $5 $6 $7 $8 $9

