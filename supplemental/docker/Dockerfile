FROM tomcat:jre8
MAINTAINER Jason Rivard


# setup environment
RUN mkdir /config ; mkdir /appliance
ENV JAVA_HOME /usr
ENV PWM_APPLICATIONPATH /config
ENV PWM_APPLICATIONFLAGS Appliance,ManageHttps

VOLUME /config
EXPOSE 8443

RUN mkdir /usr/local/pwm

# clear tomcat webapps directory
RUN rm -rf /usr/local/tomcat/webapps/* ; mkdir /usr/local/tomcat/webapps/ROOT

# copy war
COPY pwm.war /appliance/pwm.war

# copy scripts
COPY startup.sh /appliance/startup.sh
COPY shell.sh /appliance/shell.sh

COPY ROOT-index.jsp /usr/local/tomcat/webapps/ROOT/index.jsp
COPY server.xml.source /appliance/server.xml.source
RUN chmod a+x /appliance/startup.sh; chmod a+x /appliance/shell.sh

VOLUME /config
EXPOSE 8443

WORKDIR /appliance
CMD ./startup.sh



