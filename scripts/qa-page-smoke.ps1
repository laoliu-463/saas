param(
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Label = "admin_default",
    [string[]]$Routes = @(
        "/dashboard",
        "/product/library",
        "/product/manage",
        "/orders",
        "/talent",
        "/sample",
        "/system"
    )
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$scriptName = "qa-page-smoke"
$outDir = Join-Path $repoRoot "runtime\qa\out\$scriptName-$timestamp"
$nodeScript = Join-Path $repoRoot "runtime\qa\page-smoke.cjs"

if (-not (Test-Path -LiteralPath $nodeScript)) {
    throw "Node script not found: $nodeScript"
}

New-Item -ItemType Directory -Path $outDir -Force | Out-Null
# Use "|" so comma-inside-shell does not truncate the route list when invoking from CI / nested shells.
$routeArg = ($Routes -join "|")

Push-Location $repoRoot
try {
    & node $nodeScript $outDir $Username $Password $Label $routeArg
}
finally {
    Pop-Location
}

Write-Host "QA page smoke output: $outDir" -ForegroundColor Green
