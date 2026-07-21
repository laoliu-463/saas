param(
    [int]$MaxRedlineDebt = 11,
    [switch]$RequireRedlineZero,
    [switch]$SkipMaven,
    [switch]$DocsOnly,
    [switch]$FailOnUnexpectedDirty,
    [string]$OutputPath = "runtime/qa/out/latest-ddd-acceptance-report.md"
)

$ErrorActionPreference = "Stop"

$script:Warnings = @()
$script:Failures = @()
$script:Checks = @()
$script:CommandResults = @()

function Add-Check {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][ValidateSet("PASS", "WARN", "FAIL", "SKIP")][string]$Status,
        [Parameter(Mandatory = $true)][string]$Summary,
        [string]$Command = ""
    )

    $script:Checks += [pscustomobject]@{
        Name = $Name
        Status = $Status
        Summary = $Summary
        Command = $Command
    }
}

function Add-Warning {
    param([Parameter(Mandatory = $true)][string]$Message)
    $script:Warnings += $Message
}

function Add-Failure {
    param([Parameter(Mandatory = $true)][string]$Message)
    $script:Failures += $Message
}

function Get-RepoRoot {
    return (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
}

function Resolve-ReportPath {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$Path
    )

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return (Join-Path $RepoRoot $Path)
}

function Get-GitValue {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    Push-Location $RepoRoot
    try {
        $value = & git @Arguments 2>$null
        if ($LASTEXITCODE -eq 0) {
            return (($value | Out-String).Trim())
        }
    }
    finally {
        Pop-Location
    }
    return ""
}

function Get-StatusEntries {
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    Push-Location $RepoRoot
    try {
        $lines = & git -c core.quotepath=false status --porcelain=v1
        if ($LASTEXITCODE -ne 0 -or $null -eq $lines) {
            return @()
        }
    }
    finally {
        Pop-Location
    }

    $entries = @()
    foreach ($line in $lines) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.Length -lt 4) {
            continue
        }
        $status = $line.Substring(0, 2)
        $path = $line.Substring(3).Trim().Trim('"')
        if ($path -match "\s+->\s+") {
            $path = ($path -split "\s+->\s+")[-1].Trim().Trim('"')
        }
        $entries += [pscustomobject]@{
            Status = $status
            Path = $path.Replace('\', '/')
            Raw = $line
        }
    }
    return $entries
}

function Test-ExpectedDirty {
    param([Parameter(Mandatory = $true)][string]$Path)

    $knownHistorical = @(
        "backend/src/test/java/com/colonel/saas/domain/user/policy/DataScopePolicyParityTest.java",
        "backend/src/test/java/com/colonel/saas/domain/user/policy/OrgValidationPolicyTest.java"
    )
    if ($knownHistorical -contains $Path) {
        return "known-historical-test"
    }
    if ($Path.StartsWith("harness/") -or $Path.StartsWith("docs/")) {
        return "docs-or-harness"
    }
    return "unexpected"
}

function Count-WhitelistEntries {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        Add-Failure "$Name whitelist missing: $Path"
        Add-Check $Name "FAIL" "whitelist missing" ""
        return [pscustomobject]@{ Name = $Name; Path = $Path; TotalLines = 0; ActiveCount = -1 }
    }

    $lines = @(Get-Content -LiteralPath $Path -Encoding UTF8)
    $active = @($lines | Where-Object {
            $trimmed = $_.Trim()
            $trimmed -and -not $trimmed.StartsWith("#")
        })
    $summary = "active=$($active.Count), totalLines=$($lines.Count)"
    Add-Check $Name "PASS" $summary ""
    return [pscustomobject]@{
        Name = $Name
        Path = $Path
        TotalLines = $lines.Count
        ActiveCount = $active.Count
    }
}

function Get-MatrixSectionLines {
    param([Parameter(Mandatory = $true)][string]$MatrixPath)

    if (-not (Test-Path -LiteralPath $MatrixPath)) {
        Add-Failure "DDD evidence matrix missing: $MatrixPath"
        return @()
    }

    $lines = @(Get-Content -LiteralPath $MatrixPath -Encoding UTF8)
    $start = -1
    $end = $lines.Count
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^##\s+8\.\s+178") {
            $start = $i
            break
        }
    }
    if ($start -lt 0) {
        Add-Failure "DDD evidence matrix section 8 was not found."
        return @()
    }
    for ($i = $start + 1; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^##\s+9\.") {
            $end = $i
            break
        }
    }
    return @($lines[$start..($end - 1)])
}

