param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "admin123",
    [int]$SyncWindowMinutes = 30,
    [int]$RawProbeWindowDays = 3,
    [int]$RawProbeCount = 20,
    [int]$SampleLookbackDays = 30,
    [int]$SampleLimit = 20,
    [string]$EnvFile = ".env.real-pre",
    [string]$PostgresContainer = "saas-postgres",
    [string]$DbUser = "saas",
    [string]$DbName = "saas_real_pre"
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
    $dir = Join-Path $RepoRoot ("runtime\qa\out\real-pre-attribution-evidence-" + $stamp)
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

function SqlLiteral {
    param([object]$Value)

    if ($null -eq $Value) {
        return "NULL"
    }
    $text = [string]$Value
    return "'" + $text.Replace("'", "''") + "'"
}

function Invoke-ScalarSql {
    param([string]$Sql)

    $output = & docker exec -i $PostgresContainer `
        psql -X -q -v ON_ERROR_STOP=1 `
        -U $DbUser -d $DbName `
        -t -A -F "|" -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ("SQL command failed:`n" + (($output | Out-String).Trim()))
    }
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

function Parse-DateTimeValue {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $null
    }
    try {
        return [datetime]::Parse($Value)
    } catch {
        return $null
    }
}

function As-IntOrZero {
    param([object]$Value)

    if ($null -eq $Value) {
        return 0
    }
    $text = [string]$Value
    if ([string]::IsNullOrWhiteSpace($text)) {
        return 0
    }
    return [int]$Value
}

function Get-OrderRows {
    param(
        [datetime]$StartTime,
        [int]$Limit,
        [bool]$RequirePickSource
    )

    $pickSourceClause = if ($RequirePickSource) { "AND COALESCE(pick_source, '') <> ''" } else { "" }
    $sql = @"
SELECT order_id,
       product_id,
       COALESCE(colonel_activity_id, '') AS activity_id,
       COALESCE(pick_source, '') AS pick_source,
       COALESCE(create_time::text, '') AS order_create_time,
       COALESCE(settle_time::text, '') AS settle_time,
       COALESCE(order_amount, 0)::text AS order_amount,
       COALESCE(settle_colonel_commission, 0)::text AS service_fee,
       COALESCE(attribution_status, '') AS attribution_status,
       COALESCE(attribution_remark, '') AS unattributed_reason,
       COALESCE(channel_user_id::text, '') AS channel_id,
       COALESCE(colonel_buyin_id::text, '') AS colonel_buyin_id,
       COALESCE(extra_data ->> 'pick_extra', '') AS pick_extra
FROM colonelsettlement_order
WHERE deleted = 0
  AND create_time >= $(SqlLiteral (Format-DateTimeString $StartTime))
  $pickSourceClause
ORDER BY create_time DESC
LIMIT $Limit;
"@
    return @(Invoke-ScalarSql -Sql $sql | ForEach-Object {
        Parse-PipeRow -Row $_ -Columns @(
            "orderId",
            "productId",
            "activityId",
            "pickSource",
            "orderCreateTime",
            "settleTime",
            "orderAmount",
            "serviceFee",
            "attributionStatus",
            "unattributedReason",
            "channelId",
            "colonelBuyinId",
            "pickExtra"
        )
    })
}

function Get-OrderCounts {
    param([datetime]$StartTime)

    $sql = @"
SELECT COUNT(*)::text AS total_orders,
       COUNT(*) FILTER (WHERE COALESCE(pick_source, '') <> '')::text AS orders_with_pick_source,
       COUNT(*) FILTER (WHERE COALESCE(pick_source, '') <> '' AND attribution_status = 'ATTRIBUTED')::text AS attributed_pick_source_orders,
       COUNT(*) FILTER (WHERE COALESCE(pick_source, '') <> '' AND attribution_status = 'UNATTRIBUTED')::text AS unattributed_pick_source_orders,
       COUNT(*) FILTER (WHERE attribution_status = 'ATTRIBUTED')::text AS attributed_orders,
       COUNT(*) FILTER (WHERE attribution_status = 'UNATTRIBUTED')::text AS unattributed_orders
FROM colonelsettlement_order
WHERE deleted = 0
  AND create_time >= $(SqlLiteral (Format-DateTimeString $StartTime));
"@
    $rows = @(Invoke-ScalarSql -Sql $sql)
    $row = $rows[0]
    return Parse-PipeRow -Row $row -Columns @(
        "totalOrders",
        "ordersWithPickSource",
        "attributedPickSourceOrders",
        "unattributedPickSourceOrders",
        "attributedOrders",
        "unattributedOrders"
    )
}

