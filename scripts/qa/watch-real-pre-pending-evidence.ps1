param(
    [string]$ApiBaseUrl = "http://localhost:8081"
)

$ErrorActionPreference = "Stop"

$scriptPath = $PSCommandPath
if (-not $scriptPath) {
    $scriptPath = $MyInvocation.MyCommand.Path
}
$scriptDir = Split-Path -Parent $scriptPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..\..")).Path

Set-Location $repoRoot
$env:API_BASE_URL = $ApiBaseUrl

node .\runtime\qa\real-pre-pending-evidence-watch.cjs
exit $LASTEXITCODE
