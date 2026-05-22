param(
    [string]$BaseUrl = "http://localhost:8081/api",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "admin123",
    [string]$ChannelUsername = "channel_leader",
    [string]$ChannelPassword = "admin123",
    [string]$SampleActivityId = "",
    [string]$SampleProductId = "",
    [string]$ComposeFile = "docker-compose.real-pre.yml",
    [string]$EnvFile = ".env.real-pre",
    [string]$DbUser = "saas",
    [string]$DbName = "saas_real_pre"
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $scriptPath = $PSCommandPath
    if (-not $scriptPath) { $scriptPath = $MyInvocation.PSCommandPath }
    return (Resolve-Path (Join-Path (Split-Path -Parent $scriptPath) "..\..")).Path
}

function Invoke-Compose {
    param([string]$RepoRoot, [string[]]$Arguments)
    $composeArgs = @()
    $envPath = Join-Path $RepoRoot $EnvFile
    if (Test-Path $envPath) { $composeArgs += @("--env-file", $envPath) }
    $composeArgs += @("--project-name", "saas-active", "-f", (Join-Path $RepoRoot $ComposeFile))
    $composeArgs += $Arguments
    & docker compose @composeArgs
}

function Get-PostgresContainer {
    param([string]$RepoRoot)
    $lines = Invoke-Compose -RepoRoot $RepoRoot -Arguments @("ps", "--format", "{{.Name}}")
    $name = @($lines | Where-Object { $_ -match "postgres-real-pre" } | Select-Object -First 1)
    if (-not $name) { throw "postgres-real-pre container not found." }
    return $name.Trim()
}

function Invoke-ScalarSql {
    param([string]$Container, [string]$Sql)
    $output = docker exec -i $Container psql -X -q -v ON_ERROR_STOP=1 -U $DbUser -d $DbName -t -A -F "|" -c $Sql
    return @($output | Where-Object { $_ -and $_.Trim() -ne "" })
}

function Invoke-ApiJson {
    param([string]$Method, [string]$Uri, [hashtable]$Headers, [object]$Body)
    $p = @{ Method = $Method; Uri = $Uri }
    if ($Headers) { $p.Headers = $Headers }
    if ($null -ne $Body) {
        $p.ContentType = "application/json"
        $p.Body = ($Body | ConvertTo-Json -Depth 20 -Compress)
    }
    return Invoke-RestMethod @p
}

function Get-Token {
    param([string]$User, [string]$Pass)
    $r = Invoke-ApiJson -Method Post -Uri ($BaseUrl.TrimEnd("/") + "/auth/login") -Headers @{} -Body @{
        username = $User; password = $Pass
    }
    if (-not $r.data.token) { throw "Login failed for $User" }
    return $r.data.token
}

Set-Location (Get-RepoRoot)
$repoRoot = (Get-Location).Path
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $repoRoot "runtime\qa\out\product-chain-audit-$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$pg = Get-PostgresContainer -RepoRoot $repoRoot

$report = [ordered]@{
    generatedAt = (Get-Date).ToString("s")
    baseUrl = $BaseUrl
    outputDir = $outDir
    steps = @()
    status = "PASS"
}

function Add-Step {
    param([string]$Id, [string]$Result, [object]$Detail, [string[]]$Issues = @())
    $step = [ordered]@{ id = $Id; result = $Result; detail = $Detail; issues = $Issues }
    $report.steps += ,$step
    if ($Result -eq "FAIL" -and $report.status -eq "PASS") { $report.status = "FAIL" }
    if ($Result -eq "WARN" -and $report.status -eq "PASS") { $report.status = "WARN" }
}

try {
    $health = Invoke-RestMethod -Uri ($BaseUrl.TrimEnd("/") + "/system/health") -TimeoutSec 10
    Add-Step "health" "PASS" @{ status = $health.status }
} catch {
    Add-Step "health" "FAIL" @{ error = $_.Exception.Message }
    throw
}

