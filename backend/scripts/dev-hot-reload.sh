#!/bin/sh
set -eu

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR/backend"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local-mock}"
export SPRING_DEVTOOLS_RESTART_ENABLED="${SPRING_DEVTOOLS_RESTART_ENABLED:-true}"
export SPRING_DEVTOOLS_POLL_INTERVAL="${SPRING_DEVTOOLS_POLL_INTERVAL:-3s}"
export SPRING_DEVTOOLS_QUIET_PERIOD="${SPRING_DEVTOOLS_QUIET_PERIOD:-2s}"

echo "[dev] Spring Boot hot reload profile=${SPRING_PROFILES_ACTIVE} poll=${SPRING_DEVTOOLS_POLL_INTERVAL} quiet=${SPRING_DEVTOOLS_QUIET_PERIOD}"
exec mvn -DskipTests spring-boot:run \
  -Dspring-boot.run.fork=true \
  -Dspring-boot.run.jvmArguments="${JAVA_OPTS:-}"
