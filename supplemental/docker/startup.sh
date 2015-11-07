#!/usr/bin/env bash
#PWM Docker Container startup script

#fix script executable flag
chmod a+x /home/pwm/pwm/WEB-INF/command.sh
cd /home/pwm/pwm/WEB-INF

# update the https certificate file used by tomcat
rm /appliance/https-cert
/home/pwm/pwm/WEB-INF/command.sh ExportHttpsKeyStore /appliance/https-cert password

# update the https configuration used by tomcat
rm /home/pwm/tomcat/conf/server.xml
/home/pwm/pwm/WEB-INF/command.sh ExportHttpsTomcatConfig /home/pwm/tomcat/conf/server.xml.source /home/pwm/tomcat/conf/server.xml

# start tomcat
cd /home/pwm/tomcat/bin
./catalina.sh run


