param(
    [string]$ApiBaseUrl
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$auditScript = Join-Path $repoRoot "scripts\qa-mock-data-audit.ps1"
$mockAudit = Join-Path $repoRoot "runtime\qa\mock-data-audit.cjs"
$mockAuditTest = Join-Path $repoRoot "runtime\qa\mock-data-audit.test.cjs"
$businessStateFlow = Join-Path $repoRoot "runtime\qa\business-state-flow-regression.cjs"
$businessStateFlowTest = Join-Path $repoRoot "runtime\qa\business-state-flow-regression.test.cjs"
$pageRoleBusinessSmoke = Join-Path $repoRoot "runtime\qa\page-role-business-smoke.cjs"
$pageRoleBusinessSmokeTest = Join-Path $repoRoot "runtime\qa\page-role-business-smoke.test.cjs"
$dashboardReconcile = Join-Path $repoRoot "runtime\qa\dashboard-reconcile.cjs"
$dashboardReconcileTest = Join-Path $repoRoot "runtime\qa\dashboard-reconcile.test.cjs"

function Invoke-ExternalStep {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    Write-Host "==> $Name" -ForegroundColor Cyan
    $global:LASTEXITCODE = 0
    & $Action
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "$Name failed with exit code $exitCode"
    }
    Write-Host "PASS: $Name" -ForegroundColor Green
}

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "node is not available in PATH"
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "mvn is not available in PATH"
}
if (-not (Test-Path -LiteralPath $mockAudit)) {
    throw "Mock audit script not found: $mockAudit"
}
if (-not (Test-Path -LiteralPath $mockAuditTest)) {
    throw "Mock audit test not found: $mockAuditTest"
}
if (-not (Test-Path -LiteralPath $auditScript)) {
    throw "QA mock data audit wrapper not found: $auditScript"
}
foreach ($requiredScript in @(
    $businessStateFlow,
    $businessStateFlowTest,
    $pageRoleBusinessSmoke,
    $pageRoleBusinessSmokeTest,
    $dashboardReconcile,
    $dashboardReconcileTest
)) {
    if (-not (Test-Path -LiteralPath $requiredScript)) {
        throw "QA final phase script not found: $requiredScript"
    }
}

Push-Location $repoRoot
try {
    Invoke-ExternalStep "node --check runtime/qa/mock-data-audit.cjs" {
        & node "--check" $mockAudit
    }

    Invoke-ExternalStep "node --check runtime/qa/mock-data-audit.test.cjs" {
        & node "--check" $mockAuditTest
    }

    Invoke-ExternalStep "node runtime/qa/mock-data-audit.test.cjs" {
        & node $mockAuditTest
    }

    Invoke-ExternalStep "backend mvn -DskipTests compile" {
        Push-Location (Join-Path $repoRoot "backend")
        try {
            & mvn "-DskipTests" compile
        }
        finally {
            Pop-Location
        }
    }

    Invoke-ExternalStep "scripts/qa-mock-data-audit.ps1" {
        $auditArgs = @()
        if ($ApiBaseUrl) {
            $auditArgs += @("-ApiBaseUrl", $ApiBaseUrl)
        }
        & $auditScript @auditArgs
    }

    Invoke-ExternalStep "node --check runtime/qa/business-state-flow-regression.cjs" {
        & node "--check" $businessStateFlow
    }

    Invoke-ExternalStep "node --check runtime/qa/page-role-business-smoke.cjs" {
        & node "--check" $pageRoleBusinessSmoke
    }

    Invoke-ExternalStep "node --check runtime/qa/dashboard-reconcile.cjs" {
        & node "--check" $dashboardReconcile
    }

    Invoke-ExternalStep "node runtime/qa/business-state-flow-regression.test.cjs" {
        & node $businessStateFlowTest
    }

    Invoke-ExternalStep "node runtime/qa/page-role-business-smoke.test.cjs" {
        & node $pageRoleBusinessSmokeTest
    }

    Invoke-ExternalStep "node runtime/qa/dashboard-reconcile.test.cjs" {
        & node $dashboardReconcileTest
    }

    Invoke-ExternalStep "runtime/qa/business-state-flow-regression.cjs" {
        if ($ApiBaseUrl) {
            $oldApiBaseUrl = $env:API_BASE_URL
            $env:API_BASE_URL = $ApiBaseUrl
            try {
                & node $businessStateFlow
            }
            finally {
                $env:API_BASE_URL = $oldApiBaseUrl
            }
        }
        else {
            & node $businessStateFlow
        }
    }

    Invoke-ExternalStep "runtime/qa/page-role-business-smoke.cjs" {
        if ($ApiBaseUrl) {
            $oldApiBaseUrl = $env:API_BASE_URL
            $env:API_BASE_URL = $ApiBaseUrl
            try {
                & node $pageRoleBusinessSmoke
            }
            finally {
                $env:API_BASE_URL = $oldApiBaseUrl
            }
        }
        else {
            & node $pageRoleBusinessSmoke
        }
    }

    Invoke-ExternalStep "runtime/qa/dashboard-reconcile.cjs" {
        if ($ApiBaseUrl) {
            $oldApiBaseUrl = $env:API_BASE_URL
            $env:API_BASE_URL = $ApiBaseUrl
            try {
                & node $dashboardReconcile
            }
            finally {
                $env:API_BASE_URL = $oldApiBaseUrl
            }
        }
        else {
            & node $dashboardReconcile
        }
    }
}
finally {
    Pop-Location
}

Write-Host "TEST/mock final regression checks passed." -ForegroundColor Green
