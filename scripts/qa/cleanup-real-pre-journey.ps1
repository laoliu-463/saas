<#
.SYNOPSIS
  Generate or execute a guarded cleanup plan for one real-pre QA runId.

.DESCRIPTION
  PlanOnly is the default. Use -Execute -RunId QA... only after a human reviews
  cleanup-plan.json/sql and confirms the plan contains only this run's QA data.
#>

[CmdletBinding()]
param(
    [string]$RunId,
    [string]$StateFile,
    [switch]$Execute,
    [string]$BaseUrl = "http://localhost:8081/api",
    [string]$PostgresContainer = "saas-active-postgres-real-pre-1",
    [string]$DbUser = "saas",
    [string]$DbName = "saas_real_pre",
    [string]$EvidenceDir
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..\..")).Path
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"

if (-not $EvidenceDir) {
    $EvidenceDir = Join-Path $repoRoot "runtime\qa\out\real-pre-cleanup-$timestamp"
}
New-Item -ItemType Directory -Path $EvidenceDir -Force | Out-Null

if ($StateFile) {
    $StateFile = (Resolve-Path $StateFile).Path
    if (-not $RunId) {
        $stateJson = Get-Content -LiteralPath $StateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($stateJson.PSObject.Properties.Name -contains "runId") {
            $RunId = [string]$stateJson.runId
        }
        elseif ($stateJson.PSObject.Properties.Name -contains "flow" -and $stateJson.flow.PSObject.Properties.Name -contains "runId") {
            $RunId = [string]$stateJson.flow.runId
        }
    }
}

if (-not $RunId -or $RunId -notmatch '^QA[A-Za-z0-9_-]+$') {
    throw "RunId is required and must start with QA. Refusing cleanup."
}

function Get-UnwrappedData {
    param([object]$Body)
    if ($null -ne $Body -and $Body.PSObject.Properties.Name -contains "data") {
        return $Body.data
    }
    return $Body
}

function Assert-RealPreApiGuard {
    $url = ($BaseUrl.TrimEnd("/")) + "/system/env"
    $body = Invoke-RestMethod -Method Get -Uri $url -TimeoutSec 15
    $envData = Get-UnwrappedData $body
    $label = [string]$envData.environmentLabel
    $profiles = @($envData.activeProfiles)
    $isRealPre = $label.ToUpperInvariant() -eq "REAL-PRE" -or ($profiles -contains "real-pre")
    if (-not $isRealPre) {
        throw "Expected /api/system/env to be REAL-PRE, got: $($envData | ConvertTo-Json -Compress -Depth 8)"
    }
    if ($envData.appTestEnabled -ne $false) {
        throw "APP_TEST_ENABLED must be false in real-pre cleanup guard."
    }
    if ($envData.douyinTestEnabled -ne $false) {
        throw "DOUYIN_TEST_ENABLED must be false in real-pre cleanup guard."
    }
    if ($BaseUrl -notmatch '^https?://(localhost|127\.0\.0\.1)(:\d+)?(/|$)') {
        throw "Cleanup must target local real-pre API only. BaseUrl=$BaseUrl"
    }
    return $envData
}

function Invoke-PsqlScalar {
    param([string]$Sql)
    $out = & docker exec $PostgresContainer psql -X -q -v ON_ERROR_STOP=1 -U $DbUser -d $DbName -tAc $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql scalar failed: $Sql"
    }
    return ([string]$out).Trim()
}

function Invoke-PsqlScript {
    param([string]$Sql)
    $dockerArgs = @(
        "exec", "-i", $PostgresContainer,
        "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1", "-U", $DbUser, "-d", $DbName
    )
    $Sql | & docker @dockerArgs
    if ($LASTEXITCODE -ne 0) {
        throw "psql script failed"
    }
}

