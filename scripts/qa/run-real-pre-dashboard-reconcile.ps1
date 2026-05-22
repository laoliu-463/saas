param(
    [string]$BaseUrl = "http://localhost:8081/api",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "admin123",
    [string]$PostgresContainer = "saas-active-postgres-real-pre-1",
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
    $dir = Join-Path $RepoRoot ("runtime\qa\out\real-pre-dashboard-reconcile-" + $stamp)
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    return $dir
}

function Write-JsonFile {
    param(
        [string]$Path,
        [object]$Value
    )

    $json = $Value | ConvertTo-Json -Depth 20
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

function As-LongOrZero {
    param([object]$Value)

    if ($null -eq $Value) {
        return 0L
    }
    $text = [string]$Value
    if ([string]::IsNullOrWhiteSpace($text)) {
        return 0L
    }
    return [long]$text
}

function Compare-Metric {
    param(
        [string]$Name,
        [object]$ApiValue,
        [object]$DbValue
    )

    $apiLong = As-LongOrZero $ApiValue
    $dbLong = As-LongOrZero $DbValue
    return [pscustomobject]@{
        name = $Name
        apiValue = $apiLong
        dbValue = $dbLong
        diff = [math]::Abs($apiLong - $dbLong)
        pass = $apiLong -eq $dbLong
    }
}

function Get-DiffReasonCandidates {
    param(
        [object[]]$FailedMetrics
    )

    if (-not $FailedMetrics -or $FailedMetrics.Count -eq 0) {
        return @()
    }

    $names = @($FailedMetrics | ForEach-Object { [string]$_.name })
    $reasons = New-Object System.Collections.Generic.List[string]

    if ($names | Where-Object { $_ -in @("orderCount", "orderAmount", "serviceFee", "attributedOrderCount", "unattributedOrderCount") }) {
        $reasons.Add("Time range mismatch: verify that the API default summary scope and the SQL filters are identical.")
        $reasons.Add("Order status filter mismatch: verify that both API and SQL only count deleted=0 orders.")
    }
    if ($names | Where-Object { $_ -in @("orderAmount", "serviceFee") }) {
        $reasons.Add("Money unit mismatch: verify that both API and SQL use cent-based values with no yuan/cent mix.")
        $reasons.Add("serviceFee field mismatch: verify that both sides use settle_colonel_commission.")
    }
    if ($names | Where-Object { $_ -in @("upstreamProductUncoveredCount", "cannotAutoAttributionCount", "ambiguousMappingCount", "unsafeBecauseCreatedAfterOrderCount", "nativeKeyMismatchCount") }) {
        $reasons.Add("Diagnosis enum mismatch: verify that the SQL CASE logic matches DashboardService exactly.")
    }

    $reasons.Add("Dashboard may still hit the 30-second cache: wait for cache expiry and rerun if needed.")
    return [object[]]@($reasons | Select-Object -Unique)
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

$health = Invoke-RestMethod -Method Get -Uri ($BaseUrl.TrimEnd("/") + "/system/health")
if ($health.status -ne "UP") {
    throw "/system/health is not UP."
}

$adminToken = Get-ApiToken -BaseUrl $BaseUrl -Username $AdminUsername -Password $AdminPassword
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
$dashboardResponse = Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/dashboard/summary") -Headers $adminHeaders -Body $null
$dashboardSummary = $dashboardResponse.data
Write-JsonFile -Path (Join-Path $outputDir "api-dashboard-summary.json") -Value $dashboardResponse
Write-JsonFile -Path (Join-Path $outputDir "dashboard-summary.json") -Value $dashboardResponse

$dbSummarySql = @"
WITH filtered_orders AS (
    SELECT co.order_id,
           co.order_amount,
           co.settle_colonel_commission,
           co.product_id,
           co.colonel_activity_id AS activity_id,
           co.second_colonel_activity_id,
           co.colonel_buyin_id,
           co.attribution_status,
           co.attribution_remark,
           co.create_time
    FROM colonelsettlement_order co
    WHERE co.deleted = 0
),
diagnosed AS (
    SELECT fo.order_id,
           CASE
               WHEN COALESCE(fo.attribution_status, 'UNATTRIBUTED') = 'ATTRIBUTED' THEN 'ATTRIBUTED'
               WHEN fo.attribution_remark = 'COLONEL_MAPPING_AMBIGUOUS' THEN 'AMBIGUOUS_MAPPING'
               WHEN (fo.activity_id IS NULL OR BTRIM(fo.activity_id) = '')
                    AND (fo.second_colonel_activity_id IS NULL OR BTRIM(fo.second_colonel_activity_id) = '') THEN 'MISSING_ACTIVITY_ID'
               WHEN fo.product_id IS NULL OR BTRIM(fo.product_id) = '' THEN 'MISSING_PRODUCT_ID'
               WHEN NOT EXISTS (
                    SELECT 1
                    FROM product_snapshot ps
                    WHERE ps.deleted = 0
                      AND ps.activity_id = fo.activity_id
                      AND ps.product_id = fo.product_id
               ) AND NOT EXISTS (
                    SELECT 1
                    FROM product_operation_state pos
                    WHERE pos.deleted = 0
                      AND pos.activity_id = fo.activity_id
                      AND pos.product_id = fo.product_id
               ) THEN 'UPSTREAM_PRODUCT_UNCOVERED'
               WHEN (
                    SELECT COUNT(*)
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
               ) > 1 THEN 'AMBIGUOUS_MAPPING'
               WHEN (
                    SELECT COUNT(*)
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
                      AND psm.colonel_buyin_id = CAST(fo.colonel_buyin_id AS varchar)
               ) = 1
               AND EXISTS (
                    SELECT 1
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
                      AND psm.colonel_buyin_id = CAST(fo.colonel_buyin_id AS varchar)
                      AND psm.create_time > fo.create_time
               ) THEN 'MECHANISM_HIT_HISTORY_UNSAFE'
               WHEN (
                    SELECT COUNT(*)
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
                      AND psm.colonel_buyin_id = CAST(fo.colonel_buyin_id AS varchar)
               ) = 0
               AND (
                    SELECT COUNT(*)
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
               ) = 1
               AND EXISTS (
                    SELECT 1
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
                      AND psm.colonel_buyin_id <> CAST(fo.colonel_buyin_id AS varchar)
               ) THEN 'NATIVE_KEY_MISMATCH'
               ELSE 'CANNOT_AUTO_ATTRIBUTION'
           END AS diagnostic_category
    FROM filtered_orders fo
    WHERE COALESCE(fo.attribution_status, 'UNATTRIBUTED') <> 'ATTRIBUTED'
)
SELECT
    (SELECT COUNT(*) FROM filtered_orders)::text AS order_count,
    (SELECT COALESCE(SUM(order_amount), 0) FROM filtered_orders)::text AS order_amount,
    (SELECT COALESCE(SUM(settle_colonel_commission), 0) FROM filtered_orders)::text AS service_fee,
    (SELECT COUNT(*) FROM filtered_orders WHERE attribution_status = 'ATTRIBUTED')::text AS attributed_order_count,
    (SELECT COUNT(*) FROM filtered_orders WHERE attribution_status = 'UNATTRIBUTED')::text AS unattributed_order_count,
    (SELECT COUNT(*) FROM diagnosed WHERE diagnostic_category = 'MECHANISM_HIT_HISTORY_UNSAFE')::text AS unsafe_because_created_after_order_count,
    (SELECT COUNT(*) FROM diagnosed WHERE diagnostic_category = 'UPSTREAM_PRODUCT_UNCOVERED')::text AS upstream_product_uncovered_count,
    (SELECT COUNT(*) FROM diagnosed WHERE diagnostic_category = 'CANNOT_AUTO_ATTRIBUTION')::text AS cannot_auto_attribution_count,
    (SELECT COUNT(*) FROM diagnosed WHERE diagnostic_category = 'NATIVE_KEY_MISMATCH')::text AS native_key_mismatch_count,
    (SELECT COUNT(*) FROM diagnosed WHERE diagnostic_category = 'AMBIGUOUS_MAPPING')::text AS ambiguous_mapping_count;
"@

$diagnosticBreakdownSql = @"
WITH filtered_orders AS (
    SELECT co.order_id,
           co.product_id,
           co.colonel_activity_id AS activity_id,
           co.second_colonel_activity_id,
           co.colonel_buyin_id,
           co.attribution_status,
           co.attribution_remark,
           co.create_time
    FROM colonelsettlement_order co
    WHERE co.deleted = 0
),
diagnosed AS (
    SELECT fo.order_id,
           CASE
               WHEN COALESCE(fo.attribution_status, 'UNATTRIBUTED') = 'ATTRIBUTED' THEN 'ATTRIBUTED'
               WHEN fo.attribution_remark = 'COLONEL_MAPPING_AMBIGUOUS' THEN 'AMBIGUOUS_MAPPING'
               WHEN (fo.activity_id IS NULL OR BTRIM(fo.activity_id) = '')
                    AND (fo.second_colonel_activity_id IS NULL OR BTRIM(fo.second_colonel_activity_id) = '') THEN 'MISSING_ACTIVITY_ID'
               WHEN fo.product_id IS NULL OR BTRIM(fo.product_id) = '' THEN 'MISSING_PRODUCT_ID'
               WHEN NOT EXISTS (
                    SELECT 1
                    FROM product_snapshot ps
                    WHERE ps.deleted = 0
                      AND ps.activity_id = fo.activity_id
                      AND ps.product_id = fo.product_id
               ) AND NOT EXISTS (
                    SELECT 1
                    FROM product_operation_state pos
                    WHERE pos.deleted = 0
                      AND pos.activity_id = fo.activity_id
                      AND pos.product_id = fo.product_id
               ) THEN 'UPSTREAM_PRODUCT_UNCOVERED'
               WHEN (
                    SELECT COUNT(*)
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
               ) > 1 THEN 'AMBIGUOUS_MAPPING'
               WHEN (
                    SELECT COUNT(*)
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
                      AND psm.colonel_buyin_id = CAST(fo.colonel_buyin_id AS varchar)
               ) = 1
               AND EXISTS (
                    SELECT 1
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
                      AND psm.colonel_buyin_id = CAST(fo.colonel_buyin_id AS varchar)
                      AND psm.create_time > fo.create_time
               ) THEN 'MECHANISM_HIT_HISTORY_UNSAFE'
               WHEN (
                    SELECT COUNT(*)
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
                      AND psm.colonel_buyin_id = CAST(fo.colonel_buyin_id AS varchar)
               ) = 0
               AND (
                    SELECT COUNT(*)
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
               ) = 1
               AND EXISTS (
                    SELECT 1
                    FROM pick_source_mapping psm
                    WHERE psm.deleted = 0
                      AND psm.status = 1
                      AND psm.source_type = 'NATIVE'
                      AND psm.activity_id = fo.activity_id
                      AND psm.product_id = fo.product_id
                      AND psm.colonel_buyin_id <> CAST(fo.colonel_buyin_id AS varchar)
               ) THEN 'NATIVE_KEY_MISMATCH'
               ELSE 'CANNOT_AUTO_ATTRIBUTION'
           END AS diagnostic_category
    FROM filtered_orders fo
    WHERE COALESCE(fo.attribution_status, 'UNATTRIBUTED') <> 'ATTRIBUTED'
)
SELECT diagnostic_category, COUNT(*)::text AS total_count
FROM diagnosed
WHERE diagnostic_category IN (
    'MECHANISM_HIT_HISTORY_UNSAFE',
    'UPSTREAM_PRODUCT_UNCOVERED',
    'CANNOT_AUTO_ATTRIBUTION',
    'NATIVE_KEY_MISMATCH',
    'AMBIGUOUS_MAPPING'
)
GROUP BY diagnostic_category
ORDER BY diagnostic_category;
"@

$reasonBreakdownSql = @"
SELECT COALESCE(attribution_remark, '') AS attribution_remark,
       COUNT(*)::text AS total_count
FROM colonelsettlement_order
WHERE deleted = 0
  AND attribution_status = 'UNATTRIBUTED'
GROUP BY COALESCE(attribution_remark, '')
ORDER BY COUNT(*) DESC, attribution_remark;
"@

$dbSummaryRows = @(Invoke-ScalarSql -Sql $dbSummarySql)
$dbSummary = Parse-PipeRow -Row $dbSummaryRows[0] -Columns @(
    "orderCount",
    "orderAmount",
    "serviceFee",
    "attributedOrderCount",
    "unattributedOrderCount",
    "unsafeBecauseCreatedAfterOrderCount",
    "upstreamProductUncoveredCount",
    "cannotAutoAttributionCount",
    "nativeKeyMismatchCount",
    "ambiguousMappingCount"
)

$diagnosticRows = @(Invoke-ScalarSql -Sql $diagnosticBreakdownSql | ForEach-Object {
    Parse-PipeRow -Row $_ -Columns @("diagnosticCategory", "totalCount")
})

$reasonRows = @(Invoke-ScalarSql -Sql $reasonBreakdownSql | ForEach-Object {
    Parse-PipeRow -Row $_ -Columns @("attributionRemark", "totalCount")
})

Write-JsonFile -Path (Join-Path $outputDir "db-dashboard-summary.json") -Value ([pscustomobject]@{
    summary = $dbSummary
    diagnosticBreakdown = [object[]]$diagnosticRows
    unattributedReasonBreakdown = [object[]]$reasonRows
})
Write-JsonFile -Path (Join-Path $outputDir "db-summary.json") -Value ([pscustomobject]@{
    summary = $dbSummary
    diagnosticBreakdown = [object[]]$diagnosticRows
    unattributedReasonBreakdown = [object[]]$reasonRows
})

$metricResults = @(
    Compare-Metric -Name "orderCount" -ApiValue $dashboardSummary.orderCount -DbValue $dbSummary.orderCount
    Compare-Metric -Name "orderAmount" -ApiValue $dashboardSummary.orderAmount -DbValue $dbSummary.orderAmount
    Compare-Metric -Name "serviceFee" -ApiValue $dashboardSummary.serviceFee -DbValue $dbSummary.serviceFee
    Compare-Metric -Name "attributedOrderCount" -ApiValue $dashboardSummary.attributedOrderCount -DbValue $dbSummary.attributedOrderCount
    Compare-Metric -Name "unattributedOrderCount" -ApiValue $dashboardSummary.unattributedOrderCount -DbValue $dbSummary.unattributedOrderCount
    Compare-Metric -Name "unsafeBecauseCreatedAfterOrderCount" -ApiValue $dashboardSummary.unsafeBecauseCreatedAfterOrderCount -DbValue $dbSummary.unsafeBecauseCreatedAfterOrderCount
    Compare-Metric -Name "upstreamProductUncoveredCount" -ApiValue $dashboardSummary.upstreamProductUncoveredCount -DbValue $dbSummary.upstreamProductUncoveredCount
    Compare-Metric -Name "cannotAutoAttributionCount" -ApiValue $dashboardSummary.cannotAutoAttributionCount -DbValue $dbSummary.cannotAutoAttributionCount
    Compare-Metric -Name "nativeKeyMismatchCount" -ApiValue $dashboardSummary.nativeKeyMismatchCount -DbValue $dbSummary.nativeKeyMismatchCount
    Compare-Metric -Name "ambiguousMappingCount" -ApiValue $dashboardSummary.ambiguousMappingCount -DbValue $dbSummary.ambiguousMappingCount
)

$failedMetrics = @($metricResults | Where-Object { -not $_.pass })
$overallPass = $failedMetrics.Count -eq 0
$status = if ($overallPass) { "PASS" } else { "DIFF_FOUND" }
$diffReasonCandidates = Get-DiffReasonCandidates -FailedMetrics $failedMetrics

$ordersBreakdown = [pscustomobject]@{
    api = [pscustomobject]@{
        unattributedReasons = [object[]]$dashboardSummary.unattributedReasons
        diagnosticBreakdown = [object[]]$dashboardSummary.diagnosticBreakdown
    }
    db = [pscustomobject]@{
        unattributedReasonBreakdown = [object[]]$reasonRows
        diagnosticBreakdown = [object[]]$diagnosticRows
    }
}
Write-JsonFile -Path (Join-Path $outputDir "orders-breakdown.json") -Value $ordersBreakdown

$diffPayload = [pscustomobject]@{
    status = $status
    metrics = [object[]]$failedMetrics
    candidateReasons = [object[]]@($diffReasonCandidates)
}
Write-JsonFile -Path (Join-Path $outputDir "diff.json") -Value $diffPayload

$summary = [pscustomobject]@{
    evidenceType = "real-pre-dashboard-reconcile"
    generatedAt = (Get-Date).ToString("s")
    evidenceDir = $outputDir
    status = $status
    overallPass = $overallPass
    environment = [pscustomobject]@{
        activeProfiles = $envResponse.activeProfiles
        environmentLabel = $envResponse.environmentLabel
        appTestEnabled = $envResponse.appTestEnabled
        douyinTestEnabled = $envResponse.douyinTestEnabled
        database = $envResponse.database
        healthStatus = $health.status
    }
    apiSummary = $dashboardSummary
    dbSummary = [pscustomobject]@{
        orderCount = As-LongOrZero $dbSummary.orderCount
        orderAmount = As-LongOrZero $dbSummary.orderAmount
        serviceFee = As-LongOrZero $dbSummary.serviceFee
        attributedOrderCount = As-LongOrZero $dbSummary.attributedOrderCount
        unattributedOrderCount = As-LongOrZero $dbSummary.unattributedOrderCount
        unsafeBecauseCreatedAfterOrderCount = As-LongOrZero $dbSummary.unsafeBecauseCreatedAfterOrderCount
        upstreamProductUncoveredCount = As-LongOrZero $dbSummary.upstreamProductUncoveredCount
        cannotAutoAttributionCount = As-LongOrZero $dbSummary.cannotAutoAttributionCount
        nativeKeyMismatchCount = As-LongOrZero $dbSummary.nativeKeyMismatchCount
        ambiguousMappingCount = As-LongOrZero $dbSummary.ambiguousMappingCount
    }
    metricResults = [object[]]$metricResults
    failedMetrics = [object[]]$failedMetrics
    diffReasonCandidates = [object[]]@($diffReasonCandidates)
    diagnosticBreakdown = [object[]]$diagnosticRows
    conclusion = if ($overallPass) {
        "Dashboard summary matches PostgreSQL aggregates for the current real-pre order pool."
    } else {
        "Dashboard summary does not yet match PostgreSQL aggregates for all required metrics."
    }
}

Write-JsonFile -Path (Join-Path $outputDir "summary.json") -Value $summary

$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add("# P3-7 Dashboard Reconcile Report")
$reportLines.Add("")
$reportLines.Add("- generatedAt: $($summary.generatedAt)")
$reportLines.Add("- status: $($summary.status)")
$reportLines.Add("- overallPass: $($summary.overallPass)")
$reportLines.Add("- environment: $($summary.environment.environmentLabel) / $([string]::Join(',', $summary.environment.activeProfiles)) / appTestEnabled=$($summary.environment.appTestEnabled) / douyinTestEnabled=$($summary.environment.douyinTestEnabled)")
$reportLines.Add("- database: $($summary.environment.database)")
$reportLines.Add("")
$reportLines.Add("## Key Results")
$reportLines.Add("")
foreach ($metric in $metricResults) {
    if ($metric.pass) {
        $reportLines.Add("- $($metric.name): API = DB ($($metric.apiValue))")
    } else {
        $reportLines.Add("- $($metric.name): API = $($metric.apiValue), DB = $($metric.dbValue), diff = $($metric.diff)")
    }
}
$reportLines.Add("")
$reportLines.Add("## Conclusion")
$reportLines.Add("")
if ($overallPass) {
    $reportLines.Add("PASS")
    $reportLines.Add("")
    $reportLines.Add("This run proves that /api/dashboard/summary matches the PostgreSQL aggregation logic.")
    $reportLines.Add("The current real-pre dashboard values are traceable to the real order fact table.")
} else {
    $reportLines.Add("DIFF_FOUND")
    $reportLines.Add("")
    $reportLines.Add("This run found differences between Dashboard and PostgreSQL aggregates, so P3-7 is not marked as passed yet.")
}

if ($diffReasonCandidates.Count -gt 0) {
    $reportLines.Add("")
    $reportLines.Add("## Initial Diagnosis")
    $reportLines.Add("")
    foreach ($reason in $diffReasonCandidates) {
        $reportLines.Add("- $reason")
    }
}

$reportLines.Add("")
$reportLines.Add("## Constraints")
$reportLines.Add("")
$reportLines.Add("- No OAuth code, access token, refresh token, JWT, or client secret is written to this evidence directory.")

Write-TextFile -Path (Join-Path $outputDir "report.md") -Value ($reportLines -join [Environment]::NewLine)

Write-Host "real-pre dashboard reconcile output: $outputDir" -ForegroundColor Green

if (-not $overallPass) {
    exit 1
}
exit 0
