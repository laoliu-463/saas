param(
    [Alias("Env")]
    [ValidateSet("test", "real-pre")]
    [string]$TargetEnv = "real-pre",
    [ValidateSet("backend", "frontend", "full", "docs", "apifox")]
    [string]$Scope = "full",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

$config = Get-HarnessEnvConfig -Env $TargetEnv

Write-HarnessStage "Compose restart"
& (Join-Path $PSScriptRoot "safety-check.ps1") -Env $TargetEnv -Scope $Scope -DryRun:$DryRun

if ($Scope -eq "docs" -or $Scope -eq "apifox") {
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
$upArgs = $composeArgs + @("up", "-d", "--build") + $services
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
