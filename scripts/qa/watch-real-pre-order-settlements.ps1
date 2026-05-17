param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [int]$SettlementWindowDays = 30,
    [int]$RawOrderWindowDays = 3,
    [int]$SettlementSize = 20,
    [int]$RawOrderCount = 5,
    [string]$ComposeFile = "docker-compose.yml",
    [string]$EnvFile = ".env.real-pre",
    [string]$PostgresService = "postgres",
    [string]$DbUser = "saas",
    [string]$DbName = "saas_real_pre",
    [switch]$SkipDockerEvidence
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
    $dir = Join-Path $RepoRoot ("runtime\qa\out\real-pre-order-settlements-watch-" + $stamp)
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
    $composeArgs += @("--project-name", "saas-active")
    $composeArgs += @("-f", (Join-Path $RepoRoot $ComposeFile))
    $composeArgs += $Arguments
    & docker compose @composeArgs
}

function Invoke-ScalarSql {
    param(
        [string]$RepoRoot,
        [string]$Sql
    )

    $output = Invoke-Compose -RepoRoot $RepoRoot -Arguments @(
        "exec", "-T", $PostgresService,
        "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1",
        "-U", $DbUser, "-d", $DbName,
        "-t", "-A", "-F", "|", "-c", $Sql
    )
    return @($output | Where-Object { $_ -and $_.Trim() -ne "" })
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
    param([string]$BaseUrl)

    $login = Invoke-ApiJson -Method "Post" -Uri ($BaseUrl.TrimEnd("/") + "/auth/login") -Headers @{} -Body @{
        username = $Username
        password = $Password
    }
    if (-not $login.data.token) {
        throw "Login succeeded but token was empty."
    }
    return $login.data.token
}

function Format-DateTimeString {
    param([datetime]$Value)
    return $Value.ToString("yyyy-MM-dd HH:mm:ss")
}

function Get-SettlementOrders {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers
    )

    $end = Get-Date
    $start = $end.AddDays(-1 * $SettlementWindowDays)
    $uri = ($BaseUrl.TrimEnd("/") + "/douyin/order-settlements" +
        "?startTime=" + [uri]::EscapeDataString((Format-DateTimeString $start)) +
        "&endTime=" + [uri]::EscapeDataString((Format-DateTimeString $end)) +
        "&size=" + $SettlementSize +
        "&cursor=0&timeType=update")
    return Invoke-ApiJson -Method "Get" -Uri $uri -Headers $Headers -Body $null
}

function Get-RawOrders {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers
    )

    $end = Get-Date
    $start = $end.AddDays(-1 * $RawOrderWindowDays)
    return Invoke-ApiJson -Method "Post" -Uri ($BaseUrl.TrimEnd("/") + "/douyin/order-sync-probes/raw") -Headers $Headers -Body @{
        start_time = Format-DateTimeString $start
        end_time = Format-DateTimeString $end
        count = $RawOrderCount
        cursor = "0"
    }
}

function Get-SettlementOrderIds {
    param([object]$SettlementResponse)

    if ($SettlementResponse.data.status -ne "success") {
        return @()
    }
    $ordersNode = $SettlementResponse.data.remoteResponse.data.orders
    if ($null -eq $ordersNode) {
        return @()
    }
    $orders = @($ordersNode | Where-Object { $null -ne $_ })
    return @($orders | ForEach-Object { $_.order_id } | Where-Object { $_ -and $_.Trim() -ne "" })
}

function Invoke-WebhookProbe {
    param(
        [string]$BaseUrl,
        [string]$OrderId,
        [string]$WebhookClientSecret
    )

    $eventId = "evt-realpre-watch-" + [guid]::NewGuid().ToString("N").Substring(0, 10)
    $uri = $BaseUrl.TrimEnd("/") + "/douyin/webhooks/colonel-open-events"
    $body = @{
        event = "doudian_alliance_colonelOpenEvent"
        event_id = $eventId
        data = @{
            order_id = $OrderId
            note = "watch-real-pre-order-settlements"
        }
    }
    $rawBody = ($body | ConvertTo-Json -Depth 10 -Compress)
    $webhookHeaders = @{}
    if ($WebhookClientSecret) {
        $webhookHeaders["x-doudian-sign"] = Get-HmacSha256Hex -Body $rawBody -Secret $WebhookClientSecret
    }
    $response = Invoke-WebRequest -Method Post -Uri $uri -ContentType "application/json" -Headers $webhookHeaders -Body $rawBody -UseBasicParsing
    return [pscustomobject]@{
        eventId = $eventId
        orderId = $OrderId
        statusCode = [int]$response.StatusCode
        body = $response.Content
    }
}

