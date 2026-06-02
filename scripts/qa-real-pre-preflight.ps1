param(
    [string]$EnvFile = ".env.real-pre",
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [string]$BackendContainerName = "saas-active-backend-real-pre-1"
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $scriptPath = $PSCommandPath
    if (-not $scriptPath) {
        $scriptPath = $MyInvocation.PSCommandPath
    }
    $scriptDir = Split-Path -Parent $scriptPath
    return (Resolve-Path (Join-Path $scriptDir "..")).Path
}

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$List,
        [string]$Name,
        [string]$Status,
        [object]$Detail
    )

    $List.Add([ordered]@{
            name   = $Name
            status = $Status
            ok     = $Status -eq "PASS"
            detail = $Detail
        }) | Out-Null
}

function Read-KeyValueFile {
    param([string]$Path)

    $map = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $map
    }

    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }
        $parts = $trimmed -split "=", 2
        if ($parts.Count -eq 2) {
            $map[$parts[0].Trim()] = $parts[1].Trim()
        }
    }
    return $map
}

function Normalize-BoolString {
    param([object]$Value)

    if ($null -eq $Value) {
        return $null
    }
    return "$Value".Trim().ToLowerInvariant()
}

function Get-MaskedConfigSummary {
    param([object]$Value)

    $text = if ($null -eq $Value) { "" } else { "$Value".Trim() }
    if (-not $text) {
        return "missing"
    }

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($text))
    } finally {
        $sha256.Dispose()
    }
    $hashHex = ($hashBytes | ForEach-Object { $_.ToString("x2") }) -join ""
    return "configured(len=$($text.Length),sha256_8=$($hashHex.Substring(0, 8)))"
}

function Get-ConfigValueAssessment {
    param(
        [string]$Name,
        [object]$Value
    )

    $text = if ($null -eq $Value) { "" } else { "$Value".Trim() }
    $normalized = $text.ToLowerInvariant()
    $isMissing = -not $text
    $placeholderReason = $null

    if (-not $isMissing) {
        if ($normalized -match 'todo|changeme|change-me|change_me|placeholder|replace[_-]?me|fill[_-]?me|your[_-]?(app|client|secret|key)|tbd') {
            $placeholderReason = "placeholder-token"
        }
    }

    return [ordered]@{
        name        = $Name
        present     = -not $isMissing
        valid       = (-not $isMissing) -and (-not $placeholderReason)
        placeholder = [bool]$placeholderReason
        summary     = if ($isMissing) {
            "missing"
        } elseif ($placeholderReason) {
            "placeholder($placeholderReason)"
        } else {
            Get-MaskedConfigSummary -Value $text
        }
    }
}

function Get-ProfileConfigDefaults {
    param([string]$Path)

    $defaults = [ordered]@{
        hasApplicationRealPre = $false
        seedOnStartupFalse    = $false
        appTestDisabled       = $false
        douyinTestDisabled    = $false
    }

    if (-not (Test-Path -LiteralPath $Path)) {
        return $defaults
    }

    $defaults.hasApplicationRealPre = $true
    $content = Get-Content -LiteralPath $Path -Encoding UTF8
    $defaults.seedOnStartupFalse = [bool]($content | Select-String -Pattern 'seed-on-startup:\s*false' -SimpleMatch:$false)
    $defaults.appTestDisabled = [bool]($content | Select-String -Pattern 'enabled:\s*false' -SimpleMatch:$false | Select-Object -First 1)
    $defaults.douyinTestDisabled = [bool]($content | Select-String -Pattern 'douyin:\s*$|test:\s*$|enabled:\s*false' -SimpleMatch:$false)
    return $defaults
}

function Read-ContainerEnv {
    param([string]$ContainerName)

    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        return $null
    }

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $container = & docker ps --filter "name=^/${ContainerName}$" --format "{{.Names}}" 2>$null
        if ($LASTEXITCODE -ne 0 -or -not $container) {
            return $null
        }

        $envLines = & docker exec $ContainerName printenv 2>$null
        if ($LASTEXITCODE -ne 0 -or -not $envLines) {
            return $null
        }

        $map = @{}
        foreach ($line in $envLines) {
            if ($line -match "^([^=]+)=(.*)$") {
                $map[$Matches[1]] = $Matches[2]
            }
        }
        return $map
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Invoke-ReadOnlyJson {
    param([string]$Uri)

    try {
        return Invoke-RestMethod -Uri $Uri -TimeoutSec 15
    } catch {
        return $null
    }
}

