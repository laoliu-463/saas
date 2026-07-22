param(
    [Alias('Env')][ValidateSet('test', 'real-pre')][string]$TargetEnv = 'real-pre',
    [ValidateSet('backend', 'frontend', 'full', 'docs', 'apifox')][string]$Scope = 'full',
    [Parameter(Mandatory = $true)][string]$ReportKey,
    [AllowEmptyCollection()][string[]]$OwnedFiles = @(),
    [string]$BuildResult = 'not collected',
    [string]$HealthResult = 'not collected',
    [string]$BusinessResult = 'not collected',
    [ValidateSet('PASS', 'PARTIAL', 'FAIL')][string]$Conclusion = 'PARTIAL',
    [string]$RetroSummary = 'No actionable Harness improvement was recorded.',
    [string]$RepoRoot = '',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'collect-evidence.ps1') `
    -Env $TargetEnv -Scope $Scope -ReportKey $ReportKey -OwnedFiles $OwnedFiles `
    -BuildResult $BuildResult -HealthResult $HealthResult -BusinessResult $BusinessResult `
    -Conclusion $Conclusion -RetroSummary $RetroSummary -RepoRoot $RepoRoot -DryRun:$DryRun
