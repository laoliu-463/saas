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
        Where-Object { $_.Name -like 'local-mock-supplement-*' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $dir) {
        return $null
    }

    return (Join-Path $dir.FullName 'report.md')
}

$repoRoot = Get-RepoRoot
$qaScript = Join-Path $repoRoot 'runtime\qa\local-mock-supplement.cjs'
$outRoot = Join-Path $repoRoot 'runtime\qa\out'

if (-not (Test-Path -LiteralPath $qaScript)) {
    throw "QA script not found: $qaScript"
}

Assert-HttpOk -Name 'local-mock frontend' -Url 'http://localhost:3000'
Assert-HttpOk -Name 'local-mock backend health' -Url 'http://localhost:8080/api/actuator/health'

Push-Location $repoRoot
try {
    & node $qaScript
}
finally {
    Pop-Location
}

$reportPath = Get-LatestReportPath -OutRoot $outRoot
if ($null -eq $reportPath -or -not (Test-Path -LiteralPath $reportPath)) {
    throw "local-mock supplement completed, but report.md was not found."
}

Write-Host ""
Write-Host "local-mock supplement completed." -ForegroundColor Green
Write-Host "Report: $reportPath"

if ($OpenReport) {
    Start-Process $reportPath
}
