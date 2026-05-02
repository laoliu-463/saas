param(
    [switch]$RemoveVolumes
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

function Assert-DockerAvailable {
    try {
        & docker.exe version | Out-Null
    }
    catch {
        throw "Docker is not available. Start Docker Desktop / Docker Engine first, then retry."
    }
}

$repoRoot = Get-RepoRoot
Assert-DockerAvailable
$composeArgs = @(
    "compose",
    "--project-name", "saas-test",
    "-f", (Join-Path $repoRoot "docker-compose.yml"),
    "-f", (Join-Path $repoRoot "docker-compose.test.yml"),
    "down"
)

if ($RemoveVolumes) {
    $composeArgs += "-v"
}

Push-Location $repoRoot
try {
    & docker.exe @composeArgs
}
finally {
    Pop-Location
}

if ($RemoveVolumes) {
    Write-Host "test environment stopped and volumes removed." -ForegroundColor Green
} else {
    Write-Host "test environment stopped." -ForegroundColor Green
}
