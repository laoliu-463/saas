# run-all-tests.ps1
#
# 项目统一测试入口，按四层结构串行执行。
# 各层可通过参数跳过，以适应不同 CI / 本地场景。
#
# 层级：
#   L0  纯 Node.js 单元测试   — 无需任何服务，始终可跑
#   L1  环境就绪探针           — 需要 Docker + saas-active 全栈
#   L2  API 冒烟 + Mock 回归   — 需要后端 8080 在线
#   L3  浏览器 E2E             — 需要前端 3000 + 后端 8080
#   L4  real-pre 专项          — 需要真实 Token，默认跳过
#
# 用法：
#   # 只跑 L0 单元（最常用，秒级）
#   .\scripts\run-all-tests.ps1 -Only L0
#
#   # L0 + L1 环境检查
#   .\scripts\run-all-tests.ps1 -Only L0,L1
#
#   # 全量（不含 real-pre）
#   .\scripts\run-all-tests.ps1
#
#   # 包含 real-pre
#   .\scripts\run-all-tests.ps1 -IncludeRealPre
#
#   # 指定后端地址（默认 http://localhost:8080）
#   .\scripts\run-all-tests.ps1 -ApiBaseUrl http://localhost:8081

param(
    [string[]]$Only,          # 若指定，只跑对应层（如 'L0','L2'）
    [switch]$IncludeRealPre,  # 是否跑 L4 real-pre 专项
    [string]$ApiBaseUrl       # 覆盖后端地址
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot  = (Resolve-Path (Join-Path $scriptDir "..")).Path

$timestamp  = Get-Date -Format "yyyyMMdd-HHmmss"
$outRoot    = Join-Path $repoRoot "runtime\qa\out"
$runOutDir  = Join-Path $outRoot "run-all-tests-$timestamp"
New-Item -ItemType Directory -Path $runOutDir -Force | Out-Null

if ($Only -and $Only.Count -gt 0) {
    $Only = @(
        $Only |
            ForEach-Object { $_ -split "," } |
            ForEach-Object { $_.Trim().ToUpperInvariant() } |
            Where-Object { $_ }
    )
}

# ─────────────────────────────────────────────────────────────────────
# 工具函数
# ─────────────────────────────────────────────────────────────────────

$global:results = [System.Collections.Generic.List[object]]::new()

function Write-Section { param([string]$Title)
    Write-Host ""
    Write-Host ("-" * 60) -ForegroundColor DarkGray
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host ("-" * 60) -ForegroundColor DarkGray
}

function Invoke-Step {
    param(
        [string]$Layer,
        [string]$Name,
        [scriptblock]$Action
    )
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $global:LASTEXITCODE = 0
    $ok = $true
    $err = ""
    try {
        & $Action
        if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) {
            throw "exit code $LASTEXITCODE"
        }
        Write-Host "  [PASS] $Name" -ForegroundColor Green
    } catch {
        $ok = $false
        $err = $_.Exception.Message
        Write-Host "  [FAIL] $Name - $err" -ForegroundColor Red
    }
    $sw.Stop()
    $global:results.Add([ordered]@{
        layer  = $Layer
        name   = $Name
        ok     = $ok
        ms     = $sw.ElapsedMilliseconds
        error  = $err
    }) | Out-Null
    return $ok
}

function Should-RunLayer { param([string]$Layer)
    if (-not $Only -or $Only.Count -eq 0) { return $true }
    return ($Only -contains $Layer)
}

function Get-ApiArg {
    if ($ApiBaseUrl) { return @("-ApiBaseUrl", $ApiBaseUrl) }
    return @()
}

# ─────────────────────────────────────────────────────────────────────
# L0 — 纯 Node.js 单元测试（无需任何服务）
# ─────────────────────────────────────────────────────────────────────

if (Should-RunLayer "L0") {
    Write-Section "L0 - Node unit tests"

    $unitTests = @(
        "runtime\qa\mock-data-audit.test.cjs",
        "runtime\qa\business-state-flow-regression.test.cjs",
        "runtime\qa\page-role-business-smoke.test.cjs",
        "runtime\qa\dashboard-reconcile.test.cjs",
        "runtime\qa\real-pre-full-business-journey.preflight.test.cjs",
        "runtime\qa\real-pre-safe-upstream.test.cjs",
        "runtime\qa\real-pre-cleanup-plan.test.cjs"
    )

    foreach ($rel in $unitTests) {
        $abs = Join-Path $repoRoot $rel
        $label = Split-Path -Leaf $rel
        if (-not (Test-Path -LiteralPath $abs)) {
            Write-Host "  [SKIP] $label (missing file)" -ForegroundColor Yellow
            continue
        }
        Invoke-Step -Layer "L0" -Name $label -Action {
            Push-Location $repoRoot
            try { & node $abs }
            finally { Pop-Location }
        } | Out-Null
    }
}

# ─────────────────────────────────────────────────────────────────────
# L1 — 环境就绪探针（需要 Docker + 全栈在线）
# ─────────────────────────────────────────────────────────────────────

if (Should-RunLayer "L1") {
    Write-Section "L1 - qa-env probe"

    $envScript = Join-Path $repoRoot "scripts\qa-env.ps1"
    Invoke-Step -Layer "L1" -Name "qa-env.ps1" -Action {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $envScript
    } | Out-Null
}

