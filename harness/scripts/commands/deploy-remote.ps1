param(
    [Alias("Env")]
    [ValidateSet("real-pre")]
    [string]$TargetEnv = "real-pre",
    [string]$RemoteHost = "saas",
    [string]$RemoteDir = "/opt/saas/app",
    [string]$RemoteEnvFile = "/opt/saas/env/.env.real-pre",
    [string]$ExpectedCommit = "",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
if ([string]::IsNullOrWhiteSpace($ExpectedCommit)) {
    $ExpectedCommit = (& git -C $repoRoot rev-parse HEAD).Trim()
}
if ($ExpectedCommit -notmatch "^[0-9a-fA-F]{40}$") {
    throw "ExpectedCommit must be a full 40-character Git commit hash. actual=$ExpectedCommit"
}

Write-HarnessStage "Remote deploy"
& (Join-Path $PSScriptRoot "safety-check.ps1") -Env $TargetEnv -Scope full

$remoteScript = @"
set -e
cd '$RemoteDir'
git pull --ff-only
actual_commit="`$(git rev-parse HEAD)"
if [ "`$actual_commit" != '$ExpectedCommit' ]; then
  echo "Remote commit mismatch: expected=$ExpectedCommit actual=`$actual_commit"
  exit 1
fi
if [ ! -f '$RemoteEnvFile' ]; then
  echo "Canonical remote env file not found: $RemoteEnvFile"
  exit 1
fi
if [ -e .env.real-pre ] && [ ! -L .env.real-pre ]; then
  echo "Repository .env.real-pre is not a symlink; refusing to overwrite it"
  exit 1
fi
ln -sfn '$RemoteEnvFile' .env.real-pre
repo_env_real="`$(readlink -f .env.real-pre)"
canonical_env_real="`$(readlink -f '$RemoteEnvFile')"
if [ "`$repo_env_real" != "`$canonical_env_real" ]; then
  echo "Remote env link mismatch: expected=`$canonical_env_real actual=`$repo_env_real"
  exit 1
fi
echo "Checking product sync env vars ..."
if grep -q PRODUCT_ACTIVITY_SYNC_ENABLED '$RemoteEnvFile' 2>/dev/null; then
  grep PRODUCT_ACTIVITY_SYNC '$RemoteEnvFile'
else
  echo "WARNING: PRODUCT_ACTIVITY_SYNC_ENABLED not found in $RemoteEnvFile"
  echo "Compose and real-pre profile default to enabled, but remote env must set PRODUCT_ACTIVITY_SYNC_* explicitly."
fi
compose() {
  docker compose --project-name 'saas-active' --env-file '$RemoteEnvFile' -f docker-compose.real-pre.yml "`$@"
}
echo "Checking product sync compose config ..."
compose config | grep PRODUCT_ACTIVITY || {
  echo "PRODUCT_ACTIVITY sync env not found in docker compose config"
  exit 1
}
echo "Preparing postgres-real-pre before schema guard ..."
compose up -d postgres-real-pre redis-real-pre
for service in postgres-real-pre redis-real-pre; do
  service_container="`$(compose ps -q "`$service")"
  if [ -z "`$service_container" ]; then
    echo "Stateful service container not found: `$service"
    exit 1
  fi
  service_project="`$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "`$service_container")"
  service_working_dir="`$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "`$service_container")"
  service_config_files="`$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project.config_files" }}' "`$service_container")"
  service_environment_file="`$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project.environment_file" }}' "`$service_container")"
  if [ "`$service_project" != "saas-active" ] || [ "`$service_working_dir" != '$RemoteDir' ] || [ "`$service_config_files" != '$RemoteDir/docker-compose.real-pre.yml' ] || [ "`$service_environment_file" != '$RemoteEnvFile' ]; then
    echo "Stateful service provenance mismatch: service=`$service project=`$service_project workingDir=`$service_working_dir configFiles=`$service_config_files environmentFile=`$service_environment_file"
    exit 1
  fi
done
echo "Stateful service provenance guard passed."
ready=false
for i in `$(seq 1 60); do
  if compose exec -T postgres-real-pre sh -lc 'pg_isready -U "`$POSTGRES_USER" -d "`$POSTGRES_DB"' </dev/null >/dev/null 2>&1; then
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
activity_list_sync_migration="backend/src/main/resources/db/alter-colonel-activity-list-sync.sql"
if [ ! -f "`$activity_list_sync_migration" ]; then
  echo "Required activity list sync migration not found: `$activity_list_sync_migration"
  exit 1
fi
config_migration="backend/src/main/resources/db/alter-v2-config-20260523.sql"
if [ ! -f "`$config_migration" ]; then
  echo "Required V2 config migration not found: `$config_migration"
  exit 1
fi
sample_standard_migration="backend/src/main/resources/db/alter-sample-default-standard-disable-20260716.sql"
if [ ! -f "`$sample_standard_migration" ]; then
  echo "Required sample standard migration not found: `$sample_standard_migration"
  exit 1