function Assert-DatabaseGuard {
    if ($DbName -ne "saas_real_pre") {
        throw "DbName must be exactly saas_real_pre. Got $DbName"
    }
    if ($PostgresContainer -match '(?i)(prod|production|formal)') {
        throw "PostgresContainer looks like a production container: $PostgresContainer"
    }
    $currentDb = Invoke-PsqlScalar "select current_database();"
    if ($currentDb -ne "saas_real_pre") {
        throw "Connected database must be saas_real_pre. Got $currentDb"
    }
    $server = Invoke-PsqlScalar "select inet_server_addr()::text;"
    return [ordered]@{
        dbName = $currentDb
        container = $PostgresContainer
        server = $server
    }
}

function New-CleanupPlan {
    $helper = Join-Path $repoRoot "runtime\qa\real-pre-cleanup-plan.cjs"
    $args = @($helper, "--run-id", $RunId, "--evidence-dir", $EvidenceDir)
    if ($StateFile) {
        $args += @("--state-file", $StateFile)
    }
    $json = & node @args
    if ($LASTEXITCODE -ne 0) {
        throw "cleanup plan helper failed"
    }
    return ($json -join "`n") | ConvertFrom-Json
}

Write-Host "Generating real-pre cleanup plan for RunId=$RunId" -ForegroundColor Cyan
$apiGuard = Assert-RealPreApiGuard
$dbGuard = Assert-DatabaseGuard
$plan = New-CleanupPlan

$countedTargets = @()
foreach ($target in @($plan.targets)) {
    $count = Invoke-PsqlScalar ([string]$target.countSql)
    $target | Add-Member -NotePropertyName matchedRows -NotePropertyValue ([int]$count) -Force
    $countedTargets += $target
}

$guardReport = [ordered]@{
    runId = $RunId
    mode = if ($Execute) { "Execute" } else { "PlanOnly" }
    generatedAt = (Get-Date).ToString("o")
    api = $apiGuard
    database = $dbGuard
    stateFile = $StateFile
    evidenceDir = $EvidenceDir
}

$guardReport | ConvertTo-Json -Depth 12 |
    Set-Content -LiteralPath (Join-Path $EvidenceDir "cleanup-guards.json") -Encoding UTF8
$plan | ConvertTo-Json -Depth 20 |
    Set-Content -LiteralPath (Join-Path $EvidenceDir "cleanup-plan.json") -Encoding UTF8
[string]$plan.executeSql |
    Set-Content -LiteralPath (Join-Path $EvidenceDir "cleanup-plan.sql") -Encoding UTF8
[string]$plan.verifySql |
    Set-Content -LiteralPath (Join-Path $EvidenceDir "cleanup-verify.sql") -Encoding UTF8

Write-Host "Plan written: $EvidenceDir" -ForegroundColor Green
foreach ($target in $countedTargets) {
    Write-Host ("  {0}: {1}" -f $target.table, $target.matchedRows)
}

if (-not $Execute) {
    Write-Host "PlanOnly complete. Review cleanup-plan.json/sql before running with -Execute -RunId $RunId." -ForegroundColor Yellow
    exit 0
}

Write-Host "Executing reviewed cleanup plan for RunId=$RunId" -ForegroundColor Yellow
Invoke-PsqlScript ([string]$plan.executeSql)

$verifyResults = @()
$hasResidual = $false
foreach ($target in @($plan.targets)) {
    $remaining = [int](Invoke-PsqlScalar ([string]$target.verifySql))
    $verifyResults += [ordered]@{
        table = $target.table
        remainingRows = $remaining
    }
    if ($remaining -ne 0) {
        $hasResidual = $true
    }
}

$verifyResults | ConvertTo-Json -Depth 8 |
    Set-Content -LiteralPath (Join-Path $EvidenceDir "cleanup-verify-result.json") -Encoding UTF8

if ($hasResidual) {
    throw "Cleanup verification failed: residual rows remain for RunId=$RunId. See cleanup-verify-result.json."
}

Write-Host "Cleanup executed and verified: all RunId residual counts are 0." -ForegroundColor Green
