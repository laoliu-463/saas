param(
    [Alias('Env')][ValidateSet('test', 'real-pre')][string]$TargetEnv = 'real-pre',
    [ValidateSet('backend', 'frontend', 'full', 'docs', 'apifox', 'deploy', 'ci')][string]$Scope = 'full',
    [object]$UsedAgentDo = $true,
    [object]$DeployRemote = $false,
    [string]$Notes = '',
    [string]$RepoRoot = '',
    [string]$ReportKey = 'agent-do',
    [switch]$Actionable,
    [string]$Owner = '',
    [string]$NextAction = '',
    [string]$Verification = '',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_lib.ps1')

if (-not $Actionable) {
    Write-Output 'Retro not generated: no actionable Harness improvement was provided; keep the summary inline in evidence.'
    return
}
if ([string]::IsNullOrWhiteSpace($Owner)) { throw 'Owner is required for an actionable standalone retro.' }
if ([string]::IsNullOrWhiteSpace($NextAction)) { throw 'NextAction is required for an actionable standalone retro.' }
if ([string]::IsNullOrWhiteSpace($Verification)) { throw 'Verification is required for an actionable standalone retro.' }

if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Get-HarnessRepoRoot }
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$usedAgentDoValue = Convert-HarnessBool -Value $UsedAgentDo
$deployRemoteValue = Convert-HarnessBool -Value $DeployRemote
$reportPath = New-HarnessReportPath -RepoRoot $RepoRoot -ReportKey "retro-$(ConvertTo-HarnessReportKey -ReportKey $ReportKey)"
$now = Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'
$branch = (& git -C $RepoRoot branch --show-current 2>$null).Trim()
$commit = (& git -C $RepoRoot rev-parse --short HEAD 2>$null).Trim()

$content = @"
# Harness Actionable Retro

## Metadata

- Time: $now
- Environment: $TargetEnv
- Scope: $Scope
- Branch: $branch
- Commit: $commit
- Used agent-do.ps1: $usedAgentDoValue
- Deploy remote requested: $deployRemoteValue

## Owner

$Owner

## Next Action

$NextAction

## Verification

$Verification

## Notes

$Notes
"@

Write-HarnessStage 'New actionable retro'
if ($DryRun) {
    Write-Host $content
}
else {
    [void](Write-HarnessFileIfChanged -Path $reportPath -Content $content)
}
Write-Output $reportPath
