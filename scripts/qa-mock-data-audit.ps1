param(
    [string]$ApiBaseUrl
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$scriptName = "qa-mock-data-audit"
$outDir = Join-Path $repoRoot "runtime\qa\out\$scriptName-$timestamp"
$nodeScript = Join-Path $repoRoot "runtime\qa\mock-data-audit.cjs"
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

if (-not $ApiBaseUrl) {
    $ApiBaseUrl = $env:API_BASE_URL
}
if (-not $ApiBaseUrl) {
    $ApiBaseUrl = "http://localhost:8081"
}

$backendPort = Read-EnvValue -Path $envFile -Name "BACKEND_HOST_PORT"
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
    $env:API_BASE_URL = $ApiBaseUrl
    & node $nodeScript $outDir
    $exitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

Write-Host "QA mock data audit output: $outDir" -ForegroundColor Green
if ($exitCode -ne 0) {
    exit 1
}
