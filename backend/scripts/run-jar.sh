#!/bin/sh
set -eu

TARGET_DIR=/app/target

# Wait for host-compiled jar to appear (host runs: mvn package -DskipTests)
echo "[dev] Waiting for jar in ${TARGET_DIR} ..."
retries=0
while [ ! -f "${TARGET_DIR}/colonel-saas.jar" ]; do
  retries=$((retries + 1))
  if [ "$retries" -ge 60 ]; then
    echo "[dev] ERROR: ${TARGET_DIR}/colonel-saas.jar not found after 60s."
    echo "[dev] Run 'mvn package -DskipTests' on the host first."
    exit 1
  fi
  sleep 1
done

echo "[dev] Starting Spring Boot from host-compiled jar"
exec java ${JAVA_OPTS:-} -jar "${TARGET_DIR}/colonel-saas.jar"
