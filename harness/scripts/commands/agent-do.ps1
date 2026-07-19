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
    [switch]$SkipRemoteBackup,
    [switch]$SkipBusinessValidation,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

$config = Get-HarnessEnvConfig -Env $TargetEnv
$deployRemoteValue = Convert-HarnessBool -Value $DeployRemote
$ownedFilesValue = @(Expand-HarnessOwnedFiles -OwnedFiles $OwnedFiles)
$buildResult = "not collected"
$healthResult = "not collected"
$businessResult = "not collected"
$contentMaintenanceResult = "not collected"
$contentMaintenanceOwnedFiles = @()
$taskOwnedFiles = @($ownedFilesValue)
$remoteResult = "remote not deployed"
$conclusion = "PARTIAL"

try {
    Write-HarnessStage "Agent do"
    Write-Host "Env: $TargetEnv"
    Write-Host "Scope: $Scope"
    Write-Host "DeployRemote: $deployRemoteValue"
    Write-Host "ContentMaintenance: $ContentMaintenance"
    Write-Host "ReportKey: $ReportKey"
    Write-Host "OwnedFiles: $($ownedFilesValue.Count)"
    Write-Host "SkipRemoteBackup: $SkipRemoteBackup"
    Write-Host "DryRun: $($DryRun.IsPresent)"

    $allChangedFiles = @(Get-HarnessChangedFiles)
    if (-not $DryRun -and $allChangedFiles.Count -gt 0 -and $ownedFilesValue.Count -eq 0) {
        throw "OwnedFiles is required when the worktree has changes."
    }

    & (Join-Path $PSScriptRoot "safety-check.ps1") -Env $TargetEnv -Scope $Scope -DryRun:$DryRun

    Push-Location $config.RepoRoot
    try {
        if ($Scope -eq "docs") {
            $buildResult = "Scope=docs: build skipped."
            $businessResult = "Scope=docs: business validation not applicable; safety check executed."
        }
        elseif ($Scope -eq "apifox") {
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
            if ($DryRun) {
                $buildResult = "Scope=apifox: DRY-RUN; application build skipped and Apifox/OpenAPI local harness not executed by agent-do."
            }
            else {
                $buildResult = "Scope=apifox: application build skipped; Apifox/OpenAPI local harness PASS."
            }
            $businessResult = "Scope=apifox: business validation not applicable; cloud import not executed."
        }
        else {
            if ($Scope -eq "backend" -or $Scope -eq "full") {
                Write-HarnessStage "Build backend"
                $cmd = "mvn -f backend/pom.xml -DskipTests package"
                Write-Host $cmd
                if (-not $DryRun) {
                    mvn -f backend/pom.xml -DskipTests package
                    if ($LASTEXITCODE -ne 0) {
                        throw "Backend build failed."
                    }
                }
                $buildResult = ($buildResult + "`nBackend build: PASS ($cmd)").Trim()
            }

            if ($Scope -eq "frontend" -or $Scope -eq "full") {
                Write-HarnessStage "Build frontend"
                $installCmd = if (Test-Path -LiteralPath (Join-Path $config.RepoRoot "frontend\package-lock.json")) {
                    "npm --prefix frontend ci"
                } else {
                    "npm --prefix frontend install"
                }
                Write-Host $installCmd
                if (-not $DryRun) {
                    if ($installCmd -like "* ci") {
                        npm --prefix frontend ci
                    } else {
                        npm --prefix frontend install
                    }
                    if ($LASTEXITCODE -ne 0) {
                        throw "Frontend dependency install failed."
                    }
                    npm --prefix frontend run build
                    if ($LASTEXITCODE -ne 0) {
                        throw "Frontend build failed."
                    }
                }
                $buildResult = ($buildResult + "`nFrontend build: PASS ($installCmd; npm --prefix frontend run build)").Trim()
            }

        }
    }
    finally {
        Pop-Location
    }

    if ($Scope -eq "docs" -or $Scope -eq "apifox") {
        $healthResult = "Scope=${Scope}: compose restart and HTTP health checks skipped by scoped local harness path."
    }
    else {
        & (Join-Path $PSScriptRoot "restart-compose.ps1") -Env $TargetEnv -Scope $Scope -DryRun:$DryRun
        & (Join-Path $PSScriptRoot "verify-local.ps1") -Env $TargetEnv -Scope $Scope -DryRun:$DryRun
        $healthResult = "Local health verification: PASS"
    }

    if ($Scope -ne "docs" -and $Scope -ne "apifox") {
        if ($SkipBusinessValidation) {
            $businessResult = "Business validation skipped by -SkipBusinessValidation; not a full PASS."
        }
        else {
            $effectiveBusinessCommand = $BusinessCommand
            if ([string]::IsNullOrWhiteSpace($effectiveBusinessCommand)) {
                if ($TargetEnv -eq "real-pre") {
                    $effectiveBusinessCommand = "npm run e2e:real-pre:p0:preflight"
                }
                else {
                    $effectiveBusinessCommand = "npm run e2e:v1-p0"
                }
            }

            Write-HarnessStage "Business validation"
            Write-Host $effectiveBusinessCommand
            if (-not $DryRun) {
                Push-Location $config.RepoRoot
                try {
                    powershell -NoProfile -ExecutionPolicy Bypass -Command $effectiveBusinessCommand
                    if ($LASTEXITCODE -ne 0) {
                        throw "Business validation failed: $effectiveBusinessCommand"
                    }
                }
                finally {
                    Pop-Location
                }
            }
            $businessResult = "Business validation: PASS ($effectiveBusinessCommand)"
        }
    }

    if ($ContentMaintenance -eq "off") {
        $contentMaintenanceResult = "Content maintenance skipped by -ContentMaintenance off."
    }
    else {
        $contentActionMap = @{
            plan = "Plan"
            archive = "Archive"
            delete = "Delete"
        }
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

    if ($Scope -eq "docs" -or $SkipBusinessValidation -or $deployRemoteValue) {
        $conclusion = "PARTIAL"
    }
    else {
        $conclusion = "PASS"
    }

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
        -DeployRemote $deployRemoteValue `
        -ReportKey $ReportKey `
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

    if ($deployRemoteValue) {
        & (Join-Path $PSScriptRoot "deploy-remote.ps1") `
            -Env real-pre `
            -SkipBackup:$SkipRemoteBackup `
            -DryRun:$DryRun
        $remoteResult = "Remote deploy: PASS"
        $remoteReportPath = & (Join-Path $PSScriptRoot "collect-evidence.ps1") `
            -Env $TargetEnv `
            -Scope $Scope `
            -BuildResult $buildResult `
            -HealthResult $healthResult `
            -BusinessResult $businessResult `
            -ContentMaintenanceResult $contentMaintenanceResult `
            -RemoteResult $remoteResult `
            -Conclusion "PASS" `
            -DeployRemote $true `
            -ReportKey $ReportKey `
            -OwnedFiles $taskOwnedFiles `
            -RetroSummary $RetroSummary `
            -DryRun:$DryRun
        if (-not $DryRun -and (Test-Path -LiteralPath $remoteReportPath)) {
            $remoteReportRelative = Get-HarnessRepoRelativePath -RepoRoot $config.RepoRoot -Path $remoteReportPath
            & (Join-Path $PSScriptRoot "git-push-safe.ps1") `
                -RepoRoot $config.RepoRoot `
                -Message "docs(harness): record remote deployment evidence" `
                -OwnedFiles @($remoteReportRelative)
        }
        $conclusion = "PASS"
    }

    Write-Host "Review HARNESS_CHANGELOG.md and update it when Harness behavior changed." -ForegroundColor Yellow

    Write-HarnessStage "Agent do result"
    Write-Host "Conclusion: $conclusion" -ForegroundColor Green
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
            -DeployRemote $deployRemoteValue `
            -ReportKey $ReportKey `
            -OwnedFiles $taskOwnedFiles `
            -RetroSummary "agent-do failed: $failure" `
            -DryRun:$DryRun
    }
    catch {
        Write-Host "Failed to collect failure evidence: $($_.Exception.Message)" -ForegroundColor Red
    }
    throw
}
