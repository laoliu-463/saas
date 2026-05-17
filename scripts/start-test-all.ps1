$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
Write-Host "scripts/start-test-all.ps1 is kept for compatibility. Delegating to scripts/start-test.ps1." -ForegroundColor Yellow
& (Join-Path $scriptDir "start-test.ps1")
