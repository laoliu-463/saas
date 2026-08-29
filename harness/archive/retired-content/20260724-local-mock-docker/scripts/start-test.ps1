$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$composeFile = Join-Path $repoRoot "docker-compose.test.yml"
$envFile = Join-Path $repoRoot ".env.test"
$projectName = "saas-test"
$timeoutSeconds = 120

. (Join-Path $scriptDir "stack-utils.ps1")

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Env file not found: $envFile"
}

$envMap = Read-EnvFile -Path $envFile
$healthUrl = Get-BackendHealthUrl -EnvMap $envMap
$frontendUrl = Get-FrontendBaseUrl -EnvMap $envMap

# Stop any running SAAS environment first (guarantees single environment)
& (Join-Path $scriptDir "stop-all.ps1")
Wait-ContainersStopped -TimeoutSeconds $timeoutSeconds -PollIntervalMilliseconds 2000 -CheckScript {
    $names = docker ps -a --filter "name=saas" --format "{{.Names}}"
    return -not $names
} | Out-Null

Push-Location $repoRoot
try {
    docker compose --env-file $envFile --project-name $projectName -f $composeFile up -d --build
    Assert-LastExitCode -CommandName "docker compose up -d --build"

    $redisPassword = if ($envMap.ContainsKey("REDIS_PASSWORD") -and $envMap["REDIS_PASSWORD"]) {
        $envMap["REDIS_PASSWORD"]
    } else {
        "test-redis123"
    }
    $testLoginKeys = @("admin", "biz_leader", "channel_leader", "channel_staff", "ops_staff")
    $authRedisKeys = @()
    foreach ($loginKey in $testLoginKeys) {
        $authRedisKeys += "auth:login:fail:$loginKey"
        $authRedisKeys += "auth:login:lock:$loginKey"
    }
    & docker exec "$projectName-redis-1" redis-cli -a $redisPassword -n 1 DEL $authRedisKeys | Out-Null
    Assert-LastExitCode -CommandName "redis auth lock reset"

    & (Join-Path $scriptDir "apply-test-db-patches.ps1") -ContainerName "$projectName-postgres-1"

    $health = Wait-HttpHealth -Url $healthUrl -TimeoutSeconds $timeoutSeconds -PollIntervalMilliseconds 2000
    $frontend = Wait-HttpOk -Url $frontendUrl -TimeoutSeconds $timeoutSeconds -PollIntervalMilliseconds 2000

    Write-Host ""
    Write-Host "Compose project: $projectName" -ForegroundColor Green
    docker compose --env-file $envFile --project-name $projectName -f $composeFile ps
    Assert-LastExitCode -CommandName "docker compose ps"
    Write-Host ""
    Write-Host "docker ps:" -ForegroundColor Green
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    Assert-LastExitCode -CommandName "docker ps"
    Write-Host ""
    Write-Host "Health URL: $healthUrl" -ForegroundColor Green
    Write-Host "Health body: $($health.body)" -ForegroundColor Green
    Write-Host "Frontend URL: $frontendUrl" -ForegroundColor Green
    Write-Host "Frontend status: $($frontend.statusCode)" -ForegroundColor Green
}
catch {
    Write-Host ""
    Write-Host "TEST environment startup failed: $($_.Exception.Message)" -ForegroundColor Red
    Show-StartupDiagnostics -RepoRoot $repoRoot -ComposeFile $composeFile -EnvFile $envFile -ProjectName $projectName
    exit 1
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "SAAS TEST environment is running as project '$projectName'." -ForegroundColor Green
Write-Host "Default for system features, permission checks, and browser E2E."
Write-Host "Frontend: $frontendUrl"
Write-Host "Backend health: $healthUrl"
Write-Host "Containers: saas-test-frontend-1, saas-test-backend-1, saas-test-postgres-1, saas-test-redis-1"
