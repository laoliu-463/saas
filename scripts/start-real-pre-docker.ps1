$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
Write-Host "scripts/start-real-pre-docker.ps1 is kept for compatibility. Delegating to scripts/start-real-pre.ps1." -ForegroundColor Yellow
& (Join-Path $scriptDir "start-real-pre.ps1")
