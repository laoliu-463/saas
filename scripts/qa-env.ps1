$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$scriptName = "qa-env"
$outDir = Join-Path $repoRoot "runtime\qa\out\$scriptName-$timestamp"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$List,
        [string]$Name,
        [bool]$Ok,
        [object]$Detail
    )

    $List.Add([ordered]@{
            name   = $Name
            ok     = $Ok
            detail = $Detail
        }) | Out-Null
}

$checks = [System.Collections.Generic.List[object]]::new()
$reportLines = [System.Collections.Generic.List[string]]::new()
$reportLines.Add("# QA Environment Check")
$reportLines.Add("")
$reportLines.Add("- GeneratedAt: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$reportLines.Add("- OutputDir: runtime/qa/out/$scriptName-$timestamp")
$reportLines.Add("")

Push-Location $repoRoot
try {
    $containers = docker ps --filter "label=com.docker.compose.project=saas-active" --format "{{.Names}}|{{.Status}}|{{.Ports}}" 2>$null
    if (-not $containers) {
        $containerRows = @()
    } else {
        $containerRows = @($containers -split "`n" | Where-Object { $_ })
    }

    $expectedNames = @("saas-frontend", "saas-backend", "saas-postgres", "saas-redis")
    $runningNames = @($containerRows | ForEach-Object { ($_ -split "\|")[0] })
    $missingNames = @($expectedNames | Where-Object { $_ -notin $runningNames })
    $unexpectedNames = @($runningNames | Where-Object { $_ -notin $expectedNames })
    $singleActive = $runningNames.Count -eq 4 -and $missingNames.Count -eq 0 -and $unexpectedNames.Count -eq 0
    Add-Check -List $checks -Name "single_saas_active_container_set" -Ok $singleActive -Detail @{
        running    = $runningNames
        missing    = $missingNames
        unexpected = $unexpectedNames
    }

    $reportLines.Add("## Docker Containers")
    $reportLines.Add('```text')
    if ($containerRows.Count -eq 0) {
        $reportLines.Add("(empty)")
    } else {
        foreach ($row in $containerRows) {
            $reportLines.Add($row)
        }
    }
    $reportLines.Add('```')
    $reportLines.Add("")

    $healthOk = $false
    $healthDetail = $null
    try {
        $healthResponse = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/actuator/health" -TimeoutSec 20
        $healthOk = $healthResponse.status -eq "UP"
        $healthDetail = $healthResponse
    } catch {
        $healthDetail = $_.Exception.Message
    }
    Add-Check -List $checks -Name "backend_health_up" -Ok $healthOk -Detail $healthDetail

    $reportLines.Add("## Backend Health")
    $reportLines.Add('```json')
    $reportLines.Add(($healthDetail | ConvertTo-Json -Depth 8))
    $reportLines.Add('```')
    $reportLines.Add("")

    $envOk = $false
    $envDetail = $null
    try {
        $loginBody = @{ username = 'admin'; password = 'admin123' } | ConvertTo-Json
        $loginResponse = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/auth/login" -ContentType "application/json" -Body $loginBody -TimeoutSec 20
        $token = $loginResponse.data.token
        $envResponse = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/system/env" -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 20
        $envDetail = $envResponse
        $envOk = $envResponse.code -eq 200 -and $envResponse.data.environmentLabel -eq "TEST"
    } catch {
        $envDetail = $_.Exception.Message
    }
    Add-Check -List $checks -Name "system_env_returns_test" -Ok $envOk -Detail $envDetail

    $reportLines.Add("## /api/system/env")
    $reportLines.Add('```json')
    $reportLines.Add(($envDetail | ConvertTo-Json -Depth 8))
    $reportLines.Add('```')
    $reportLines.Add("")

    $frontendOk = $false
    $frontendDetail = $null
    try {
        $playwrightScript = @'
const { chromium } = require("playwright");
(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ baseURL: "http://127.0.0.1:3000" });
  await page.goto("/login", { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.getByTestId("login-username").locator("input").fill("admin");
  await page.getByTestId("login-password").locator("input").fill("admin123");
  await page.getByTestId("login-submit").click();
  await page.waitForURL(/\/dashboard$/, { timeout: 30000 });
  await page.waitForTimeout(3000);
  const badgeText = await page.locator("[data-testid='current-env-badge']").innerText().catch(() => "");
  const badgeVisible = badgeText.includes("TEST");
  console.log(JSON.stringify({
    url: page.url(),
    badgeText,
    badgeVisible,
    excerpt: badgeText
  }));
  await browser.close();
})().catch(async (error) => {
  console.error(error?.stack || String(error));
  process.exit(1);
});
'@
        $frontendRaw = $playwrightScript | node -
        $frontendDetail = $frontendRaw | ConvertFrom-Json
        $frontendOk = [bool]$frontendDetail.badgeVisible
    } catch {
        $frontendDetail = $_.Exception.Message
    }
    Add-Check -List $checks -Name "frontend_shows_test_badge" -Ok $frontendOk -Detail $frontendDetail

    $reportLines.Add("## Frontend TEST Badge")
    $reportLines.Add('```json')
    $reportLines.Add(($frontendDetail | ConvertTo-Json -Depth 8))
    $reportLines.Add('```')
    $reportLines.Add("")

    $overallPass = -not ($checks | Where-Object { -not $_.ok })
    $summary = [ordered]@{
        script      = $scriptName
        timestamp   = $timestamp
        outputDir   = "runtime/qa/out/$scriptName-$timestamp"
        overallPass = $overallPass
        checks      = @($checks)
    }

    $summary | ConvertTo-Json -Depth 12 | Set-Content -Path (Join-Path $outDir "summary.json") -Encoding UTF8

    $reportLines.Add("## Result")
    $reportLines.Add("- overallPass: **$overallPass**")
    foreach ($check in $checks) {
        $reportLines.Add("- $($check.name): $(if ($check.ok) { 'PASS' } else { 'FAIL' })")
    }
    $reportLines -join "`n" | Set-Content -Path (Join-Path $outDir "report.md") -Encoding UTF8

    Write-Host "QA env output: $outDir" -ForegroundColor Green
    if (-not $overallPass) {
        exit 1
    }
}
finally {
    Pop-Location
}