# ─────────────────────────────────────────────────────────────────────
# L2 — API 冒烟 + TEST/Mock 全量回归（需要后端 8080 在线）
# ─────────────────────────────────────────────────────────────────────

if (Should-RunLayer "L2") {
    Write-Section "L2a - API smoke (qa-api-smoke)"

    $apiSmokeScript = Join-Path $repoRoot "scripts\qa-api-smoke.ps1"
    Invoke-Step -Layer "L2" -Name "qa-api-smoke.ps1" -Action {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $apiSmokeScript
    } | Out-Null

    Write-Section "L2b - TEST/Mock regression (qa-test-mock-final)"

    $mockFinalScript = Join-Path $repoRoot "scripts\qa-test-mock-final.ps1"
    $apiArgs = Get-ApiArg
    Invoke-Step -Layer "L2" -Name "qa-test-mock-final.ps1" -Action {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $mockFinalScript @apiArgs
    } | Out-Null
}

# ─────────────────────────────────────────────────────────────────────
# L3 — 浏览器 E2E（需要前端 3000 + 后端 8080）
# ─────────────────────────────────────────────────────────────────────

if (Should-RunLayer "L3") {
    Write-Section "L3a - Playwright smoke (e2e:smoke)"

    Invoke-Step -Layer "L3" -Name "npm run e2e:smoke" -Action {
        Push-Location $repoRoot
        try { & npm run e2e:smoke }
        finally { Pop-Location }
    } | Out-Null

    Write-Section "L3b - Playwright V1-P0 (e2e:v1-p0)"

    Invoke-Step -Layer "L3" -Name "npm run e2e:v1-p0" -Action {
        Push-Location $repoRoot
        try { & npm run e2e:v1-p0 }
        finally { Pop-Location }
    } | Out-Null

    Write-Section "L3c - page smoke (qa-page-smoke)"

    $pageSmokeScript = Join-Path $repoRoot "scripts\qa-page-smoke.ps1"
    $roles = @(
        @{ u="admin";          p="admin123"; l="admin"          },
        @{ u="biz_leader";     p="admin123"; l="biz_leader"     },
        @{ u="channel_leader"; p="admin123"; l="channel_leader" },
        @{ u="ops_staff";      p="admin123"; l="ops_staff"      }
    )
    foreach ($role in $roles) {
        $label = $role.l
        Invoke-Step -Layer "L3" -Name "qa-page-smoke [$label]" -Action {
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $pageSmokeScript `
                -Username $role.u -Password $role.p -Label $label
        } | Out-Null
    }
}

# ─────────────────────────────────────────────────────────────────────
# L4 — real-pre 专项（仅 -IncludeRealPre 时跑）
# ─────────────────────────────────────────────────────────────────────

if ($IncludeRealPre) {
    Write-Section "L4 - real-pre (requires token)"

    Invoke-Step -Layer "L4" -Name "npm run e2e:real-pre" -Action {
        Push-Location $repoRoot
        try { & npm run e2e:real-pre }
        finally { Pop-Location }
    } | Out-Null
} else {
    if (Should-RunLayer "L4") {
        Write-Host ""
        Write-Host "  [SKIP] L4 real-pre (use -IncludeRealPre)" -ForegroundColor DarkGray
    }
}

# ─────────────────────────────────────────────────────────────────────
# 汇总报告
# ─────────────────────────────────────────────────────────────────────

Write-Host ""
Write-Host ("=" * 60) -ForegroundColor DarkGray
Write-Host "  Test summary" -ForegroundColor White
Write-Host ("=" * 60) -ForegroundColor DarkGray

$pass  = @($global:results | Where-Object { $_.ok })
$fail  = @($global:results | Where-Object { -not $_.ok })
$total = $global:results.Count
$overallPass = $fail.Count -eq 0

foreach ($r in $global:results) {
    $icon  = if ($r.ok) { "OK" } else { "X" }
    $color = if ($r.ok) { "Green" } else { "Red" }
    $time  = "$($r.ms)ms"
    Write-Host "  $icon [$($r.layer)] $($r.name)  ($time)" -ForegroundColor $color
    if (-not $r.ok -and $r.error) {
        Write-Host "      -> $($r.error)" -ForegroundColor DarkRed
    }
}

Write-Host ""
$statusColor = if ($overallPass) { "Green" } else { "Red" }
$statusText  = if ($overallPass) { "ALL PASS" } else { "FAIL ($($fail.Count) failures)" }
Write-Host "  Result: $statusText  ($total steps, $($pass.Count) passed)" -ForegroundColor $statusColor

# 写出 summary
$summary = [ordered]@{
    timestamp   = $timestamp
    outputDir   = $runOutDir
    overallPass = $overallPass
    total       = $total
    pass        = $pass.Count
    fail        = $fail.Count
    steps       = @($global:results)
}
$summary | ConvertTo-Json -Depth 10 |
    Set-Content -Path (Join-Path $runOutDir "summary.json") -Encoding UTF8

Write-Host "  Report: $runOutDir\summary.json" -ForegroundColor DarkGray
Write-Host ""

if (-not $overallPass) {
    exit 1
}
