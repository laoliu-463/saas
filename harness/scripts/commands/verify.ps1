param(
    [Alias('Env')][ValidateSet('test', 'real-pre')][string]$TargetEnv = 'real-pre',
    [ValidateSet('backend', 'frontend', 'full')][string]$Scope = 'full',
    [string]$RepoRoot = '',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$verifyLocal = Join-Path $PSScriptRoot 'verify-local.ps1'
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path }
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
Push-Location $RepoRoot
try { & $verifyLocal -Env $TargetEnv -Scope $Scope -DryRun:$DryRun }
finally { Pop-Location }
