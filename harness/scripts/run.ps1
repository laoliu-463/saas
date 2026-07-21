param(
    [Parameter(Position = 0)]
    [ValidateSet("inspect", "verify", "help")]
    [string]$Action = "help",
    [ValidateSet("test", "real-pre")]
    [string]$TargetEnv = "real-pre",
    [string]$ReportKey = "",
    [string]$BusinessCommand = "",
    [switch]$SkipBusinessValidation,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
. (Join-Path $repoRoot "harness\scripts\commands\_lib.ps1")

function Show-HarnessHelp {
    @(
        "Harness developer entrypoint",
        "",
        "Usage:",
        "  .\harness.cmd inspect",
        "  .\harness.cmd verify",
        "",
        "Default environment: real-pre; use -TargetEnv test to override.",
        "verify auto-selects docs, backend, frontend, or full from worktree changes.",
        "",
        "Safety: inspect, verify, and write evidence only; no commit, push, SSH, or remote deploy.",
        "Advanced options: -ReportKey, -BusinessCommand, -SkipBusinessValidation, -DryRun."
    ) | Write-Host
}

function Get-HarnessAutoScope {
    $changed = @(Get-HarnessChangedFiles | ForEach-Object { ([string]$_).Replace("\\", "/") })
    if ($changed.Count -eq 0) { return "full" }

    $backend = $false
    $frontend = $false
    $requiresFull = $false
    foreach ($file in $changed) {
        if ($file -match '^backend/') {
            $backend = $true
            continue
        }
        if ($file -match '^frontend/') {
            $frontend = $true
            continue
        }
        $isSimpleDocs = $file -match '^(README[^/]*|CONTRIBUTING\.md|docs/|docs\\|runtime/qa/out/)' -or
            $file -match '\.(md|txt)$'
        if (-not $isSimpleDocs) { $requiresFull = $true }
    }

    if ($requiresFull -or ($backend -and $frontend)) { return "full" }
    if ($backend) { return "backend" }
    if ($frontend) { return "frontend" }
    return "docs"
}

function Get-HarnessDefaultReportKey {
    if (-not [string]::IsNullOrWhiteSpace($ReportKey)) {
        return ConvertTo-HarnessReportKey -ReportKey $ReportKey
    }
    $branch = Get-HarnessGitValue -Arguments @("branch", "--show-current")
    if ([string]::IsNullOrWhiteSpace($branch)) { $branch = "local" }
    $key = ("verify-" + $branch.ToLowerInvariant() -replace '[^a-z0-9-]', '-')
    $key = $key.Trim('-')
    if ($key.Length -gt 64) { $key = $key.Substring(0, 64).TrimEnd('-') }
    return ConvertTo-HarnessReportKey -ReportKey $key
}

function Invoke-HarnessNodeInspect {
    Push-Location $repoRoot
    try {
        Write-Host "=== Harness inspect (read-only) ===" -ForegroundColor Cyan
        $output = & npm run harness:node:inspect -- --env $TargetEnv 2>&1
        $exitCode = $LASTEXITCODE
        $output | ForEach-Object { Write-Host ([string]$_) }
        return $exitCode
    }
    finally { Pop-Location }
}

function Invoke-HarnessDocsVerify {
    $ownedFiles = @(Get-HarnessChangedFiles | ForEach-Object { ([string]$_).Replace("\\", "/") })
    Write-HarnessStage "Docs verification"
    & (Join-Path $repoRoot "harness\scripts\commands\safety-check.ps1") -Env $TargetEnv -Scope docs -DryRun:$DryRun
    if ($LASTEXITCODE -ne 0) { return 1 }

    $governanceArgs = @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $repoRoot "harness\scripts\check-harness-limits.ps1"),
        "-RepoRoot", $repoRoot, "-BaselineRef", "HEAD", "-NoReport"
    )
    if ($ownedFiles.Count -gt 0) { $governanceArgs += @("-OwnedFiles", ($ownedFiles -join ";")) }
    & powershell @governanceArgs
    if ($LASTEXITCODE -ne 0) { return 1 }

    $report = & (Join-Path $repoRoot "harness\scripts\commands\collect-evidence.ps1") `
        -Env $TargetEnv -Scope docs `
        -BuildResult "Application build: NOT_REQUIRED (documentation/governance-only change)." `
        -HealthResult "Container restart and health check: NOT_REQUIRED (documentation/governance-only change)." `
        -BusinessResult "E2E: NOT_REQUIRED (documentation/governance-only change)." `
        -ContentMaintenanceResult "Content maintenance: NOT_REQUIRED." `
        -RemoteResult "remote not deployed" -Conclusion PASS -DeployRemote $false `
        -ReportKey (Get-HarnessDefaultReportKey) -OwnedFiles $ownedFiles `
        -RetroSummary "No actionable Harness improvement was recorded; no standalone retro is required." `
        -SkipRuntimeCollection -DryRun:$DryRun
    Write-Host "Evidence: $report" -ForegroundColor Green
    return 0
}

function Invoke-HarnessNodeVerify {
    param([Parameter(Mandatory = $true)][ValidateSet("backend", "frontend", "full")][string]$Scope)

    Assert-HarnessNoSensitiveChangedFiles
    $reportKeyValue = Get-HarnessDefaultReportKey
    $snapshotFile = New-HarnessNodeGitSnapshotFile -RepoRoot $repoRoot
    $invocation = [guid]::NewGuid().ToString("N")
    $arguments = @("run", "harness:node:verify", "--", "--env", $TargetEnv, "--scope", $Scope, "--report-key", $reportKeyValue)
    if (-not [string]::IsNullOrWhiteSpace($BusinessCommand)) {
        $arguments += @("--business-command", $BusinessCommand)
    }
    if ($SkipBusinessValidation) { $arguments += "--skip-business-validation" }
    if ($DryRun) { $arguments += "--dry-run" }

    Write-Host "=== Harness verify (auto scope: $Scope) ===" -ForegroundColor Cyan
    $previousInvocation = $env:HARNESS_VERIFY_INVOCATION_ID
    $previousSnapshot = $env:HARNESS_VERIFY_GIT_SNAPSHOT_JSON
    $previousSnapshotFile = $env:HARNESS_VERIFY_GIT_SNAPSHOT_FILE
    try {
        $env:HARNESS_VERIFY_INVOCATION_ID = $invocation
        $env:HARNESS_VERIFY_GIT_SNAPSHOT_JSON = $null
        $env:HARNESS_VERIFY_GIT_SNAPSHOT_FILE = $snapshotFile
        $output = & npm @arguments 2>&1
        $exitCode = $LASTEXITCODE
        $output | ForEach-Object { Write-Host ([string]$_) }
        return $exitCode
    }
    finally {
        $env:HARNESS_VERIFY_INVOCATION_ID = $previousInvocation
        $env:HARNESS_VERIFY_GIT_SNAPSHOT_JSON = $previousSnapshot
        $env:HARNESS_VERIFY_GIT_SNAPSHOT_FILE = $previousSnapshotFile
        Remove-Item -LiteralPath $snapshotFile -Force -ErrorAction SilentlyContinue
    }
}

try {
    if ($Action -eq "help") { Show-HarnessHelp; exit 0 }
    Push-Location $repoRoot
    try {
        if ($Action -eq "inspect") { exit (Invoke-HarnessNodeInspect) }
        $scope = Get-HarnessAutoScope
        if ($scope -eq "docs") { exit (Invoke-HarnessDocsVerify) }
        exit (Invoke-HarnessNodeVerify -Scope $scope)
    }
    finally { Pop-Location }
}
catch {
    Write-Error ("Harness execution blocked: " + $_.Exception.Message)
    exit 2
}
