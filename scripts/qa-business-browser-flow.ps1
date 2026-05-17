param(
    [string]$BaseUrl,
    [string]$ApiBaseUrl
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$scriptName = "qa-business-browser-flow"
$outDir = Join-Path $repoRoot "runtime\qa\out\$scriptName-$timestamp"
$nodeScript = Join-Path $repoRoot "runtime\qa\business-browser-flow.cjs"
$envFile = Join-Path $repoRoot ".env.test"

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "node is not available in PATH"
}
if (-not (Test-Path -LiteralPath $nodeScript)) {
    throw "Node script not found: $nodeScript"
}

function Test-HttpOk {
    param([string]$Url)
    try {
        $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 3 -UseBasicParsing
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
    }
    catch {
        return $false
    }
}

function Read-EnvValue {
    param([string]$Path, [string]$Name)
    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    $line = Get-Content -LiteralPath $Path | Where-Object { $_ -match "^\s*$Name\s*=" } | Select-Object -First 1
    if (-not $line) {
        return $null
    }
    return (($line -split "=", 2)[1]).Trim()
}

if (-not $BaseUrl) {
    $BaseUrl = $env:E2E_BASE_URL
}
if (-not $ApiBaseUrl) {
    $ApiBaseUrl = $env:API_BASE_URL
}

# Defaults requested by the QA contract. The checked-in TEST compose may bind
# to different ports; when no explicit env var is provided, prefer .env.test so
# the wrapper works with scripts/start-test.ps1 out of the box.
if (-not $BaseUrl) {
    $frontendPort = Read-EnvValue -Path $envFile -Name "FRONTEND_HOST_PORT"
    if ($frontendPort) {
        $BaseUrl = "http://localhost:$frontendPort"
    } else {
        $BaseUrl = "http://localhost:3002"
    }
}
if (-not $ApiBaseUrl) {
    $backendPort = Read-EnvValue -Path $envFile -Name "BACKEND_HOST_PORT"
    if ($backendPort) {
        $ApiBaseUrl = "http://localhost:$backendPort"
    } else {
        $ApiBaseUrl = "http://localhost:8081"
    }
}

# Keep the requested contract defaults above, but auto-align with the current
# checked-in TEST compose ports when those defaults are not listening locally.
if (-not (Test-HttpOk "$ApiBaseUrl/api/system/env")) {
    $backendPort = Read-EnvValue -Path $envFile -Name "BACKEND_HOST_PORT"
    if ($backendPort) {
        $candidateApi = "http://localhost:$backendPort"
        if (Test-HttpOk "$candidateApi/api/system/env") {
            Write-Host "Default API_BASE_URL $ApiBaseUrl is not reachable; using TEST env API $candidateApi" -ForegroundColor Yellow
            $ApiBaseUrl = $candidateApi
        }
    }
}

if (-not (Test-HttpOk $BaseUrl)) {
    $frontendPort = Read-EnvValue -Path $envFile -Name "FRONTEND_HOST_PORT"
    if ($frontendPort) {
        $candidateBase = "http://localhost:$frontendPort"
        if (Test-HttpOk $candidateBase) {
            Write-Host "Default E2E_BASE_URL $BaseUrl is not reachable; using TEST env frontend $candidateBase" -ForegroundColor Yellow
            $BaseUrl = $candidateBase
        }
    }
}

New-Item -ItemType Directory -Path $outDir -Force | Out-Null

Push-Location $repoRoot
try {
    $env:E2E_BASE_URL = $BaseUrl
    $env:API_BASE_URL = $ApiBaseUrl
    & node $nodeScript $outDir
    $exitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

Write-Host "QA business browser flow output: $outDir" -ForegroundColor Green
if ($exitCode -ne 0) {
    exit $exitCode
}
