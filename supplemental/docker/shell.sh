#!/bin/bash
# Run command line shell environment inside the docker container.  Execute using
# docker exec -it <container name> /home/pwm/shell.sh

chmod a+x /home/pwm/pwm/WEB-INF/command.sh
cd /home/pwm/pwm/WEB-INF
/home/pwm/pwm/WEB-INF/command.sh Shell 

