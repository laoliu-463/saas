param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "admin123",
    [int]$SyncWindowMinutes = 30,
    [int]$RawProbeWindowDays = 3,
    [int]$RawProbeCount = 20,
    [int]$LocalLookbackDays = 30,
    [int]$CandidateLimit = 20,
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
    $dir = Join-Path $RepoRoot ("runtime\qa\out\real-pre-pick-source-watch-" + $stamp)
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

function Get-LocalCorpus {
    param([datetime]$StartTime)

    $sql = @"
SELECT COUNT(*)::text AS total_orders,
       COUNT(*) FILTER (WHERE COALESCE(pick_source, '') <> '')::text AS orders_with_pick_source
FROM colonelsettlement_order
WHERE deleted = 0
  AND create_time >= $(SqlLiteral (Format-DateTimeString $StartTime));
"@
    $rows = @(Invoke-ScalarSql -Sql $sql)
    return Parse-PipeRow -Row $rows[0] -Columns @(
        "totalOrders",
        "ordersWithPickSource"
    )
}

function Get-LocalOrdersWithPickSource {
    param(
        [datetime]$StartTime,
        [int]$Limit
    )

    $sql = @"
SELECT order_id,
       COALESCE(product_id, '') AS product_id,
       COALESCE(colonel_activity_id, '') AS activity_id,
       COALESCE(pick_source, '') AS pick_source,
       COALESCE(create_time::text, '') AS order_create_time,
       COALESCE(settle_time::text, '') AS settle_time,
       COALESCE(order_amount, 0)::text AS order_amount,
       COALESCE(settle_colonel_commission, 0)::text AS service_fee,
       COALESCE(attribution_status, '') AS attribution_status,
       COALESCE(attribution_remark, '') AS unattributed_reason,
       COALESCE(channel_user_id::text, '') AS channel_id,
       COALESCE(colonel_buyin_id::text, '') AS colonel_buyin_id
FROM colonelsettlement_order
WHERE deleted = 0
  AND create_time >= $(SqlLiteral (Format-DateTimeString $StartTime))
  AND COALESCE(pick_source, '') <> ''
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
SELECT COALESCE(activity_id, '') AS activity_id,
       COALESCE(product_id, '') AS product_id,
       COALESCE(user_id::text, '') AS user_id,
       COALESCE(create_time::text, '') AS mapping_created_at,
       COALESCE(source_type, '') AS mapping_source_type,
       COALESCE(colonel_buyin_id::text, '') AS colonel_buyin_id
FROM pick_source_mapping
WHERE deleted = 0
  AND pick_source = $(SqlLiteral $PickSource)
ORDER BY create_time DESC;
"@
    return @(Invoke-ScalarSql -Sql $sql | ForEach-Object {
        Parse-PipeRow -Row $_ -Columns @(
            "activityId",
            "productId",
            "userId",
            "mappingCreatedAt",
            "mappingSourceType",
            "colonelBuyinId"
        )
    })
}

