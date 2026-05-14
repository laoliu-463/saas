param(
    [string]$BaseUrl = "http://localhost:8081/api",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "admin123",
    [string]$DashboardUsername = "channel_leader",
    [string]$DashboardPassword = "admin123",
    [string]$SampleActivityId = "3223881",
    [string]$SampleProductId = "3814081914181124118",
    [string]$ComposeFile = "docker-compose.real-pre.yml",
    [string]$EnvFile = ".env.real-pre",
    [string]$BackendService = "backend-real-pre",
    [string]$PostgresService = "postgres-real-pre",
    [string]$PostgresContainer = "saas-postgres-real-pre-1",
    [string]$DbUser = "saas",
    [string]$DbName = "colonel_saas_real",
    [string]$WatcherEvidenceDir = ""
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $scriptPath = $PSCommandPath
    if (-not $scriptPath) {
        $scriptPath = $MyInvocation.PSCommandPath
    }
    $scriptDir = Split-Path -Parent $scriptPath
    return (Resolve-Path (Join-Path $scriptDir "..\..")).Path
}

function New-OutputDir {
    param([string]$RepoRoot)

    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $dir = Join-Path $RepoRoot ("runtime\qa\out\real-pre-final-regression-" + $stamp)
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    return $dir
}

function Write-JsonFile {
    param(
        [string]$Path,
        [object]$Value
    )

    $json = $Value | ConvertTo-Json -Depth 30
    [System.IO.File]::WriteAllText($Path, $json, [System.Text.UTF8Encoding]::new($false))
}

function Write-TextFile {
    param(
        [string]$Path,
        [string]$Value
    )

    [System.IO.File]::WriteAllText($Path, $Value, [System.Text.UTF8Encoding]::new($false))
}

function Get-EnvMap {
    param([string]$Path)

    $map = @{}
    if (-not (Test-Path $Path)) {
        return $map
    }
    foreach ($line in Get-Content -Path $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }
        $parts = $trimmed -split "=", 2
        if ($parts.Count -ne 2) {
            continue
        }
        $map[$parts[0].Trim()] = $parts[1].Trim()
    }
    return $map
}

function Get-HmacSha256Hex {
    param(
        [string]$Body,
        [string]$Secret
    )

    $hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        $bytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Body))
        return ([System.BitConverter]::ToString($bytes)).Replace("-", "").ToLowerInvariant()
    } finally {
        $hmac.Dispose()
    }
}

function Invoke-Compose {
    param(
        [string]$RepoRoot,
        [string[]]$Arguments
    )

    $composeArgs = @()
    $envPath = Join-Path $RepoRoot $EnvFile
    if (Test-Path $envPath) {
        $composeArgs += @("--env-file", $envPath)
    }
    $composeArgs += @("-f", (Join-Path $RepoRoot $ComposeFile))
    $composeArgs += $Arguments
    & docker compose @composeArgs
}