function Get-LatestMappings {
    param([int]$Limit)

    $sql = @"
SELECT COALESCE(pick_source, '') AS pick_source,
       COALESCE(activity_id, '') AS activity_id,
       COALESCE(product_id, '') AS product_id,
       COALESCE(user_id::text, '') AS user_id,
       COALESCE(create_time::text, '') AS mapping_created_at,
       COALESCE(source_type, '') AS mapping_source_type,
       COALESCE(colonel_buyin_id::text, '') AS colonel_buyin_id
FROM pick_source_mapping
WHERE deleted = 0
ORDER BY create_time DESC
LIMIT $Limit;
"@
    return @(Invoke-ScalarSql -Sql $sql | ForEach-Object {
        Parse-PipeRow -Row $_ -Columns @(
            "pickSource",
            "activityId",
            "productId",
            "userId",
            "mappingCreatedAt",
            "mappingSourceType",
            "colonelBuyinId"
        )
    })
}

function Get-MappingRowsForPickSource {
    param([string]$PickSource)

    if ([string]::IsNullOrWhiteSpace($PickSource)) {
        return @()
    }
    $sql = @"
SELECT COALESCE(id::text, '') AS mapping_id,
       COALESCE(activity_id, '') AS activity_id,
       COALESCE(product_id, '') AS product_id,
       COALESCE(user_id::text, '') AS user_id,
       COALESCE(dept_id::text, '') AS dept_id,
       COALESCE(create_time::text, '') AS mapping_created_at,
       COALESCE(source_type, '') AS mapping_source_type,
       COALESCE(colonel_buyin_id::text, '') AS colonel_buyin_id,
       COALESCE(status, 0)::text AS status,
       COALESCE(converted_url, '') AS converted_url
FROM pick_source_mapping
WHERE deleted = 0
  AND pick_source = $(SqlLiteral $PickSource)
ORDER BY create_time DESC;
"@
    return @(Invoke-ScalarSql -Sql $sql | ForEach-Object {
        Parse-PipeRow -Row $_ -Columns @(
            "mappingId",
            "activityId",
            "productId",
            "userId",
            "deptId",
            "mappingCreatedAt",
            "mappingSourceType",
            "colonelBuyinId",
            "status",
            "convertedUrl"
        )
    })
}

function Test-ProductCoverage {
    param(
        [string]$ActivityId,
        [string]$ProductId
    )

    if ([string]::IsNullOrWhiteSpace($ActivityId) -or [string]::IsNullOrWhiteSpace($ProductId)) {
        return $false
    }
    $sql = @"
SELECT CASE
         WHEN EXISTS (
            SELECT 1
            FROM product_snapshot
            WHERE deleted = 0
              AND activity_id = $(SqlLiteral $ActivityId)
              AND product_id = $(SqlLiteral $ProductId)
         ) OR EXISTS (
            SELECT 1
            FROM product_operation_state
            WHERE deleted = 0
              AND activity_id = $(SqlLiteral $ActivityId)
              AND product_id = $(SqlLiteral $ProductId)
         )
         THEN '1'
         ELSE '0'
       END;
"@
    $values = @(Invoke-ScalarSql -Sql $sql)
    $value = $values[0]
    return $value -eq "1"
}

function Get-OrderDetail {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [string]$OrderId
    )

    return Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/orders/" + $OrderId) -Headers $Headers -Body $null
}

