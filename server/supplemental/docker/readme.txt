This docker build script and artifacts can be used to create a PWM docker image.

1) Place desired pwm.war in this directory (it must be named pwm.war)
2) Execute the make-docker-image.sh script

This script will create a docker image tagged as 'pwm', and also create a pwm-docker-image.tar.gz save of the image.

Docker image usage notes:

--Load docker image from file
docker load --input=pwm-docker-image.tar.gz

--Create docker container and run--
docker run -d --name <container name> -p 8443:8443 pwm

This will set the https port to 8443.  You can also manage the exposed configuration volume of /config if you want to preserve
the /config directory when you destroy/create the container in the future.  The docker image will place all of it's configuration
and runtime data in the /config volume.

--Run PWM shell inside the container--
To reset the https config, unlock the config or other commands, you can execute the PWM shell inside the running docker container:

docker exec -it <container name> /appliance/shell.sh






