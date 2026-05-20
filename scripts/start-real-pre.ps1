$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$composeFile = Join-Path $repoRoot "docker-compose.real-pre.yml"
$envFile = Join-Path $repoRoot ".env.real-pre"

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Env file not found: $envFile"
}

& (Join-Path $scriptDir "stop-all.ps1")

Push-Location $repoRoot
try {
    docker compose --env-file $envFile --project-name saas-active -f $composeFile up -d --build
    docker compose --env-file $envFile --project-name saas-active -f $composeFile ps
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "SAAS real-pre environment is starting as project saas-active." -ForegroundColor Green
Write-Host "Use this environment only for real SDK integration."
Write-Host "Frontend: http://localhost:3000"
Write-Host "Backend health: http://localhost:8080/api/actuator/health"
Write-Host "Containers: saas-frontend, saas-backend, saas-postgres, saas-redis"
