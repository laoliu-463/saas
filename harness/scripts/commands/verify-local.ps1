param(
    [Alias("Env")]
    [ValidateSet("test", "real-pre")]
    [string]$TargetEnv = "real-pre",
    [ValidateSet("backend", "frontend", "full", "docs", "apifox", "deploy", "ci")]
    [string]$Scope = "full",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

$config = Get-HarnessEnvConfig -Env $TargetEnv

Write-HarnessStage "Local verification"
Write-Host "Env: $TargetEnv"
Write-Host "Scope: $Scope"

$repoRoot = Get-HarnessRepoRoot
Assert-HarnessRepoRoot -RepoRoot $repoRoot

if ($Scope -in @("docs", "apifox", "deploy", "ci")) {
    Write-Host "Scope=${Scope}: repository structure check passed; HTTP health checks skipped."
    return
}

$envMap = Read-HarnessEnvFile -Path $config.EnvFile
$backendDefault = Get-HarnessPortFromCompose -ComposeFile $config.ComposeFile -EnvKey "BACKEND_HOST_PORT" -Default $config.BackendPort
$frontendDefault = Get-HarnessPortFromCompose -ComposeFile $config.ComposeFile -EnvKey "FRONTEND_HOST_PORT" -Default $config.FrontendPort
$backendPort = Get-HarnessPort -EnvMap $envMap -Key "BACKEND_HOST_PORT" -Default $backendDefault
$frontendPort = Get-HarnessPort -EnvMap $envMap -Key "FRONTEND_HOST_PORT" -Default $frontendDefault

$failures = @()

if ($Scope -eq "backend" -or $Scope -eq "full") {
    $backendUrl = "http://127.0.0.1:$backendPort$($config.BackendHealthPath)"
    Write-Host "Backend health: $backendUrl"
    if ($DryRun) {
        Write-Host "DRY-RUN backend check skipped."
    }
    else {
        # real-pre startup performs schema validation and read-only bootstrap work.
        # Keep this window longer than the compose start_period so a healthy but
        # deliberately-not-ready process is not reported as a deployment failure.
        $maxRetries = 24
        $retryInterval = 10
        $backendUp = $false
        for ($attempt = 1; $attempt -le $maxRetries; $attempt++) {
            $backend = Invoke-HarnessHttp -Url $backendUrl
            $backendUp = $backend.Ok -and ($backend.Body -match '"status"\s*:\s*"UP"')
            if ($backendUp) {
                Write-Host "Backend statusCode=$($backend.StatusCode)"
                Write-Host "Backend body=$($backend.Body)"
                break
            }
            if ($attempt -lt $maxRetries) {
                Write-Host "Backend not ready (attempt $attempt/$maxRetries), retrying in ${retryInterval}s..."
                Start-Sleep -Seconds $retryInterval
            }
        }
        if (-not $backendUp) {
            Write-Host "Backend statusCode=$($backend.StatusCode)"
            if ($backend.Body) {
                Write-Host "Backend body=$($backend.Body)"
            }
            $failures += "Backend health failed after $maxRetries attempts: $($backend.Error)"
        }
    }
}

if ($Scope -eq "frontend" -or $Scope -eq "full") {
    $frontendOk = $false
    foreach ($path in $config.FrontendHealthCandidates) {
        $frontendUrl = "http://127.0.0.1:$frontendPort$path"
        Write-Host "Frontend probe: $frontendUrl"
        if ($DryRun) {
            $frontendOk = $true
            Write-Host "DRY-RUN frontend check skipped."
            break
        }
        $frontend = Invoke-HarnessHttp -Url $frontendUrl
        Write-Host "Frontend statusCode=$($frontend.StatusCode)"
        if ($frontend.Ok) {
            $frontendOk = $true
            break
        }
    }
    if (-not $frontendOk) {
        $failures += "Frontend health failed for all candidates."
    }
}

if ($failures.Count -gt 0) {
    foreach ($failure in $failures) {
        Write-Host $failure -ForegroundColor Red
    }
    throw "Local verification failed."
}

Write-Host "Local verification passed." -ForegroundColor Green
