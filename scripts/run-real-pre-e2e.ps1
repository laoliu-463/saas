param(
    [switch]$OpenReport
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

function Assert-HttpOk {
    param(
        [string]$Name,
        [string]$Url
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 400) {
            throw "$Name returned unexpected status: $($response.StatusCode)"
        }
    }
    catch {
        throw "$Name is not ready: $Url"
    }
}

function Get-LatestReportPath {
    param([string]$OutRoot)

    $dir = Get-ChildItem -LiteralPath $OutRoot -Directory |
        Where-Object { $_.Name -like 'e2e-*' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $dir) {
        return $null
    }

    return (Join-Path $dir.FullName 'report.md')
}

$repoRoot = Get-RepoRoot
$qaScript = Join-Path $repoRoot 'runtime\qa\full-browser-e2e.cjs'
$outRoot = Join-Path $repoRoot 'runtime\qa\out'

if (-not (Test-Path -LiteralPath $qaScript)) {
    throw "QA script not found: $qaScript"
}

Assert-HttpOk -Name 'real-pre frontend' -Url 'http://localhost:3001'
Assert-HttpOk -Name 'real-pre backend health' -Url 'http://localhost:8081/api/actuator/health'

Push-Location $repoRoot
try {
    & node $qaScript
}
finally {
    Pop-Location
}

$reportPath = Get-LatestReportPath -OutRoot $outRoot
if ($null -eq $reportPath -or -not (Test-Path -LiteralPath $reportPath)) {
    throw "real-pre E2E completed, but report.md was not found."
}

Write-Host ""
Write-Host "real-pre E2E completed." -ForegroundColor Green
Write-Host "Report: $reportPath"

if ($OpenReport) {
    Start-Process $reportPath
}