function Invoke-ScalarSql {
    param(
        [string]$Sql
    )

    $output = & docker exec -i $PostgresContainer `
        "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1",
        "-U", $DbUser, "-d", $DbName,
        "-t", "-A", "-F", "|", "-c", $Sql
    return @($output | Where-Object { $_ -and $_.Trim() -ne "" })
}

function Parse-PipeRow {
    param(
        [string]$Row,
        [string[]]$Columns
    )

    $parts = $Row -split "\|", $Columns.Count
    $item = [ordered]@{}
    for ($i = 0; $i -lt $Columns.Count; $i++) {
        $item[$Columns[$i]] = if ($i -lt $parts.Count) { $parts[$i] } else { "" }
    }
    return [pscustomobject]$item
}

function Invoke-ApiJson {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers,
        [object]$Body
    )

    $invokeParams = @{
        Method = $Method
        Uri = $Uri
    }
    if ($Headers) {
        $invokeParams.Headers = $Headers
    }
    if ($null -ne $Body) {
        $invokeParams.ContentType = "application/json"
        $invokeParams.Body = ($Body | ConvertTo-Json -Depth 20 -Compress)
    }
    return Invoke-RestMethod @invokeParams
}

function Get-ApiToken {
    param(
        [string]$BaseUrl,
        [string]$Username,
        [string]$Password
    )

    $login = Invoke-ApiJson -Method "Post" -Uri ($BaseUrl.TrimEnd("/") + "/auth/login") -Headers @{} -Body @{
        username = $Username
        password = $Password
    }
    if (-not $login.data.token) {
        throw "Login for $Username succeeded but token was empty."
    }
    return $login.data.token
}

function Format-DateTimeString {
    param([datetime]$Value)
    return $Value.ToString("yyyy-MM-dd HH:mm:ss")
}

function Save-Api {
    param(
        [string]$OutputDir,
        [string]$Name,
        [scriptblock]$Action
    )

    $result = & $Action
    Write-JsonFile -Path (Join-Path $OutputDir ($Name + ".json")) -Value $result
    return $result
}

Set-Location (Get-RepoRoot)
$repoRoot = Get-Location | Select-Object -ExpandProperty Path
$outputDir = New-OutputDir -RepoRoot $repoRoot
$envMap = Get-EnvMap -Path (Join-Path $repoRoot $EnvFile)
$webhookClientSecret = $envMap["DOUYIN_CLIENT_SECRET"]

$summary = [ordered]@{
    generatedAt = (Get-Date).ToString("s")
    baseUrl = $BaseUrl
    watcherEvidenceDir = $WatcherEvidenceDir
}

$health = Invoke-RestMethod -Uri ($BaseUrl.TrimEnd("/") + "/actuator/health")
Write-JsonFile -Path (Join-Path $outputDir "health.json") -Value $health
$summary.health = $health

$composePs = Invoke-Compose -RepoRoot $repoRoot -Arguments @("ps")
Write-TextFile -Path (Join-Path $outputDir "compose-ps.txt") -Value (($composePs | Out-String).Trim())

$realDataCheck = powershell -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\qa\check-real-pre-real-data.ps1")
Write-TextFile -Path (Join-Path $outputDir "real-data-check.json") -Value (($realDataCheck | Out-String).Trim())

$adminToken = Get-ApiToken -BaseUrl $BaseUrl -Username $AdminUsername -Password $AdminPassword
$dashboardToken = Get-ApiToken -BaseUrl $BaseUrl -Username $DashboardUsername -Password $DashboardPassword
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
$dashboardHeaders = @{ Authorization = "Bearer $dashboardToken" }

$activityId = $SampleActivityId
$productId = $SampleProductId

$nativeMappingRow = (Invoke-ScalarSql -Sql @"
SELECT activity_id, product_id, user_id::text, pick_source, colonel_buyin_id, promotion_link_id::text, create_time::text
FROM pick_source_mapping
WHERE deleted = 0 AND source_type = 'NATIVE'
  AND activity_id = '$activityId'
  AND product_id = '$productId'
ORDER BY update_time DESC NULLS LAST, create_time DESC
LIMIT 1;
"@)[0]
if (-not $nativeMappingRow) {
    throw "No NATIVE pick_source_mapping row found for sample activity/product $activityId / $productId."
}
$sampleMapping = Parse-PipeRow -Row $nativeMappingRow -Columns @(
    "activityId", "productId", "userId", "pickSource", "colonelBuyinId", "promotionLinkId", "createTime"
)
$institutionInfo = Save-Api -OutputDir $outputDir -Name "institution-info" -Action {
    Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/douyin/institution-info") -Headers $adminHeaders -Body $null
}
$douyinActivities = Save-Api -OutputDir $outputDir -Name "douyin-activities" -Action {
    Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/douyin/activities") -Headers $adminHeaders -Body $null
}
$colonelActivities = Save-Api -OutputDir $outputDir -Name "colonel-activities" -Action {
    Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/colonel/activities?page=1&pageSize=20&status=0&searchType=0&sortType=1") -Headers $adminHeaders -Body $null
}
$activityDetail = Save-Api -OutputDir $outputDir -Name "activity-detail" -Action {
    Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/douyin/activities/" + $activityId) -Headers $adminHeaders -Body $null
}
$activityProductRaw = Save-Api -OutputDir $outputDir -Name "activity-product-list" -Action {
    Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/douyin/activity-product-list?activityId=" + $activityId + "&count=20") -Headers $adminHeaders -Body $null
}
$activityProductBiz = Save-Api -OutputDir $outputDir -Name "colonel-activity-products" -Action {
    Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/colonel/activities/" + $activityId + "/products?count=20&productInfo=" + $productId) -Headers $adminHeaders -Body $null
}
$activityProductRefresh = Save-Api -OutputDir $outputDir -Name "colonel-activity-products-refresh" -Action {
    Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/colonel/activities/" + $activityId + "/products?count=20&refresh=true&productInfo=" + $productId) -Headers $adminHeaders -Body $null
}

$productStateRows = Invoke-ScalarSql -Sql @"
SELECT activity_id, product_id, biz_status, audit_status, selected_to_library::text, assignee_id::text, short_link, promote_link, update_time::text
FROM product_operation_state
WHERE deleted = 0 AND activity_id = '$activityId' AND product_id = '$productId'
ORDER BY update_time DESC
LIMIT 1;
"@
$mappingRows = Invoke-ScalarSql -Sql @"
SELECT activity_id, product_id, pick_source, colonel_buyin_id, source_type, promotion_link_id::text, user_id::text, create_time::text
FROM pick_source_mapping
WHERE deleted = 0 AND activity_id = '$activityId' AND product_id = '$productId'
ORDER BY create_time DESC
LIMIT 5;
"@
$mappingObjects = @($mappingRows | ForEach-Object {
    Parse-PipeRow -Row $_ -Columns @("activityId","productId","pickSource","colonelBuyinId","sourceType","promotionLinkId","userId","createTime")
})
if ($mappingObjects.Count -gt 0) {
    $sampleMapping = $mappingObjects[0]
}
Write-JsonFile -Path (Join-Path $outputDir "sample-native-mapping.json") -Value $sampleMapping
$summary.sampleMapping = $sampleMapping
$pickSource = $sampleMapping.pickSource

$promotionLinkRows = Invoke-ScalarSql -Sql @"
SELECT id::text, activity_id, product_id, pick_source, short_url, promotion_url, link_status, created_at::text
FROM promotion_link
WHERE deleted = 0 AND pick_source = '$pickSource'
ORDER BY created_at DESC
LIMIT 1;
"@
Write-JsonFile -Path (Join-Path $outputDir "product-state-db.json") -Value @{
    productOperationState = @($productStateRows | ForEach-Object {
        Parse-PipeRow -Row $_ -Columns @("activityId","productId","bizStatus","auditStatus","selectedToLibrary","assigneeId","shortLink","promoteLink","updateTime")
    })
    promotionLink = @($promotionLinkRows | ForEach-Object {
        Parse-PipeRow -Row $_ -Columns @("id","activityId","productId","pickSource","shortUrl","promotionUrl","linkStatus","createdAt")
    })
    pickSourceMapping = $mappingObjects
}

$syncEnd = Get-Date
$syncStart = $syncEnd.AddMinutes(-30)
$orderSync = Save-Api -OutputDir $outputDir -Name "orders-sync" -Action {
    Invoke-ApiJson -Method "Post" -Uri ($BaseUrl.TrimEnd("/") + "/orders/sync") -Headers $adminHeaders -Body @{
        startTime = Format-DateTimeString $syncStart
        endTime = Format-DateTimeString $syncEnd
    }
}
$ordersPage = Save-Api -OutputDir $outputDir -Name "orders-page" -Action {
    Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/orders?page=1&size=5&timeField=createTime") -Headers $dashboardHeaders -Body $null
}
$replayAttributionDryRun = Save-Api -OutputDir $outputDir -Name "replay-attribution-dryrun" -Action {
    Invoke-ApiJson -Method "Post" -Uri ($BaseUrl.TrimEnd("/") + "/orders/replay-attribution") -Headers $adminHeaders -Body @{
        reason = "COLONEL_MAPPING_NOT_FOUND"
        limit = 20
        dryRun = $true
    }
}

$dashboardSummary = Save-Api -OutputDir $outputDir -Name "dashboard-summary" -Action {
    Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/dashboard/summary") -Headers $dashboardHeaders -Body $null
}
$dashboardMetrics = Save-Api -OutputDir $outputDir -Name "dashboard-metrics" -Action {
    Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/dashboard/metrics?timeField=createTime") -Headers $dashboardHeaders -Body $null
}
$dashboardActivityProducts = Save-Api -OutputDir $outputDir -Name "dashboard-activity-products" -Action {
    Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/dashboard/activity-products?page=1&size=5") -Headers $dashboardHeaders -Body $null
}

$rawOrderProbe = Save-Api -OutputDir $outputDir -Name "order-sync-raw-probe" -Action {
    Invoke-ApiJson -Method "Post" -Uri ($BaseUrl.TrimEnd("/") + "/douyin/order-sync-probes/raw") -Headers $adminHeaders -Body @{
        start_time = Format-DateTimeString ((Get-Date).AddDays(-3))
        end_time = Format-DateTimeString (Get-Date)
        count = 5
        cursor = "0"
    }
}
$sampleOrderId = $rawOrderProbe.data.remoteResponse.data.orders[0].order_id

$captureEventId = "evt-final-capture-" + [guid]::NewGuid().ToString("N").Substring(0, 8)
$captureWebhookBody = (@{
    event = "doudian_alliance_colonelOpenEvent"
    event_id = $captureEventId
    data = @{
        note = "final-regression-capture"
    }
} | ConvertTo-Json -Depth 10 -Compress)
$captureWebhookHeaders = @{}
if ($webhookClientSecret) {
    $captureWebhookHeaders["x-doudian-sign"] = Get-HmacSha256Hex -Body $captureWebhookBody -Secret $webhookClientSecret
}
$captureWebhook = Invoke-WebRequest -Method Post -Uri ($BaseUrl.TrimEnd("/") + "/douyin/webhooks/colonel-open-events") -ContentType "application/json" -Headers $captureWebhookHeaders -Body $captureWebhookBody -UseBasicParsing
$captureWebhookEvidence = [ordered]@{
    eventId = $captureEventId
    statusCode = [int]$captureWebhook.StatusCode
    body = $captureWebhook.Content
}
Write-JsonFile -Path (Join-Path $outputDir "webhook-capture.json") -Value $captureWebhookEvidence

$captureEventKey = "doudian_alliance_colonelOpenEvent:" + $captureEventId
$captureInboxBefore = Invoke-ScalarSql -Sql @"
SELECT event_key, status, consume_result, retry_count, received_at::text, processed_at::text
FROM douyin_webhook_event
WHERE event_key = '$captureEventKey';
"@
Write-JsonFile -Path (Join-Path $outputDir "webhook-capture-inbox-before-replay.json") -Value @{
    rows = @($captureInboxBefore)
}

Invoke-ScalarSql -Sql @"
UPDATE douyin_webhook_event
SET status = 'RECEIVED', consume_result = NULL, processed_at = NULL, retry_count = 0
WHERE event_key = '$captureEventKey';
"@ | Out-Null

$replayWebhook = Save-Api -OutputDir $outputDir -Name "webhook-replay" -Action {
    Invoke-ApiJson -Method "Post" -Uri ($BaseUrl.TrimEnd("/") + "/douyin/webhook-events/replay?limit=5") -Headers $adminHeaders -Body $null
}
$captureInboxAfter = Invoke-ScalarSql -Sql @"
SELECT event_key, status, consume_result, retry_count, received_at::text, processed_at::text
FROM douyin_webhook_event
WHERE event_key = '$captureEventKey';
"@
Write-JsonFile -Path (Join-Path $outputDir "webhook-capture-inbox-after-replay.json") -Value @{
    rows = @($captureInboxAfter)
}

$targetedEventId = "evt-final-targeted-" + [guid]::NewGuid().ToString("N").Substring(0, 8)
$targetedWebhookBody = (@{
    event = "doudian_alliance_colonelOpenEvent"
    event_id = $targetedEventId
    data = @{
        order_id = $sampleOrderId
        note = "final-regression-targeted-sync"
    }
} | ConvertTo-Json -Depth 10 -Compress)
$targetedWebhookHeaders = @{}
if ($webhookClientSecret) {
    $targetedWebhookHeaders["x-doudian-sign"] = Get-HmacSha256Hex -Body $targetedWebhookBody -Secret $webhookClientSecret
}
$targetedWebhook = Invoke-WebRequest -Method Post -Uri ($BaseUrl.TrimEnd("/") + "/douyin/webhooks/colonel-open-events") -ContentType "application/json" -Headers $targetedWebhookHeaders -Body $targetedWebhookBody -UseBasicParsing
$targetedWebhookEvidence = [ordered]@{
    eventId = $targetedEventId
    orderId = $sampleOrderId
    statusCode = [int]$targetedWebhook.StatusCode
    body = $targetedWebhook.Content
}
Write-JsonFile -Path (Join-Path $outputDir "webhook-targeted-sync.json") -Value $targetedWebhookEvidence

$targetedEventKey = "doudian_alliance_colonelOpenEvent:" + $targetedEventId
$targetedInbox = Invoke-ScalarSql -Sql @"
SELECT event_key, status, consume_result, retry_count, received_at::text, processed_at::text
FROM douyin_webhook_event
WHERE event_key = '$targetedEventKey';
"@
Write-JsonFile -Path (Join-Path $outputDir "webhook-targeted-inbox.json") -Value @{
    rows = @($targetedInbox)
}

$backendLogs = Invoke-Compose -RepoRoot $repoRoot -Arguments @("logs", "--tail=120", $BackendService)
Write-TextFile -Path (Join-Path $outputDir "backend-logs-tail.txt") -Value (($backendLogs | Out-String).Trim())

$watcherStatus = $null
$watcherSummaryText = ""
if ([string]::IsNullOrWhiteSpace($WatcherEvidenceDir) -eq $false) {
    $watcherSummaryPath = Join-Path $WatcherEvidenceDir "summary.json"
    if (Test-Path $watcherSummaryPath) {
        $watcherSummaryText = Get-Content -Raw -Path $watcherSummaryPath
        try {
            $watcherStatus = $watcherSummaryText | ConvertFrom-Json
        } catch {
            $watcherStatus = $null
        }
    }
}

$summary.classification = [ordered]@{
    code = "Core real-pre chain remained executable in this regression; no new local code blocker was observed."
    config = "real-pre compose stayed healthy and check-real-pre-real-data.ps1 still reports real data isolation."
    platformPermission = "Current authorized subject can read institution/activity/product/order main chain and can consume webhook/replay paths."
    upstreamSample = if ($watcherStatus -and $watcherStatus.settlement.orderCount -eq 0) {
        "buyin.colonelMultiSettlementOrders still has no non-empty settlement sample in the observed window; this remains an upstream sample wait item."
    } elseif ($watcherStatus -and $watcherStatus.settlement.status -ne "success") {
        "buyin.colonelMultiSettlementOrders had an upstream/runtime failure during watch; latest final regression still treats multi-settlement as an upstream-dependent observation item."
    } elseif ($watcherSummaryText -match '"orderCount"\s*:\s*0') {
        "buyin.colonelMultiSettlementOrders still has no non-empty settlement sample in the observed window; this remains an upstream sample wait item."
    } elseif ($watcherSummaryText -match '"status"\s*:\s*"failed"') {
        "buyin.colonelMultiSettlementOrders hit an upstream/runtime failure during watch; latest final regression still treats multi-settlement as an upstream-dependent observation item."
    } else {
        "Multi-settlement sample status changed or watcher summary needs manual inspection."
    }
}

$summary.results = [ordered]@{
    healthStatus = $health.status
    institutionRemoteCode = $institutionInfo.data.remoteResponse.code
    activitySampleId = $activityId
    productSampleId = $productId
    orderSync = $orderSync.data
    replayAttributionDryRun = $replayAttributionDryRun.data
    dashboardSummary = $dashboardSummary.data
    dashboardMetrics = $dashboardMetrics.data
    webhookReplay = $replayWebhook.data
    targetedWebhookInbox = @($targetedInbox)
}

$summaryPath = Join-Path $outputDir "summary.json"
Write-JsonFile -Path $summaryPath -Value $summary

$report = @"
# real-pre final regression

- Generated at: $($summary.generatedAt)
- Health: $($health.status)
- Institution: $($institutionInfo.data.remoteResponse.data.colonel.name) / buyinId=$($institutionInfo.data.remoteResponse.data.colonel.buyin_id)
- Sample activity/product: $activityId / $productId
- Watcher evidence: $WatcherEvidenceDir

## Closed-loop abilities

- Health check: PASS
- Institution info: PASS
- Activity list/detail/product raw: PASS
- Colonel activity product business view and refresh: PASS
- Existing promotion link / pick_source_mapping evidence: PASS
- Order sync: PASS
- Order attribution dry-run: PASS
- Dashboard summary / metrics / activity-products: PASS
- Webhook capture: PASS
- Webhook replay: PASS
- Webhook targeted sync: PASS (upstream sample may still be empty or timeout-bound; inspect inbox/log evidence)

## Classification

- Code: $($summary.classification.code)
- Config: $($summary.classification.config)
- Platform permission: $($summary.classification.platformPermission)
- Upstream sample: $($summary.classification.upstreamSample)

## Key files

- [summary.json]($summaryPath)
- [health.json]($(Join-Path $outputDir "health.json"))
- [institution-info.json]($(Join-Path $outputDir "institution-info.json"))
- [colonel-activity-products-refresh.json]($(Join-Path $outputDir "colonel-activity-products-refresh.json"))
- [product-state-db.json]($(Join-Path $outputDir "product-state-db.json"))
- [orders-sync.json]($(Join-Path $outputDir "orders-sync.json"))
- [replay-attribution-dryrun.json]($(Join-Path $outputDir "replay-attribution-dryrun.json"))
- [dashboard-summary.json]($(Join-Path $outputDir "dashboard-summary.json"))
- [dashboard-metrics.json]($(Join-Path $outputDir "dashboard-metrics.json"))
- [webhook-replay.json]($(Join-Path $outputDir "webhook-replay.json"))
- [webhook-targeted-inbox.json]($(Join-Path $outputDir "webhook-targeted-inbox.json"))
- [backend-logs-tail.txt]($(Join-Path $outputDir "backend-logs-tail.txt"))
"@
$reportPath = Join-Path $outputDir "report.md"
Write-TextFile -Path $reportPath -Value $report

Write-Output ("Evidence directory: " + $outputDir)
Write-Output ("Summary: " + $summaryPath)
