param(
    [string]$BaseUrl = "http://127.0.0.1:8081/api",
    [string]$ActivityId = "3859423",
    [int]$PageSize = 20,
    [int]$MaxPages = 300,
    [int]$MaxActivities = 50,
    [string]$ActivityScope = "ACTIVE_ONLY"
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..\..")).Path
$reportDir = Join-Path $repoRoot "harness\reports"
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$reportPath = Join-Path $reportDir "product-library-count-phase2-dryrun-pagination-$stamp.md"

function Read-EnvFile {
    param([string]$Path)
    $map = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }
        $idx = $trimmed.IndexOf("=")
        if ($idx -lt 1) { continue }
        $map[$trimmed.Substring(0, $idx).Trim()] = $trimmed.Substring($idx + 1).Trim().Trim("'`"")
    }
    return $map
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers,
        [object]$Body,
        [int]$TimeoutSec = 120
    )
    $uri = "$BaseUrl$Path"
    $args = @{
        Method = $Method
        Uri = $uri
        ContentType = "application/json"
        TimeoutSec = $TimeoutSec
    }
    if ($Headers) { $args.Headers = $Headers }
    if ($null -ne $Body) { $args.Body = ($Body | ConvertTo-Json -Depth 20) }
    $response = Invoke-RestMethod @args
    if ($null -ne $response.code -and $response.code -ne 200) {
        throw "API failed: $Path code=$($response.code) message=$($response.message)"
    }
    return $response
}

function Invoke-DbScalar {
    param([string]$Sql)
    $envMap = Read-EnvFile -Path (Join-Path $repoRoot ".env.real-pre")
    $dbName = $envMap["DB_NAME"]
    $dbUser = $envMap["DB_USER"]
    if (-not $dbName -or -not $dbUser) {
        throw "DB_NAME/DB_USER missing from .env.real-pre"
    }
    Push-Location $repoRoot
    try {
        $args = @(
            "--env-file", ".env.real-pre",
            "-f", "docker-compose.real-pre.yml",
            "-p", "saas-active",
            "exec", "-T", "postgres-real-pre",
            "psql", "-U", $dbUser, "-d", $dbName, "-At", "-c", $Sql
        )
        $value = & docker compose @args
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose psql failed"
        }
        return (($value -join "`n").Trim())
    }
    finally {
        Pop-Location
    }
}

function Get-DbCounts {
    $sql = @"
select
  (select count(*) from product_snapshot where deleted = 0)::text || ',' ||
  (select count(*) from product_operation_state where deleted = 0)::text;
"@
    $parts = (Invoke-DbScalar -Sql $sql).Split(",")
    return [pscustomobject]@{
        Snapshot = [long]$parts[0]
        OperationState = [long]$parts[1]
    }
}

function Get-ProductsTotal {
    param([hashtable]$Headers)
    $response = Invoke-Api -Method "Get" -Path "/products?page=1&size=1" -Headers $Headers -Body $null
    return [long]$response.data.total
}

function Add-Line {
    param([System.Collections.Generic.List[string]]$Lines, [string]$Line)
    [void]$Lines.Add($Line)
}

$startedAt = Get-Date
$failures = [System.Collections.Generic.List[string]]::new()

$beforeCounts = Get-DbCounts
$login = Invoke-Api -Method "Post" -Path "/auth/login" -Headers $null `
    -Body @{ username = "admin"; password = "admin123" } -TimeoutSec 30
$token = $login.data.token
if (-not $token) { throw "admin login did not return token" }
$headers = @{ Authorization = "Bearer $token" }
$beforeProductsTotal = Get-ProductsTotal -Headers $headers

$deep = Invoke-Api -Method "Post" -Path "/product-sync-probes/activity-products-deep-dry-run" -Headers $headers `
    -Body @{
        activityId = $ActivityId
        pageSize = $PageSize
        maxPages = $MaxPages
        stopOnRepeatedCursor = $true
        dryRun = $true
    } -TimeoutSec 900

