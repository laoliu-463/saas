param(
    [Alias("Env")]
    [ValidateSet("real-pre")]
    [string]$TargetEnv = "real-pre",
    [string]$RemoteHost = "saas",
    [string]$RemoteDir = "/opt/saas/app",
    [string]$RemoteEnvFile = "/opt/saas/env/.env.real-pre",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

Write-HarnessStage "Remote deploy"
& (Join-Path $PSScriptRoot "safety-check.ps1") -Env $TargetEnv -Scope full

$remoteScript = @"
set -e
cd '$RemoteDir'
git pull --ff-only
echo "Checking product sync env vars ..."
if grep -q PRODUCT_ACTIVITY_SYNC_ENABLED '$RemoteEnvFile' 2>/dev/null; then
  grep PRODUCT_ACTIVITY_SYNC '$RemoteEnvFile'
else
  echo "WARNING: PRODUCT_ACTIVITY_SYNC_ENABLED not found in $RemoteEnvFile"
  echo "Compose and real-pre profile default to enabled, but remote env must set PRODUCT_ACTIVITY_SYNC_* explicitly."
fi
compose() {
  docker compose --env-file '$RemoteEnvFile' -f docker-compose.real-pre.yml "`$@"
}
echo "Checking product sync compose config ..."
compose config | grep PRODUCT_ACTIVITY || {
  echo "PRODUCT_ACTIVITY sync env not found in docker compose config"
  exit 1
}
echo "Preparing postgres-real-pre before schema guard ..."
compose up -d postgres-real-pre
ready=false
for i in `$(seq 1 60); do
  if compose exec -T postgres-real-pre sh -lc 'pg_isready -U "`$POSTGRES_USER" -d "`$POSTGRES_DB"' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done
if [ "`$ready" != "true" ]; then
  echo "postgres-real-pre did not become ready before schema guard"
  compose logs --tail=200 postgres-real-pre
  exit 1
fi
activity_migration="backend/src/main/resources/db/migrate/V20260529_001__alter-colonel-activity-add-recruiter-fields.sql"
if [ ! -f "`$activity_migration" ]; then
  echo "Required activity schema migration not found: `$activity_migration"
  exit 1
fi
pg_container="`$(compose ps -q postgres-real-pre)"
if [ -z "`$pg_container" ]; then
  echo "postgres-real-pre container id not found"
  exit 1
fi
echo "Applying required activity schema migration ..."
docker cp "`$activity_migration" "`$pg_container:/tmp/V20260529_001__alter-colonel-activity-add-recruiter-fields.sql"
compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/V20260529_001__alter-colonel-activity-add-recruiter-fields.sql'
schema_count="`$(compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -c "\d public.colonel_activity"' | grep -E '^[[:space:]]*(recruiter_user_id|recruiter_dept_id|assigned_at|assigned_by|activity_status_code|activity_status_text)[[:space:]]' | wc -l | tr -d '[:space:]')"
if [ "`$schema_count" != "6" ]; then
  echo "colonel_activity schema guard failed: expected 6 required columns, got `$schema_count"
  exit 1
fi
echo "Activity schema guard passed."
mkdir -p "`$HOME/.m2"
docker run --rm \
  -v "`$PWD:/workspace" \
  -v "`$HOME/.m2:/root/.m2" \
  -w /workspace \
  maven:3.9.10-eclipse-temurin-17 \
  mvn -f backend/pom.xml -DskipTests package
compose up -d --build backend-real-pre frontend-real-pre
compose ps
curl -fsS http://127.0.0.1:8081/api/system/health
curl -fsS http://127.0.0.1:3001/healthz
echo "Checking backend product sync env ..."
docker exec "`$(compose ps -q backend-real-pre)" printenv | grep PRODUCT_ACTIVITY || {
  echo "PRODUCT_ACTIVITY env not found in backend container"
  exit 1
}
echo "Checking ProductActivitySyncJob config ..."
docker logs --tail=200 "`$(compose ps -q backend-real-pre)" 2>&1 | grep -i ProductActivitySyncJob || echo "WARNING: ProductActivitySyncJob config log not found"
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
$command = "echo $encoded | base64 -d | bash"
ssh $RemoteHost $command
if ($LASTEXITCODE -ne 0) {
    throw "Remote deploy failed with exit code $LASTEXITCODE."
}

Write-Host "Remote deploy completed." -ForegroundColor Green
