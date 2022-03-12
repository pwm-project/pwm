#!/usr/bin/env sh
#Docker container startup script

mkdir -p /config/logs

PRIMARY_OPTIONS_FILE="/app/java.vmoptions"
USER_OPTIONS_FILE="/config/java.vmoptions"
USER_REPLACE_FILE="/config/java.vmoptions.replace"

JAVA_OPTS=""

if [ -f ${PRIMARY_OPTIONS_FILE} ]; then
   echo "file $PRIMARY_OPTIONS_FILE exists, adding to java options"
   JAVA_OPTS="$JAVA_OPTS $(sed 's/./&/' ${PRIMARY_OPTIONS_FILE} | tr '\n' ' ')"
else
   echo "file $PRIMARY_OPTIONS_FILE does not exist."
fi

if [ -f ${USER_OPTIONS_FILE} ]; then
   if [ -f ${USER_REPLACE_FILE} ]; then
      echo "file $USER_OPTIONS_FILE and $USER_REPLACE_FILE both exists, replacing java options"
      JAVA_OPTS="$(sed 's/./&/' ${USER_OPTIONS_FILE} | tr '\n' ' ')"
   else
      echo "file $USER_OPTIONS_FILE exists, adding to java options"
      JAVA_OPTS="$JAVA_OPTS $(sed 's/./&/' ${USER_OPTIONS_FILE} | tr '\n' ' ')"
   fi
else
   echo "file $USER_OPTIONS_FILE does not exist."
fi

export JAVA_OPTS
echo "effective java options: $JAVA_OPTS"

JAVA_CMD_LINE="java $JAVA_OPTS -jar /app/libs/*onejar*.jar -applicationPath /config"
export JAVA_CMD_LINE
echo "starting java with command line: $JAVA_CMD_LINE"

$JAVA_CMD_LINE
