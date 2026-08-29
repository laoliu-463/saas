FROM eclipse-temurin:17-jre
<<<<<<< HEAD
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.revision=${GIT_COMMIT}
=======
ARG GIT_SHA=UNAVAILABLE
LABEL org.opencontainers.image.revision="${GIT_SHA}"
>>>>>>> b8cb837b (refactor: establish real-pre single-channel CD)
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
