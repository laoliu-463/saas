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
mkdir -p "`$HOME/.m2"
docker run --rm \
  -v "`$PWD:/workspace" \
  -v "`$HOME/.m2:/root/.m2" \
  -w /workspace \
  maven:3.9.10-eclipse-temurin-17 \
  mvn -f backend/pom.xml -DskipTests package
docker compose --env-file '$RemoteEnvFile' -f docker-compose.real-pre.yml up -d --build backend-real-pre frontend-real-pre
docker compose --env-file '$RemoteEnvFile' -f docker-compose.real-pre.yml ps
curl -fsS http://127.0.0.1:8081/api/system/health
curl -fsS http://127.0.0.1:3001/healthz
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
