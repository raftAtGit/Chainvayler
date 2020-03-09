# builds chainvayler and install to local Maven repository
# so it can be used by other Docker images

FROM gradle:5.6.4
 
COPY . /chainvayler

WORKDIR /chainvayler
RUN gradle clean build publishToMavenLocal


