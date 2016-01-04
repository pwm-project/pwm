#!/bin/bash
# Run command line shell environment inside the docker container.  Execute using
# docker exec -it <container name> /appliance/shell.sh

chmod a+x /usr/local/tomcat/webapps/pwm/WEB-INF/command.sh
cd /usr/local/tomcat/webapps/pwm/WEB-INF
./command.sh Shell 