function Get-ExpectedAnalysis {
    param(
        [pscustomobject]$Order,
        [object[]]$Mappings,
        [bool]$ProductCovered
    )

    $orderCreateTime = Parse-DateTimeValue $Order.orderCreateTime
    $exactMappings = @($Mappings | Where-Object {
        $_.activityId -eq $Order.activityId -and $_.productId -eq $Order.productId
    })
    $safeMappings = @($exactMappings | Where-Object {
        $mappingTime = Parse-DateTimeValue $_.mappingCreatedAt
        $orderCreateTime -and $mappingTime -and $mappingTime -le $orderCreateTime
    })
    $safeMappingsWithUser = @($safeMappings | Where-Object { -not [string]::IsNullOrWhiteSpace($_.userId) })

    $expectedStatus = "UNATTRIBUTED"
    $expectedReason = ""
    if ([string]::IsNullOrWhiteSpace($Order.pickSource)) {
        $expectedReason = "NO_PICK_SOURCE"
    } elseif ($Mappings.Count -eq 0) {
        $expectedReason = "MAPPING_NOT_FOUND"
    } elseif ([string]::IsNullOrWhiteSpace($Order.activityId) -or [string]::IsNullOrWhiteSpace($Order.productId)) {
        $expectedReason = "UPSTREAM_ORDER_FIELDS_MISSING"
    } elseif ($exactMappings.Count -eq 0) {
        $expectedReason = if ($ProductCovered) { "MAPPING_NOT_FOUND" } else { "PRODUCT_UNCOVERED" }
    } elseif ($safeMappings.Count -eq 0) {
        $expectedReason = "MAPPING_CREATED_AFTER_ORDER"
    } elseif ($safeMappingsWithUser.Count -eq 0) {
        $expectedReason = "CHANNEL_NOT_FOUND"
    } else {
        $expectedStatus = "ATTRIBUTED"
        $expectedReason = "ATTRIBUTED"
    }

    $actualStatus = [string]$Order.attributionStatus
    $actualReason = [string]$Order.unattributedReason
    $statusMatches = $actualStatus -eq $expectedStatus
    $reasonMatches = $expectedStatus -eq "ATTRIBUTED" -or $actualReason -eq $expectedReason

    return [pscustomobject]@{
        expectedAttributionStatus = $expectedStatus
        expectedReason = $expectedReason
        actualAttributionStatus = $actualStatus
        actualReason = $actualReason
        statusMatchesExpectation = $statusMatches
        reasonMatchesExpectation = $reasonMatches
        exactMappingCount = $exactMappings.Count
        safeExactMappingCount = $safeMappings.Count
        safeExactMappingWithUserCount = $safeMappingsWithUser.Count
        exactMappings = $exactMappings
        safeMappings = $safeMappings
        safeMappingsWithUser = $safeMappingsWithUser
    }
}

function Build-DashboardExplanation {
    param(
        [pscustomobject]$Candidate,
        [object]$DashboardSummary,
        [object]$OrderDetail
    )

    if ($null -eq $Candidate) {
        return [pscustomobject]@{
            sampleOrderFound = $false
            includedInSummary = $null
            explanation = "No local real order with pick_source was found. /dashboard/summary only proves the global totals: orderCount=$($DashboardSummary.orderCount), unattributedOrderCount=$($DashboardSummary.unattributedOrderCount). A single-order inclusion explanation is not available yet."
        }
    }

    $bucket = if ($Candidate.attributionStatus -eq "ATTRIBUTED") { "attributedOrderCount" } else { "unattributedOrderCount" }
    $mappingCreatedAt = $null
    if ($OrderDetail -and $OrderDetail.data -and $OrderDetail.data.promotion) {
        $mappingCreatedAt = $OrderDetail.data.promotion.createdAt
    }
    return [pscustomobject]@{
        sampleOrderFound = $true
        includedInSummary = $true
        summaryEndpoint = "/api/dashboard/summary"
        inclusionRule = "Dashboard summary aggregates all non-deleted orders by default. It filters by settle_time only when startTime/endTime are provided."
        attributionBucket = $bucket
        mappingCreatedAt = $mappingCreatedAt
        explanation = "Sample order $($Candidate.orderId) has deleted=0, and this run called /api/dashboard/summary without a time range. It therefore contributes to orderCount/orderAmount/serviceFee. Because attribution_status=$($Candidate.attributionStatus), it also contributes to $bucket."
    }
}

