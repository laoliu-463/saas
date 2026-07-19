param(
    [Alias("Env")]
    [ValidateSet("real-pre")]
    [string]$TargetEnv = "real-pre",
    [string]$RemoteHost = "saas",
    [string]$RemoteDir = "/opt/saas/app",
    [string]$RemoteEnvFile = "/opt/saas/env/.env.real-pre",
    [switch]$SkipBackup,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

Write-HarnessStage "Remote deploy"
& (Join-Path $PSScriptRoot "safety-check.ps1") -Env $TargetEnv -Scope full

$backupStep = if ($SkipBackup) {
    'echo "Skipping pre-migration PostgreSQL backup by explicit operator request ..."'
}
else {
    @"
echo "Creating and validating the pre-migration PostgreSQL backup ..."
ENV_FILE='$RemoteEnvFile' COMPOSE_FILE=docker-compose.real-pre.yml COMPOSE_PROJECT_NAME=saas-active \
  BACKUP_DIR="/opt/saas/backups/remote-`$(date +%Y%m%d-%H%M%S)" bash scripts/backup-db.sh
"@
}

$remoteScript = @"
set -e
cd '$RemoteDir'
echo "Using the pre-aligned remote checkout; refusing implicit mirror pulls ..."
if [ -n "`$(git status --porcelain)" ]; then
  echo "Remote worktree is dirty; refusing to deploy."
  git status --short
  exit 1
fi
remote_commit="`$(git rev-parse HEAD)"
image_tag="`$(grep -E '^[[:space:]]*IMAGE_TAG=' '$RemoteEnvFile' | tail -1 | cut -d= -f2- | tr -d '[:space:]\"')"
if ! printf '%s' "`$image_tag" | grep -Eq '^[0-9a-fA-F]{40}$'; then
  echo "IMAGE_TAG must be a full 40-character commit SHA; refusing floating-tag deployment."
  exit 1
fi
if [ "`$remote_commit" != "`$image_tag" ]; then
  echo "Remote checkout SHA (`$remote_commit) does not match IMAGE_TAG (`$image_tag)."
  exit 1
fi
echo "Checking product sync env vars ..."
if ! grep -q PRODUCT_ACTIVITY_SYNC_ENABLED '$RemoteEnvFile' 2>/dev/null; then
  echo "ERROR: PRODUCT_ACTIVITY_SYNC_ENABLED is not explicitly configured in $RemoteEnvFile"
  exit 1
fi
compose() {
  docker compose --env-file '$RemoteEnvFile' --project-name saas-active -f docker-compose.real-pre.yml "`$@"
}
echo "Checking compose syntax without rendering environment values ..."
compose config --quiet
$backupStep
echo "Building immutable images for `$image_tag ..."
mkdir -p "`$HOME/.m2"
docker run --rm \
  -v "`$PWD:/workspace" \
  -v "`$HOME/.m2:/root/.m2" \
  -w /workspace \
  maven:3.9.10-eclipse-temurin-17 \
  mvn -f backend/pom.xml -DskipTests clean package
IMAGE_TAG="`$image_tag" docker compose --env-file '$RemoteEnvFile' --project-name saas-active \
  -f docker-compose.real-pre.yml build --build-arg GIT_COMMIT="`$image_tag" backend-real-pre frontend-real-pre
test "`$(docker image inspect "colonel-saas/backend:`$image_tag" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')" = "`$image_tag"
test "`$(docker image inspect "colonel-saas/frontend:`$image_tag" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')" = "`$image_tag"
echo "Running Flyway migrations with backend schedulers paused ..."
REAL_PRE_COMPOSE_ENV='$RemoteEnvFile' REAL_PRE_COMPOSE_FILE=docker-compose.real-pre.yml \
  REAL_PRE_COMPOSE_PROJECT=saas-active IMAGE_TAG="`$image_tag" REQUIRE_PINNED_IMAGE=true \
  BACKEND_HEALTH_URL=http://127.0.0.1:8081/api/system/health \
  sh scripts/run-real-pre-db-migrations.sh
REAL_PRE_COMPOSE_ENV='$RemoteEnvFile' REAL_PRE_COMPOSE_FILE=docker-compose.real-pre.yml \
  REAL_PRE_COMPOSE_PROJECT=saas-active sh scripts/check-real-pre-schema.sh
echo "Deploying the already-built immutable images ..."
IMAGE_TAG="`$image_tag" docker compose --env-file '$RemoteEnvFile' --project-name saas-active \
  -f docker-compose.real-pre.yml up -d --no-build backend-real-pre frontend-real-pre
compose ps
backend_container="`$(compose ps -q backend-real-pre)"
if [ -z "`$backend_container" ]; then
  echo "backend-real-pre container id not found after compose up"
  exit 1
fi
host_jar_size="`$(stat -c %s backend/target/colonel-saas.jar)"
container_jar_size="`$(docker exec "`$backend_container" stat -c %s /app/app.jar)"
if [ "`$host_jar_size" != "`$container_jar_size" ]; then
  echo "Backend jar guard failed: host=`$host_jar_size container=`$container_jar_size"
  exit 1
fi
echo "Backend jar guard passed: size=`$container_jar_size"
curl -fsS http://127.0.0.1:8081/api/system/health
curl -fsS http://127.0.0.1:3001/healthz
echo "Checking backend product sync env ..."
docker exec "`$backend_container" printenv | grep PRODUCT_ACTIVITY || {
  echo "PRODUCT_ACTIVITY env not found in backend container"
  exit 1
}
echo "Checking ProductActivitySyncJob config ..."
docker logs --tail=200 "`$backend_container" 2>&1 | grep -i ProductActivitySyncJob || echo "WARNING: ProductActivitySyncJob config log not found"
"@

Write-Host "Remote host: $RemoteHost"
Write-Host "Remote dir: $RemoteDir"
Write-Host "Remote env file: $RemoteEnvFile"

if ($DryRun) {
    Write-Host "DRY-RUN remote script:"
    Write-Host $remoteScript
    return
}

$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($remoteScript))
$remoteTemp = "/tmp/saas-remote-deploy-$([Guid]::NewGuid().ToString('N')).sh"
$command = "echo $encoded | base64 -d > '$remoteTemp' && bash '$remoteTemp'; rc=`$?; rm -f '$remoteTemp'; exit `$rc"
ssh $RemoteHost $command
if ($LASTEXITCODE -ne 0) {
    throw "Remote deploy failed with exit code $LASTEXITCODE."
}

Write-Host "Remote deploy completed." -ForegroundColor Green
