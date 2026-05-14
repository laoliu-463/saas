param(
    [string]$ComposeFile = "docker-compose.real-pre.yml",
    [string]$EnvFile = ".env.real-pre",
    [string]$BackendService = "backend-real-pre",
    [string]$PostgresService = "postgres-real-pre",
    [string]$DbUser = "saas",
    [string]$DbName = "colonel_saas_real"
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

function Invoke-Compose {
    param([string[]]$Arguments)

    $composeArgs = @()
    if (Test-Path $EnvFile) {
        $composeArgs += @("--env-file", $EnvFile)
    }
    $composeArgs += @("-f", $ComposeFile)
    $composeArgs += $Arguments

    & docker compose @composeArgs
}

function Read-RealPreEnv {
    $lines = Invoke-Compose @("exec", "-T", $BackendService, "printenv")
    $map = @{}
    foreach ($line in $lines) {
        if ($line -match "^([^=]+)=(.*)$") {
            $map[$Matches[1]] = $Matches[2]
        }
    }
    return $map
}

function Invoke-ScalarSql {
    param([string]$Sql)

    $output = Invoke-Compose @(
        "exec", "-T", $PostgresService,
        "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1",
        "-U", $DbUser, "-d", $DbName,
        "-t", "-A", "-c", $Sql
    )
    return @($output | Where-Object { $_ -and $_.Trim() -ne "" })
}

Set-Location (Get-RepoRoot)

$envMap = Read-RealPreEnv
$envChecks = [ordered]@{
    SPRING_PROFILES_ACTIVE = "real"
    APP_TEST_ENABLED = "false"
    DOUYIN_TEST_ENABLED = "false"
    ORDER_SYNC_ENABLED = "true"
    REDIS_DATABASE = "0"
    DB_NAME = $DbName
}

$violations = New-Object System.Collections.Generic.List[string]
foreach ($entry in $envChecks.GetEnumerator()) {
    $actual = $envMap[$entry.Key]
    if ($actual -ne $entry.Value) {
        $violations.Add("Env $($entry.Key) expected '$($entry.Value)' but was '$actual'.")
    }
}

$suspiciousSql = @'
SELECT 'orders' AS scope, COUNT(*) AS suspicious_rows
FROM colonelsettlement_order
WHERE deleted = 0
  AND (
    order_id ILIKE '%mock%' OR order_id ILIKE '%test%' OR
    product_id ILIKE '%mock%' OR product_id ILIKE '%test%' OR
    product_name ILIKE '%mock%' OR product_name ILIKE '%测试%' OR product_name ILIKE '%演示%' OR
    product_title ILIKE '%mock%' OR product_title ILIKE '%测试%' OR product_title ILIKE '%演示%' OR
    pick_source ILIKE '%mock%' OR pick_source ILIKE '%test%' OR
    extra_data::text ILIKE '%mock%' OR extra_data::text ILIKE '%测试%' OR extra_data::text ILIKE '%演示%'
  )
UNION ALL
SELECT 'product_snapshot', COUNT(*)
FROM product_snapshot
WHERE deleted = 0
  AND (
    activity_id ILIKE '%mock%' OR activity_id ILIKE '%test%' OR
    product_id ILIKE '%mock%' OR product_id ILIKE '%test%' OR
    title ILIKE '%mock%' OR title ILIKE '%测试%' OR title ILIKE '%演示%' OR
    raw_payload::text ILIKE '%mock%' OR raw_payload::text ILIKE '%测试%' OR raw_payload::text ILIKE '%演示%'
  )
UNION ALL
SELECT 'pick_source_mapping', COUNT(*)
FROM pick_source_mapping
WHERE deleted = 0
  AND (
    pick_source ILIKE '%mock%' OR pick_source ILIKE '%test%' OR
    activity_id ILIKE '%mock%' OR activity_id ILIKE '%test%' OR
    product_id ILIKE '%mock%' OR product_id ILIKE '%test%' OR
    talent_id ILIKE '%mock%' OR talent_id ILIKE '%test%' OR
    talent_name ILIKE '%mock%' OR talent_name ILIKE '%测试%' OR talent_name ILIKE '%演示%' OR
    converted_url ILIKE '%mock%' OR converted_url ILIKE '%test%'
  )
UNION ALL
SELECT 'product_operation_state', COUNT(*)
FROM product_operation_state
WHERE deleted = 0
  AND (
    activity_id ILIKE '%mock%' OR activity_id ILIKE '%test%' OR
    product_id ILIKE '%mock%' OR product_id ILIKE '%test%' OR
    bound_activity_id ILIKE '%mock%' OR bound_activity_id ILIKE '%test%' OR
    promote_link ILIKE '%mock%' OR promote_link ILIKE '%test%' OR
    short_link ILIKE '%mock%' OR short_link ILIKE '%test%' OR
    external_unique_id ILIKE '%mock%' OR external_unique_id ILIKE '%test%' OR
    audit_payload ILIKE '%mock%' OR audit_payload ILIKE '%测试%' OR audit_payload ILIKE '%演示%'
  )
UNION ALL
SELECT 'product', COUNT(*)
FROM product
WHERE deleted = 0
  AND (
    product_id ILIKE '%mock%' OR product_id ILIKE '%test%' OR
    name ILIKE '%mock%' OR name ILIKE '%测试%' OR name ILIKE '%演示%' OR
    detail_url ILIKE '%mock%' OR detail_url ILIKE '%test%' OR
    cover ILIKE '%mock%' OR cover ILIKE '%test%'
  )
UNION ALL
SELECT 'sample_request', COUNT(*)
FROM sample_request
WHERE deleted = 0
  AND (
    request_no ILIKE '%mock%' OR request_no ILIKE '%test%' OR
    talent_uid ILIKE '%mock%' OR talent_uid ILIKE '%test%' OR
    talent_nickname ILIKE '%mock%' OR talent_nickname ILIKE '%测试%' OR talent_nickname ILIKE '%演示%' OR
    activity_id ILIKE '%mock%' OR activity_id ILIKE '%test%'
  )
UNION ALL
SELECT 'talent', COUNT(*)
FROM talent
WHERE deleted = 0
  AND (
    douyin_uid ILIKE '%mock%' OR douyin_uid ILIKE '%test%' OR
    nickname ILIKE '%mock%' OR nickname ILIKE '%测试%' OR nickname ILIKE '%演示%' OR
    data_source ILIKE '%mock%' OR data_source ILIKE '%test%'
  )
UNION ALL
SELECT 'talent_claim', COUNT(*)
FROM talent_claim
WHERE deleted = 0
  AND (
    talent_uid ILIKE '%mock%' OR talent_uid ILIKE '%test%' OR
    talent_id IN (
      SELECT id FROM talent
      WHERE douyin_uid ILIKE '%talent_test%' OR nickname ILIKE '%演示%' OR data_source ILIKE '%test%'
    )
  );
'@

$rows = Invoke-ScalarSql $suspiciousSql
$suspicious = @()
foreach ($row in $rows) {
    $parts = $row -split "\|", 2
    $scope = $parts[0]
    $count = [int]$parts[1]
    $suspicious += [pscustomobject]@{
        scope = $scope
        suspiciousRows = $count
    }
    if ($count -gt 0) {
        $violations.Add("Scope $scope has $count active mock/test/demo rows.")
    }
}

$summarySql = @'
SELECT
  COUNT(*) AS active_orders,
  COUNT(*) FILTER (WHERE attribution_status = 'ATTRIBUTED') AS attributed_orders,
  COUNT(*) FILTER (WHERE attribution_status = 'UNATTRIBUTED') AS unattributed_orders,
  ROUND(COALESCE(SUM(order_amount), 0) / 100.0, 2) AS total_gmv
FROM colonelsettlement_order
WHERE deleted = 0;
'@
$summaryRows = @(Invoke-ScalarSql $summarySql)
$summary = $summaryRows[0]
$summaryParts = $summary -split "\|"

$result = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("s")
    status = if ($violations.Count -eq 0) { "OK" } else { "FAIL" }
    environment = [pscustomobject]@{
        profile = $envMap["SPRING_PROFILES_ACTIVE"]
        appTestEnabled = $envMap["APP_TEST_ENABLED"]
        douyinTestEnabled = $envMap["DOUYIN_TEST_ENABLED"]
        orderSyncEnabled = $envMap["ORDER_SYNC_ENABLED"]
        redisDatabase = $envMap["REDIS_DATABASE"]
        dbName = $envMap["DB_NAME"]
    }
    orderSummary = [pscustomobject]@{
        activeOrders = [int]$summaryParts[0]
        attributedOrders = [int]$summaryParts[1]
        unattributedOrders = [int]$summaryParts[2]
        totalGmv = [decimal]$summaryParts[3]
    }
    suspicious = $suspicious
    violations = @($violations)
}

$result | ConvertTo-Json -Depth 6

if ($violations.Count -gt 0) {
    exit 1
}
