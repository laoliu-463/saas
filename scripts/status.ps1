$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$envFile = Join-Path $repoRoot ".env.test"

. (Join-Path $scriptDir "stack-utils.ps1")

Push-Location $repoRoot
try {
    $envMap = if (Test-Path -LiteralPath $envFile) { Read-EnvFile -Path $envFile } else { @{} }
    $healthUrl = Get-BackendHealthUrl -EnvMap $envMap

    Write-Host "Compose project: saas-active"
    Write-Host ""
    Write-Host "Running containers matching saas names:"
    docker ps -a --filter "name=saas" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

    Write-Host ""
    Write-Host "Backend health:"
    try {
        $health = Wait-HttpHealth -Url $healthUrl -TimeoutSeconds 10 -PollIntervalMilliseconds 1000
        Write-Host "$healthUrl -> $($health.status)"
    } catch {
        Write-Host "$healthUrl -> UNREACHABLE"
    }

    Write-Host ""
    Write-Host "Backend environment log hint:"
    $backend = docker ps -a --filter "name=^/saas-backend$" --format "{{.Names}}"
    if ($backend) {
        docker logs saas-backend --tail 80 2>$null | Select-String -Pattern "SAAS environment" -SimpleMatch
    } else {
        Write-Host "saas-backend is not present."
    }
}
finally {
    Pop-Location
}