Set-Location (Get-RepoRoot)
$repoRoot = Get-Location | Select-Object -ExpandProperty Path
$outputDir = New-OutputDir -RepoRoot $repoRoot
$envMap = Get-EnvMap -Path (Join-Path $repoRoot $EnvFile)
$webhookClientSecret = $envMap["DOUYIN_CLIENT_SECRET"]

$summary = [ordered]@{
    generatedAt = (Get-Date).ToString("s")
    baseUrl = $BaseUrl
    settlementWindowDays = $SettlementWindowDays
    rawOrderWindowDays = $RawOrderWindowDays
    settlementSize = $SettlementSize
    rawOrderCount = $RawOrderCount
}

$token = Get-ApiToken -BaseUrl $BaseUrl
$headers = @{ Authorization = "Bearer $token" }

$institutionInfo = Invoke-ApiJson -Method "Get" -Uri ($BaseUrl.TrimEnd("/") + "/douyin/institution-info") -Headers $headers -Body $null
$settlementResponse = Get-SettlementOrders -BaseUrl $BaseUrl -Headers $headers
$rawOrderResponse = Get-RawOrders -BaseUrl $BaseUrl -Headers $headers

Write-JsonFile -Path (Join-Path $outputDir "institution-info.json") -Value $institutionInfo
Write-JsonFile -Path (Join-Path $outputDir "order-settlements.json") -Value $settlementResponse
Write-JsonFile -Path (Join-Path $outputDir "order-sync-raw.json") -Value $rawOrderResponse

$settlementOrders = @()
if ($settlementResponse.data.status -eq "success" -and $null -ne $settlementResponse.data.remoteResponse.data.orders) {
    $settlementOrders = @($settlementResponse.data.remoteResponse.data.orders | Where-Object { $null -ne $_ })
}
$rawOrders = @()
if ($rawOrderResponse.data.status -eq "success" -and $null -ne $rawOrderResponse.data.remoteResponse.data.orders) {
    $rawOrders = @($rawOrderResponse.data.remoteResponse.data.orders | Where-Object { $null -ne $_ })
}
$settlementOrderIds = Get-SettlementOrderIds -SettlementResponse $settlementResponse

$summary.institution = [ordered]@{
    institutionId = $institutionInfo.data.remoteResponse.data.institution_id
    colonelBuyinId = $institutionInfo.data.remoteResponse.data.colonel.buyin_id
    colonelName = $institutionInfo.data.remoteResponse.data.colonel.name
    mcnBuyinId = $institutionInfo.data.remoteResponse.data.mcn.buyin_id
    mcnName = $institutionInfo.data.remoteResponse.data.mcn.name
}
$summary.settlement = [ordered]@{
    status = $settlementResponse.data.status
    remoteCode = if ($settlementResponse.data.status -eq "success") { $settlementResponse.data.remoteResponse.code } else { $settlementResponse.data.errorCode }
    subCode = if ($settlementResponse.data.status -eq "success") { $settlementResponse.data.remoteResponse.sub_code } else { $settlementResponse.data.subCode }
    message = if ($settlementResponse.data.status -eq "success") { $settlementResponse.data.remoteResponse.msg } else { $settlementResponse.data.message }
    orderCount = $settlementOrders.Count
    orderIds = $settlementOrderIds
}
$summary.rawOrders = [ordered]@{
    status = $rawOrderResponse.data.status
    remoteCode = $rawOrderResponse.data.remoteResponse.code
    orderCount = $rawOrders.Count
    sampleOrderIds = @($rawOrders | Select-Object -First 5 | ForEach-Object { $_.order_id })
    unsettledSampleCount = @($rawOrders | Where-Object {
        $_.settled_goods_amount -eq 0 -and
        (
            ($null -ne $_.colonel_order_info -and $_.colonel_order_info.real_commission -eq 0) -or
            ($null -ne $_.colonel_order_info_second -and $_.colonel_order_info_second.real_commission -eq 0)
        )
    }).Count
}