function Test-Matrix {
    param([Parameter(Mandatory = $true)][string]$MatrixPath)

    $section = @(Get-MatrixSectionLines -MatrixPath $MatrixPath)
    $rows = @()
    $counts = @{
        DONE = 0
        PARTIAL = 0
        TODO = 0
        BLOCKED = 0
    }

    foreach ($line in $section) {
        if ($line -match "^\|\s*([^|]+?)\s*\|\s*(DONE|PARTIAL|TODO|BLOCKED)\s*\|") {
            $id = $Matches[1].Trim()
            $status = $Matches[2].Trim()
            $counts[$status] = $counts[$status] + 1
            $rows += [pscustomobject]@{
                Id = $id
                Status = $status
                Line = $line
            }
        }
    }

    $total = $rows.Count
    if ($total -ne 178) {
        Add-Failure "DDD matrix row count must be 178, actual=$total."
    }

    $doneTokens = @(
        (-join ([char[]](20195, 30721, 65306))),
        (-join ([char[]](27979, 35797, 65306))),
        (-join ([char[]](21629, 20196, 65306))),
        (-join ([char[]](32467, 26524, 65306)))
    )
    foreach ($row in @($rows | Where-Object { $_.Status -eq "DONE" })) {
        foreach ($token in $doneTokens) {
            if (-not $row.Line.Contains($token)) {
                Add-Failure "DONE card $($row.Id) misses evidence token $token"
            }
        }
    }

    $blockedTokens = @(
        (-join ([char[]](38459, 22622, 21407, 22240, 65306))),
        (-join ([char[]](35299, 38459, 26465, 20214, 65306)))
    )
    foreach ($row in @($rows | Where-Object { $_.Status -eq "BLOCKED" })) {
        foreach ($token in $blockedTokens) {
            if (-not $row.Line.Contains($token)) {
                Add-Failure "BLOCKED card $($row.Id) misses blocked token $token"
            }
        }
    }

    $summary = "DONE=$($counts.DONE), PARTIAL=$($counts.PARTIAL), TODO=$($counts.TODO), BLOCKED=$($counts.BLOCKED), total=$total"
    if ($script:Failures | Where-Object { $_ -like "DDD matrix*" -or $_ -like "DONE card*" -or $_ -like "BLOCKED card*" }) {
        Add-Check "DDD evidence matrix" "FAIL" $summary ""
    }
    else {
        Add-Check "DDD evidence matrix" "PASS" $summary ""
    }

    return [pscustomobject]@{
        Done = $counts.DONE
        Partial = $counts.PARTIAL
        Todo = $counts.TODO
        Blocked = $counts.BLOCKED
        Total = $total
    }
}

function Invoke-ExternalCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$CommandText,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$Command,
        [string[]]$Arguments = @()
    )

    $text = ""
    $exitCode = 0
    try {
        $resolvedCommand = $Command
        try {
            $commandInfo = Get-Command $Command -ErrorAction Stop
            if ($commandInfo.Source) {
                $resolvedCommand = $commandInfo.Source
            }
        }
        catch {
            $resolvedCommand = $Command
        }

        Push-Location $WorkingDirectory
        try {
            $global:LASTEXITCODE = 0
            $previousErrorActionPreference = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            try {
                $output = & $resolvedCommand @Arguments 2>&1
            }
            finally {
                $ErrorActionPreference = $previousErrorActionPreference
            }
            if ($null -ne $LASTEXITCODE) {
                $exitCode = $LASTEXITCODE
            }
            else {
                $exitCode = 0
            }
            $text = (($output | Out-String).Trim())
        }
        finally {
            Pop-Location
        }
    }
    catch {
        $text = $_.Exception.Message
        $exitCode = 1
    }

    $status = "PASS"
    $summary = "exitCode=$exitCode"
    if ($exitCode -ne 0) {
        $status = "FAIL"
        Add-Failure "$Name failed with exitCode=$exitCode"
    }

    Add-Check $Name $status $summary $CommandText
    $script:CommandResults += [pscustomobject]@{
        Name = $Name
        Command = $CommandText
        ExitCode = $exitCode
        Output = $text
    }
    return [pscustomobject]@{
        Name = $Name
        ExitCode = $exitCode
        Output = $text
    }
}

