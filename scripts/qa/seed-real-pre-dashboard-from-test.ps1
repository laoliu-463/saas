# 将 TEST 环境（colonel_saas_test）的订单 + 业绩记录复制到 REAL-PRE（saas_real_pre），
# 仅用于本地 3001 看板联调演示；不写入抖店 Token，不替代真实上游回流。
#
# 用法（仓库根目录）：
#   pwsh -File scripts/qa/seed-real-pre-dashboard-from-test.ps1
#
# 前置：docker compose test + real-pre 均已启动，且 test 已执行过 /api/test/seed。

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $scriptPath = $PSCommandPath
    if (-not $scriptPath) { $scriptPath = $MyInvocation.PSCommandPath }
    return (Resolve-Path (Join-Path (Split-Path -Parent $scriptPath) "..\..")).Path
}

Set-Location (Get-RepoRoot)

$testPg = "saas-test-postgres-1"
$realPg = "saas-active-postgres-real-pre-1"
$testDb = "colonel_saas_test"
$realDb = "saas_real_pre"

foreach ($c in @($testPg, $realPg)) {
    docker inspect $c *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Container not running: $c. Start test + real-pre stacks first."
    }
}

$orderCount = docker exec $testPg psql -U saas -d $testDb -t -A -c "SELECT COUNT(*) FROM colonelsettlement_order WHERE deleted=0;"
if ([int]$orderCount -le 0) {
    Write-Host "TEST DB has no orders. Run: POST http://localhost:8080/api/test/seed (admin)" -ForegroundColor Yellow
    exit 1
}

$partitions = @(docker exec $testPg psql -U saas -d $testDb -t -A -c @"
SELECT DISTINCT tableoid::regclass::text
FROM colonelsettlement_order
WHERE deleted = 0;
"@ | Where-Object { $_ -and $_.Trim() -ne "" })

$dumpPath = "/tmp/saas-real-pre-dashboard-seed.sql"
$hostDump = Join-Path $env:TEMP "saas-real-pre-dashboard-seed.sql"

Write-Host "Exporting $orderCount orders from partitions: $($partitions -join ', ') ..." -ForegroundColor Cyan

$dumpTables = ($partitions | ForEach-Object { "-t $_" }) -join " "
docker exec $testPg sh -c "pg_dump -U saas -d $testDb --data-only --column-inserts $dumpTables -t performance_records > $dumpPath"
docker cp "${testPg}:${dumpPath}" $hostDump

Write-Host "Clearing REAL-PRE dashboard tables ..." -ForegroundColor Cyan
docker exec $realPg psql -U saas -d $realDb -v ON_ERROR_STOP=1 -c @"
TRUNCATE performance_records;
TRUNCATE colonelsettlement_order;
"@ | Out-Host

Write-Host "Importing into REAL-PRE ..." -ForegroundColor Cyan
docker cp $hostDump "${realPg}:${dumpPath}"
docker exec $realPg psql -U saas -d $realDb -v ON_ERROR_STOP=1 -f $dumpPath | Out-Host
docker exec $testPg rm -f $dumpPath
docker exec $realPg rm -f $dumpPath
Remove-Item -Force $hostDump -ErrorAction SilentlyContinue

$verify = docker exec $realPg psql -U saas -d $realDb -t -A -c @"
SELECT
  (SELECT COUNT(*) FROM colonelsettlement_order WHERE deleted=0) AS orders,
  (SELECT COUNT(*) FROM performance_records) AS perf;
"@
Write-Host "REAL-PRE after import: orders/perf = $verify" -ForegroundColor Green

Write-Host "Evicting dashboard caches on backend ..." -ForegroundColor Cyan
try {
    $login = Invoke-RestMethod -Method POST -Uri "http://localhost:8081/api/auth/login" `
        -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
    $token = $login.data.token
    $null = Invoke-RestMethod -Uri "http://localhost:8081/api/dashboard/metrics" `
        -Headers @{ Authorization = "Bearer $token" }
    $m = Invoke-RestMethod -Uri "http://localhost:8081/api/dashboard/metrics" `
        -Headers @{ Authorization = "Bearer $token" }
    Write-Host "Dashboard estimate todayOrderCount: $($m.data.estimate.todayOrderCount)" -ForegroundColor Green
} catch {
    Write-Host "Could not verify API (backend may still be starting): $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "Done. Open http://localhost:3001/data and hard-refresh (Ctrl+Shift+R)." -ForegroundColor Green
