#!/usr/bin/env sh
# Run command line shell environment inside the docker container.  Execute using
# docker exec -it <container name> /application/command.sh

java -jar /app/libs/*onejar*.jar -applicationPath /config -command $1 $2 $3 $4 $5 $6 $7 $8 $9