function Reset-SurefireReportDir {
    param([Parameter(Mandatory = $true)][string]$ReportDir)

    if (Test-Path -LiteralPath $ReportDir) {
        Remove-Item -LiteralPath $ReportDir -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
}

function Get-SurefireSummary {
    param([Parameter(Mandatory = $true)][string]$ReportDir)

    $summary = [pscustomobject]@{
        Tests = 0
        Failures = 0
        Errors = 0
        Skipped = 0
        Files = 0
    }
    if (-not (Test-Path -LiteralPath $ReportDir)) {
        return $summary
    }

    $files = @(Get-ChildItem -LiteralPath $ReportDir -Filter "TEST-*.xml" -File -ErrorAction SilentlyContinue)
    foreach ($file in $files) {
        try {
            [xml]$doc = Get-Content -LiteralPath $file.FullName
            $suite = $doc.testsuite
            $summary.Tests += [int]$suite.tests
            $summary.Failures += [int]$suite.failures
            $summary.Errors += [int]$suite.errors
            $summary.Skipped += [int]$suite.skipped
            $summary.Files += 1
        }
        catch {
            Add-Warning "Failed to parse surefire XML: $($file.FullName)"
        }
    }
    return $summary
}

function Get-MavenTestSummaryFromOutput {
    param([Parameter(Mandatory = $true)][string]$Output)

    $summary = [pscustomobject]@{
        Tests = 0
        Failures = 0
        Errors = 0
        Skipped = 0
        Files = 0
    }

    $pattern = "Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)"
    $matches = [regex]::Matches($Output, $pattern)
    if ($matches.Count -gt 0) {
        $last = $matches[$matches.Count - 1]
        $summary.Tests = [int]$last.Groups[1].Value
        $summary.Failures = [int]$last.Groups[2].Value
        $summary.Errors = [int]$last.Groups[3].Value
        $summary.Skipped = [int]$last.Groups[4].Value
    }

    $summary.Files = ([regex]::Matches($Output, "(?m)^\[INFO\]\s+Running\s+")).Count
    return $summary
}

function Invoke-MavenTest {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$TestPattern
    )

    $backendDir = Join-Path $RepoRoot "backend"
    $display = "cd backend; mvn test -Dtest='$TestPattern'"
    $result = Invoke-ExternalCommand -Name $Name -CommandText $display -WorkingDirectory $backendDir -Command "mvn" -Arguments @("test", "-Dtest=$TestPattern")
    $summary = Get-MavenTestSummaryFromOutput -Output $result.Output
    $status = "PASS"
    if ($result.ExitCode -ne 0 -or $summary.Failures -gt 0 -or $summary.Errors -gt 0) {
        $status = "FAIL"
    }
    Add-Check "$Name surefire" $status "tests=$($summary.Tests), failures=$($summary.Failures), errors=$($summary.Errors), skipped=$($summary.Skipped), files=$($summary.Files)" $display
    return $summary
}

function Escape-ReportCell {
    param([string]$Value)
    if ($null -eq $Value) {
        return ""
    }
    return $Value.Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Format-ReportList {
    param(
        [Parameter(Mandatory = $true)]$Items,
        [int]$MaxItems = 30,
        [string]$Prefix = "",
        [string]$Empty = "(none)"
    )

    $allItems = @($Items)
    if ($allItems.Count -eq 0) {
        return $Empty
    }

    $lines = @()
    $take = [Math]::Min($allItems.Count, $MaxItems)
    for ($i = 0; $i -lt $take; $i++) {
        $lines += "$Prefix$($allItems[$i])"
    }
    if ($allItems.Count -gt $MaxItems) {
        $lines += "$Prefix... omitted $($allItems.Count - $MaxItems) more"
    }
    return ($lines -join "`n")
}

function Write-Report {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Branch,
        [Parameter(Mandatory = $true)][string]$Head,
        [Parameter(Mandatory = $true)]$DirtyEntries,
        [Parameter(Mandatory = $true)]$MapperWhitelist,
        [Parameter(Mandatory = $true)]$RedlineWhitelist,
        [Parameter(Mandatory = $true)]$MatrixStats
    )

    $dir = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }

    $conclusion = "PASS"
    if ($script:Failures.Count -gt 0) {
        $conclusion = "FAIL"
    }
    $now = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
    $dirtyTotal = @($DirtyEntries).Count
    $dirtyDocsOrHarness = 0
    $dirtyKnownHistorical = 0
    $dirtyUnexpected = 0
    foreach ($entry in @($DirtyEntries)) {
        $class = Test-ExpectedDirty -Path $entry.Path
        if ($class -eq "docs-or-harness") {
            $dirtyDocsOrHarness++
        }
        elseif ($class -eq "known-historical-test") {
            $dirtyKnownHistorical++
        }
        else {
            $dirtyUnexpected++
        }
    }
    $dirtyLines = @($DirtyEntries | ForEach-Object { "$($_.Raw)" })
    $dirtyBlock = Format-ReportList -Items $dirtyLines -MaxItems 35 -Empty "(clean)"
    $warningBlock = Format-ReportList -Items $script:Warnings -MaxItems 35 -Prefix "- " -Empty "(none)"
    $failureBlock = Format-ReportList -Items $script:Failures -MaxItems 35 -Prefix "- " -Empty "(none)"

    $checkRows = ""
    foreach ($check in $script:Checks) {
        $checkRows += "| $(Escape-ReportCell $check.Name) | $($check.Status) | $(Escape-ReportCell $check.Summary) | $(Escape-ReportCell $check.Command) |`n"
    }

    $content = @"
