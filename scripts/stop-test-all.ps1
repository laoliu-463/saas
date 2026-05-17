param(
    [switch]$RemoveVolumes
)

$ErrorActionPreference = "Stop"

if ($RemoveVolumes) {
    Write-Host "Volume removal is no longer part of the compatibility stop script. Volumes are kept." -ForegroundColor Yellow
}

$scriptDir = Split-Path -Parent $PSCommandPath
Write-Host "scripts/stop-test-all.ps1 is kept for compatibility. Delegating to scripts/stop-all.ps1." -ForegroundColor Yellow
& (Join-Path $scriptDir "stop-all.ps1")
