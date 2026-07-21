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
$requiresRuntimeEnvironment = $Scope -ne "docs"

Write-HarnessStage "Safety check"
Assert-HarnessRepoRoot -RepoRoot $config.RepoRoot
Assert-HarnessNoSensitiveChangedFiles

if (-not (Test-Path -LiteralPath $config.ComposeFile)) {
    throw "Compose file not found: $($config.ComposeFile)"
}

$envMap = @{}
if ($requiresRuntimeEnvironment) {
    if (-not (Test-Path -LiteralPath $config.EnvFile)) {
        throw "$TargetEnv env file not found: $($config.EnvFile)"
    }
    $envMap = Read-HarnessEnvFile -Path $config.EnvFile
}

if ($TargetEnv -eq "real-pre" -and $requiresRuntimeEnvironment) {
    $appTest = if ($envMap.ContainsKey("APP_TEST_ENABLED")) { $envMap["APP_TEST_ENABLED"] } else { "(missing)" }
    $douyinTest = if ($envMap.ContainsKey("DOUYIN_TEST_ENABLED")) { $envMap["DOUYIN_TEST_ENABLED"] } else { "(missing)" }
    $upstream = if ($envMap.ContainsKey("DOUYIN_REAL_UPSTREAM_MODE")) { $envMap["DOUYIN_REAL_UPSTREAM_MODE"] } else { "(missing)" }

    if ($appTest -ne "false") {
        throw "real-pre must keep APP_TEST_ENABLED=false. actual=$appTest"
    }
    if ($douyinTest -ne "false") {
        throw "real-pre must keep DOUYIN_TEST_ENABLED=false. actual=$douyinTest"
    }
    if ($upstream -ne "live") {
        throw "real-pre must keep DOUYIN_REAL_UPSTREAM_MODE=live. actual=$upstream"
    }

    foreach ($key in @("DOUYIN_APP_ID", "DOUYIN_CLIENT_KEY", "DOUYIN_CLIENT_SECRET")) {
        $value = if ($envMap.ContainsKey($key)) { [string]$envMap[$key] } else { "" }
        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "real-pre requires $key; value is missing."
        }
        if ($value -match '(?i)MUST_CHANGE|PLACEHOLDER|CHANGE_ME|REDACTED|TODO') {
            throw "real-pre requires a real $key; placeholder value detected."
        }
    }
}

$secretKeys = @(
    "DB_PASSWORD",
    "REDIS_PASSWORD",
    "JWT_SECRET",
    "DOUYIN_CLIENT_SECRET",
    "LOGISTICS_KD100_KEY",
    "TALENT_PROFILE_HTTP_TOKEN",
    "TALENT_PROFILE_HTTP_AUTHORIZATION"
)

Write-Host "Env: $TargetEnv"
Write-Host "Scope: $Scope"
Write-Host "Compose: $($config.ComposeFile)"
if ($requiresRuntimeEnvironment) {
    Write-Host "Env file: $($config.EnvFile)"
}
else {
    Write-Host "Runtime environment config: not required for Scope=docs"
}
Write-Host "DryRun: $($DryRun.IsPresent)"
Write-Host "Harness dir: present"
Write-Host "AGENTS.md: present"
if ($requiresRuntimeEnvironment) {
    Write-Host ""
    Write-Host "Secret presence only:"
    foreach ($key in $secretKeys) {
        $present = $envMap.ContainsKey($key) -and -not [string]::IsNullOrWhiteSpace($envMap[$key])
        Write-Host ("- {0}: {1}" -f $key, $(if ($present) { "present" } else { "missing" }))
    }
}

$scanRoots = @("scripts", "harness")
$dangerPattern = "docker\s+compose.*down\s+-v|down\s+--volumes|docker\s+volume\s+(rm|prune)|Remove-Item.*(postgres|redis).*volume|DROP\s+DATABASE"
$dangerHits = @()
foreach ($root in $scanRoots) {
    $path = Join-Path $config.RepoRoot $root
    if (Test-Path -LiteralPath $path) {
        $files = Get-ChildItem -LiteralPath $path -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Extension -eq ".ps1" } |
            Where-Object { $_.Name -ne "safety-check.ps1" }
        $hits = $files | Select-String -Pattern $dangerPattern -ErrorAction SilentlyContinue
        if ($hits) {
            $dangerHits += $hits
        }
    }
}

if ($dangerHits.Count -gt 0) {
    Write-Host "Potential destructive command references:" -ForegroundColor Yellow
    foreach ($hit in $dangerHits) {
        Write-Host ("- {0}:{1}: {2}" -f $hit.Path, $hit.LineNumber, $hit.Line.Trim())
    }
    throw "Destructive command references found in scripts/harness. Review before proceeding."
}

Write-Host "Safety check passed." -ForegroundColor Green
