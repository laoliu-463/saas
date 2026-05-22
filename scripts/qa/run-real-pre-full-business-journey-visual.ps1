$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$BackendUrl = if ($env:E2E_BACKEND_URL) { $env:E2E_BACKEND_URL.TrimEnd("/") } elseif ($env:BACKEND_URL) { $env:BACKEND_URL.TrimEnd("/") } else { "http://localhost:8081" }
$FrontendUrl = if ($env:E2E_BASE_URL) { $env:E2E_BASE_URL.TrimEnd("/") } elseif ($env:FRONTEND_URL) { $env:FRONTEND_URL.TrimEnd("/") } else { "http://localhost:3001" }

Write-Host "== real-pre full business journey visual regression =="
Write-Host "Root: $Root"

Write-Host "Checking backend 8080..."
Invoke-WebRequest "$BackendUrl/api/system/health" -UseBasicParsing -TimeoutSec 5 | Out-Null
Write-Host "backend 8080 OK"

Write-Host "Checking frontend 3000..."
Invoke-WebRequest "$FrontendUrl/login" -UseBasicParsing -TimeoutSec 5 | Out-Null
Write-Host "frontend 3000 OK"

if (-not $env:PW_SLOWMO_MS) {
  $env:PW_SLOWMO_MS = "700"
}
if (-not $env:PW_STEP_PAUSE_MS) {
  $env:PW_STEP_PAUSE_MS = "900"
}
if (-not $env:PW_AFTER_ACTION_PAUSE_MS) {
  $env:PW_AFTER_ACTION_PAUSE_MS = "700"
}
if (-not $env:PW_WORKERS) {
  $env:PW_WORKERS = "1"
}
if (-not $env:E2E_VISUAL_CAPTURE) {
  $env:E2E_VISUAL_CAPTURE = "true"
}

$env:E2E_HEADLESS = "false"
$env:E2E_BASE_URL = $FrontendUrl
$env:E2E_BACKEND_URL = $BackendUrl

if (-not $env:E2E_JOURNEY_EVIDENCE_DIR) {
  $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
  $env:E2E_JOURNEY_EVIDENCE_DIR = Join-Path $Root "runtime\qa\out\real-pre-full-business-journey-$timestamp"
}

Write-Host "PW_SLOWMO_MS=$env:PW_SLOWMO_MS"
Write-Host "PW_STEP_PAUSE_MS=$env:PW_STEP_PAUSE_MS"
Write-Host "PW_AFTER_ACTION_PAUSE_MS=$env:PW_AFTER_ACTION_PAUSE_MS"
Write-Host "PW_WORKERS=$env:PW_WORKERS"
Write-Host "headed=true"
Write-Host "trace=on video=on screenshot=on"
Write-Host "Evidence directory: $env:E2E_JOURNEY_EVIDENCE_DIR"

Push-Location $Root
try {
  npm run e2e:real-pre:journey:visual
  if ($LASTEXITCODE -ne 0) {
    throw "npm run e2e:real-pre:journey:visual failed with exit code $LASTEXITCODE"
  }
} finally {
  Pop-Location
}

Write-Host "== visual journey finished =="
Write-Host "Evidence directory: $env:E2E_JOURNEY_EVIDENCE_DIR"
