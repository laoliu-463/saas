#!/bin/sh
set -eu

cd /app

echo "[dev] Skip blocking initial compile; Spring Boot will compile on startup"

snapshot() {
  find src/main -type f \( -name "*.java" -o -name "*.xml" -o -name "*.yml" -o -name "*.yaml" -o -name "*.properties" \) \
    -exec stat -c "%Y %n" {} \; | sort | sha1sum | awk '{print $1}'
}

watch_and_compile() {
  last=""
  while true; do
    current="$(snapshot)"
    if [ "$current" != "$last" ]; then
      if [ -n "$last" ]; then
        echo "[dev] Source changed, running incremental compile"
        mvn -q -DskipTests compile || echo "[dev] Compile failed, waiting for next change"
      fi
      last="$current"
    fi
    sleep 1
  done
}

watch_and_compile &

echo "[dev] Starting Spring Boot with DevTools"
exec mvn -Dmaven.test.skip=true spring-boot:run \
  -Dspring-boot.run.fork=true \
  -Dspring-boot.run.jvmArguments="${JAVA_OPTS:-}"