function Get-ActiveProfiles {
    param([object]$EnvResponse)

    if ($null -eq $EnvResponse) {
        return @()
    }

    $data = if ($EnvResponse.data -and $EnvResponse.data -is [object]) { $EnvResponse.data } else { $EnvResponse }
    $profiles = @()
    foreach ($key in @("activeProfiles", "profiles")) {
        $value = $data.$key
        if ($value -is [System.Collections.IEnumerable] -and -not ($value -is [string])) {
            $profiles += @($value)
        }
    }
    foreach ($key in @("activeProfile", "profile")) {
        $value = $data.$key
        if ($value) {
            $profiles += @($value)
        }
    }
    return @($profiles | Where-Object { $_ } | ForEach-Object { $_.ToString().Trim().ToLowerInvariant() } | Select-Object -Unique)
}

$repoRoot = Get-RepoRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$scriptName = "qa-real-pre-preflight"
$outDir = Join-Path $repoRoot "runtime\qa\out\$scriptName-$timestamp"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$checks = [System.Collections.Generic.List[object]]::new()
$notes = [System.Collections.Generic.List[string]]::new()
$reportLines = [System.Collections.Generic.List[string]]::new()
$reportLines.Add("# real-pre Preflight")
$reportLines.Add("")
$reportLines.Add("- GeneratedAt: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$reportLines.Add("- OutputDir: runtime/qa/out/$scriptName-$timestamp")
$reportLines.Add("- Guardrails: read-only checks only; no DB cleanup; no production writes; no real business API calls")
$reportLines.Add("")

Push-Location $repoRoot
try {
    $envPath = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $repoRoot $EnvFile }
    $profilePath = Join-Path $repoRoot "backend\src\main\resources\application-real-pre.yml"
    $envMap = Read-KeyValueFile -Path $envPath
    $profileDefaults = Get-ProfileConfigDefaults -Path $profilePath

    if (Test-Path -LiteralPath $envPath) {
        Add-Check -List $checks -Name "env_file_exists" -Status "PASS" -Detail $envPath
    } else {
        Add-Check -List $checks -Name "env_file_exists" -Status "FAIL" -Detail $envPath
    }

    $configuredProfile = ""
    if ($envMap.ContainsKey("SPRING_PROFILES_ACTIVE")) {
        $configuredProfile = "$($envMap["SPRING_PROFILES_ACTIVE"])".Trim().ToLowerInvariant()
    }
    $profileOk = $configuredProfile -eq "real-pre" -or $configuredProfile -eq "real"
    Add-Check -List $checks -Name "configured_active_profile" -Status $(if ($profileOk) { "PASS" } else { "FAIL" }) -Detail @{
        configured = $configuredProfile
        expected   = @("real-pre", "real")
    }

    $appTestEnabled = Normalize-BoolString ($envMap["APP_TEST_ENABLED"])
    Add-Check -List $checks -Name "configured_app_test_disabled" -Status $(if ($appTestEnabled -eq "false") { "PASS" } else { "FAIL" }) -Detail @{
        configured = $appTestEnabled
        expected   = "false"
    }

    $douyinTestEnabled = Normalize-BoolString ($envMap["DOUYIN_TEST_ENABLED"])
    Add-Check -List $checks -Name "configured_douyin_test_disabled" -Status $(if ($douyinTestEnabled -eq "false") { "PASS" } else { "FAIL" }) -Detail @{
        configured = $douyinTestEnabled
        expected   = "false"
    }

    $productActivitySyncEnabled = Normalize-BoolString ($envMap["PRODUCT_ACTIVITY_SYNC_ENABLED"])
    Add-Check -List $checks -Name "configured_product_activity_sync_enabled" -Status $(if ($productActivitySyncEnabled -eq "true") { "PASS" } else { "FAIL" }) -Detail @{
        configured = $productActivitySyncEnabled
        expected   = "true"
    }

    $seedConfigured = Normalize-BoolString ($envMap["APP_TEST_SEED_ON_STARTUP"])
    $seedOk = $seedConfigured -eq "false" -or (-not $seedConfigured -and $profileDefaults.seedOnStartupFalse)
    Add-Check -List $checks -Name "configured_test_seed_disabled" -Status $(if ($seedOk) { "PASS" } else { "FAIL" }) -Detail @{
        configured = if ($seedConfigured) { $seedConfigured } else { "(not set)" }
        profileDefault = $profileDefaults.seedOnStartupFalse
        expected   = "false"
    }

    $requiredSdkFields = @("DOUYIN_BASE_URL", "DOUYIN_APP_ID", "DOUYIN_CLIENT_KEY", "DOUYIN_CLIENT_SECRET")
    $sdkFieldAssessments = @($requiredSdkFields | ForEach-Object {
            Get-ConfigValueAssessment -Name $_ -Value $envMap[$_]
        })
    $invalidSdkFields = @($sdkFieldAssessments | Where-Object { -not $_.valid } | ForEach-Object { $_.name })
    Add-Check -List $checks -Name "real_sdk_config_present" -Status $(if ($invalidSdkFields.Count -eq 0) { "PASS" } else { "FAIL" }) -Detail @{
        required = $requiredSdkFields
        invalid  = $invalidSdkFields
        fields   = $sdkFieldAssessments
    }
    if ($invalidSdkFields.Count -gt 0) {
        $notes.Add("Blocked by real SDK credentials in .env.real-pre: $($invalidSdkFields -join ', '). Fill real values locally, then rerun qa-real-pre-preflight.ps1.") | Out-Null
    }

    $jwtSecretAssessment = Get-ConfigValueAssessment -Name "JWT_SECRET" -Value $envMap["JWT_SECRET"]
    $jwtSecretText = if ($null -eq $envMap["JWT_SECRET"]) { "" } else { "$($envMap["JWT_SECRET"])".Trim() }
    $jwtSecretOk = $jwtSecretAssessment.valid -and $jwtSecretText.Length -ge 32
    Add-Check -List $checks -Name "real_security_config_present" -Status $(if ($jwtSecretOk) { "PASS" } else { "FAIL" }) -Detail @{
        required = @("JWT_SECRET")
        fields   = @($jwtSecretAssessment)
        minLength = 32
    }
    if (-not $jwtSecretOk) {
        $notes.Add("Blocked by real security config in .env.real-pre: JWT_SECRET must be a non-placeholder value with at least 32 characters.") | Out-Null
    }

    $containerEnv = Read-ContainerEnv -ContainerName $BackendContainerName
    if ($containerEnv) {
        $runtimeProfile = ""
        if ($containerEnv.ContainsKey("SPRING_PROFILES_ACTIVE")) {
            $runtimeProfile = "$($containerEnv["SPRING_PROFILES_ACTIVE"])".Trim().ToLowerInvariant()
        }
        $runtimeAppTest = Normalize-BoolString ($containerEnv["APP_TEST_ENABLED"])
        $runtimeDouyinTest = Normalize-BoolString ($containerEnv["DOUYIN_TEST_ENABLED"])
        $runtimeProductActivitySync = Normalize-BoolString ($containerEnv["PRODUCT_ACTIVITY_SYNC_ENABLED"])
        $runtimeSeed = Normalize-BoolString ($containerEnv["APP_TEST_SEED_ON_STARTUP"])
        $runtimeSeedDisabled = $runtimeSeed -eq "false" -or (-not $runtimeSeed -and $profileDefaults.seedOnStartupFalse)
        $runtimeOk = ($runtimeProfile -eq "real-pre" -or $runtimeProfile -eq "real") -and $runtimeAppTest -eq "false" -and $runtimeDouyinTest -eq "false" -and $runtimeProductActivitySync -eq "true" -and $runtimeSeedDisabled
        Add-Check -List $checks -Name "runtime_container_env_safe" -Status $(if ($runtimeOk) { "PASS" } else { "FAIL" }) -Detail @{
            SPRING_PROFILES_ACTIVE = $runtimeProfile
            APP_TEST_ENABLED = $runtimeAppTest
            DOUYIN_TEST_ENABLED = $runtimeDouyinTest
            PRODUCT_ACTIVITY_SYNC_ENABLED = $runtimeProductActivitySync
            APP_TEST_SEED_ON_STARTUP = $runtimeSeed
            APP_TEST_SEED_DISABLED_EFFECTIVE = $runtimeSeedDisabled
        }
    } else {
        Add-Check -List $checks -Name "runtime_container_env_safe" -Status "SKIP" -Detail "Container '$BackendContainerName' is not running."
    }

    $health = Invoke-ReadOnlyJson -Uri ($BaseUrl.TrimEnd("/") + "/api/system/health")
    if ($health) {
        Add-Check -List $checks -Name "runtime_health_up" -Status $(if ($health.status -eq "UP") { "PASS" } else { "FAIL" }) -Detail $health
    } else {
        Add-Check -List $checks -Name "runtime_health_up" -Status "SKIP" -Detail "GET /api/system/health not reachable."
    }

    $systemEnv = Invoke-ReadOnlyJson -Uri ($BaseUrl.TrimEnd("/") + "/api/system/env")
    if ($systemEnv) {
        $profiles = Get-ActiveProfiles -EnvResponse $systemEnv
        $data = if ($systemEnv.data -and $systemEnv.data -is [object]) { $systemEnv.data } else { $systemEnv }
        $runtimeApiProfileOk = (($profiles | Where-Object { $_ -eq "real-pre" -or $_ -eq "real" }).Count -gt 0) -and (($profiles | Where-Object { $_ -eq "test" }).Count -eq 0)
        $runtimeApiAppTest = Normalize-BoolString $data.appTestEnabled
        $runtimeApiDouyinTest = Normalize-BoolString $data.douyinTestEnabled
        $runtimeApiOk = $runtimeApiProfileOk -and $runtimeApiAppTest -eq "false" -and $runtimeApiDouyinTest -eq "false"
        Add-Check -List $checks -Name "runtime_system_env_safe" -Status $(if ($runtimeApiOk) { "PASS" } else { "FAIL" }) -Detail @{
            activeProfiles = $profiles
            environmentLabel = $data.environmentLabel
            appTestEnabled = $runtimeApiAppTest
            douyinTestEnabled = $runtimeApiDouyinTest
            database = $data.database
        }
    } else {
        Add-Check -List $checks -Name "runtime_system_env_safe" -Status "SKIP" -Detail "GET /api/system/env not reachable."
    }

    $blockingChecks = @($checks | Where-Object { $_.status -eq "FAIL" })
    $summary = [ordered]@{
        script = $scriptName
        timestamp = $timestamp
        outputDir = "runtime/qa/out/$scriptName-$timestamp"
        overallPass = ($blockingChecks.Count -eq 0)
        constraints = [ordered]@{
            databaseCleared = $false
            productionDataWritten = $false
            realBusinessApisCalled = $false
        }
        configured = [ordered]@{
            envFile = $envPath
            springProfilesActive = $configuredProfile
            appTestEnabled = $appTestEnabled
            douyinTestEnabled = $douyinTestEnabled
            productActivitySyncEnabled = $productActivitySyncEnabled
            appTestSeedOnStartup = if ($seedConfigured) { $seedConfigured } else { "(not set)" }
        }
        blockingChecks = @($blockingChecks | ForEach-Object { $_.name })
        checks = @($checks)
        notes = @($notes)
    }

    $summary | ConvertTo-Json -Depth 12 | Set-Content -Path (Join-Path $outDir "summary.json") -Encoding UTF8

    $reportLines.Add("## Checks")
    foreach ($check in $checks) {
        $reportLines.Add("- [$($check.status)] $($check.name)")
        $reportLines.Add("  - detail: $(([string]($check.detail | ConvertTo-Json -Depth 10 -Compress)))")
    }
    $reportLines.Add("")
    $reportLines.Add("## Result")
    $reportLines.Add("- overallPass: **$($summary.overallPass)**")
    $reportLines.Add("- databaseCleared: false")
    $reportLines.Add("- productionDataWritten: false")
    $reportLines.Add("- realBusinessApisCalled: false")
    $reportLines -join "`n" | Set-Content -Path (Join-Path $outDir "report.md") -Encoding UTF8

    $blockingReportLines = [System.Collections.Generic.List[string]]::new()
    $blockingReportLines.Add("# real-pre Preflight Blocking Report")
    $blockingReportLines.Add("")
    $blockingReportLines.Add("- GeneratedAt: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    $blockingReportLines.Add("- OutputDir: runtime/qa/out/$scriptName-$timestamp")
    $blockingReportLines.Add("- overallPass: $($summary.overallPass)")
    $blockingReportLines.Add("")
    if ($blockingChecks.Count -eq 0) {
        $blockingReportLines.Add("No blocking checks.")
    } else {
        $blockingReportLines.Add("## Blocking Checks")
        foreach ($blockingCheck in $blockingChecks) {
            $blockingReportLines.Add("- $($blockingCheck.name)")
            $blockingReportLines.Add("  - detail: $(([string]($blockingCheck.detail | ConvertTo-Json -Depth 10 -Compress)))")
        }
        $blockingReportLines.Add("")
        $blockingReportLines.Add("## Next Step")
        $blockingReportLines.Add("- Fill real values in `.env.real-pre` for the blocked SDK fields.")
        $blockingReportLines.Add("- Keep `APP_TEST_ENABLED=false` and `DOUYIN_TEST_ENABLED=false`.")
        $blockingReportLines.Add("- Rerun `powershell -ExecutionPolicy Bypass -File .\\scripts\\qa-real-pre-preflight.ps1 -EnvFile .\\.env.real-pre` before any real-pre cutover.")
    }
    $blockingReportLines -join "`n" | Set-Content -Path (Join-Path $outDir "blocking-report.md") -Encoding UTF8

    Write-Host "real-pre preflight output: $outDir" -ForegroundColor Green
    if (-not $summary.overallPass) {
        exit 1
    }
}
finally {
    Pop-Location
}
