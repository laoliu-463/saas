param(
    [string]$BaseUrl = "http://localhost:8081/api",
    [string]$SampleActivityId = "",
    [string]$SampleProductId = "",
    [switch]$SkipAttributionEvidence,
    [switch]$SkipProductChainAudit
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $scriptPath = $PSCommandPath
    if (-not $scriptPath) { $scriptPath = $MyInvocation.PSCommandPath }
    return (Resolve-Path (Join-Path (Split-Path -Parent $scriptPath) "..\..")).Path
}

function Get-PostgresContainer {
    param([string]$RepoRoot)
    $lines = docker compose --project-name saas-active -f (Join-Path $RepoRoot "docker-compose.real-pre.yml") ps --format "{{.Name}}" 2>$null
    $name = @($lines | Where-Object { $_ -match "postgres-real-pre" } | Select-Object -First 1)
    if (-not $name) { throw "postgres-real-pre container not found. Run scripts/start-real-pre.ps1 first." }
    return $name.Trim()
}

function Invoke-ScalarSql {
    param([string]$Container, [string]$Sql, [string]$DbUser = "saas", [string]$DbName = "saas_real_pre")
    $output = docker exec -i $Container psql -X -q -v ON_ERROR_STOP=1 -U $DbUser -d $DbName -t -A -F "|" -c $Sql
    return @($output | Where-Object { $_ -and $_.Trim() -ne "" })
}

Set-Location (Get-RepoRoot)
$repoRoot = (Get-Location).Path
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $repoRoot "runtime\qa\out\product-order-attribution-audit-$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$pg = Get-PostgresContainer -RepoRoot $repoRoot

$summary = [ordered]@{
    generatedAt = (Get-Date).ToString("s")
    baseUrl     = $BaseUrl
    outputDir   = $outDir
    steps       = @()
    status      = "PASS"
}

function Add-Step {
    param([string]$Id, [string]$Result, [object]$Detail)
    $summary.steps += ,([ordered]@{ id = $Id; result = $Result; detail = $Detail })
    if ($Result -eq "FAIL" -and $summary.status -eq "PASS") { $summary.status = "FAIL" }
    if ($Result -eq "WARN" -and $summary.status -eq "PASS") { $summary.status = "WARN" }
}

# --- SQL: attribution distribution ---
$ord = Invoke-ScalarSql -Container $pg -Sql @"
SELECT COUNT(*) AS active,
       COUNT(*) FILTER (WHERE attribution_status = 'ATTRIBUTED') AS attributed,
       COUNT(*) FILTER (WHERE attribution_status = 'UNATTRIBUTED') AS unattributed,
       COUNT(*) FILTER (WHERE pick_source IS NOT NULL AND pick_source <> '') AS with_pick,
       ROUND(COALESCE(SUM(order_amount),0)/100.0,2) AS gmv_yuan
FROM colonelsettlement_order WHERE deleted = 0;
"@
$op = ($ord[0] -split "\|")
Add-Step "orders_attribution_summary" $(if ([int]$op[0] -gt 0) { "PASS" } else { "WARN" }) @{
    activeOrders = $op[0]; attributed = $op[1]; unattributed = $op[2]; withPickSource = $op[3]; gmvYuan = $op[4]
}

$reasonRows = Invoke-ScalarSql -Container $pg -Sql @"
SELECT COALESCE(unattributed_reason, '(null)') AS reason, COUNT(*)::text
FROM colonelsettlement_order
WHERE deleted = 0 AND attribution_status = 'UNATTRIBUTED'
GROUP BY 1 ORDER BY COUNT(*) DESC LIMIT 12;
"@
Add-Step "unattributed_reason_distribution" "PASS" @{ rows = $reasonRows }

# --- SQL: mapping coverage ---
$map = Invoke-ScalarSql -Container $pg -Sql @"
SELECT COUNT(*) AS total,
       COUNT(*) FILTER (WHERE pick_source LIKE 'v.%') AS v_prefix,
       COUNT(*) FILTER (WHERE activity_id IS NOT NULL AND activity_id <> '' AND product_id IS NOT NULL AND product_id <> '') AS with_activity_product
FROM pick_source_mapping WHERE deleted = 0;
"@
$mp = ($map[0] -split "\|")
$mapResult = if ([int]$mp[2] -gt 0) { "PASS" } elseif ([int]$mp[0] -gt 0) { "WARN" } else { "WARN" }
Add-Step "pick_source_mapping_coverage" $mapResult @{
    total = $mp[0]; vPrefix = $mp[1]; withActivityProduct = $mp[2]
    note = "WARN if only colonel_native seeds without activity_id+product_id"
}

$gapRows = Invoke-ScalarSql -Container $pg -Sql @"
SELECT ps.activity_id, ps.product_id
FROM product_snapshot ps
LEFT JOIN pick_source_mapping m
  ON m.deleted = 0 AND m.activity_id = ps.activity_id AND m.product_id = ps.product_id AND m.status = 1
