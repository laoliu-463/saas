$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$roleScript = Join-Path $scriptDir "qa-page-role.ps1"

$roles = @("admin", "biz_leader")
foreach ($r in $roles) {
    Write-Host "=== qa-page-role: $r ===" -ForegroundColor Cyan
    & $roleScript $r
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

Write-Host "All role page QA runs passed: $($roles -join ', ')" -ForegroundColor Green