Set-Location (Get-RepoRoot)
$repoRoot = Get-Location | Select-Object -ExpandProperty Path
$outputDir = New-OutputDir -RepoRoot $repoRoot

$envApiResponse = Invoke-RestMethod -Method Get -Uri ($BaseUrl.TrimEnd("/") + "/system/env")
$envResponse = $envApiResponse.data
if (-not ($envResponse.activeProfiles -contains "real-pre")) {
    throw "Current runtime is not real-pre."
}
if ($envResponse.appTestEnabled -or $envResponse.douyinTestEnabled) {
    throw "Current runtime still has test switches enabled."
}

$health = Invoke-RestMethod -Method Get -Uri ($BaseUrl.TrimEnd("/") + "/actuator/health")
$adminToken = Get-ApiToken -BaseUrl $BaseUrl -Username $AdminUsername -Password $AdminPassword
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

$syncEnd = Get-Date
$syncStart = $syncEnd.AddMinutes(-1 * $SyncWindowMinutes)
$rawProbeStart = $syncEnd.AddDays(-1 * $RawProbeWindowDays)
$sampleLookbackStart = $syncEnd.AddDays(-1 * $SampleLookbackDays)

$syncResult = Invoke-ApiJson -Method "Post" -Uri ($BaseUrl.TrimEnd("/") + "/orders/sync") -Headers $adminHeaders -Body @{
    startTime = Format-DateTimeString $syncStart
    endTime = Format-DateTimeString $syncEnd
}
Write-JsonFile -Path (Join-Path $outputDir "order-sync.json") -Value $syncResult

$rawProbe = Invoke-ApiJson -Method "Post" -Uri ($BaseUrl.TrimEnd("/") + "/douyin/order-sync-probes/raw") -Headers $adminHeaders -Body @{
    start_time = Format-DateTimeString $rawProbeStart
    end_time = Format-DateTimeString $syncEnd
    count = $RawProbeCount
    cursor = "0"
}
Write-JsonFile -Path (Join-Path $outputDir "order-sync-probe-raw.json") -Value $rawProbe

$dashboardSummaryResponse = Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/dashboard/summary") -Headers $adminHeaders -Body $null
Write-JsonFile -Path (Join-Path $outputDir "dashboard-summary.json") -Value $dashboardSummaryResponse

$orderCounts = Get-OrderCounts -StartTime $sampleLookbackStart
$recentOrdersWithPickSource = Get-OrderRows -StartTime $sampleLookbackStart -Limit $SampleLimit -RequirePickSource $true
$recentOrdersWithoutFilter = Get-OrderRows -StartTime $sampleLookbackStart -Limit ([Math]::Min($SampleLimit, 10)) -RequirePickSource $false
$latestMappings = Get-LatestMappings -Limit 10

$rawOrders = @()
if ($rawProbe.data -and $rawProbe.data.remoteResponse -and $rawProbe.data.remoteResponse.data -and $rawProbe.data.remoteResponse.data.orders) {
    $rawOrders = @($rawProbe.data.remoteResponse.data.orders)
}
$rawOrdersNormalized = @($rawOrders | ForEach-Object {
    [pscustomobject]@{
        orderId = [string]$_.order_id
        productId = [string]$_.product_id
        activityId = [string]$_.colonel_activity_id
        pickSource = [string]$_.pick_source
        pickExtra = [string]$_.pick_extra
        colonelBuyinId = [string]$_.colonel_buyin_id
        orderCreateTime = [string]$_.create_time
        settleTime = [string]$_.settle_time
    }
})
$rawPickSourceOrderCount = @($rawOrdersNormalized | Where-Object { -not [string]::IsNullOrWhiteSpace($_.pickSource) }).Count