fi
backfill_migration="backend/src/main/resources/db/migrate/V20260615_001__product_activity_backfill_state.sql"
if [ ! -f "`$backfill_migration" ]; then
  echo "Required product backfill schema migration not found: `$backfill_migration"
  exit 1
fi
colonel_partner_mapping_migration="backend/src/main/resources/db/alter-pick-source-mapping-colonel-name.sql"
if [ ! -f "`$colonel_partner_mapping_migration" ]; then
  echo "Required colonel partner mapping schema migration not found: `$colonel_partner_mapping_migration"
  exit 1
fi
pg_container="`$(compose ps -q postgres-real-pre)"
if [ -z "`$pg_container" ]; then
  echo "postgres-real-pre container id not found"
  exit 1
fi
echo "Applying required activity schema migration ..."
docker cp "`$activity_migration" "`$pg_container:/tmp/V20260529_001__alter-colonel-activity-add-recruiter-fields.sql"
compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/V20260529_001__alter-colonel-activity-add-recruiter-fields.sql' </dev/null
echo "Applying required activity list sync schema migration ..."
docker cp "`$activity_list_sync_migration" "`$pg_container:/tmp/alter-colonel-activity-list-sync.sql"
compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/alter-colonel-activity-list-sync.sql' </dev/null
schema_count="`$(compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -tAc "SELECT count(*) FROM information_schema.columns WHERE table_schema = '\''public'\'' AND table_name = '\''colonel_activity'\'' AND column_name IN ('\''recruiter_user_id'\'', '\''recruiter_dept_id'\'', '\''assigned_at'\'', '\''assigned_by'\'', '\''activity_status_code'\'', '\''activity_status_text'\'', '\''activity_status_synced_at'\'')"' </dev/null | tr -d '[:space:]')"
if [ "`$schema_count" != "7" ]; then
  echo "colonel_activity schema guard failed: expected 7 required columns, got `$schema_count"
  exit 1
fi
echo "Activity schema guard passed."
echo "Applying required colonel partner mapping schema migration ..."
docker cp "`$colonel_partner_mapping_migration" "`$pg_container:/tmp/alter-pick-source-mapping-colonel-name.sql"
compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/alter-pick-source-mapping-colonel-name.sql' </dev/null
colonel_name_schema_count="`$(compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -tAc "SELECT count(*) FROM information_schema.columns WHERE table_schema = '\''public'\'' AND table_name = '\''pick_source_mapping'\'' AND column_name = '\''colonel_name'\''"' </dev/null | tr -d '[:space:]')"
if [ "`$colonel_name_schema_count" != "1" ]; then
  echo "Colonel partner mapping schema guard failed: pick_source_mapping.colonel_name is missing"
  exit 1
fi
echo "Colonel partner mapping schema guard passed."
echo "Applying required V2 config schema migration ..."
docker cp "`$config_migration" "`$pg_container:/tmp/alter-v2-config-20260523.sql"
compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/alter-v2-config-20260523.sql' </dev/null
config_version_count="`$(compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -tAc "SELECT count(*) FROM information_schema.columns WHERE table_schema = '"'"'public'"'"' AND table_name = '"'"'commissions'"'"' AND column_name = '"'"'version'"'"'"' </dev/null | tr -d '[:space:]')"
if [ "`$config_version_count" != "1" ]; then
  echo "V2 config schema guard failed: commissions.version is missing"
  exit 1
fi
echo "V2 config schema guard passed."
echo "Applying required sample default standard migration ..."
docker cp "`$sample_standard_migration" "`$pg_container:/tmp/alter-sample-default-standard-disable-20260716.sql"
compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/alter-sample-default-standard-disable-20260716.sql' </dev/null
echo "Sample default standard migration passed."
echo "Applying required product backfill schema migration ..."
docker cp "`$backfill_migration" "`$pg_container:/tmp/V20260615_001__product_activity_backfill_state.sql"
compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/V20260615_001__product_activity_backfill_state.sql' </dev/null
backfill_table_count="`$(compose exec -T postgres-real-pre sh -lc 'psql -U "`$POSTGRES_USER" -d "`$POSTGRES_DB" -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema = '"'"'public'"'"' AND table_name IN ('"'"'product_sync_job_log'"'"','"'"'product_activity_sync_state'"'"')"' </dev/null | tr -d '[:space:]')"
if [ "`$backfill_table_count" != "2" ]; then
  echo "product backfill schema guard failed: expected 2 tables, got `$backfill_table_count"
  exit 1
fi
echo "Product backfill schema guard passed."
mkdir -p "`$HOME/.m2"
docker run --rm \
  -v "`$PWD:/workspace" \
  -v "`$HOME/.m2:/root/.m2" \
  -w /workspace \
  maven:3.9.10-eclipse-temurin-17 \
  mvn -f backend/pom.xml -DskipTests clean package
echo "Remote backend jar after Maven build:"
ls -l backend/target/colonel-saas.jar
compose up -d --build backend-real-pre frontend-real-pre
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
Write-Host "Expected commit: $ExpectedCommit"

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
