Docker image usage notes:

--Load docker image from file
docker load --input=pwm-docker-image.tar.gz

--Create docker container and run--
docker run -d --name <container name> -p 8443:8443 pwm

This will expose the https port to 8443.  You can also manage the exposed configuration volume of /config if you want to preserve
the /config directory when you destroy/create the container in the future.  The docker image will place all of it's configuration
and runtime data in the /config volume.
