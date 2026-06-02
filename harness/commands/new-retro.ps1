param(
    [Alias("Env")]
    [ValidateSet("test", "real-pre")]
    [string]$TargetEnv = "real-pre",
    [ValidateSet("backend", "frontend", "full", "docs")]
    [string]$Scope = "full",
    [object]$UsedAgentDo = $true,
    [object]$DeployRemote = $false,
    [string]$Notes = "not collected",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

$repoRoot = Get-HarnessRepoRoot
$usedAgentDoValue = Convert-HarnessBool -Value $UsedAgentDo
$deployRemoteValue = Convert-HarnessBool -Value $DeployRemote
$reportsDir = Join-Path $repoRoot "harness\reports"
if (-not (Test-Path -LiteralPath $reportsDir)) {
    New-Item -ItemType Directory -Force -Path $reportsDir | Out-Null
}
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $reportsDir "retro-$stamp.md"
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
$branch = Get-HarnessGitValue -Arguments @("branch", "--show-current")
$commit = Get-HarnessGitValue -Arguments @("rev-parse", "--short", "HEAD")

$content = @"
# Harness Retro Summary

## 1. Harness execution

- Time: $now
- Environment: $TargetEnv
- Scope: $Scope
- Branch: $branch
- Commit: $commit
- Used agent-do.ps1: $usedAgentDoValue
- Deploy remote requested: $deployRemoteValue

## 2. Repeated probing

Not collected automatically. Review command history manually if needed.

## 3. Script failures

Not collected automatically. Check latest evidence report and terminal output.

## 4. Verification sufficiency

Scope=$Scope. If Scope=docs, build/restart/business E2E may be intentionally skipped and must not be treated as PASS for code.

## 5. Harness issues exposed

$Notes

## 6. Files to upgrade

- AGENTS.md: update only if execution rules changed.
- CURRENT_STATE.md: update if project state changed.
- TASK_ROUTING.md: update if routing gaps appeared.
- commands: update if script behavior failed or was ambiguous.
- evals: update if validation could not prove business result.
- skills: update if debugging workflow was incomplete.
- runbooks: update if humans/agents cannot execute directly.

## 7. Need new script

Review manually.

## 8. Need new Eval

Review manually.

## 9. Need update AGENTS.md

Review manually.

## 10. Must fix before next task

- Do not claim completion without evidence.
- Keep HARNESS_CHANGELOG.md current when Harness files change.
"@

Write-HarnessStage "New retro"
Write-Host "Retro path: $reportPath"
if ($DryRun) {
    Write-Host "DRY-RUN retro content:"
    Write-Host $content
}
else {
    Set-Content -LiteralPath $reportPath -Value $content -Encoding UTF8
}
Write-Host "Retro summary generated: $reportPath" -ForegroundColor Green
