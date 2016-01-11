#!/usr/bin/env bash
#PWM Docker Image Build Script

DOCKER_IMAGE_NAME="pwm"
SAVED_IMAGE_NAME="pwm-docker-image"

start=`date +%s`
if [ "$(id -u)" != "0" ]; then
   echo "This script must be run as root"
   exit 1
fi

if [ ! -f pwm.war ];
then
    echo "pwm.war file not found in current directory"
    exit 1
fi

echo "Clearing previous build artifacts and images..."
docker rmi $DOCKER_IMAGE_NAME
rm -f $SAVED_IMAGE_NAME.tar
rm -f $SAVED_IMAGE_NAME.tar.gz

echo "Beginning docker build process..."
docker build --pull=true -t $DOCKER_IMAGE_NAME .
if [ $? -ne 0 ];
then
    echo "Docker build failed"
    exit 1
fi

echo "Docker build complete" 

echo "Beginning docker image save..."
docker save --output=$SAVED_IMAGE_NAME.tar $DOCKER_IMAGE_NAME
if [[ $? -ne 0 ]];
then
    echo "Docker image save failed"
    exit 1
fi

echo "Docker image save complete"
ls -l $SAVED_IMAGE_NAME.*

echo "Beginning image gzip..."
gzip $SAVED_IMAGE_NAME.tar
echo "Image gzip complete"
ls -l $SAVED_IMAGE_NAME.*
end=`date +%s`
runtime=$((end-start))
echo "total docker script runtime: $runtime seconds"
