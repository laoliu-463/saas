param(
    [switch]$Detach,
    [switch]$SkipHealthChecks,
    [switch]$SkipDbPatches,
    [int]$BackendHealthTimeoutSeconds = 180,
    [int]$FrontendHealthTimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $scriptPath = $PSCommandPath
    if (-not $scriptPath) {
        $scriptPath = $MyInvocation.PSCommandPath
    }
    if (-not $scriptPath) {
        throw "Unable to resolve script path."
    }
    $scriptDir = Split-Path -Parent $scriptPath
    return (Resolve-Path (Join-Path $scriptDir "..")).Path
}

function Invoke-Compose {
    param(
        [string]$RepoRoot,
        [string[]]$ComposeArgs
    )

    Push-Location $RepoRoot
    try {
        & docker.exe @ComposeArgs
    }
    finally {
        Pop-Location
    }
}

function Assert-DockerAvailable {
    try {
        & docker.exe version | Out-Null
    }
    catch {
        throw "Docker is not available. Start Docker Desktop / Docker Engine first, then retry."
    }
}

function Wait-HttpOk {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                Write-Host "$Name is ready: $Url" -ForegroundColor Green
                return
            }
        }
        catch {
            Start-Sleep -Seconds 3
            continue
        }

        Start-Sleep -Seconds 3
    }

    throw "$Name did not become ready within $TimeoutSeconds seconds: $Url"
}

function Wait-BackendHealth {
    param(
        [string]$Url,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $Url -TimeoutSec 5
            if ($response.status -eq "UP") {
                Write-Host "Backend health is UP: $Url" -ForegroundColor Green
                return
            }
        }
        catch {
            Start-Sleep -Seconds 3
            continue
        }

        Start-Sleep -Seconds 3
    }

    throw "Backend health did not become UP within $TimeoutSeconds seconds: $Url"
}

$repoRoot = Get-RepoRoot
Assert-DockerAvailable
$composeFiles = @(
    "-f", (Join-Path $repoRoot "docker-compose.test.yml")
)

$composeArgs = @(
    "compose",
    "--project-name", "saas-test",
    "--env-file", (Join-Path $repoRoot ".env.test")
) + $composeFiles + @(
    "up",
    "--build"
)

if ($Detach) {
    $composeArgs += "-d"
}

Invoke-Compose -RepoRoot $repoRoot -ComposeArgs $composeArgs

if (-not $Detach) {
    return
}

if (-not $SkipDbPatches) {
    Write-Host ""
    Write-Host "Applying idempotent test DB patches for existing volumes..." -ForegroundColor Cyan
    & (Join-Path $repoRoot "scripts\apply-test-db-patches.ps1")
}

if ($SkipHealthChecks) {
    Write-Host "Startup requested in detached mode. Health checks skipped." -ForegroundColor Yellow
    return
}

Write-Host ""
Write-Host "Waiting for test environment health checks..." -ForegroundColor Cyan
Wait-BackendHealth -Url "http://localhost:8080/api/actuator/health" -TimeoutSeconds $BackendHealthTimeoutSeconds
Wait-HttpOk -Name "Frontend" -Url "http://localhost:3000" -TimeoutSeconds $FrontendHealthTimeoutSeconds

Write-Host ""
Write-Host "test environment startup completed." -ForegroundColor Green
Write-Host "Backend : http://localhost:8080/api"
Write-Host "Frontend: http://localhost:3000"
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Open http://localhost:3000"
Write-Host "2. Login with admin / admin123"
Write-Host "3. Visit /dev/test and run reset + seed"
Write-Host "4. Run backend tests with: cd `"$repoRoot\backend`"; mvn test"
