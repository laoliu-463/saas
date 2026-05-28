param(
    [switch]$OpenReports
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

function Get-LatestReportPath {
    param(
        [string]$OutRoot,
        [string]$Pattern
    )

    $dir = Get-ChildItem -LiteralPath $OutRoot -Directory |
        Where-Object { $_.Name -like $Pattern } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $dir) {
        return $null
    }

    return (Join-Path $dir.FullName 'report.md')
}

$repoRoot = Get-RepoRoot
$realPreScript = Join-Path $repoRoot 'scripts\run-real-pre-e2e.ps1'
$outRoot = Join-Path $repoRoot 'runtime\qa\out'

Write-Host "Running test V1-P0 E2E..." -ForegroundColor Cyan
Push-Location $repoRoot
try {
    & npm.cmd run e2e:v1-p0
    if ($LASTEXITCODE -ne 0) {
        throw "test V1-P0 E2E failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Running real-pre E2E..." -ForegroundColor Cyan
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $realPreScript
if ($LASTEXITCODE -ne 0) {
    throw "real-pre E2E failed with exit code $LASTEXITCODE."
}

Write-Host ""
Write-Host "All QA commands completed." -ForegroundColor Green

if ($OpenReports) {
    $realReport = Get-LatestReportPath -OutRoot $outRoot -Pattern 'e2e-*'

    if ($realReport) {
        Start-Process $realReport
    }
}
