$ErrorActionPreference = 'Stop'

$configPath = Join-Path $env:USERPROFILE '.docker\config.json'
$registry = "crpi-5yw4kk2bxbk3nj6k.cn-hangzhou.personal.cr.aliyuncs.com"

if (-not (Test-Path -LiteralPath $configPath)) {
    Write-Output 'Docker credential configuration is missing.'
    exit 1
}

$cfg = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
$registryEntry = $cfg.auths.PSObject.Properties.Name -contains $registry
$credentialHelper = [string]$cfg.credsStore

if ($registryEntry) {
    Write-Output 'Registry credential entry exists; secret value is intentionally hidden.'
    exit 0
}
if ($credentialHelper) {
    Write-Output "Docker credential helper is configured: $credentialHelper; registry login must be verified with docker login."
    exit 0
}

Write-Output 'No registry credential entry or credential helper is configured.'
exit 1