$dockerEvidence = $null
if (-not $SkipDockerEvidence) {
    try {
        $dbSettleCountRows = Invoke-ScalarSql -RepoRoot $repoRoot -Sql @"
SELECT COUNT(*)
FROM colonelsettlement_order
WHERE deleted = 0 AND settle_time IS NOT NULL;
"@
        $dbRecentRows = Invoke-ScalarSql -RepoRoot $repoRoot -Sql @"
SELECT order_id, settle_time, update_time, attribution_status
FROM colonelsettlement_order
WHERE deleted = 0
ORDER BY update_time DESC
LIMIT 10;
"@
        $dockerEvidence = [ordered]@{
            dbSettledOrderCount = if ($dbSettleCountRows.Count -gt 0) { [int]$dbSettleCountRows[0] } else { $null }
            recentOrders = @($dbRecentRows)
        }
        Write-JsonFile -Path (Join-Path $outputDir "db-evidence.json") -Value $dockerEvidence
        $summary.dbEvidence = $dockerEvidence
    } catch {
        $summary.dbEvidenceError = $_.Exception.Message
    }
}

$webhookProbe = $null
if ($settlementOrderIds.Count -gt 0) {
    $webhookProbe = Invoke-WebhookProbe -BaseUrl $BaseUrl -OrderId $settlementOrderIds[0] -WebhookClientSecret $webhookClientSecret
    Write-JsonFile -Path (Join-Path $outputDir "webhook-probe.json") -Value $webhookProbe

    if (-not $SkipDockerEvidence) {
        try {
            $eventKey = "doudian_alliance_colonelOpenEvent:" + $webhookProbe.eventId
            $webhookRows = Invoke-ScalarSql -RepoRoot $repoRoot -Sql @"
SELECT event_key, status, consume_result, retry_count, received_at, processed_at
FROM douyin_webhook_event
WHERE event_key = '$eventKey';
"@
            $summary.webhookInbox = @($webhookRows)
            Write-JsonFile -Path (Join-Path $outputDir "webhook-inbox.json") -Value @{
                eventKey = $eventKey
                rows = @($webhookRows)
            }
        } catch {
            $summary.webhookInboxError = $_.Exception.Message
        }
    }
} else {
    $summary.webhookProbeSkipped = "No settlement order sample was returned by buyin.colonelMultiSettlementOrders."
}

$summaryPath = Join-Path $outputDir "summary.json"
Write-JsonFile -Path $summaryPath -Value $summary

$report = @"
# real-pre order settlements watch

- Generated at: $($summary.generatedAt)
- Base URL: $BaseUrl
- Institution: $($summary.institution.colonelName) / colonelBuyinId=$($summary.institution.colonelBuyinId)
- Settlement status: $($summary.settlement.status)
- Settlement remote code: $($summary.settlement.remoteCode)
- Settlement order count: $($summary.settlement.orderCount)
- Raw order count: $($summary.rawOrders.orderCount)
- Raw unsettled sample count: $($summary.rawOrders.unsettledSampleCount)

## Settlement samples

$([string]::Join([Environment]::NewLine, @($summary.settlement.orderIds | ForEach-Object { "- $_" })))

## Raw order samples

$([string]::Join([Environment]::NewLine, @($summary.rawOrders.sampleOrderIds | ForEach-Object { "- $_" })))

## Webhook probe

$(
    if ($webhookProbe) {
        "- Triggered eventId=$($webhookProbe.eventId) for orderId=$($webhookProbe.orderId)`n- HTTP $($webhookProbe.statusCode) $($webhookProbe.body)"
    } else {
        "- Skipped because settlement order count was 0."
    }
)

## Files

- [summary.json]($summaryPath)
- [institution-info.json]($(Join-Path $outputDir "institution-info.json"))
- [order-settlements.json]($(Join-Path $outputDir "order-settlements.json"))
- [order-sync-raw.json]($(Join-Path $outputDir "order-sync-raw.json"))
"@

if (Test-Path (Join-Path $outputDir "db-evidence.json")) {
    $report += "`n- [db-evidence.json](" + (Join-Path $outputDir "db-evidence.json") + ")"
}
if (Test-Path (Join-Path $outputDir "webhook-probe.json")) {
    $report += "`n- [webhook-probe.json](" + (Join-Path $outputDir "webhook-probe.json") + ")"
}
if (Test-Path (Join-Path $outputDir "webhook-inbox.json")) {
    $report += "`n- [webhook-inbox.json](" + (Join-Path $outputDir "webhook-inbox.json") + ")"
}

$reportPath = Join-Path $outputDir "report.md"
Write-TextFile -Path $reportPath -Value $report

Write-Output ("Evidence directory: " + $outputDir)
Write-Output ("Summary: " + $summaryPath)