$mappingChecks = @()
$candidate = $null
$candidateDetail = $null
foreach ($order in $recentOrdersWithPickSource) {
    $mappings = Get-MappingRowsForPickSource -PickSource $order.pickSource
    $productCovered = Test-ProductCoverage -ActivityId $order.activityId -ProductId $order.productId
    $analysis = Get-ExpectedAnalysis -Order $order -Mappings $mappings -ProductCovered $productCovered
    $record = [pscustomobject]@{
        orderId = $order.orderId
        productId = $order.productId
        activityId = $order.activityId
        pickSource = $order.pickSource
        orderCreateTime = $order.orderCreateTime
        settleTime = $order.settleTime
        orderAmount = [long]$order.orderAmount
        serviceFee = [long]$order.serviceFee
        attributionStatus = $order.attributionStatus
        unattributedReason = $order.unattributedReason
        channelId = $order.channelId
        colonelBuyinId = $order.colonelBuyinId
        mappingCreatedAt = @($analysis.safeMappingsWithUser | Select-Object -First 1).mappingCreatedAt
        mappingSourceType = @($analysis.safeMappingsWithUser | Select-Object -First 1).mappingSourceType
        productCovered = $productCovered
        analysis = $analysis
        mappings = $mappings
    }
    $mappingChecks += $record

    if ($null -eq $candidate -and $analysis.safeMappingsWithUser.Count -gt 0 -and $order.attributionStatus -eq "ATTRIBUTED") {
        $candidate = $record
    }
}
if ($null -eq $candidate -and $mappingChecks.Count -gt 0) {
    $candidate = $mappingChecks[0]
}
if ($candidate -ne $null) {
    $candidateDetail = Get-OrderDetail -BaseUrl $BaseUrl -Headers $adminHeaders -OrderId $candidate.orderId
    Write-JsonFile -Path (Join-Path $outputDir "candidate-order-detail.json") -Value $candidateDetail
}

$ordersSample = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("s")
    sampleLookbackDays = $SampleLookbackDays
    syncWindowMinutes = $SyncWindowMinutes
    rawProbeWindowDays = $RawProbeWindowDays
    corpus = [pscustomobject]@{
        totalOrders = [int]$orderCounts.totalOrders
        ordersWithPickSource = [int]$orderCounts.ordersWithPickSource
        attributedPickSourceOrders = [int]$orderCounts.attributedPickSourceOrders
        unattributedPickSourceOrders = [int]$orderCounts.unattributedPickSourceOrders
        attributedOrders = [int]$orderCounts.attributedOrders
        unattributedOrders = [int]$orderCounts.unattributedOrders
    }
    rawProbeSummary = [pscustomobject]@{
        status = [string]$rawProbe.data.status
        upstreamCode = $rawProbe.data.remoteResponse.code
        orderCount = $rawOrdersNormalized.Count
        rawOrdersWithPickSource = $rawPickSourceOrderCount
    }
    recentOrdersWithPickSource = [object[]]$recentOrdersWithPickSource
    recentOrdersSample = [object[]]$recentOrdersWithoutFilter
    latestMappings = [object[]]$latestMappings
}
Write-JsonFile -Path (Join-Path $outputDir "orders-sample.json") -Value $ordersSample

$mappingCheck = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("s")
    corpusOrdersWithPickSource = [int]$orderCounts.ordersWithPickSource
    rawOrdersWithPickSource = $rawPickSourceOrderCount
    checkedOrders = [object[]]$mappingChecks
    candidate = $candidate
    candidateOrderDetail = if ($candidateDetail) { $candidateDetail.data } else { $null }
    conclusion = if ($candidate -eq $null) {
        "Neither the raw probe nor the local persisted orders contain a real order with pick_source, so mapping-hit verification cannot start."
    } elseif ($candidate.analysis.expectedAttributionStatus -eq "ATTRIBUTED" -and $candidate.analysis.statusMatchesExpectation) {
        "A real order with pick_source was found, and the mapping hit is consistent with attribution_status."
    } else {
        "A real order with pick_source was found, but it does not yet satisfy the pass condition: mapping hit and correct attribution_status."
    }
}
Write-JsonFile -Path (Join-Path $outputDir "mapping-check.json") -Value $mappingCheck

