[CmdletBinding()]
param(
    [string]$RepoRoot = (Get-Location).Path,
    [ValidateSet("Markdown", "Json")]
    [string]$Format = "Markdown"
)

$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    param([string]$Path)
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    if (Test-Path -LiteralPath (Join-Path $resolved "backend/src/main/java")) {
        return $resolved
    }
    if ((Split-Path -Leaf $resolved) -eq "backend") {
        return (Split-Path -Parent $resolved)
    }
    throw "Repo root not found from path: $Path"
}

function Count-SourceLoc {
    param([string]$Path)
    $physical = 0
    $source = 0
    foreach ($line in Get-Content -LiteralPath $Path) {
        $physical++
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0) { continue }
        if ($trimmed.StartsWith("//") -or $trimmed.StartsWith("/*") -or $trimmed.StartsWith("*")) { continue }
        $source++
    }
    [pscustomobject]@{
        physical = $physical
        source = $source
    }
}

function Get-Bucket {
    param([string]$RelativePath)
    if ($RelativePath -like "domain/*") { return "ddd_domain" }
    if ($RelativePath -like "service/*" -or $RelativePath -like "auth/service/*") { return "legacy_service" }
    if ($RelativePath -like "controller/*" -or $RelativePath -like "auth/controller/*") { return "legacy_controller" }
    if ($RelativePath -like "job/*" -or $RelativePath -like "listener/*") { return "legacy_job_listener" }
    if ($RelativePath -like "mapper/*" -or $RelativePath -like "entity/*" -or
        $RelativePath -like "config/*" -or $RelativePath -like "common/*" -or
        $RelativePath -like "security/*" -or $RelativePath -like "exception/*" -or
        $RelativePath -like "thirdparty/*" -or $RelativePath -like "gateway/*" -or
        $RelativePath -like "dto/*" -or $RelativePath -like "vo/*" -or
        $RelativePath -like "testsupport/*") {
        return "support_infra_or_dto"
    }
    return "other"
}

function Get-DddLayer {
    param([string]$RelativePath)
    if ($RelativePath -match "^domain/[^/]+/([^/]+)/") { return $Matches[1] }
    if ($RelativePath -like "domain/*") { return "domain_root" }
    return ""
}

function Get-Domain {
    param([string]$RelativePath)
    if ($RelativePath -match "^domain/([^/]+)/") { return $Matches[1] }
    if ($RelativePath -match "^auth/" -or
        $RelativePath -match "^service/(SysUser|SysRole|SysMenu|SysDept|Org|User|OperationLog)" -or
        $RelativePath -match "^controller/(SysUser|SysRole|SysMenu|SysDept|Auth|User|Org)") {
        return "user"
    }
    if ($RelativePath -match "^service/(SysConfig|RuleCenter|BusinessRule|CommissionConfig|Config)" -or
        $RelativePath -match "^controller/(Config|RuleCenter|BusinessRule|SysConfig)" -or
        $RelativePath -match "^config/") {
        return "config"
    }
    if ($RelativePath -match "^service/(Order|OrderSync|OrderQuery|OrderAttribution|OrderRefund|OrderDualTrack)" -or
        $RelativePath -match "^controller/(Order|OrderSync)" -or
        $RelativePath -match "^listener/Order") {
        return "order"
    }
    if ($RelativePath -match "^service/(Performance|Commission|ExclusiveMerchant|Merchant)" -or
        $RelativePath -match "^controller/(Performance|Commission|ExclusiveMerchant|Merchant)") {
        return "performance"
    }
    if ($RelativePath -match "^service/(Dashboard|Report)" -or
        $RelativePath -match "^service/data/" -or
        $RelativePath -match "^controller/(Dashboard|Data|Report)") {
        return "analytics"
    }
    if ($RelativePath -match "^service/(Product|ColonelActivity|ActivityProduct)" -or
        $RelativePath -match "^controller/(Product|ColonelActivity|ActivityProduct)" -or
        $RelativePath -match "^job/Product") {
        return "product"
    }
    if ($RelativePath -match "^service/(Talent|TalentQuery|TalentClaim)" -or
        $RelativePath -match "^controller/Talent") {
        return "talent"
    }
    if ($RelativePath -match "^service/sample/" -or
        $RelativePath -match "^service/(Sample|QuickSample)" -or
        $RelativePath -match "^controller/Sample") {
        return "sample"
    }
    if ($RelativePath -match "^event/" -or $RelativePath -match "^domain/.+/event/") {
        return "event"
    }
    return "other"
}

function Sum-Loc {
    param([object[]]$Records, [string]$Property = "source")
    if (-not $Records -or $Records.Count -eq 0) { return 0 }
    $sum = ($Records | Measure-Object -Property $Property -Sum).Sum
    if ($null -eq $sum) { return 0 }
    return [int]$sum
}

$repo = Resolve-RepoRoot -Path $RepoRoot
$sourceRoot = Join-Path $repo "backend/src/main/java/com/colonel/saas"
$sourceRootResolved = (Resolve-Path -LiteralPath $sourceRoot).Path
$files = Get-ChildItem -LiteralPath $sourceRootResolved -Recurse -Filter "*.java" -File