# DDD Acceptance Latest Report

## Metadata

- Time: $now
- Branch: $Branch
- HEAD: $Head
- Conclusion: $conclusion

## Dirty Files

- Total: $dirtyTotal
- docs/harness: $dirtyDocsOrHarness
- known historical tests: $dirtyKnownHistorical
- unexpected non docs/harness: $dirtyUnexpected

~~~text
$dirtyBlock
~~~

## Whitelist

| File | Active | Total Lines |
| --- | ---: | ---: |
| cross-domain-mapper-legacy-whitelist.txt | $($MapperWhitelist.ActiveCount) | $($MapperWhitelist.TotalLines) |
| architecture-redline-legacy-whitelist.txt | $($RedlineWhitelist.ActiveCount) | $($RedlineWhitelist.TotalLines) |

## Matrix

| DONE | PARTIAL | TODO | BLOCKED | Total |
| ---: | ---: | ---: | ---: | ---: |
| $($MatrixStats.Done) | $($MatrixStats.Partial) | $($MatrixStats.Todo) | $($MatrixStats.Blocked) | $($MatrixStats.Total) |

## Checks

| Check | Status | Summary | Command |
| --- | --- | --- | --- |
$checkRows
## Warnings

$warningBlock

## Failures

$failureBlock

## Next Steps

- If conclusion is FAIL, fix the listed failures and rerun this script.
- Keep cross-domain mapper whitelist at 0.
- Reduce architecture redline debt by lowering -MaxRedlineDebt over future DDD slices.
"@

    Set-Content -LiteralPath $Path -Value $content -Encoding UTF8
}

$repoRoot = Get-RepoRoot
$reportPath = Resolve-ReportPath -RepoRoot $repoRoot -Path $OutputPath
$backendDir = Join-Path $repoRoot "backend"
$branch = Get-GitValue -RepoRoot $repoRoot -Arguments @("branch", "--show-current")
$head = Get-GitValue -RepoRoot $repoRoot -Arguments @("rev-parse", "--short", "HEAD")
$dirtyEntries = @(Get-StatusEntries -RepoRoot $repoRoot)

Write-Host "DDD acceptance check"
Write-Host "Branch: $branch"
Write-Host "HEAD: $head"
Write-Host "OutputPath: $reportPath"

foreach ($entry in $dirtyEntries) {
    $class = Test-ExpectedDirty -Path $entry.Path
    if ($class -eq "known-historical-test") {
        Add-Warning "Known historical dirty test: $($entry.Path)"
    }
    elseif ($class -eq "unexpected") {
        $message = "Unexpected non docs/harness dirty file: $($entry.Path)"
        if ($FailOnUnexpectedDirty) {
            Add-Failure $message
        }
        else {
            Add-Warning $message
        }
    }
}
if ($dirtyEntries.Count -eq 0) {
    Add-Check "git status" "PASS" "worktree clean" "git status --short"
}
else {
    Add-Check "git status" "WARN" "dirtyFiles=$($dirtyEntries.Count)" "git status --short"
}