$dashboardSummary = $dashboardSummaryResponse.data
$dashboardCheck = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("s")
    summary = $dashboardSummary
    explanation = Build-DashboardExplanation -Candidate $candidate -DashboardSummary $dashboardSummary -OrderDetail $candidateDetail
}
Write-JsonFile -Path (Join-Path $outputDir "dashboard-check.json") -Value $dashboardCheck

$syncData = $syncResult.data
$syncFailed = As-IntOrZero $syncData.failed
$syncOk = $syncResult.code -eq 200 -and $syncFailed -eq 0
$sampleVerified = $candidate -ne $null -and $candidate.analysis.expectedAttributionStatus -eq "ATTRIBUTED" -and $candidate.analysis.statusMatchesExpectation
$reportStatus = if (-not $syncOk) {
    "SYNC_FAILED"
} elseif ($sampleVerified) {
    "ATTRIBUTABLE_SAMPLE_VERIFIED"
} else {
    "SYNC_OK_NO_SAMPLE"
}

$summary = [pscustomobject]@{
    evidenceType = "real-pre-attribution-evidence"
    generatedAt = (Get-Date).ToString("s")
    evidenceDir = $outputDir
    baseUrl = $BaseUrl
    reportStatus = $reportStatus
    executionPass = $syncOk
    sampleVerified = $sampleVerified
    environment = [pscustomobject]@{
        activeProfiles = $envResponse.activeProfiles
        environmentLabel = $envResponse.environmentLabel
        appTestEnabled = $envResponse.appTestEnabled
        douyinTestEnabled = $envResponse.douyinTestEnabled
        database = $envResponse.database
        healthStatus = $health.status
    }
    syncWindow = [pscustomobject]@{
        startTime = Format-DateTimeString $syncStart
        endTime = Format-DateTimeString $syncEnd
    }
    sync = [pscustomobject]@{
        totalFetched = As-IntOrZero $syncData.totalFetched
        created = As-IntOrZero $syncData.created
        updated = As-IntOrZero $syncData.updated
        attributed = As-IntOrZero $syncData.attributed
        unattributed = As-IntOrZero $syncData.unattributed
        failed = $syncFailed
    }
    rawProbe = [pscustomobject]@{
        status = [string]$rawProbe.data.status
        upstreamCode = $rawProbe.data.remoteResponse.code
        orderCount = $rawOrdersNormalized.Count
        rawOrdersWithPickSource = $rawPickSourceOrderCount
    }
    corpus = $ordersSample.corpus
    candidate = if ($candidate) {
        [pscustomobject]@{
            orderId = $candidate.orderId
            productId = $candidate.productId
            activityId = $candidate.activityId
            pickSource = $candidate.pickSource
            orderCreateTime = $candidate.orderCreateTime
            settleTime = $candidate.settleTime
            attributionStatus = $candidate.attributionStatus
            unattributedReason = $candidate.unattributedReason
            mappingCreatedAt = $candidate.mappingCreatedAt
            mappingSourceType = $candidate.mappingSourceType
            expectedAttributionStatus = $candidate.analysis.expectedAttributionStatus
            expectedReason = $candidate.analysis.expectedReason
            statusMatchesExpectation = $candidate.analysis.statusMatchesExpectation
            reasonMatchesExpectation = $candidate.analysis.reasonMatchesExpectation
        }
    } else {
        $null
    }
    dashboard = [pscustomobject]@{
        orderCount = $dashboardSummary.orderCount
        orderAmount = $dashboardSummary.orderAmount
        serviceFee = $dashboardSummary.serviceFee
        attributedOrderCount = $dashboardSummary.attributedOrderCount
        unattributedOrderCount = $dashboardSummary.unattributedOrderCount
        upstreamProductUncoveredCount = $dashboardSummary.upstreamProductUncoveredCount
        cannotAutoAttributionCount = $dashboardSummary.cannotAutoAttributionCount
        nativeKeyMismatchCount = $dashboardSummary.nativeKeyMismatchCount
        ambiguousMappingCount = $dashboardSummary.ambiguousMappingCount
    }
    conclusion = if ($sampleVerified) {
        "Verified at least one real pick_source order sample: sync succeeded, pick_source_mapping matched, mapping.created_at <= order.create_time, attribution_status is correct, and Dashboard inclusion is explained."
    } elseif ($syncOk) {
        "Order sync succeeded, but the current upstream window has no attributable order sample. The latest raw probe returned no pick_source, and the local corpus for the last ${SampleLookbackDays} days contains 0 orders with pick_source. Continue watching the next window."
    } else {
        "Order sync did not pass, so this run cannot produce real attribution evidence."
    }
}
Write-JsonFile -Path (Join-Path $outputDir "summary.json") -Value $summary