$records = foreach ($file in $files) {
    $relative = $file.FullName.Substring($sourceRootResolved.Length + 1).Replace("\", "/")
    $loc = Count-SourceLoc -Path $file.FullName
    [pscustomobject]@{
        path = $relative
        domain = Get-Domain -RelativePath $relative
        bucket = Get-Bucket -RelativePath $relative
        layer = Get-DddLayer -RelativePath $relative
        source = [int]$loc.source
        physical = [int]$loc.physical
    }
}

$dddRecords = @($records | Where-Object { $_.bucket -eq "ddd_domain" })
$legacyServiceRecords = @($records | Where-Object { $_.bucket -eq "legacy_service" })
$legacyEntryRecords = @($records | Where-Object {
        $_.bucket -in @("legacy_service", "legacy_controller", "legacy_job_listener")
    })

$domains = @($records | Select-Object -ExpandProperty domain -Unique | Sort-Object)
$domainMetrics = foreach ($domain in $domains) {
    $domainRecords = @($records | Where-Object { $_.domain -eq $domain })
    $domainDdd = @($domainRecords | Where-Object { $_.bucket -eq "ddd_domain" })
    $domainLegacyService = @($domainRecords | Where-Object { $_.bucket -eq "legacy_service" })
    $domainLegacyEntry = @($domainRecords | Where-Object {
            $_.bucket -in @("legacy_service", "legacy_controller", "legacy_job_listener")
        })
    $dddLoc = Sum-Loc -Records $domainDdd
    $legacyEntryLoc = Sum-Loc -Records $domainLegacyEntry
    $proxyBase = $dddLoc + $legacyEntryLoc
    $proxy = if ($proxyBase -gt 0) { [math]::Round($dddLoc * 100.0 / $proxyBase, 1) } else { 0 }

    [pscustomobject]@{
        domain = $domain
        dddTotal = $dddLoc
        application = Sum-Loc -Records @($domainDdd | Where-Object { $_.layer -eq "application" })
        query = Sum-Loc -Records @($domainDdd | Where-Object { $_.layer -eq "query" })
        port = Sum-Loc -Records @($domainDdd | Where-Object { $_.layer -eq "port" })
        policy = Sum-Loc -Records @($domainDdd | Where-Object { $_.layer -eq "policy" })
        facade = Sum-Loc -Records @($domainDdd | Where-Object { $_.layer -eq "facade" })
        infrastructure = Sum-Loc -Records @($domainDdd | Where-Object { $_.layer -eq "infrastructure" })
        api = Sum-Loc -Records @($domainDdd | Where-Object { $_.layer -eq "api" })
        domainModel = Sum-Loc -Records @($domainDdd | Where-Object { $_.layer -eq "domain" })
        event = Sum-Loc -Records @($domainDdd | Where-Object { $_.layer -eq "event" })
        otherDdd = Sum-Loc -Records @($domainDdd | Where-Object {
                $_.layer -notin @("application", "query", "port", "policy", "facade", "infrastructure", "api", "domain", "event")
            })
        legacyService = Sum-Loc -Records $domainLegacyService
        legacyEntry = $legacyEntryLoc
        migrationProxy = $proxy
        files = $domainRecords.Count
    }
}

$totalSource = Sum-Loc -Records $records
$dddSource = Sum-Loc -Records $dddRecords
$legacyServiceSource = Sum-Loc -Records $legacyServiceRecords
$legacyEntrySource = Sum-Loc -Records $legacyEntryRecords
$rawShare = if ($totalSource -gt 0) { [math]::Round($dddSource * 100.0 / $totalSource, 1) } else { 0 }
$proxyShare = if (($dddSource + $legacyEntrySource) -gt 0) {
    [math]::Round($dddSource * 100.0 / ($dddSource + $legacyEntrySource), 1)
} else {
    0
}

$result = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
    repoRoot = $repo
    sourceRoot = "backend/src/main/java/com/colonel/saas"
    countedFiles = $files.Count
    totalSourceLoc = $totalSource
    dddSourceLoc = $dddSource
    legacyServiceSourceLoc = $legacyServiceSource
    legacyEntrySourceLoc = $legacyEntrySource
    rawDomainShare = $rawShare
    businessMigrationProxy = $proxyShare
    domains = @($domainMetrics | Sort-Object @{Expression = "legacyEntry"; Descending = $true}, domain)
}

if ($Format -eq "Json") {
    $result | ConvertTo-Json -Depth 8
    return
}

Write-Output "# DDD Migration Metrics"
Write-Output ""
Write-Output "| Metric | Value |"
Write-Output "|---|---:|"
Write-Output "| Counted production Java files | $($result.countedFiles) |"
Write-Output "| Production Java source LOC | $($result.totalSourceLoc) |"
Write-Output "| DDD domain source LOC | $($result.dddSourceLoc) |"
Write-Output "| Legacy service source LOC | $($result.legacyServiceSourceLoc) |"
Write-Output "| Legacy entry source LOC | $($result.legacyEntrySourceLoc) |"
Write-Output "| Raw domain share | $($result.rawDomainShare)% |"
Write-Output "| Business migration proxy | $($result.businessMigrationProxy)% |"
Write-Output ""
Write-Output "## Domain Metrics"
Write-Output ""
Write-Output "| Domain | DDD | App | Query | Port | Policy | Facade | Infra | API | Model | Event | Other DDD | Legacy Service | Legacy Entry | Proxy |"
Write-Output "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"
foreach ($domain in $result.domains) {
    Write-Output ("| {0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} | {8} | {9} | {10} | {11} | {12} | {13} | {14}% |" -f
        $domain.domain,
        $domain.dddTotal,
        $domain.application,
        $domain.query,
        $domain.port,
        $domain.policy,
        $domain.facade,
        $domain.infrastructure,
        $domain.api,
        $domain.domainModel,
        $domain.event,
        $domain.otherDdd,
        $domain.legacyService,
        $domain.legacyEntry,
        $domain.migrationProxy)
}
