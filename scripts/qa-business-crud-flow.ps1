param(
    [switch]$Headful,
    [int]$SlowMo = 0,
    [string]$BaseUrl,
    [string]$ApiBaseUrl
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$scriptName = "qa-business-crud-flow"
$outDir = Join-Path $repoRoot "runtime\qa\out\$scriptName-$timestamp"
$nodeScript = Join-Path $repoRoot "runtime\qa\business-crud-flow.cjs"
$envFile = Join-Path $repoRoot ".env.test"

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "node is not available in PATH"
}
if (-not (Test-Path -LiteralPath $nodeScript)) {
    throw "Node script not found: $nodeScript"
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

function Test-HttpReachable {
    param([string]$Url)
    try {
        $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 3 -UseBasicParsing
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
    } catch {
        return $false
    }
}

if (-not $BaseUrl) {
    $BaseUrl = $env:E2E_BASE_URL
}
if (-not $ApiBaseUrl) {
    $ApiBaseUrl = $env:API_BASE_URL
}

$frontendPort = Read-EnvValue -Path $envFile -Name "FRONTEND_HOST_PORT"
$backendPort = Read-EnvValue -Path $envFile -Name "BACKEND_HOST_PORT"

if (-not $BaseUrl) {
    if ($frontendPort) {
        $BaseUrl = "http://localhost:$frontendPort"
    } else {
        $BaseUrl = "http://localhost:3002"
    }
}
if (-not $ApiBaseUrl) {
    if ($backendPort) {
        $ApiBaseUrl = "http://localhost:$backendPort"
    } else {
        $ApiBaseUrl = "http://localhost:8081"
    }
}

if ($frontendPort -and -not (Test-HttpReachable $BaseUrl)) {
    $candidateBaseUrl = "http://localhost:$frontendPort"
    if (Test-HttpReachable $candidateBaseUrl) {
        Write-Host "Default E2E_BASE_URL $BaseUrl is not reachable; using TEST env frontend $candidateBaseUrl" -ForegroundColor Yellow
        $BaseUrl = $candidateBaseUrl
    }
}

if ($backendPort -and -not (Test-HttpReachable "$ApiBaseUrl/api/system/env")) {
    $candidateApiBaseUrl = "http://localhost:$backendPort"
    if (Test-HttpReachable "$candidateApiBaseUrl/api/system/env") {
        Write-Host "Default API_BASE_URL $ApiBaseUrl is not reachable; using TEST env API $candidateApiBaseUrl" -ForegroundColor Yellow
        $ApiBaseUrl = $candidateApiBaseUrl
    }
}

New-Item -ItemType Directory -Path $outDir -Force | Out-Null

Push-Location $repoRoot
try {
    $env:E2E_BASE_URL = $BaseUrl
    $env:API_BASE_URL = $ApiBaseUrl
    if ($Headful) {
        $env:QA_HEADFUL = "true"
    } else {
        $env:QA_HEADFUL = $null
    }
    if ($SlowMo -gt 0) {
        $env:QA_SLOW_MO = [string]$SlowMo
    } else {
        $env:QA_SLOW_MO = $null
    }

    & node $nodeScript $outDir
    $exitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

Write-Host "QA business CRUD flow output: $outDir" -ForegroundColor Green
if ($exitCode -ne 0) {
    exit $exitCode
}
