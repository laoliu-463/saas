param(
    [Alias("Env")]
    [ValidateSet("test", "real-pre")]
    [string]$TargetEnv = "test",
    [ValidateSet("backend", "frontend", "full", "docs")]
    [string]$Scope = "full",
    [object]$DeployRemote = $false,
    [string]$Message = "chore: harness automated run",
    [string]$BusinessCommand = "",
    [ValidateSet("off", "plan", "archive", "delete")]
    [string]$ContentMaintenance = "plan",
    [string]$ContentMaintenanceManifest = "",
    [switch]$AllowSourceCodeRetire,
    [switch]$SkipBusinessValidation,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

$config = Get-HarnessEnvConfig -Env $TargetEnv
$deployRemoteValue = Convert-HarnessBool -Value $DeployRemote
$buildResult = "not collected"
$healthResult = "not collected"
$businessResult = "not collected"
$contentMaintenanceResult = "not collected"
$remoteResult = "remote not deployed"
$conclusion = "PARTIAL"

try {
    Write-HarnessStage "Agent do"
    Write-Host "Env: $TargetEnv"
    Write-Host "Scope: $Scope"
    Write-Host "DeployRemote: $deployRemoteValue"
    Write-Host "ContentMaintenance: $ContentMaintenance"
    Write-Host "DryRun: $($DryRun.IsPresent)"

    & (Join-Path $PSScriptRoot "safety-check.ps1") -Env $TargetEnv -Scope $Scope -DryRun:$DryRun

    Push-Location $config.RepoRoot
    try {
        if ($Scope -eq "docs") {
            $buildResult = "Scope=docs: build skipped."
            $businessResult = "Scope=docs: business validation not applicable; safety check executed."
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
                    powershell -NoProfile -ExecutionPolicy Bypass -Command $effectiveBusinessCommand
                    if ($LASTEXITCODE -ne 0) {
                        throw "Business validation failed: $effectiveBusinessCommand"
                    }
                }
                $businessResult = "Business validation: PASS ($effectiveBusinessCommand)"
            }
        }
    }
    finally {
        Pop-Location
    }

    & (Join-Path $PSScriptRoot "restart-compose.ps1") -Env $TargetEnv -Scope $Scope -DryRun:$DryRun
    & (Join-Path $PSScriptRoot "verify-local.ps1") -Env $TargetEnv -Scope $Scope -DryRun:$DryRun
    if ($Scope -eq "docs") {
        $healthResult = "Scope=docs: compose restart and HTTP health checks skipped by docs-only path."
    }
    else {
        $healthResult = "Local health verification: PASS"
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
        & (Join-Path $PSScriptRoot "retire-content.ps1") @retireParams
        $contentMaintenanceResult = "Content maintenance: $contentAction. Manifest=$ContentMaintenanceManifest. DryRun=$($DryRun.IsPresent)."
    }

    & (Join-Path $PSScriptRoot "collect-evidence.ps1") `
        -Env $TargetEnv `
        -Scope $Scope `
        -BuildResult $buildResult `
        -HealthResult $healthResult `
        -BusinessResult $businessResult `
        -ContentMaintenanceResult $contentMaintenanceResult `
        -RemoteResult $remoteResult `
        -Conclusion "PARTIAL" `
        -DeployRemote $deployRemoteValue `
        -DryRun:$DryRun

    & (Join-Path $PSScriptRoot "git-push-safe.ps1") -Message $Message -DryRun:$DryRun

    if ($deployRemoteValue) {
        & (Join-Path $PSScriptRoot "deploy-remote.ps1") -Env real-pre -DryRun:$DryRun
        $remoteResult = "Remote deploy: PASS"
        & (Join-Path $PSScriptRoot "collect-evidence.ps1") `
            -Env $TargetEnv `
            -Scope $Scope `
            -BuildResult $buildResult `
            -HealthResult $healthResult `
            -BusinessResult $businessResult `
            -ContentMaintenanceResult $contentMaintenanceResult `
            -RemoteResult $remoteResult `
            -Conclusion "PASS" `
            -DeployRemote $true `
            -DryRun:$DryRun
    }

    & (Join-Path $PSScriptRoot "new-retro.ps1") `
        -Env $TargetEnv `
        -Scope $Scope `
        -UsedAgentDo $true `
        -DeployRemote $deployRemoteValue `
        -Notes "agent-do completed; review whether HARNESS_CHANGELOG.md needs an entry." `
        -DryRun:$DryRun

    Write-Host "Review HARNESS_CHANGELOG.md and update it when Harness behavior changed." -ForegroundColor Yellow

    if ($Scope -eq "docs" -or $SkipBusinessValidation) {
        $conclusion = "PARTIAL"
    }
    else {
        $conclusion = "PASS"
    }

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
            -DryRun:$DryRun
    }
    catch {
        Write-Host "Failed to collect failure evidence: $($_.Exception.Message)" -ForegroundColor Red
    }
    throw
}
