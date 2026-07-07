param(
    [Alias("Env")]
    [ValidateSet("test", "real-pre")]
    [string]$TargetEnv = "real-pre",
    [ValidateSet("backend", "frontend", "full", "docs", "apifox")]
    [string]$Scope = "full",
    [string]$BuildResult = "not collected",
    [string]$HealthResult = "not collected",
    [string]$BusinessResult = "not collected",
    [string]$ContentMaintenanceResult = "not collected",
    [string]$RemoteResult = "remote not deployed",
    [ValidateSet("PASS", "PARTIAL", "FAIL")]
    [string]$Conclusion = "PARTIAL",
    [object]$DeployRemote = $false,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

$config = Get-HarnessEnvConfig -Env $TargetEnv
$deployRemoteValue = Convert-HarnessBool -Value $DeployRemote
$repoRoot = $config.RepoRoot
$reportPath = New-HarnessReportPath -RepoRoot $repoRoot

Write-HarnessStage "Collect evidence"
Write-Host "Report path: $reportPath"

$branch = Get-HarnessGitValue -Arguments @("branch", "--show-current")
$commit = Get-HarnessGitValue -Arguments @("rev-parse", "--short", "HEAD")
$statusOutput = & git -c core.quotepath=false status --short 2>$null
$status = if ($LASTEXITCODE -eq 0) { ($statusOutput -join "`n") } else { "" }
$changedFiles = @(Get-HarnessChangedFiles)
$changedFilesBlock = if ($changedFiles.Count -eq 0) { "(none)" } else { ($changedFiles -join "`n") }
$dirty = if ([string]::IsNullOrWhiteSpace($status)) { "clean" } else { "dirty" }

$composePs = "not collected"
$dockerPs = "not collected"
if (-not $DryRun) {
    Push-Location $repoRoot
    try {
        $composeArgs = Get-HarnessComposeArgs -Config $config
        $composePsOutput = & docker @($composeArgs + @("ps")) 2>&1
        $composePs = ($composePsOutput -join "`n").Trim()
        $dockerPsOutput = & docker ps --format "table {{.Names}}`t{{.Status}}`t{{.Ports}}" 2>&1
        $dockerPs = ($dockerPsOutput -join "`n").Trim()
    }
    catch {
        $composePs = "collection failed: $($_.Exception.Message)"
        $dockerPs = "collection failed: $($_.Exception.Message)"
    }
    finally {
        Pop-Location
    }
}

$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
$remoteFlag = if ($deployRemoteValue) { "true" } else { "false" }
$statusBlock = if ([string]::IsNullOrWhiteSpace($status)) { "(clean)" } else { $status }

$content = @"
# Evidence Report

## Metadata

- Time: $now
- Environment: $TargetEnv
- Scope: $Scope
- Branch: $branch
- Commit: $commit
- Worktree: $dirty
- Deploy remote: $remoteFlag

## Modified Files

~~~text
$changedFilesBlock
~~~

## Git Status

~~~text
$statusBlock
~~~

## Build Result

~~~text
$BuildResult
~~~

## Docker Status

### docker compose ps

~~~text
$composePs
~~~

### docker ps

~~~text
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

## Conclusion

$Conclusion

## Residual Risk

- Items marked as not collected are not proof of success.
- If real-pre lacks real orders or pick_source samples, record the result as PENDING or PARTIAL.
"@

if ($DryRun) {
    Write-Host "DRY-RUN evidence content:"
    Write-Host $content
}
else {
    Set-Content -LiteralPath $reportPath -Value $content -Encoding UTF8
}

Write-Host "Evidence report generated: $reportPath" -ForegroundColor Green
