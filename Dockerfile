FROM eclipse-temurin:17-jdk
MAINTAINER Traversium Developers
WORKDIR /opt/social-service

COPY target/*.jar app.jar
ENTRYPOINT ["java","-jar","/opt/social-service/app.jar"]