WHERE ps.deleted = 0 AND m.id IS NULL
LIMIT 5;
"@
Add-Step "snapshot_without_mapping_sample" "INFO" @{ sampleGaps = $gapRows; totalNote = "See full gap query in runbook 20" }

# --- SQL: pick_source orders still unattributed ---
$badPick = Invoke-ScalarSql -Container $pg -Sql @"
SELECT COUNT(*)::text FROM colonelsettlement_order
WHERE deleted = 0 AND pick_source IS NOT NULL AND pick_source <> '' AND attribution_status = 'UNATTRIBUTED';
"@
Add-Step "pick_source_but_unattributed" $(if ([int]$badPick[0] -eq 0) { "PASS" } else { "WARN" }) @{
    count = $badPick[0]
}

($summary | ConvertTo-Json -Depth 8) | Set-Content (Join-Path $outDir "attribution-sql-summary.json") -Encoding UTF8

# --- Child: real-pre data isolation ---
$isoScript = Join-Path $repoRoot "scripts\qa\check-real-pre-real-data.ps1"
if (Test-Path $isoScript) {
    try {
        & powershell -ExecutionPolicy Bypass -File $isoScript `
            -ComposeFile "docker-compose.real-pre.yml" `
            -BackendService "backend-real-pre" `
            -PostgresService "postgres-real-pre" 2>&1 | Tee-Object -FilePath (Join-Path $outDir "check-real-pre-real-data.log")
        Add-Step "check_real_pre_real_data" "PASS" @{ script = "check-real-pre-real-data.ps1" }
    } catch {
        Add-Step "check_real_pre_real_data" "WARN" @{ error = $_.Exception.Message }
    }
}

# --- Child: product chain audit ---
if (-not $SkipProductChainAudit) {
    $chainScript = Join-Path $repoRoot "scripts\qa\audit-product-chain-real-pre.ps1"
    $chainArgs = @("-ExecutionPolicy", "Bypass", "-File", $chainScript, "-BaseUrl", $BaseUrl)
    if ($SampleActivityId) { $chainArgs += @("-SampleActivityId", $SampleActivityId) }
    if ($SampleProductId) { $chainArgs += @("-SampleProductId", $SampleProductId) }
    try {
        & powershell @chainArgs 2>&1 | Tee-Object -FilePath (Join-Path $outDir "audit-product-chain.log")
        Add-Step "audit_product_chain_real_pre" "PASS" @{ script = "audit-product-chain-real-pre.ps1" }
    } catch {
        Add-Step "audit_product_chain_real_pre" "WARN" @{ error = $_.Exception.Message }
    }
}

# --- Child: P3-6 attribution evidence ---
if (-not $SkipAttributionEvidence) {
    $evScript = Join-Path $repoRoot "scripts\qa\run-real-pre-attribution-evidence.ps1"
    if (Test-Path $evScript) {
        try {
            & powershell -ExecutionPolicy Bypass -File $evScript -BaseUrl $BaseUrl 2>&1 |
                Tee-Object -FilePath (Join-Path $outDir "run-real-pre-attribution-evidence.log")
            Add-Step "run_real_pre_attribution_evidence" "PASS" @{ script = "run-real-pre-attribution-evidence.ps1" }
        } catch {
            Add-Step "run_real_pre_attribution_evidence" "WARN" @{ error = $_.Exception.Message }
        }
    } else {
        Add-Step "run_real_pre_attribution_evidence" "WARN" @{ error = "script not found" }
    }
}

$md = @(
    "# real-pre 商品订单归因逻辑排查",
    "",
    "- 时间: $($summary.generatedAt)",
    "- BaseUrl: $BaseUrl",
    "- 结论: **$($summary.status)**",
    "- 输出: ``$outDir``",
    "",
    "## SQL 摘要",
    "",
    "| 指标 | 值 |",
    "| --- | --- |",
    "| 活跃订单 | $($op[0]) |",
    "| 已归因 | $($op[1]) |",
    "| 未归因 | $($op[2]) |",
    "| 带 pick_source | $($op[3]) |",
    "| GMV(元) | $($op[4]) |",
    "",
    "## 步骤",
    ""
)
foreach ($s in $summary.steps) {
    $md += "- **$($s.id)**: $($s.result)"
}
$md += ""
$md += "Runbook: ``docs/archive/runbooks/20-real-pre商品订单归因逻辑排查.md``"
$mdPath = Join-Path $outDir "report.md"
$md -join "`n" | Set-Content $mdPath -Encoding UTF8

Write-Host "Product-order attribution audit: $($summary.status)" -ForegroundColor $(if ($summary.status -eq "PASS") { "Green" } elseif ($summary.status -eq "WARN") { "Yellow" } else { "Red" })
Write-Host "Report: $mdPath"
if ($summary.status -eq "FAIL") { exit 1 }
