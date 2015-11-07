FROM opensuse
MAINTAINER Jason Rivard

# update and install modules
RUN zypper --non-interactive up
RUN zypper --non-interactive in tar unzip vim 
RUN useradd pwm -m

RUN mkdir /config; mkdir /appliance
RUN chown -R pwm. /config
VOLUME /config

# copy/explode java
COPY java.tar.gz /home/pwm/java.tar.gz
RUN mkdir /home/pwm/java; \
  tar -zxf /home/pwm/java.tar.gz -C /home/pwm/java --strip-components=1; \
  rm /home/pwm/java.tar.gz

# copy/explode tomcat
COPY tomcat.tar.gz /home/pwm/tomcat.tar.gz
RUN mkdir /home/pwm/tomcat; \
 tar -zxf /home/pwm/tomcat.tar.gz -C /home/pwm/tomcat --strip-components=1; \
 rm /home/pwm/tomcat.tar.gz; \
 rm -rf /home/pwm/tomcat/webapps/*; \
 mkdir /home/pwm/tomcat/webapps/ROOT
COPY ROOT-index.jsp /home/pwm/tomcat/webapps/ROOT/index.jsp

# copy/explode war
COPY pwm.war /home/pwm/pwm.war
RUN mkdir /home/pwm/pwm; \
  unzip /home/pwm/pwm.war -d /home/pwm/pwm/; \
  rm /home/pwm/pwm.war; \
  ln -s /home/pwm/pwm /home/pwm/tomcat/webapps/pwm

# copy scripts
COPY startup.sh /home/pwm/startup.sh
COPY shell.sh /home/pwm/shell.sh
COPY server.xml.source /home/pwm/tomcat/conf/server.xml.source
RUN chmod a+x /home/pwm/startup.sh; chmod a+x /home/pwm/shell.sh

# fix ownership
RUN chown -R pwm. /home/pwm/; \
  chown -R pwm. /config; \
  chown -R pwm. /appliance

# setup environment
ENV JAVA_HOME /home/pwm/java
ENV PWM_APPLICATIONPATH /config
ENV PWM_APPLICATIONFLAGS Appliance,ManageHttps
USER pwm
WORKDIR /home/pwm

EXPOSE 8443
CMD ./startup.sh



