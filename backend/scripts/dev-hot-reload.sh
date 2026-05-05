#!/bin/sh
set -eu

cd /app

echo "[dev] Starting Spring Boot with single-run compile path"
exec mvn -Dmaven.test.skip=true spring-boot:run \
  -Dspring-boot.run.fork=true \
  -Dspring-boot.run.jvmArguments="${JAVA_OPTS:-}"
