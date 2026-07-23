param(
    [ValidateSet("Plan", "Archive", "Delete")]
    [string]$Action = "Plan",
    [string]$Manifest = "",
    [string]$Reason = "post-task content maintenance",
    [string]$ArchiveRoot = "runtime/qa/out/archive",
    [string]$RepoRoot = "",
    [string]$ReportKey = "content-retire",
    [switch]$AllowSourceCode,
    [switch]$PassThruResult,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_lib.ps1")

function Assert-PathInside {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Label
    )

    $resolvedPath = (Get-Item -LiteralPath $Path).FullName
    $resolvedRoot = (Get-Item -LiteralPath $Root).FullName
    $separatorChars = [char[]]@([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $rootPrefix = $resolvedRoot.TrimEnd($separatorChars) + [System.IO.Path]::DirectorySeparatorChar
    $comparison = if ([System.IO.Path]::DirectorySeparatorChar -eq "\") {
        [System.StringComparison]::OrdinalIgnoreCase
    }
    else {
        [System.StringComparison]::Ordinal
    }
    if ($resolvedPath -ne $resolvedRoot -and -not $resolvedPath.StartsWith($rootPrefix, $comparison)) {
        throw "$Label is outside allowed root. path=$resolvedPath root=$resolvedRoot"
    }
    return $resolvedPath
}

function Get-RepoRelativePath {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $separatorChars = [char[]]@([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $resolvedRoot = (Get-Item -LiteralPath $RepoRoot).FullName.TrimEnd($separatorChars)
    $resolvedPath = (Get-Item -LiteralPath $Path).FullName
    if ($resolvedPath -eq $resolvedRoot) {
        return "."
    }
    $rootPrefix = $resolvedRoot + [System.IO.Path]::DirectorySeparatorChar
    $comparison = if ([System.IO.Path]::DirectorySeparatorChar -eq "\") {
        [System.StringComparison]::OrdinalIgnoreCase
    }
    else {
        [System.StringComparison]::Ordinal
    }
    if (-not $resolvedPath.StartsWith($rootPrefix, $comparison)) {
        throw "Cannot compute relative path outside repo. path=$resolvedPath root=$resolvedRoot"
    }
    return $resolvedPath.Substring($rootPrefix.Length)
}

function Test-ProtectedPath {
    param([Parameter(Mandatory = $true)][string]$RelativePath)

    $normalized = $RelativePath.Replace("/", "\").TrimStart("\")
    $lower = $normalized.ToLowerInvariant()
    $leaf = Split-Path -Leaf $normalized

    if ($lower -eq ".git" -or $lower.StartsWith(".git\")) {
        return "git metadata is protected"
    }
    if ($leaf -like ".env*" -and -not $leaf.EndsWith(".example")) {
        return "env files are protected"
    }
    if ($lower.EndsWith(".pem") -or $lower.EndsWith(".key") -or $lower.EndsWith(".p12") -or $lower.EndsWith(".jks")) {
        return "secret/certificate files are protected"
    }
    if ($lower -like "docker-compose*.yml" -or $lower -like "docker-compose*.yaml") {
        return "docker compose files are protected"
    }
    if ($lower.StartsWith("backend\src\main\resources\db\")) {
        return "database migration resources are protected"
    }
    if ($lower -eq "agENTs.md".ToLowerInvariant() -or $lower -eq "claude.md" -or $lower -eq "codex.md") {
        return "agent entry documents are protected"
    }
    return ""
}

function Test-SourceLikePath {
    param([Parameter(Mandatory = $true)][string]$RelativePath)

    $lower = $RelativePath.Replace("/", "\").TrimStart("\").ToLowerInvariant()
    return $lower.StartsWith("backend\src\") `
        -or $lower.StartsWith("frontend\src\") `
        -or $lower.StartsWith("scripts\") `
        -or $lower.StartsWith("harness\scripts\commands\") `
        -or $lower.EndsWith(".java") `
        -or $lower.EndsWith(".vue") `
        -or $lower.EndsWith(".ts") `
        -or $lower.EndsWith(".tsx") `
        -or $lower.EndsWith(".js") `
        -or $lower.EndsWith(".cjs")
}

function New-PlanCandidate {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Category,
        [Parameter(Mandatory = $true)][string]$SuggestedAction,
        [Parameter(Mandatory = $true)][string]$Evidence
    )

    return [pscustomobject]@{
        path = $Path
        category = $Category
        suggestedAction = $SuggestedAction
        evidence = $Evidence
    }
}

function Get-AutoCandidates {
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    $candidates = @()

    $stateDocDir = Join-Path $RepoRoot "docs\harness-maintenance\legacy-rules\state\snapshots"
    $debtRegisterItem = Get-ChildItem -LiteralPath $stateDocDir -Filter "05-*.md" -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($debtRegisterItem -and (Test-Path -LiteralPath $debtRegisterItem.FullName)) {
        $content = Get-Content -Raw -Encoding UTF8 -LiteralPath $debtRegisterItem.FullName
        foreach ($line in ($content -split "`r?`n")) {
            if (-not $line.StartsWith("|") -or $line -match "^\|\s*-") {
                continue
            }
            $cells = $line -split "\|"
            if ($cells.Count -lt 3 -or $cells[1].Trim() -eq "文件") {
                continue
            }
            $status = $cells[$cells.Count - 2].Trim().ToUpperInvariant()
            if ($status -in @("ARCHIVED", "RESOLVED")) {
                continue
            }
            $matches = [regex]::Matches($cells[1], '`([^`]+)`')
            foreach ($match in $matches) {
                $candidatePath = $match.Groups[1].Value
                if ($candidatePath -and -not ($candidatePath.Contains("*"))) {
                    $full = Join-Path $RepoRoot $candidatePath
                    if (Test-Path -LiteralPath $full) {
                        $candidates += New-PlanCandidate -Path $candidatePath -Category "document-debt" -SuggestedAction "review/resolve-from-debt-register" -Evidence "listed in docs/harness-maintenance/legacy-rules/state/snapshots/05-*.md debt register"
                    }
                }
            }
        }
    }

    $transientPatterns = @("snap*.txt", "*.log", "args.json", "env.json", "health.json")
    foreach ($pattern in $transientPatterns) {
        $items = Get-ChildItem -LiteralPath $RepoRoot -Filter $pattern -File -ErrorAction SilentlyContinue
        foreach ($item in $items) {
            $relative = Get-RepoRelativePath -RepoRoot $RepoRoot -Path $item.FullName
            $candidates += New-PlanCandidate -Path $relative -Category "root-transient-file" -SuggestedAction "review/delete-if-no-longer-needed" -Evidence "top-level generated/transient filename pattern: $pattern"
        }
    }

    foreach ($dir in @("tmp", "out", "test-results", "playwright-report")) {
        $fullDir = Join-Path $RepoRoot $dir
        if (Test-Path -LiteralPath $fullDir) {
            $candidates += New-PlanCandidate -Path $dir -Category "generated-output-directory" -SuggestedAction "review/delete-after-retention" -Evidence "generated output directory exists"
        }
    }

    return $candidates | Sort-Object path -Unique
}

function Read-RetireManifest {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$ManifestPath
    )

    $manifestFullPath = if ([System.IO.Path]::IsPathRooted($ManifestPath)) {
        $ManifestPath
    }
    else {
        Join-Path $RepoRoot $ManifestPath
    }
    Assert-PathInside -Path $manifestFullPath -Root $RepoRoot -Label "Manifest" | Out-Null
    $json = Get-Content -Raw -Encoding UTF8 -LiteralPath $manifestFullPath | ConvertFrom-Json
    if ($null -eq $json.items) {
        throw "Manifest must contain an items array."
    }
    return @($json.items)
}

function Format-CandidatesBlock {
    param([Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Candidates)

    if ($Candidates.Count -eq 0) {
        return "(none)"
    }

    $lines = @()
    foreach ($candidate in $Candidates) {
        $lines += "- $($candidate.path) [$($candidate.category)] -> $($candidate.suggestedAction); evidence=$($candidate.evidence)"
    }
    return ($lines -join "`n")
}

Write-HarnessStage "Content retirement"

$repoRoot = if ([string]::IsNullOrWhiteSpace($RepoRoot)) { Get-HarnessRepoRoot } else { (Get-Item -LiteralPath $RepoRoot).FullName }
Assert-HarnessRepoRoot -RepoRoot $repoRoot

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = New-HarnessReportPath -RepoRoot $repoRoot -ReportKey $ReportKey
$archiveRootPath = if ([System.IO.Path]::IsPathRooted($ArchiveRoot)) {
    $ArchiveRoot
}
else {
    Join-Path $repoRoot $ArchiveRoot
}

$operations = @()
$candidates = @()
$generatedOwnedFiles = @()

if ([string]::IsNullOrWhiteSpace($Manifest)) {
    if ($Action -ne "Plan") {
        throw "Archive/Delete requires -Manifest. Plan may run without a manifest."
    }
    $candidates = @(Get-AutoCandidates -RepoRoot $repoRoot)
}
else {
    $items = @(Read-RetireManifest -RepoRoot $repoRoot -ManifestPath $Manifest)
    foreach ($item in $items) {
        if ([string]::IsNullOrWhiteSpace($item.path)) {
            throw "Manifest item missing path."
        }
        $relativePath = ([string]$item.path).Replace("/", [string][System.IO.Path]::DirectorySeparatorChar).Replace("\", [string][System.IO.Path]::DirectorySeparatorChar).TrimStart([char[]]@([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar))
        $requestedAction = if ($item.action) { ([string]$item.action).ToLowerInvariant() } else { $Action.ToLowerInvariant() }
        if ($Action -ne "Plan" -and $requestedAction -ne $Action.ToLowerInvariant()) {
            throw "Manifest item action '$requestedAction' does not match command action '$Action' for path $relativePath."
        }
        if ($requestedAction -notin @("archive", "delete", "plan")) {
            throw "Unsupported manifest item action: $requestedAction"
        }

        $protectedReason = Test-ProtectedPath -RelativePath $relativePath
        if ($protectedReason) {
            throw "Protected path cannot be retired: $relativePath ($protectedReason)"
        }
        if ((Test-SourceLikePath -RelativePath $relativePath) -and -not $AllowSourceCode) {
            throw "Source-like path requires -AllowSourceCode: $relativePath"
        }

        $hasArchiveGroup = @($item.PSObject.Properties.Name) -contains "archiveGroup"
        $archiveGroup = ""
        if ($hasArchiveGroup) {
            $archiveGroup = ([string]$item.archiveGroup).Trim().ToLowerInvariant()
            if ([string]::IsNullOrWhiteSpace($archiveGroup) -or $archiveGroup -notmatch '^[a-z0-9][a-z0-9-]{0,63}$') {
                throw "archiveGroup must be one safe path segment: $($item.archiveGroup)"
            }
        }

        $sourcePath = Join-Path $repoRoot $relativePath
        if (-not (Test-Path -LiteralPath $sourcePath)) {
            throw "Retire target not found: $relativePath"
        }
        $sourceResolved = Assert-PathInside -Path $sourcePath -Root $repoRoot -Label "Retire target"
        $isDirectory = (Get-Item -LiteralPath $sourceResolved).PSIsContainer
        if ($requestedAction -eq "delete" -and $isDirectory -and -not ($item.allowRecursive -eq $true)) {
            throw "Deleting a directory requires allowRecursive=true in manifest: $relativePath"
        }

        $operations += [pscustomobject]@{
            path = $relativePath
            action = $requestedAction
            category = if ($item.category) { $item.category } else { "manifest" }
            reason = if ($item.reason) { $item.reason } else { $Reason }
            isDirectory = $isDirectory
            source = $sourceResolved
            archiveGroup = $archiveGroup
        }
    }
}

$applied = @()
if ($Action -eq "Archive" -and $operations.Count -gt 0) {
    if (-not $DryRun) {
        New-Item -ItemType Directory -Force -Path $archiveRootPath | Out-Null
        Assert-PathInside -Path $archiveRootPath -Root $repoRoot -Label "Archive root" | Out-Null
    }
    $archiveBatchPath = Join-Path $archiveRootPath $stamp
    $destinations = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($operation in $operations) {
        $destination = if ([string]::IsNullOrWhiteSpace($operation.archiveGroup)) {
            Join-Path $archiveBatchPath $operation.path
        }
        else {
            Join-Path (Join-Path $archiveBatchPath $operation.archiveGroup) (Split-Path -Leaf $operation.path)
        }
        if (-not $destinations.Add($destination)) {
            throw "Archive destination collision: $destination"
        }
        $destinationParent = Split-Path -Parent $destination
        $separatorChars = [char[]]@([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
        $repoRootPrefix = (Get-Item -LiteralPath $repoRoot).FullName.TrimEnd($separatorChars) + [System.IO.Path]::DirectorySeparatorChar
        $destinationRelative = $destination.Substring($repoRootPrefix.Length)
        $applied += "ARCHIVE $($operation.path) -> $destinationRelative"
        if (-not $DryRun) {
            New-Item -ItemType Directory -Force -Path $destinationParent | Out-Null
            Move-Item -LiteralPath $operation.source -Destination $destination
            $generatedOwnedFiles += $operation.path.Replace('\', '/')
            $generatedOwnedFiles += $destinationRelative.Replace('\', '/')
        }
    }
}
elseif ($Action -eq "Delete" -and $operations.Count -gt 0) {
    foreach ($operation in $operations) {
        $applied += "DELETE $($operation.path)"
        if (-not $DryRun) {
            Assert-PathInside -Path $operation.source -Root $repoRoot -Label "Delete target" | Out-Null
            Remove-Item -LiteralPath $operation.source -Force -Recurse:$operation.isDirectory
            $generatedOwnedFiles += $operation.path.Replace('\', '/')
        }
    }
}
elseif ($operations.Count -gt 0) {
    foreach ($operation in $operations) {
        $applied += "PLAN $($operation.path) [$($operation.action)] reason=$($operation.reason)"
    }
}

$candidateBlock = Format-CandidatesBlock -Candidates $candidates
$operationBlock = if ($applied.Count -eq 0) { "(none)" } else { ($applied -join "`n") }
$manifestText = if ([string]::IsNullOrWhiteSpace($Manifest)) { "(none)" } else { $Manifest }
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"

$content = @"
# Content Retirement Report

## Metadata

- Time: $now
- Action: $Action
- DryRun: $($DryRun.IsPresent)
- Reason: $Reason
- Manifest: $manifestText
- ArchiveRoot: $ArchiveRoot
- AllowSourceCode: $($AllowSourceCode.IsPresent)

## Auto Candidates

~~~text
$candidateBlock
~~~

## Planned / Applied Operations

~~~text
$operationBlock
~~~

## Safety Rules

- Archive/Delete requires an explicit manifest.
- Source-like paths require -AllowSourceCode.
- Protected paths such as env files, git metadata, compose files, and database migration resources are blocked.
- Directory delete requires allowRecursive=true in the manifest.
- All targets are resolved and checked inside the repository before move/delete.
"@

if ($DryRun) {
    Write-Host "DRY-RUN content retirement report:"
    Write-Host $content
}
else {
    [void](Write-HarnessFileIfChanged -Path $reportPath -Content $content)
    $generatedOwnedFiles += Get-HarnessRepoRelativePath -RepoRoot $repoRoot -Path $reportPath
}

Write-Host "Content retirement report: $reportPath" -ForegroundColor Green
if ($PassThruResult) {
    Write-Output ([pscustomobject]@{
        ReportPath = $reportPath
        OwnedFiles = @($generatedOwnedFiles | Sort-Object -Unique)
    })
}
else {
    Write-Output $reportPath
}
