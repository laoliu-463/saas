param(
    [Alias('Env')][ValidateSet('test', 'real-pre')][string]$TargetEnv = 'real-pre',
    [ValidateSet('backend', 'frontend', 'full', 'docs', 'apifox')][string]$Scope = 'full',
    [string]$BuildResult = 'not collected',
    [string]$HealthResult = 'not collected',
    [string]$BusinessResult = 'not collected',
    [string]$ContentMaintenanceResult = 'not collected',
    [string]$RemoteResult = 'remote not deployed',
    [ValidateSet('PASS', 'PARTIAL', 'FAIL')][string]$Conclusion = 'PARTIAL',
    [object]$DeployRemote = $false,
    [string]$ReportKey = 'agent-do',
    [AllowEmptyCollection()][string[]]$OwnedFiles = @(),
    [string]$RetroSummary = 'No actionable Harness improvement was recorded.',
    [string]$RepoRoot = '',
    [switch]$SkipRuntimeCollection,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_lib.ps1')

$config = $null
if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $config = Get-HarnessEnvConfig -Env $TargetEnv
    $RepoRoot = $config.RepoRoot
}
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$deployRemoteValue = Convert-HarnessBool -Value $DeployRemote
$owned = @(Expand-HarnessOwnedFiles -OwnedFiles $OwnedFiles)
$reportPath = New-HarnessReportPath -RepoRoot $RepoRoot -ReportKey $ReportKey

function ConvertTo-EvidenceCommandText {
    param([AllowNull()][object[]]$Output)

    $lines = foreach ($item in @($Output)) {
        foreach ($line in @(([string]$item) -split "`r?`n")) {
            $line.TrimEnd()
        }
    }
    return ($lines -join "`n").Trim()
}

Write-HarnessStage 'Collect evidence'
Write-Host "Report path: $reportPath"

$branch = (& git -C $RepoRoot branch --show-current 2>$null).Trim()
$commit = (& git -C $RepoRoot rev-parse --short HEAD 2>$null).Trim()
$statusOutput = if ($owned.Count -eq 0) {
    @()
}
elseif ($owned.Count -gt 100) {
    # Windows 命令行不能安全承载数百个 OwnedFiles 路径；OwnedFiles 已在下方
    # 记录，因此大列表只读取一次未过滤状态用于人类摘要。
    @(& git -C $RepoRoot -c core.quotepath=false status --short 2>$null)
}
else {
    @(& git -C $RepoRoot -c core.quotepath=false status --short -- $owned 2>$null)
}
$status = ConvertTo-EvidenceCommandText -Output $statusOutput
$changedFilesBlock = if ($owned.Count -eq 0) { '(none)' } else { ($owned -join "`n") }
$dirty = if ([string]::IsNullOrWhiteSpace($status)) { 'clean' } else { 'dirty' }

$composePs = 'not collected'
$dockerPs = 'not collected'
if (-not $DryRun -and -not $SkipRuntimeCollection -and $Scope -notin @('docs', 'apifox')) {
    if ($null -eq $config) { throw 'Runtime collection with RepoRoot override is not supported.' }
    Push-Location $RepoRoot
    try {
        $composeArgs = Get-HarnessComposeArgs -Config $config
        $composePs = ConvertTo-EvidenceCommandText -Output @(& docker @($composeArgs + @('ps')) 2>&1)
        $dockerPs = ConvertTo-EvidenceCommandText -Output @(& docker ps --format "table {{.Names}}`t{{.Status}}`t{{.Ports}}" 2>&1)
    }
    catch {
        $composePs = "collection failed: $($_.Exception.Message)"
        $dockerPs = "collection failed: $($_.Exception.Message)"
    }
    finally { Pop-Location }
}

$now = Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'
$remoteFlag = if ($deployRemoteValue) { 'true' } else { 'false' }
$statusBlock = if ([string]::IsNullOrWhiteSpace($status)) { '(clean)' } else { $status }
$content = @"
# Evidence Report

## Metadata

- Time: $now
- Environment: $TargetEnv
- Scope: $Scope
- Branch: $branch
- Commit: $commit
- Owned worktree: $dirty
- Deploy remote: $remoteFlag

## Owned Files

~~~text
$changedFilesBlock
~~~

## Owned Git Status

~~~text
$statusBlock
~~~

## Build Result

~~~text
$BuildResult
~~~

## Docker Status

~~~text
$composePs
$dockerPs
~~~

## Health Check Result

~~~text
$HealthResult
~~~

## Business Validation Result

~~~text
$BusinessResult
~~~

## Content Maintenance Result

~~~text
$ContentMaintenanceResult
~~~

## Remote Deploy Result

~~~text
$RemoteResult
~~~

## Retro Summary

$RetroSummary

## Conclusion

$Conclusion

## Residual Risk

- Items marked as not collected are not proof of success.
"@

if ($DryRun) {
    Write-Host 'DRY-RUN evidence content:'
    Write-Host $content
}
else {
    [void](Write-HarnessFileIfChanged -Path $reportPath -Content $content)
}
Write-Output $reportPath
