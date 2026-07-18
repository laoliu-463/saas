param(
    [Alias("Env")]
    [ValidateSet("test", "real-pre")]
    [string]$TargetEnv = "real-pre",
    [ValidateSet("backend", "frontend", "full", "docs", "apifox")]
    [string]$Scope = "full",
    [object]$DeployRemote = $false,
    [string]$Message = "chore: harness automated run",
    [string]$BusinessCommand = "",
    [ValidateSet("off", "plan", "archive", "delete")]
    [string]$ContentMaintenance = "plan",
    [string]$ContentMaintenanceManifest = "",
    [string]$ReportKey = "agent-do",
    [AllowEmptyCollection()][string[]]$OwnedFiles = @(),
    [string]$RetroSummary = "No actionable Harness improvement was recorded; no standalone retro is required.",
    [switch]$AllowSourceCodeRetire,
    [switch]$SkipBusinessValidation,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

$config = Get-HarnessEnvConfig -Env $TargetEnv
$deployRemoteValue = Convert-HarnessBool -Value $DeployRemote

# 发布权限边界必须在 Node、Git、SSH 或任何其他外部动作之前执行。
if ($deployRemoteValue) {
    throw "普通 Codex 任务禁止直接部署；请提交候选变更，合并到 release/real-pre 后进入 Jenkins 唯一发布队列。"
}

$ownedFilesValue = @(Expand-HarnessOwnedFiles -OwnedFiles $OwnedFiles)
$reportKeyValue = ConvertTo-HarnessReportKey -ReportKey $ReportKey
$executionMode = Get-HarnessAgentDoExecutionMode -Scope $Scope
$allChangedFiles = @(Get-HarnessChangedFiles)
if (-not $DryRun -and $allChangedFiles.Count -gt 0 -and $ownedFilesValue.Count -eq 0) {
    throw "OwnedFiles is required when the worktree has changes."
}

Write-HarnessStage "Agent do"
Write-Host "Env: $TargetEnv"
Write-Host "Scope: $Scope"
Write-Host "ExecutionMode: $executionMode"
Write-Host "DeployRemote: false (Jenkins queue only)"
Write-Host "ReportKey: $reportKeyValue"
Write-Host "OwnedFiles: $($ownedFilesValue.Count)"
Write-Host "DryRun: $($DryRun.IsPresent)"

if ($executionMode -eq "NODE") {
    if ($ContentMaintenance -in @("archive", "delete")) {
        throw "代码 Scope 不允许在验证后执行归档或删除；请拆分为独立 docs 任务并提供 manifest。"
    }
    if ($ContentMaintenance -eq "plan") {
        Write-Host "代码 Scope 的 ContentMaintenance=plan 不产生文件变更；内容治理请使用独立 docs 任务。" -ForegroundColor Yellow
    }

    $stableJsonPath = Join-Path $config.RepoRoot "harness\reports\current\latest-$reportKeyValue.json"
    $stableMarkdownPath = Join-Path $config.RepoRoot "harness\reports\current\latest-$reportKeyValue.md"
    $previousRunId = ""
    if (Test-Path -LiteralPath $stableJsonPath -PathType Leaf) {
        try {
            $previousReport = Get-Content -Raw -Encoding UTF8 -LiteralPath $stableJsonPath | ConvertFrom-Json
            $previousRunId = [string]$previousReport.runId
        }
        catch {
            $previousRunId = "UNREADABLE_PREVIOUS_REPORT"
        }
    }

    $nodeArguments = @(New-HarnessNodeVerifyArguments `
        -Env $TargetEnv `
        -Scope $Scope `
        -ReportKey $reportKeyValue `
        -BusinessCommand $BusinessCommand `
        -SkipBusinessValidation:$SkipBusinessValidation `
        -DryRun:$DryRun)

    Assert-HarnessNoSensitiveChangedFiles
    $invocationId = [guid]::NewGuid().ToString("N")
    $gitSnapshotJson = Get-HarnessNodeGitSnapshotJson -RepoRoot $config.RepoRoot

    Write-HarnessStage "Node verify single source"
    Write-Host "npm $($nodeArguments -join ' ')"
    Push-Location $config.RepoRoot
    $previousInvocationId = $env:HARNESS_VERIFY_INVOCATION_ID
    $previousGitSnapshot = $env:HARNESS_VERIFY_GIT_SNAPSHOT_JSON
    try {
        $env:HARNESS_VERIFY_INVOCATION_ID = $invocationId
        $env:HARNESS_VERIFY_GIT_SNAPSHOT_JSON = $gitSnapshotJson
        $nodeOutput = @(npm @nodeArguments 2>&1)
        $nodeExitCode = $LASTEXITCODE
        $nodeOutput | ForEach-Object { Write-Host ([string]$_) }
    }
    finally {
        $env:HARNESS_VERIFY_INVOCATION_ID = $previousInvocationId
        $env:HARNESS_VERIFY_GIT_SNAPSHOT_JSON = $previousGitSnapshot
        Pop-Location
    }

    $receipt = $null
    if ($nodeExitCode -in @(0, 2)) {
        $receipt = Get-HarnessNodeVerifyReceipt `
            -Output $nodeOutput `
            -ExpectedInvocationId $invocationId `
            -ExpectedEnv $TargetEnv `
            -ExpectedScope $Scope `
            -ExpectedReportKey $reportKeyValue
    }

    $decision = Resolve-HarnessNodeVerifyDecision `
        -ExitCode $nodeExitCode `
        -StableJsonPath $stableJsonPath `
        -StableMarkdownPath $stableMarkdownPath `
        -ExpectedEnv $TargetEnv `
        -ExpectedScope $Scope `
        -ExpectedReportKey $reportKeyValue `
        -RepoRoot $config.RepoRoot `
        -ExpectedRunId $(if ($null -eq $receipt) { "" } else { [string]$receipt.runId }) `
        -ExpectedReceiptStatus $(if ($null -eq $receipt) { "" } else { [string]$receipt.status }) `
        -ExpectedRawJsonDigest $(if ($null -eq $receipt) { "" } else { [string]$receipt.evidenceDigests.rawJson }) `
        -ExpectedStableJsonDigest $(if ($null -eq $receipt) { "" } else { [string]$receipt.evidenceDigests.stableJson }) `
        -ExpectedStableMarkdownDigest $(if ($null -eq $receipt) { "" } else { [string]$receipt.evidenceDigests.stableMarkdown }) `
        -PreviousRunId $previousRunId
    Write-Host "Node decision: $($decision.Conclusion) - $($decision.Reason)"
    if (-not $decision.AllowGit) {
        throw $decision.Reason
    }

    $candidateOwnedFiles = @(
        $ownedFilesValue +
        "harness/reports/current/latest-$reportKeyValue.json" +
        "harness/reports/current/latest-$reportKeyValue.md" |
            Sort-Object -Unique
    )

    Write-HarnessStage "Harness file governance"
    $governanceArgs = @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $config.RepoRoot "harness\scripts\check-harness-limits.ps1"),
        "-RepoRoot", $config.RepoRoot,
        "-BaselineRef", "HEAD",
        "-NoReport",
        "-OwnedFiles", ($candidateOwnedFiles -join ";")
    )
    & powershell @governanceArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Harness file governance failed."
    }

    & (Join-Path $PSScriptRoot "git-push-safe.ps1") `
        -RepoRoot $config.RepoRoot `
        -Message $Message `
        -OwnedFiles $candidateOwnedFiles `
        -DryRun:$DryRun

    Write-HarnessStage "Agent do result"
    if ($decision.Conclusion -eq "PASS") {
        Write-Host "Conclusion: PASS；候选已受控提交/推送，未合并、未部署。" -ForegroundColor Green
        return
    }
    Write-Host "Conclusion: $($decision.Conclusion)；候选仅用于后续 CI，未升级为 PASS，未部署。" -ForegroundColor Yellow
    exit 2
}