$full = Invoke-Api -Method "Post" -Path "/product-sync-probes/full-products-dry-run" -Headers $headers `
    -Body @{
        activityScope = $ActivityScope
        activityIds = @()
        maxActivities = $MaxActivities
        pageSize = $PageSize
        maxPagesPerActivity = $MaxPages
        dryRun = $true
    } -TimeoutSec 1800

$afterCounts = Get-DbCounts
$afterProductsTotal = Get-ProductsTotal -Headers $headers

if ($beforeCounts.Snapshot -ne $afterCounts.Snapshot) {
    [void]$failures.Add("product_snapshot count changed")
}
if ($beforeCounts.OperationState -ne $afterCounts.OperationState) {
    [void]$failures.Add("product_operation_state count changed")
}
if ($beforeProductsTotal -ne $afterProductsTotal) {
    [void]$failures.Add("/api/products total changed")
}
if (-not $deep.data.dryRun -or -not $full.data.dryRun) {
    [void]$failures.Add("dryRun flag was not preserved")
}
if ([long]$deep.data.pagesFetched -lt 101) {
    [void]$failures.Add("deep dry-run did not verify page 101+ traversal")
}
if ([long]$full.data.activitiesScanned -lt 1) {
    [void]$failures.Add("full dry-run scanned no activities")
}

$conclusion = if ($failures.Count -eq 0) { "PASS" } else { "FAIL" }
$lines = [System.Collections.Generic.List[string]]::new()
Add-Line $lines "# Product Library Count Phase 2 Dry-Run Pagination"
Add-Line $lines ""
Add-Line $lines "- Time: $($startedAt.ToString('yyyy-MM-dd HH:mm:ss'))"
Add-Line $lines "- Env: real-pre local"
Add-Line $lines "- BaseUrl: $BaseUrl"
Add-Line $lines "- ActivityId: $ActivityId"
Add-Line $lines "- Scope: $ActivityScope"
Add-Line $lines "- MaxActivities: $MaxActivities"
Add-Line $lines "- PageSize/MaxPages: $PageSize/$MaxPages"
Add-Line $lines ""
Add-Line $lines "## Read-Only Guard"
Add-Line $lines "| Metric | Before | After |"
Add-Line $lines "| --- | ---: | ---: |"
Add-Line $lines "| product_snapshot deleted=0 | $($beforeCounts.Snapshot) | $($afterCounts.Snapshot) |"
Add-Line $lines "| product_operation_state deleted=0 | $($beforeCounts.OperationState) | $($afterCounts.OperationState) |"
Add-Line $lines "| /api/products total | $beforeProductsTotal | $afterProductsTotal |"
Add-Line $lines ""
Add-Line $lines "## Deep Dry-Run"
Add-Line $lines "| Metric | Value |"
Add-Line $lines "| --- | ---: |"
Add-Line $lines "| dryRun | $($deep.data.dryRun) |"
Add-Line $lines "| pagesFetched | $($deep.data.pagesFetched) |"
Add-Line $lines "| totalFetchedRows | $($deep.data.totalFetchedRows) |"
Add-Line $lines "| distinctProductIds | $($deep.data.distinctProductIds) |"
Add-Line $lines "| currentDbRowsForActivity | $($deep.data.currentDbRowsForActivity) |"
Add-Line $lines "| estimatedGapRows | $($deep.data.estimatedGapRows) |"
Add-Line $lines "| expectedMissingRowsIfCurrentMax100 | $($deep.data.expectedMissingRowsIfCurrentMax100) |"
Add-Line $lines "| stoppedReason | $($deep.data.stoppedReason) |"
Add-Line $lines "| stillHasNextWhenStopped | $($deep.data.stillHasNextWhenStopped) |"
Add-Line $lines ""
Add-Line $lines "## Full Dry-Run"
Add-Line $lines "| Metric | Value |"
Add-Line $lines "| --- | ---: |"
Add-Line $lines "| dryRun | $($full.data.dryRun) |"
Add-Line $lines "| activitiesScanned | $($full.data.activitiesScanned) |"
Add-Line $lines "| activitiesWithProducts | $($full.data.activitiesWithProducts) |"
Add-Line $lines "| activitiesReachedMaxPages | $($full.data.activitiesReachedMaxPages) |"
Add-Line $lines "| activitiesStillHasNextAfterMaxPages | $($full.data.activitiesStillHasNextAfterMaxPages) |"
Add-Line $lines "| apiFetchedRows | $($full.data.apiFetchedRows) |"
Add-Line $lines "| apiDistinctProductIds | $($full.data.apiDistinctProductIds) |"
Add-Line $lines "| dbRowsForScannedActivities | $($full.data.dbRowsForScannedActivities) |"
Add-Line $lines "| estimatedGapRows | $($full.data.estimatedGapRows) |"
Add-Line $lines "| apiErrors | $($full.data.apiErrors.Count) |"
Add-Line $lines ""
Add-Line $lines "## Top Gap Activities"
Add-Line $lines "| ActivityId | Fetched | DbRows | Gap | StopReason |"
Add-Line $lines "| --- | ---: | ---: | ---: | --- |"
foreach ($item in @($full.data.topGapActivities | Select-Object -First 5)) {
    Add-Line $lines "| $($item.activityId) | $($item.totalFetchedRows) | $($item.currentDbRowsForActivity) | $($item.estimatedGapRows) | $($item.stoppedReason) |"
}
Add-Line $lines ""
Add-Line $lines "## Conclusion"
Add-Line $lines ""
Add-Line $lines "- Result: $conclusion"
if ($failures.Count -gt 0) {
    foreach ($failure in $failures) {
        Add-Line $lines "- Failure: $failure"
    }
}
Add-Line $lines "- Phase 3 real backfill was not executed."

$lines | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Host "Phase 2 dry-run report: $reportPath"
Write-Host "Deep pagesFetched=$($deep.data.pagesFetched), rows=$($deep.data.totalFetchedRows), stop=$($deep.data.stoppedReason)"
Write-Host "Full activitiesScanned=$($full.data.activitiesScanned), rows=$($full.data.apiFetchedRows), apiErrors=$($full.data.apiErrors.Count)"

if ($failures.Count -gt 0) {
    throw "Phase 2 dry-run validation failed: $($failures -join '; ')"
}
