<#
.SYNOPSIS
    商品 backfill 异步任务提交 + 轮询进度脚本。
.DESCRIPTION
    替代同步 HTTP 长请求，走 async 接口提交任务后轮询 jobId 直到完成。
    支持 dry-run 和 real-run（需显式 -Confirm）。
.EXAMPLE
    # dry-run：RECENT_30D 20 活动
    .\backfill-async.ps1 -DryRun -MaxActivities 20

    # real-run：RECENT_30D 5 活动（需 -Confirm）
    .\backfill-async.ps1 -MaxActivities 5 -Confirm

    # 自定义轮询间隔和超时
    .\backfill-async.ps1 -DryRun -MaxActivities 20 -PollIntervalSec 10 -TimeoutMinutes 90
#>
param(
    [string]$BaseUrl = "http://127.0.0.1:8081/api",
    [string]$Scope = "RECENT_30D",
    [string[]]$ActivityIds = @(),
    [int]$MaxActivities = 20,
    [int]$PageSize = 20,
    [int]$MaxPagesPerActivity = 1000,
    [int]$MaxRowsPerActivity = 50000,
    [switch]$DryRun,
    [switch]$Confirm,
    [string]$DisplayRefreshMode = "DEFERRED",
    [int]$PollIntervalSec = 15,
    [int]$TimeoutMinutes = 60
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..\.." )).Path
$reportDir = Join-Path $repoRoot "harness\reports"
$stamp = Get-Date -Format "yyyyMMdd-HHmm"

# ── helpers ──────────────────────────────────────────────────────────

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers,
        [object]$Body,
        [int]$TimeoutSec = 30
    )
    $uri = "$BaseUrl$Path"
    $splat = @{
        Method      = $Method
        Uri         = $uri
        ContentType = "application/json"
        TimeoutSec  = $TimeoutSec
    }
    if ($Headers) { $splat.Headers = $Headers }
    if ($null -ne $Body) { $splat.Body = ($Body | ConvertTo-Json -Depth 20) }
    $response = Invoke-RestMethod @splat
    if ($null -ne $response.code -and $response.code -ne 200) {
        throw "API failed: $Path code=$($response.code) message=$($response.message)"
    }
    return $response
}