try {
$adminToken = Get-Token $AdminUsername $AdminPassword
$channelToken = Get-Token $ChannelUsername $ChannelPassword
$adminH = @{ Authorization = "Bearer $adminToken" }
$channelH = @{ Authorization = "Bearer $channelToken" }

# Env isolation (backend-real-pre)
$envLines = Invoke-Compose -RepoRoot $repoRoot -Arguments @("exec", "-T", "backend-real-pre", "printenv")
$envMap = @{}
foreach ($line in $envLines) {
    if ($line -match "^([^=]+)=(.*)$") { $envMap[$Matches[1]] = $Matches[2] }
}
$profile = $envMap["SPRING_PROFILES_ACTIVE"]
$envOk = ($profile -eq "real" -or $profile -eq "real-pre") -and ($envMap["APP_TEST_ENABLED"] -eq "false") -and ($envMap["DOUYIN_TEST_ENABLED"] -eq "false")
Add-Step "env_real_pre" $(if ($envOk) { "PASS" } else { "FAIL" }) @{
    profile = $envMap["SPRING_PROFILES_ACTIVE"]
    appTest = $envMap["APP_TEST_ENABLED"]
    douyinTest = $envMap["DOUYIN_TEST_ENABLED"]
}

if (-not $SampleActivityId) {
    try {
        $acts = Invoke-ApiJson -Method Get -Uri ($BaseUrl + "/colonel/activities?page=1&pageSize=5&status=0&searchType=0&sortType=1") -Headers $adminH -Body $null
        $first = $acts.data.activityList | Select-Object -First 1
        if ($first) { $SampleActivityId = [string]$first.activityId }
    } catch { }
}
if (-not $SampleActivityId) { $SampleActivityId = "3223881" }

# 1) product_snapshot coverage
$snapSummary = Invoke-ScalarSql -Container $pg -Sql @"
SELECT COUNT(*) AS total,
       COUNT(*) FILTER (WHERE shop_id IS NOT NULL AND shop_id > 0) AS with_shop,
       COUNT(*) FILTER (WHERE category_name IS NOT NULL AND category_name <> '') AS with_category,
       COUNT(*) FILTER (WHERE status IS NOT NULL) AS with_status,
       COUNT(*) FILTER (WHERE activity_cos_ratio_text IS NOT NULL AND activity_cos_ratio_text <> '') AS with_commission,
       COUNT(*) FILTER (WHERE sales IS NOT NULL AND sales > 0) AS with_sales,
       MAX(sync_time)::text AS last_sync
FROM product_snapshot WHERE deleted = 0;
"@
$parts = ($snapSummary[0] -split "\|")
Add-Step "product_snapshot_summary" "PASS" @{
    total = $parts[0]; withShop = $parts[1]; withCategory = $parts[2]
    withStatus = $parts[3]; withCommission = $parts[4]; withSales = $parts[5]; lastSync = $parts[6]
}

$actSnap = Invoke-ScalarSql -Container $pg -Sql @"
SELECT COUNT(*) FROM product_snapshot WHERE deleted = 0 AND activity_id = '$SampleActivityId';
"@
if ([int]$actSnap[0] -eq 0) {
    Add-Step "product_snapshot_activity" "WARN" @{ activityId = $SampleActivityId; count = 0 } @("No snapshots for sample activity; will try refresh.")
} else {
    Add-Step "product_snapshot_activity" "PASS" @{ activityId = $SampleActivityId; count = [int]$actSnap[0] }
}

# API refresh + field spot-check
$refreshUri = "$BaseUrl/colonel/activities/$SampleActivityId/products?count=5&refresh=true"
try {
    $refresh = Invoke-ApiJson -Method Get -Uri $refreshUri -Headers $adminH -Body $null
    $items = @($refresh.data.items)
    if (-not $SampleProductId -and $items.Count -gt 0) { $SampleProductId = [string]$items[0].productId }
    $fieldCheck = @{}
    if ($items.Count -gt 0) {
        $row = $items[0]
        $fieldCheck = @{
            productId = $row.productId
            shopName = $row.shopName
            categoryName = $row.categoryName
            statusText = $row.statusText
            activityCosRatioText = $row.activityCosRatioText
            sales30d = $row.sales30d
            bizStatus = $row.bizStatus
        }
    }
    ($refresh | ConvertTo-Json -Depth 8) | Set-Content (Join-Path $outDir "colonel-products-refresh.json") -Encoding UTF8
    $missing = @()
    foreach ($k in @("productId", "shopName", "statusText")) {
        if (-not $fieldCheck[$k]) { $missing += $k }
    }
    Add-Step "api_activity_products_refresh" $(if ($items.Count -gt 0 -and $missing.Count -eq 0) { "PASS" } elseif ($items.Count -gt 0) { "WARN" } else { "FAIL" }) @{
        itemCount = $items.Count; sampleFields = $fieldCheck; missingCoreFields = $missing
    } $(if ($missing.Count) { @("API item missing: $($missing -join ', ')") } else { @() })
} catch {
    Add-Step "api_activity_products_refresh" "FAIL" @{ error = $_.Exception.Message }
}

# Upstream probe
try {
    $raw = Invoke-ApiJson -Method Get -Uri ($BaseUrl + "/douyin/activity-product-list?activityId=$SampleActivityId&count=3") -Headers $adminH -Body $null
    ($raw | ConvertTo-Json -Depth 6) | Set-Content (Join-Path $outDir "douyin-activity-product-list.json") -Encoding UTF8
    Add-Step "upstream_colonelActivityProduct" "PASS" @{ endpoint = $raw.data.endpoint; hasData = $null -ne $raw.data.data }
} catch {
    Add-Step "upstream_colonelActivityProduct" "WARN" @{ error = $_.Exception.Message } @("Douyin upstream probe failed; token or auth may be missing.")
}

if (-not $SampleProductId) { $SampleProductId = "3814081914181124118" }

# 2) pick_source_mapping
$mapRows = Invoke-ScalarSql -Container $pg -Sql @"
SELECT COUNT(*) AS total,
       COUNT(*) FILTER (WHERE pick_source IS NOT NULL AND pick_source <> '') AS with_pick,
       COUNT(*) FILTER (WHERE pick_source LIKE 'v.%') AS v_prefix
FROM pick_source_mapping WHERE deleted = 0;
"@
$mp = ($mapRows[0] -split "\|")
Add-Step "pick_source_mapping_summary" $(if ([int]$mp[0] -gt 0) { "PASS" } else { "WARN" }) @{
    total = $mp[0]; withPickSource = $mp[1]; vPrefix = $mp[2]
}

$mapSample = Invoke-ScalarSql -Container $pg -Sql @"
SELECT activity_id, product_id, pick_source, source_type, colonel_buyin_id, promotion_link_id::text
FROM pick_source_mapping WHERE deleted = 0 AND activity_id = '$SampleActivityId' AND product_id = '$SampleProductId'
ORDER BY update_time DESC NULLS LAST LIMIT 1;
"@
if ($mapSample.Count -gt 0) {
    $m = ($mapSample[0] -split "\|")
    Add-Step "pick_source_mapping_sample" "PASS" @{
        activityId = $m[0]; productId = $m[1]; pickSource = $m[2]; sourceType = $m[3]
        colonelBuyinId = $m[4]; promotionLinkId = $m[5]
    }
} else {
    Add-Step "pick_source_mapping_sample" "WARN" @{ activityId = $SampleActivityId; productId = $SampleProductId } @("No mapping row for sample product.")
}

# 3) Orders + GMV
$ord = Invoke-ScalarSql -Container $pg -Sql @"
SELECT COUNT(*) AS active,
       COUNT(*) FILTER (WHERE attribution_status = 'ATTRIBUTED') AS attributed,
       COUNT(*) FILTER (WHERE pick_source IS NOT NULL AND pick_source <> '') AS with_pick,
       ROUND(COALESCE(SUM(order_amount),0)/100.0,2) AS gmv_yuan
FROM colonelsettlement_order WHERE deleted = 0;
"@
$op = ($ord[0] -split "\|")
Add-Step "orders_gmv_summary" "PASS" @{
    activeOrders = $op[0]; attributed = $op[1]; ordersWithPickSource = $op[2]; totalGmvYuan = $op[3]
}

try {
    $dash = Invoke-ApiJson -Method Get -Uri ($BaseUrl + "/dashboard/activity-products?page=1&size=5") -Headers $channelH -Body $null
    ($dash | ConvertTo-Json -Depth 8) | Set-Content (Join-Path $outDir "dashboard-activity-products.json") -Encoding UTF8
    Add-Step "dashboard_activity_products" "PASS" @{ recordCount = @($dash.data.records).Count }
} catch {
    Add-Step "dashboard_activity_products" "WARN" @{ error = $_.Exception.Message }
}

# Status distribution in snapshot
$statusDist = Invoke-ScalarSql -Container $pg -Sql @"
SELECT status::text, status_text, COUNT(*) FROM product_snapshot WHERE deleted = 0
GROUP BY status, status_text ORDER BY COUNT(*) DESC LIMIT 8;
"@
Add-Step "promotion_status_distribution" "PASS" @{ rows = $statusDist }

# 4) Not implemented APIs
Add-Step "gap_partners_pin_quick_sample" "INFO" @{
    list_partners = "NOT_IMPLEMENTED"
    pin_product = "NOT_IMPLEMENTED"
    quick_sample_apply_upstream = "NOT_IMPLEMENTED"
    local_sample_api = "POST /api/samples"
}

# Post-refresh snapshot row for sample product
if ($SampleProductId) {
    $row = Invoke-ScalarSql -Container $pg -Sql @"
SELECT product_id, shop_id::text, shop_name, status::text, status_text, category_name, sales::text,
       activity_cos_ratio_text, ad_service_ratio, sync_time::text
FROM product_snapshot WHERE deleted = 0 AND activity_id = '$SampleActivityId' AND product_id = '$SampleProductId' LIMIT 1;
"@
    if ($row.Count -gt 0) {
        $r = ($row[0] -split "\|")
        Add-Step "product_snapshot_sample_row" "PASS" @{
            productId = $r[0]; shopId = $r[1]; shopName = $r[2]; status = $r[3]; statusText = $r[4]
            categoryName = $r[5]; sales = $r[6]; commission = $r[7]; serviceFee = $r[8]; syncTime = $r[9]
        }
    }
}

$report.sampleActivityId = $SampleActivityId
$report.sampleProductId = $SampleProductId

} catch {
    $report.fatalError = $_.Exception.Message
    if ($report.status -eq "PASS") { $report.status = "FAIL" }
}

$jsonPath = Join-Path $outDir "report.json"
$report | ConvertTo-Json -Depth 8 | Set-Content $jsonPath -Encoding UTF8

$md = @(
    "# 商品链路本地排查报告",
    "",
    "- 时间: $($report.generatedAt)",
    "- BaseUrl: $BaseUrl",
    "- 结论: **$($report.status)**",
    "- 样本活动: $($report.sampleActivityId)",
    "- 样本商品: $($report.sampleProductId)",
    "",
    "## 步骤",
    ""
)
foreach ($s in $report.steps) {
    $md += "- **$($s.id)**: $($s.result)"
    if ($s.issues -and $s.issues.Count -gt 0) {
        $md += "  - 问题: $($s.issues -join '; ')"
    }
}
$md += ""
$md += "完整 JSON: ``$jsonPath``"
$mdPath = Join-Path $outDir "report.md"
$md -join "`n" | Set-Content $mdPath -Encoding UTF8

Write-Host "Product chain audit: $($report.status)" -ForegroundColor $(if ($report.status -eq "PASS") { "Green" } elseif ($report.status -eq "WARN") { "Yellow" } else { "Red" })
Write-Host "Report: $mdPath"
if ($report.status -eq "FAIL") { exit 1 }
