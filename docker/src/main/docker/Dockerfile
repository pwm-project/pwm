FROM adoptopenjdk/openjdk10
MAINTAINER Jason Rivard

# setup environment
RUN mkdir /config ; mkdir /appliance
ENV PWM_APPLICATIONPATH /config
ENV PWM_APPLICATIONFLAGS Appliance,ManageHttps

VOLUME /config
EXPOSE 8443

# copy war
COPY onejar/pwm-onejar.jar /appliance/pwm-onejar.jar

# copy scripts
COPY startup.sh /appliance/startup.sh
RUN chmod a+x /appliance/startup.sh;

VOLUME /config
EXPOSE 8443

WORKDIR /appliance
CMD ./startup.sh