$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add("# P3-6 real-pre attribution evidence")
$reportLines.Add("")
$reportLines.Add("- generatedAt: $($summary.generatedAt)")
$reportLines.Add("- reportStatus: $($summary.reportStatus)")
$reportLines.Add("- environment: $($envResponse.environmentLabel) / $([string]::Join(',', $envResponse.activeProfiles)) / appTestEnabled=$($envResponse.appTestEnabled) / douyinTestEnabled=$($envResponse.douyinTestEnabled)")
$reportLines.Add("- sync window: $($summary.syncWindow.startTime) -> $($summary.syncWindow.endTime)")
$reportLines.Add("- sync result: totalFetched=$($summary.sync.totalFetched) / created=$($summary.sync.created) / updated=$($summary.sync.updated) / attributed=$($summary.sync.attributed) / unattributed=$($summary.sync.unattributed) / failed=$($summary.sync.failed)")
$reportLines.Add("- raw probe: orderCount=$($summary.rawProbe.orderCount) / rawOrdersWithPickSource=$($summary.rawProbe.rawOrdersWithPickSource) / upstreamCode=$($summary.rawProbe.upstreamCode)")
$reportLines.Add("- local corpus ($SampleLookbackDays days): totalOrders=$($summary.corpus.totalOrders) / ordersWithPickSource=$($summary.corpus.ordersWithPickSource) / attributedPickSourceOrders=$($summary.corpus.attributedPickSourceOrders) / unattributedPickSourceOrders=$($summary.corpus.unattributedPickSourceOrders)")
$reportLines.Add("- dashboard summary: orderCount=$($summary.dashboard.orderCount) / attributed=$($summary.dashboard.attributedOrderCount) / unattributed=$($summary.dashboard.unattributedOrderCount) / upstreamProductUncovered=$($summary.dashboard.upstreamProductUncoveredCount) / cannotAutoAttribution=$($summary.dashboard.cannotAutoAttributionCount)")
$reportLines.Add("")
$reportLines.Add("## Conclusion")
$reportLines.Add("")
$reportLines.Add($summary.conclusion)
$reportLines.Add("")
$reportLines.Add("## Dashboard explanation")
$reportLines.Add("")
$reportLines.Add("- " + $dashboardCheck.explanation.explanation)
if ($candidate -ne $null) {
    $reportLines.Add("- candidate order: $($candidate.orderId)")
    $reportLines.Add("- candidate pick_source: $($candidate.pickSource)")
    $reportLines.Add("- mapping_created_at: $($candidate.mappingCreatedAt)")
    $reportLines.Add("- expected attribution: $($candidate.analysis.expectedAttributionStatus) / $($candidate.analysis.expectedReason)")
    $reportLines.Add("- actual attribution: $($candidate.attributionStatus) / $($candidate.unattributedReason)")
}
$reportLines.Add("")
$reportLines.Add("## Constraints")
$reportLines.Add("")
$reportLines.Add("- No OAuth code, access token, refresh token, JWT, or client secret is written to this evidence directory.")

Write-TextFile -Path (Join-Path $outputDir "report.md") -Value ($reportLines -join [Environment]::NewLine)

Write-Host "real-pre attribution evidence output: $outputDir" -ForegroundColor Green

if (-not $syncOk) {
    exit 1
}
exit 0
