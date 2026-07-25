param(
    [Alias("Env")]
    [ValidateSet("test", "real-pre")]
    [string]$TargetEnv = "real-pre",
    [ValidateSet("backend", "frontend", "full", "docs", "apifox", "deploy", "ci")]
    [string]$Scope = "full",
    [switch]$DryRun,
    [switch]$SkipSafetyCheck
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

$config = Get-HarnessEnvConfig -Env $TargetEnv

Write-HarnessStage "Compose restart"
if (-not $SkipSafetyCheck) {
    & (Join-Path $PSScriptRoot "safety-check.ps1") -Env $TargetEnv -Scope $Scope -DryRun:$DryRun
}

if ($Scope -in @("docs", "apifox", "deploy", "ci")) {
    Write-Host "Scope=${Scope}: compose restart skipped."
    return
}

$services = @()
if ($Scope -eq "backend" -or $Scope -eq "full") {
    $services += $config.BackendService
}
if ($Scope -eq "frontend" -or $Scope -eq "full") {
    $services += $config.FrontendService
}

$composeArgs = Get-HarnessComposeArgs -Config $config
$upArgs = $composeArgs + @("up", "-d", "--build")
if ($Scope -eq "backend" -or $Scope -eq "frontend") { $upArgs += "--no-deps" }
$upArgs += $services
$psArgs = $composeArgs + @("ps")

Push-Location $config.RepoRoot
try {
    Write-Host "docker $($upArgs -join ' ')"
    if (-not $DryRun) {
        Invoke-HarnessExternal -CommandName "docker compose up" -Script { docker @upArgs }
    }

    Write-Host ""
    Write-Host "docker $($psArgs -join ' ')"
    if (-not $DryRun) {
        Invoke-HarnessExternal -CommandName "docker compose ps" -Script { docker @psArgs }
    }
}
finally {
    Pop-Location
}

Write-Host "Compose restart completed." -ForegroundColor Green
