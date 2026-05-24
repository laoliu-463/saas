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

function Get-DotEnvValue {
    param(
        [string]$Path,
        [string]$Name,
        [string]$Default
    )

    $line = Get-Content -LiteralPath $Path |
        Where-Object { $_ -match "^\s*$([regex]::Escape($Name))\s*=" } |
        Select-Object -First 1

    if (-not $line) {
        return $Default
    }

    $value = ($line -split "=", 2)[1].Trim()
    return $value.Trim("'`"")
}

& (Join-Path $scriptDir "stop-all.ps1")

Push-Location $repoRoot
try {
    docker compose --env-file $envFile --project-name saas-active -f $composeFile up -d --build
    & (Join-Path $scriptDir "apply-test-db-patches.ps1") `
        -ContainerName "saas-active-postgres-real-pre-1" `
        -Database (Get-DotEnvValue -Path $envFile -Name "DB_NAME" -Default "saas_real_pre") `
        -User (Get-DotEnvValue -Path $envFile -Name "DB_USER" -Default "saas")
    docker compose --env-file $envFile --project-name saas-active -f $composeFile ps
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "SAAS real-pre environment is starting as project saas-active." -ForegroundColor Green
Write-Host "Use this environment only for real SDK integration."
Write-Host "Frontend: http://localhost:3001"
Write-Host "Backend health: http://localhost:8081/api/system/health"
Write-Host "Containers: saas-active-frontend-real-pre-1, saas-active-backend-real-pre-1, saas-active-postgres-real-pre-1, saas-active-redis-real-pre-1"