$mapperPath = Join-Path $repoRoot "backend\src\test\resources\ddd\cross-domain-mapper-legacy-whitelist.txt"
$redlinePath = Join-Path $repoRoot "backend\src\test\resources\ddd\architecture-redline-legacy-whitelist.txt"
$mapperWhitelist = Count-WhitelistEntries -Name "cross-domain mapper whitelist" -Path $mapperPath
$redlineWhitelist = Count-WhitelistEntries -Name "architecture redline whitelist" -Path $redlinePath

if ($mapperWhitelist.ActiveCount -ne 0) {
    Add-Failure "cross-domain mapper whitelist must be 0, actual=$($mapperWhitelist.ActiveCount)"
}
if ($RequireRedlineZero) {
    if ($redlineWhitelist.ActiveCount -ne 0) {
        Add-Failure "architecture redline whitelist must be 0 because -RequireRedlineZero is set, actual=$($redlineWhitelist.ActiveCount)"
    }
}
elseif ($redlineWhitelist.ActiveCount -gt $MaxRedlineDebt) {
    Add-Failure "architecture redline whitelist exceeds -MaxRedlineDebt $MaxRedlineDebt, actual=$($redlineWhitelist.ActiveCount)"
}

$matrixPath = Join-Path $repoRoot "docs\ddd-completion-evidence-matrix.md"
$matrixStats = Test-Matrix -MatrixPath $matrixPath

$diffCheck = Invoke-ExternalCommand -Name "git diff --check" -CommandText "git diff --check" -WorkingDirectory $repoRoot -Command "git" -Arguments @("diff", "--check")

$limitScript = Join-Path $repoRoot "harness\scripts\check-harness-limits.ps1"
if (Test-Path -LiteralPath $limitScript) {
    Invoke-ExternalCommand -Name "check-harness-limits" -CommandText "powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1" -WorkingDirectory $repoRoot -Command "powershell" -Arguments @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $limitScript) | Out-Null
}
else {
    Add-Warning "Missing script: $limitScript"
    Add-Check "check-harness-limits" "WARN" "script missing" ""
}

$safetyScript = Join-Path $repoRoot "harness\scripts\commands\safety-check.ps1"
if (Test-Path -LiteralPath $safetyScript) {
    Invoke-ExternalCommand -Name "safety-check docs dry-run" -CommandText "powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun" -WorkingDirectory $repoRoot -Command "powershell" -Arguments @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $safetyScript, "-Env", "real-pre", "-Scope", "docs", "-DryRun") | Out-Null
}
else {
    Add-Warning "Missing script: $safetyScript"
    Add-Check "safety-check docs dry-run" "WARN" "script missing" ""
}

if ($DocsOnly -or $SkipMaven) {
    $reason = "skipped"
    if ($DocsOnly) {
        $reason = "skipped by -DocsOnly"
    }
    elseif ($SkipMaven) {
        $reason = "skipped by -SkipMaven"
    }
    Add-Check "mvn -DskipTests compile" "SKIP" $reason "cd backend; mvn -DskipTests compile"
    Add-Check "DddArchitectureRedlineGuardTest" "SKIP" $reason "cd backend; mvn test -Dtest='DddArchitectureRedlineGuardTest'"
    Add-Check "wide DDD architecture tests" "SKIP" $reason "cd backend; mvn test -Dtest='*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test'"
}
else {
    Invoke-ExternalCommand -Name "mvn compile" -CommandText "cd backend; mvn -DskipTests compile" -WorkingDirectory $backendDir -Command "mvn" -Arguments @("-DskipTests", "compile") | Out-Null
    Invoke-MavenTest -RepoRoot $repoRoot -Name "DddArchitectureRedlineGuardTest" -TestPattern "DddArchitectureRedlineGuardTest" | Out-Null
    Invoke-MavenTest -RepoRoot $repoRoot -Name "wide DDD architecture tests" -TestPattern "*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test" | Out-Null
}

Write-Report -Path $reportPath -Branch $branch -Head $head -DirtyEntries $dirtyEntries -MapperWhitelist $mapperWhitelist -RedlineWhitelist $redlineWhitelist -MatrixStats $matrixStats

Write-Host ""
Write-Host "Report: $reportPath"
if ($script:Warnings.Count -gt 0) {
    Write-Host "Warnings: $($script:Warnings.Count)" -ForegroundColor Yellow
}
if ($script:Failures.Count -gt 0) {
    Write-Host "Conclusion: FAIL" -ForegroundColor Red
    foreach ($failure in $script:Failures) {
        Write-Host "- $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host "Conclusion: PASS" -ForegroundColor Green
exit 0
