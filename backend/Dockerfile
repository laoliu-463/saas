FROM eclipse-temurin:17-jre
ARG GIT_SHA=UNAVAILABLE
LABEL org.opencontainers.image.revision="${GIT_SHA}"
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
