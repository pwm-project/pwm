This docker build script and artifacts can be used to create a PWM docker image.

You will need to download the following and place it in the current directory:

------ Download needed artifacts ------

--Current Java Server JRE (Java SE Runtime Environment) 8 Download--
From oracle.com, download the current java server sdk, something like 'server-jre-8uXX-linux-x64.tar.gz'
save as 'java.tar.gz'

--Current Tomcat 8 Release--
From tomcat.apache.org, download most recent tomcat 'apache-tomcat-8.0.xx.tar.gz'
Save as 'tomcat.tar.gz'

--Current PWM war file--
Save as 'pwm.war'



------ Make docker image -------

--Make docker image:--
docker build -t <image name> .

--Create docker container and run--
docker run -d --name <container name> -p 8443:8443 <image name>

This will set the https port to 8443.  You can also manage the exposed configuration volume of /config if you want to preserve
the /config directory when you destory/create the container in the future.

--Run PWM shell inside the container--
To reset the https config, unlock the config or other commands, you can execute the PWM shell inside the running docker container:

docker exec -it <container name> /home/pwm/shell






