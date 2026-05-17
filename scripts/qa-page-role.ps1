param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Role
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$scriptName = "qa-page-role"
$outDir = Join-Path $repoRoot "runtime\qa\out\$scriptName-$Role-$timestamp"
$nodeScript = Join-Path $repoRoot "runtime\qa\page-role-smoke.cjs"

if (-not (Test-Path -LiteralPath $nodeScript)) {
    throw "Node script not found: $nodeScript"
}

New-Item -ItemType Directory -Path $outDir -Force | Out-Null

Push-Location $repoRoot
try {
    & node $nodeScript $outDir $Role
}
finally {
    Pop-Location
}

Write-Host "QA page role output: $outDir" -ForegroundColor Green
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
