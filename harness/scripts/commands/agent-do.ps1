param(
    [Alias("Env")]
    [ValidateSet("test", "real-pre")]
    [string]$TargetEnv = "real-pre",
    [ValidateSet("backend", "frontend", "full", "docs", "apifox", "deploy", "ci")]
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
    [switch]$SkipRemoteBackup,
    [switch]$SkipBusinessValidation,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

$config = Get-HarnessEnvConfig -Env $TargetEnv
$deployRemoteValue = Convert-HarnessBool -Value $DeployRemote
$harnessPowerShell = if (Get-Command pwsh -ErrorAction SilentlyContinue) { "pwsh" } else { "powershell" }

# 发布权限边界必须在 Node、Git、SSH 或任何其他外部动作之前执行。
if ($deployRemoteValue) {
    throw "Direct remote deployment is disabled. 普通 Codex 任务禁止直接部署；请提交候选变更，合并到 release/real-pre 后进入 Jenkins release queue（唯一发布队列）。"
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

    $stableJsonPath = Join-Path $config.RepoRoot "runtime\qa\out\latest-$reportKeyValue.json"
    $stableMarkdownPath = Join-Path $config.RepoRoot "runtime\qa\out\latest-$reportKeyValue.md"
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

    $scopedSkipBusinessValidation = $SkipBusinessValidation -or `
        ($Scope -in @("backend", "frontend") -and [string]::IsNullOrWhiteSpace($BusinessCommand))
    $nodeArguments = @(New-HarnessNodeVerifyArguments `
        -Env $TargetEnv `
        -Scope $Scope `
        -ReportKey $reportKeyValue `
        -BusinessCommand $BusinessCommand `
        -SkipBusinessValidation:$scopedSkipBusinessValidation `
        -DryRun:$DryRun)

    Assert-HarnessNoSensitiveChangedFiles
    $invocationId = [guid]::NewGuid().ToString("N")
    $gitSnapshotFile = New-HarnessNodeGitSnapshotFile -RepoRoot $config.RepoRoot

    Write-HarnessStage "Node verify single source"
    Write-Host "npm $($nodeArguments -join ' ')"
    Push-Location $config.RepoRoot
    $previousInvocationId = $env:HARNESS_VERIFY_INVOCATION_ID
    $previousGitSnapshot = $env:HARNESS_VERIFY_GIT_SNAPSHOT_JSON
    $previousGitSnapshotFile = $env:HARNESS_VERIFY_GIT_SNAPSHOT_FILE
    try {
        $env:HARNESS_VERIFY_INVOCATION_ID = $invocationId
        $env:HARNESS_VERIFY_GIT_SNAPSHOT_JSON = $null
        $env:HARNESS_VERIFY_GIT_SNAPSHOT_FILE = $gitSnapshotFile
        $nodeOutput = @(npm @nodeArguments 2>&1)
        $nodeExitCode = $LASTEXITCODE
        $nodeOutput | ForEach-Object { Write-Host ([string]$_) }
    }
    finally {
        $env:HARNESS_VERIFY_INVOCATION_ID = $previousInvocationId
        $env:HARNESS_VERIFY_GIT_SNAPSHOT_JSON = $previousGitSnapshot
        $env:HARNESS_VERIFY_GIT_SNAPSHOT_FILE = $previousGitSnapshotFile
        Remove-Item -LiteralPath $gitSnapshotFile -Force -ErrorAction SilentlyContinue
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
        "runtime/qa/out/latest-$reportKeyValue.json" +
        "runtime/qa/out/latest-$reportKeyValue.md" |
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
    & $harnessPowerShell @governanceArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Harness file governance failed."
    }

    Write-HarnessStage "Agent do result"
    if ($decision.Conclusion -eq "PASS") {
        Write-Host "Conclusion: PASS；候选已通过本地验证，未自动提交、推送、合并或部署。" -ForegroundColor Green
        return
    }
    Write-Host "Conclusion: $($decision.Conclusion)；候选仅用于后续 CI，未升级为 PASS，未部署。" -ForegroundColor Yellow
    exit 2
}

# docs / apifox / deploy / ci 暂留 PowerShell 兼容路径，不执行应用构建、容器重启或远端发布。
$buildResult = "not collected"
$healthResult = "Scope=${Scope}: Container restart and health check: NOT_REQUIRED (documentation/governance-only change)."
$businessResult = "not collected"
$contentMaintenanceResult = "not collected"
$contentMaintenanceOwnedFiles = @()
$taskOwnedFiles = @($ownedFilesValue)
$remoteResult = "remote not deployed; Jenkins queue required"
$conclusion = "PASS"

try {
    & (Join-Path $PSScriptRoot "safety-check.ps1") -Env $TargetEnv -Scope $Scope -DryRun:$DryRun

    if ($Scope -eq "docs") {
        $buildResult = "Application build: NOT_REQUIRED (documentation/governance-only change)."
        $businessResult = "E2E: NOT_REQUIRED (documentation/governance-only change)."
    }
    elseif ($Scope -eq "deploy") {
        Write-HarnessStage "Deployment-only verification"
        $bashPath = Get-HarnessBashPath
        $shellScripts = @(Get-ChildItem -LiteralPath (Join-Path $config.RepoRoot "scripts") -Filter "*.sh" -File -ErrorAction SilentlyContinue)
        foreach ($shellScript in $shellScripts) {
            $scriptPath = Convert-HarnessPathToMsys -Path $shellScript.FullName
            if (-not $DryRun) { & $bashPath "-n" $scriptPath; if ($LASTEXITCODE -ne 0) { throw "Deployment shell syntax failed: $($shellScript.Name)" } }
        }
        if (-not (Test-Path -LiteralPath $config.EnvFile)) { throw "Deployment validation requires $($config.EnvFile)." }
        $composeArgs = Get-HarnessComposeArgs -Config $config
        if (-not $DryRun) { Invoke-HarnessExternal -CommandName "docker compose config" -Script { docker @($composeArgs + @("config", "--quiet")) } }
        $releaseQueueTest = Join-Path $config.RepoRoot "harness\scripts\tests\release-queue-governance.Tests.ps1"
        if ((Test-Path -LiteralPath $releaseQueueTest) -and (-not $DryRun)) {
            $pester = Get-Command Invoke-Pester -ErrorAction SilentlyContinue
            if (-not $pester) { throw "Pester is required for deployment governance validation." }
            $releaseResult = Invoke-Pester -Script $releaseQueueTest -PassThru
            if ($releaseResult.FailedCount -gt 0) { throw "Deployment governance tests failed." }
        }
        $buildResult = "Scope=deploy: shell syntax, Compose config and release-queue governance validated; application build/restart skipped."
        $businessResult = "Scope=deploy: business validation not applicable."
    }
    elseif ($Scope -eq "ci") {
        Write-HarnessStage "CI-only verification"
        $pester = Get-Command Invoke-Pester -ErrorAction SilentlyContinue
        if (-not $pester) { throw "Pester is required for Scope=ci." }
        $ciTests = @(Get-ChildItem -LiteralPath (Join-Path $config.RepoRoot "harness\scripts\tests") -Filter "*.Tests.ps1" -File)
        if ($ciTests.Count -eq 0) { throw "No Harness tests found." }
        if (-not $DryRun) {
            $pesterResult = Invoke-Pester -Script @($ciTests.FullName) -PassThru
            if ($pesterResult.FailedCount -gt 0) { throw "CI-only Harness tests failed." }
        }
        $buildResult = "Scope=ci: Harness Pester validated; application/Docker build, restart and business validation skipped."
        $businessResult = "Scope=ci: business validation not applicable."
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
    & $harnessPowerShell @governanceArgs
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
        -SkipRuntimeCollection:($Scope -in @("docs", "apifox", "deploy", "ci")) `
        -DryRun:$DryRun

    Write-Host "Evidence collected at $reportPath. Git commit and push are explicit follow-up commands; agent-do does not mutate Git history." -ForegroundColor Yellow

    Write-Host "Review HARNESS_CHANGELOG.md and update it when Harness behavior changed." -ForegroundColor Yellow
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
            -SkipRuntimeCollection:($Scope -in @("docs", "apifox", "deploy", "ci")) `
            -DryRun:$DryRun
    }
    catch {
        Write-Host "Failed to collect failure evidence: $($_.Exception.Message)" -ForegroundColor Red
    }
    throw
}