function Get-ExpectedAnalysis {
    param(
        [pscustomobject]$Order,
        [object[]]$Mappings
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

    $expectedStatus = if ($safeMappingsWithUser.Count -gt 0) { "ATTRIBUTED" } else { "UNATTRIBUTED" }
    $actualStatus = [string]$Order.attributionStatus

    $reason = if ($expectedStatus -eq "ATTRIBUTED") {
        "SAFE_MAPPING_FOUND"
    } elseif ($Mappings.Count -eq 0) {
        "MAPPING_NOT_FOUND"
    } elseif ($exactMappings.Count -eq 0) {
        "MAPPING_PRODUCT_ACTIVITY_MISMATCH"
    } elseif ($safeMappings.Count -eq 0) {
        "MAPPING_CREATED_AFTER_ORDER"
    } else {
        "CHANNEL_NOT_FOUND"
    }

    return [pscustomobject]@{
        expectedAttributionStatus = $expectedStatus
        actualAttributionStatus = $actualStatus
        statusMatchesExpectation = $expectedStatus -eq $actualStatus
        reason = $reason
        exactMappingCount = $exactMappings.Count
        safeExactMappingCount = $safeMappings.Count
        safeExactMappingWithUserCount = $safeMappingsWithUser.Count
        winningMapping = @($safeMappingsWithUser | Select-Object -First 1)
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

$health = Invoke-RestMethod -Method Get -Uri ($BaseUrl.TrimEnd("/") + "/system/health")
$adminToken = Get-ApiToken -BaseUrl $BaseUrl -Username $AdminUsername -Password $AdminPassword
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

$syncEnd = Get-Date
$syncStart = $syncEnd.AddMinutes(-1 * $SyncWindowMinutes)
$rawProbeStart = $syncEnd.AddDays(-1 * $RawProbeWindowDays)
$localLookbackStart = $syncEnd.AddDays(-1 * $LocalLookbackDays)

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
Write-JsonFile -Path (Join-Path $outputDir "raw-probe.json") -Value $rawProbe

$rawOrders = @()
if ($rawProbe.data -and $rawProbe.data.remoteResponse -and $rawProbe.data.remoteResponse.data -and $rawProbe.data.remoteResponse.data.orders) {
    $rawOrders = @($rawProbe.data.remoteResponse.data.orders)
}
$rawOrdersWithPickSource = @($rawOrders | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_.pick_source) })

$localCorpus = Get-LocalCorpus -StartTime $localLookbackStart
$localOrdersWithPickSource = Get-LocalOrdersWithPickSource -StartTime $localLookbackStart -Limit $CandidateLimit
Write-JsonFile -Path (Join-Path $outputDir "local-orders.json") -Value ([pscustomobject]@{
    lookbackDays = $LocalLookbackDays
    corpus = [pscustomobject]@{
        totalOrders = As-IntOrZero $localCorpus.totalOrders
        ordersWithPickSource = As-IntOrZero $localCorpus.ordersWithPickSource
    }
    candidateOrders = [object[]]$localOrdersWithPickSource
})

$mappingChecks = @()
foreach ($order in $localOrdersWithPickSource) {
    $mappings = Get-MappingRowsForPickSource -PickSource $order.pickSource
    $analysis = Get-ExpectedAnalysis -Order $order -Mappings $mappings
    $mappingChecks += [pscustomobject]@{
        orderId = $order.orderId
        productId = $order.productId
        activityId = $order.activityId
        pickSource = $order.pickSource
        orderCreateTime = $order.orderCreateTime
        attributionStatus = $order.attributionStatus
        unattributedReason = $order.unattributedReason
        analysis = $analysis
        mappings = [object[]]$mappings
    }
}
Write-JsonFile -Path (Join-Path $outputDir "mapping-check.json") -Value ([pscustomobject]@{
    checkedOrders = [object[]]$mappingChecks
})

$verifiedOrders = @($mappingChecks | Where-Object {
    $_.analysis.expectedAttributionStatus -eq "ATTRIBUTED" -and $_.analysis.statusMatchesExpectation
})

$syncData = $syncResult.data
$syncFailed = As-IntOrZero $syncData.failed
$status = if ($syncResult.code -ne 200 -or $syncFailed -ne 0) {
    "SYNC_FAILED"
} elseif ($verifiedOrders.Count -gt 0) {
    "PICK_SOURCE_SAMPLE_VERIFIED"
} elseif ($rawOrdersWithPickSource.Count -gt 0 -or $localOrdersWithPickSource.Count -gt 0) {
    "PICK_SOURCE_SAMPLE_FOUND_UNVERIFIED"
} else {
    "SYNC_OK_NO_SAMPLE"
}

$summary = [pscustomobject]@{
    evidenceType = "real-pre-pick-source-watch"
    generatedAt = (Get-Date).ToString("s")
    evidenceDir = $outputDir
    status = $status
    environment = [pscustomobject]@{
        activeProfiles = $envResponse.activeProfiles
        environmentLabel = $envResponse.environmentLabel
        appTestEnabled = $envResponse.appTestEnabled
        douyinTestEnabled = $envResponse.douyinTestEnabled
        database = $envResponse.database
        healthStatus = $health.status
    }
    sync = [pscustomobject]@{
        startTime = Format-DateTimeString $syncStart
        endTime = Format-DateTimeString $syncEnd
        totalFetched = As-IntOrZero $syncData.totalFetched
        created = As-IntOrZero $syncData.created
        updated = As-IntOrZero $syncData.updated
        attributed = As-IntOrZero $syncData.attributed
        unattributed = As-IntOrZero $syncData.unattributed
        failed = $syncFailed
    }
    rawProbe = [pscustomobject]@{
        windowDays = $RawProbeWindowDays
        orderCount = @($rawOrders).Count
        ordersWithPickSource = $rawOrdersWithPickSource.Count
    }
    localCorpus = [pscustomobject]@{
        lookbackDays = $LocalLookbackDays
        totalOrders = As-IntOrZero $localCorpus.totalOrders
        ordersWithPickSource = As-IntOrZero $localCorpus.ordersWithPickSource
    }
    verifiedOrderCount = $verifiedOrders.Count
    verifiedOrders = [object[]]$verifiedOrders
    conclusion = if ($status -eq "SYNC_OK_NO_SAMPLE") {
        "Sync path passed, but the current upstream and local windows contain no real order with pick_source."
    } elseif ($status -eq "PICK_SOURCE_SAMPLE_VERIFIED") {
        "At least one real order with pick_source satisfies the mapping safety rule and attribution status check."
    } elseif ($status -eq "PICK_SOURCE_SAMPLE_FOUND_UNVERIFIED") {
        "A real order with pick_source exists, but mapping safety or attribution status still needs manual review."
    } else {
        "Order sync did not pass."
    }
}
Write-JsonFile -Path (Join-Path $outputDir "summary.json") -Value $summary

$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add("# P3-6 pick_source watch")
$reportLines.Add("")
$reportLines.Add("- status: $($summary.status)")
$reportLines.Add("- generatedAt: $($summary.generatedAt)")
$reportLines.Add("- environment: $($summary.environment.environmentLabel) / $([string]::Join(',', $summary.environment.activeProfiles)) / appTestEnabled=$($summary.environment.appTestEnabled) / douyinTestEnabled=$($summary.environment.douyinTestEnabled)")
$reportLines.Add("- sync: totalFetched=$($summary.sync.totalFetched) / created=$($summary.sync.created) / updated=$($summary.sync.updated) / attributed=$($summary.sync.attributed) / unattributed=$($summary.sync.unattributed) / failed=$($summary.sync.failed)")
$reportLines.Add("- raw probe: orderCount=$($summary.rawProbe.orderCount) / ordersWithPickSource=$($summary.rawProbe.ordersWithPickSource)")
$reportLines.Add("- local corpus: totalOrders=$($summary.localCorpus.totalOrders) / ordersWithPickSource=$($summary.localCorpus.ordersWithPickSource)")
$reportLines.Add("")
$reportLines.Add("## Conclusion")
$reportLines.Add("")
$reportLines.Add($summary.conclusion)
$reportLines.Add("")
$reportLines.Add("## Next action")
$reportLines.Add("")
if ($status -eq "SYNC_OK_NO_SAMPLE") {
    $reportLines.Add("- Keep watching for a real order with pick_source; rerun this script on the next window.")
} elseif ($status -eq "PICK_SOURCE_SAMPLE_VERIFIED") {
    $reportLines.Add("- Promote the matching order into P3-6-E evidence and update the main evidence index.")
} elseif ($status -eq "PICK_SOURCE_SAMPLE_FOUND_UNVERIFIED") {
    $reportLines.Add("- Inspect mapping-check.json and reconcile mapping.created_at, activity/product match, and attribution_status.")
} else {
    $reportLines.Add("- Fix the sync failure before continuing.")
}

Write-TextFile -Path (Join-Path $outputDir "report.md") -Value ($reportLines -join [Environment]::NewLine)

Write-Host "real-pre pick_source watch output: $outputDir" -ForegroundColor Green

if ($status -eq "SYNC_FAILED") {
    exit 1
}
exit 0