function Write-Progress-Line {
    param([object]$Status)
    $elapsed = if ($Status.startedAt) {
        $start = [datetime]::Parse($Status.startedAt)
        $diff = (Get-Date) - $start
        "{0:mm\:ss}" -f $diff
    } else { "N/A" }
    $line = "[{0}] status={1}  scanned={2}  success={3}  failed={4}  fetched={5}  current={6}  elapsed={7}" -f `
        (Get-Date -Format "HH:mm:ss"),
        $Status.status,
        $Status.activitiesScanned,
        $Status.activitiesSuccess,
        $Status.activitiesFailed,
        $Status.apiFetchedRows,
        $(if ($Status.currentActivityId) { $Status.currentActivityId } else { "-" }),
        $elapsed
    Write-Host $line
}

function Add-Line {
    param([System.Collections.Generic.List[string]]$Lines, [string]$Line)
    [void]$Lines.Add($Line)
}

# ── safety checks ────────────────────────────────────────────────────

if (-not $DryRun -and -not $Confirm) {
    Write-Host "ERROR: real-run (non-dry-run) 必须显式 -Confirm。" -ForegroundColor Red
    Write-Host "  dry-run:  .\backfill-async.ps1 -DryRun -MaxActivities 20"
    Write-Host "  real-run: .\backfill-async.ps1 -MaxActivities 5 -Confirm"
    exit 1
}

$isDryRun = [bool]$DryRun
$isConfirm = [bool]$Confirm

Write-Host "=== Backfill Async ===" -ForegroundColor Cyan
Write-Host "  Scope:          $Scope"
Write-Host "  MaxActivities:  $MaxActivities"
Write-Host "  DryRun:         $isDryRun"
Write-Host "  Confirm:        $isConfirm"
Write-Host "  PollInterval:   ${PollIntervalSec}s"
Write-Host "  Timeout:        ${TimeoutMinutes}min"
Write-Host ""

# ── step 1: login ────────────────────────────────────────────────────

Write-Host "[1/3] Logging in..." -ForegroundColor Yellow
$login = Invoke-Api -Method "Post" -Path "/auth/login" -Headers $null `
    -Body @{ username = "admin"; password = "admin123" } -TimeoutSec 15
$token = $login.data.token
if (-not $token) { throw "admin login did not return token" }
$headers = @{ Authorization = "Bearer $token" }
Write-Host "  Token acquired." -ForegroundColor Green

# ── step 2: submit async job ─────────────────────────────────────────

Write-Host "[2/3] Submitting async backfill job..." -ForegroundColor Yellow
$requestBody = @{
    scope                = $Scope
    activityIds          = $ActivityIds
    pageSize             = $PageSize
    maxActivities        = $MaxActivities
    maxPagesPerActivity  = $MaxPagesPerActivity
    maxRowsPerActivity   = $MaxRowsPerActivity
    dryRun               = $isDryRun
    confirm              = $isConfirm
    displayRefreshMode   = $DisplayRefreshMode
}

$submitResponse = Invoke-Api -Method "Post" `
    -Path "/product-sync/admin/backfill-activity-products/async" `
    -Headers $headers `
    -Body $requestBody `
    -TimeoutSec 15

$jobId = $submitResponse.data.jobId
$submitStatus = $submitResponse.data.status
if (-not $jobId) { throw "async submit did not return jobId" }

Write-Host "  jobId:  $jobId" -ForegroundColor Green
Write-Host "  status: $submitStatus" -ForegroundColor Green
Write-Host ""

# ── step 3: poll progress ────────────────────────────────────────────

Write-Host "[3/3] Polling progress every ${PollIntervalSec}s (timeout ${TimeoutMinutes}min)..." -ForegroundColor Yellow
Write-Host ""

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$finalStatus = $null
$pollCount = 0

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds $PollIntervalSec
    $pollCount++

    try {
        $pollResponse = Invoke-Api -Method "Get" `
            -Path "/product-sync/admin/backfill-jobs/$jobId" `
            -Headers $headers `
            -TimeoutSec 15
        $finalStatus = $pollResponse.data
        Write-Progress-Line -Status $finalStatus

        # terminal states
        if ($finalStatus.status -and $finalStatus.status -notin @("RUNNING")) {
            Write-Host ""
            Write-Host "  Job finished: $($finalStatus.status)" -ForegroundColor $(
                if ($finalStatus.status -eq "SUCCESS") { "Green" }
                elseif ($finalStatus.status -eq "PARTIAL") { "Yellow" }
                else { "Red" }
            )
            break
        }
    }
    catch {
        Write-Host "  [poll $pollCount] error: $($_.Exception.Message)" -ForegroundColor Red
        # 继续轮询，网络瞬断不放弃
    }
}

if ($null -eq $finalStatus -or $finalStatus.status -eq "RUNNING") {
    Write-Host "  TIMEOUT: job still RUNNING after ${TimeoutMinutes}min." -ForegroundColor Red
    Write-Host "  jobId: $jobId — 可手动查询: GET /product-sync/admin/backfill-jobs/$jobId"
}

# ── generate report ──────────────────────────────────────────────────

$mode = if ($isDryRun) { "dryrun" } else { "real" }
$reportPath = Join-Path $reportDir "backfill-async-$mode-$stamp.md"

$lines = [System.Collections.Generic.List[string]]::new()
Add-Line $lines "# Backfill Async Report"
Add-Line $lines ""
Add-Line $lines "- Time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Add-Line $lines "- Env: real-pre local"
Add-Line $lines "- JobId: $jobId"
Add-Line $lines "- Scope: $Scope"
Add-Line $lines "- MaxActivities: $MaxActivities"
Add-Line $lines "- DryRun: $isDryRun"
Add-Line $lines "- Confirm: $isConfirm"
Add-Line $lines "- PollInterval: ${PollIntervalSec}s"
Add-Line $lines "- PollCount: $pollCount"
Add-Line $lines ""

if ($null -ne $finalStatus) {
    Add-Line $lines "## Job Result"
    Add-Line $lines ""
    Add-Line $lines "| Metric | Value |"
    Add-Line $lines "| --- | ---: |"
    Add-Line $lines "| status | $($finalStatus.status) |"
    Add-Line $lines "| dryRun | $($finalStatus.dryRun) |"
    Add-Line $lines "| activitiesScanned | $($finalStatus.activitiesScanned) |"
    Add-Line $lines "| activitiesSuccess | $($finalStatus.activitiesSuccess) |"
    Add-Line $lines "| activitiesIncomplete | $($finalStatus.activitiesIncomplete) |"
    Add-Line $lines "| activitiesFailed | $($finalStatus.activitiesFailed) |"
    Add-Line $lines "| apiFetchedRows | $($finalStatus.apiFetchedRows) |"
    Add-Line $lines "| apiDistinctProductIds | $($finalStatus.apiDistinctProductIds) |"
    Add-Line $lines "| dbRowsBefore | $($finalStatus.dbRowsBefore) |"
    Add-Line $lines "| estimatedGapRows | $($finalStatus.estimatedGapRows) |"
    Add-Line $lines "| inserted | $($finalStatus.inserted) |"
    Add-Line $lines "| updated | $($finalStatus.updated) |"
    Add-Line $lines "| skipped | $($finalStatus.skipped) |"
    Add-Line $lines "| failed | $($finalStatus.failed) |"
    Add-Line $lines "| unchanged | $($finalStatus.unchanged) |"
    Add-Line $lines "| lockWaitCount | $($finalStatus.lockWaitCount) |"
    Add-Line $lines "| deadlockRetryCount | $($finalStatus.deadlockRetryCount) |"
    Add-Line $lines "| startedAt | $($finalStatus.startedAt) |"
    Add-Line $lines "| finishedAt | $($finalStatus.finishedAt) |"
    Add-Line $lines ""

    if ($finalStatus.stopReasonStats -and $finalStatus.stopReasonStats.PSObject.Properties.Count -gt 0) {
        Add-Line $lines "## Stop Reason Stats"
        Add-Line $lines ""
        Add-Line $lines "| Reason | Count |"
        Add-Line $lines "| --- | ---: |"
        foreach ($prop in $finalStatus.stopReasonStats.PSObject.Properties) {
            Add-Line $lines "| $($prop.Name) | $($prop.Value) |"
        }
        Add-Line $lines ""
    }
}

$conclusion = if ($null -eq $finalStatus) { "TIMEOUT" }
    elseif ($finalStatus.status -eq "SUCCESS") { "PASS" }
    elseif ($finalStatus.status -eq "PARTIAL") { "PARTIAL" }
    else { "FAIL" }

Add-Line $lines "## Conclusion"
Add-Line $lines ""
Add-Line $lines "- Result: $conclusion"

$lines | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Host ""
Write-Host "Report: $reportPath" -ForegroundColor Cyan
Write-Host "Done." -ForegroundColor Green
