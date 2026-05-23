$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$composeFile = Join-Path $repoRoot "docker-compose.prod.yml"
$envFile = Join-Path $repoRoot ".env.prod"

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Env file not found: $envFile. Copy .env.prod.example to .env.prod and replace placeholders first."
}

function Get-EnvValue {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$DefaultValue
    )

    $line = Get-Content -LiteralPath $Path |
        Where-Object { $_ -match "^\s*$([regex]::Escape($Name))\s*=" } |
        Select-Object -Last 1
    if (-not $line) {
        return $DefaultValue
    }
    $value = ($line -split "=", 2)[1].Trim()
    if (-not $value) {
        return $DefaultValue
    }
    return $value.Trim().Trim("'").Trim('"')
}

& (Join-Path $scriptDir "stop-all.ps1")

Push-Location $repoRoot
try {
    docker compose --env-file $envFile --project-name saas-prod -f $composeFile up -d --build
    docker compose --env-file $envFile --project-name saas-prod -f $composeFile ps
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "SAAS prod stack is starting as project saas-prod." -ForegroundColor Green
$frontendPort = Get-EnvValue -Path $envFile -Name "FRONTEND_HOST_PORT" -DefaultValue "3000"
$backendPort = Get-EnvValue -Path $envFile -Name "BACKEND_HOST_PORT" -DefaultValue "8080"
Write-Host "Frontend: http://localhost:$frontendPort"
Write-Host "Backend health: http://localhost:$backendPort/api/system/health"