# docs / apifox 暂留 PowerShell 兼容路径，不执行应用构建、容器重启或远端发布。
$buildResult = "not collected"
$healthResult = "Scope=${Scope}: compose restart and HTTP health checks skipped by scoped local harness path."
$businessResult = "not collected"
$contentMaintenanceResult = "not collected"
$contentMaintenanceOwnedFiles = @()
$taskOwnedFiles = @($ownedFilesValue)
$remoteResult = "remote not deployed; Jenkins queue required"
$conclusion = if ($Scope -eq "docs") { "PARTIAL" } else { "PASS" }

try {
    & (Join-Path $PSScriptRoot "safety-check.ps1") -Env $TargetEnv -Scope $Scope -DryRun:$DryRun

    if ($Scope -eq "docs") {
        $buildResult = "Scope=docs: build skipped."
        $businessResult = "Scope=docs: business validation not applicable; safety check executed."
    }
    else {
        Write-HarnessStage "Apifox OpenAPI local verification"
        $bashPath = Get-HarnessBashPath
        $repoRootForBash = Convert-HarnessPathToMsys -Path $config.RepoRoot
        $previousApifoxCli = $env:APIFOX_CLI
        if (Get-Command "apifox.cmd" -ErrorAction SilentlyContinue) {
            $env:APIFOX_CLI = "apifox.cmd"
        }
        elseif ([string]::IsNullOrWhiteSpace($env:APIFOX_CLI)) {
            $env:APIFOX_CLI = "apifox"
        }
        try {
            $verifyCommand = "cd '$repoRootForBash' && APIFOX_CLI='${env:APIFOX_CLI}' bash scripts/verify-openapi-apifox.sh"
            Write-Host "$bashPath -lc $verifyCommand"
            if (-not $DryRun) {
                & $bashPath "-lc" $verifyCommand
                if ($LASTEXITCODE -ne 0) {
                    throw "Apifox OpenAPI verification failed."
                }
            }
        }
        finally {
            $env:APIFOX_CLI = $previousApifoxCli
        }
        $buildResult = if ($DryRun) {
            "Scope=apifox: DRY-RUN; application build skipped and Apifox/OpenAPI local harness not executed by agent-do."
        }
        else {
            "Scope=apifox: application build skipped; Apifox/OpenAPI local harness PASS."
        }
        $businessResult = "Scope=apifox: business validation not applicable; cloud import not executed."
    }

    if ($ContentMaintenance -eq "off") {
        $contentMaintenanceResult = "Content maintenance skipped by -ContentMaintenance off."
    }
    else {
        $contentActionMap = @{ plan = "Plan"; archive = "Archive"; delete = "Delete" }
        $contentAction = $contentActionMap[$ContentMaintenance]
        Write-HarnessStage "Content maintenance"
        $retireParams = @{
            Action = $contentAction
            Reason = $Message
            RepoRoot = $config.RepoRoot
            PassThruResult = $true
        }
        if (-not [string]::IsNullOrWhiteSpace($ContentMaintenanceManifest)) {
            $retireParams["Manifest"] = $ContentMaintenanceManifest
        }
        if ($AllowSourceCodeRetire) {
            $retireParams["AllowSourceCode"] = $true
        }
        if ($DryRun) {
            $retireParams["DryRun"] = $true
        }
        $retireResult = & (Join-Path $PSScriptRoot "retire-content.ps1") @retireParams
        if ($null -ne $retireResult -and $retireResult.PSObject.Properties.Name -contains "OwnedFiles") {
            $contentMaintenanceOwnedFiles = @($retireResult.OwnedFiles)
        }
        $contentMaintenanceResult = "Content maintenance: $contentAction. Manifest=$ContentMaintenanceManifest. DryRun=$($DryRun.IsPresent)."
    }

    $taskOwnedFiles = @($ownedFilesValue + $contentMaintenanceOwnedFiles | Sort-Object -Unique)
    Write-HarnessStage "Harness file governance"
    $governanceArgs = @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $config.RepoRoot "harness\scripts\check-harness-limits.ps1"),
        "-RepoRoot", $config.RepoRoot,
        "-BaselineRef", "HEAD",
        "-NoReport"
    )
    if ($taskOwnedFiles.Count -gt 0) {
        $governanceArgs += @("-OwnedFiles", ($taskOwnedFiles -join ";"))
    }
    & powershell @governanceArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Harness file governance failed."
    }

    $reportPath = & (Join-Path $PSScriptRoot "collect-evidence.ps1") `
        -Env $TargetEnv `
        -Scope $Scope `
        -BuildResult $buildResult `
        -HealthResult $healthResult `
        -BusinessResult $businessResult `
        -ContentMaintenanceResult $contentMaintenanceResult `
        -RemoteResult $remoteResult `
        -Conclusion $conclusion `
        -DeployRemote $false `
        -ReportKey $reportKeyValue `
        -OwnedFiles $taskOwnedFiles `
        -RetroSummary $RetroSummary `
        -DryRun:$DryRun

    $commitOwnedFiles = @($taskOwnedFiles)
    if (-not $DryRun -and (Test-Path -LiteralPath $reportPath)) {
        $commitOwnedFiles += Get-HarnessRepoRelativePath -RepoRoot $config.RepoRoot -Path $reportPath
    }
    & (Join-Path $PSScriptRoot "git-push-safe.ps1") `
        -RepoRoot $config.RepoRoot `
        -Message $Message `
        -OwnedFiles $commitOwnedFiles `
        -DryRun:$DryRun

    Write-HarnessStage "Agent do result"
    Write-Host "Conclusion: $conclusion；未部署，发布必须进入 Jenkins 队列。" -ForegroundColor Green
}
catch {
    $failure = $_.Exception.Message
    Write-Host "agent-do failed: $failure" -ForegroundColor Red
    try {
        & (Join-Path $PSScriptRoot "collect-evidence.ps1") `
            -Env $TargetEnv `
            -Scope $Scope `
            -BuildResult $buildResult `
            -HealthResult $healthResult `
            -BusinessResult $businessResult `
            -ContentMaintenanceResult $contentMaintenanceResult `
            -RemoteResult $remoteResult `
            -Conclusion "FAIL" `
            -DeployRemote $false `
            -ReportKey $reportKeyValue `
            -OwnedFiles $taskOwnedFiles `
            -RetroSummary "agent-do failed: $failure" `
            -DryRun:$DryRun
    }
    catch {
        Write-Host "Failed to collect failure evidence: $($_.Exception.Message)" -ForegroundColor Red
    }
    throw
}
