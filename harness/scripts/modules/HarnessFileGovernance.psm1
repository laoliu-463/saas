$ErrorActionPreference = 'Stop'

$script:AllowedRootDirs = @(
    'rules', 'tasks', 'probes', 'reports', 'scripts', 'manifests', 'archive', 'templates', 'engineering',
    'src', 'contracts', 'state', 'tests'
)
$script:TextExtensions = @('.md', '.txt', '.json', '.yaml', '.yml', '.csv')
$script:ScriptExtensions = @('.ps1', '.psm1', '.psd1', '.sh', '.py', '.js', '.ts', '.tsx', '.jsx', '.java', '.kt')

function ConvertTo-GovernancePath {
    param([Parameter(Mandatory = $true)][string]$Path)

    return $Path.Replace('\', '/').TrimStart('./')
}

function Get-RepoRelativeGovernancePath {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$FullPath
    )

    # Get-Item expands Windows 8.3 path segments consistently. Resolve-Path may
    # keep a short repo root while Get-ChildItem returns long child paths.
    $root = (Get-Item -LiteralPath $RepoRoot).FullName.TrimEnd('\')
    $resolved = (Get-Item -LiteralPath $FullPath).FullName
    $prefix = $root + '\'
    if (-not $resolved.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside repository: $resolved"
    }
    return ConvertTo-GovernancePath -Path $resolved.Substring($prefix.Length)
}

function Test-IsGovernanceTextFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    $extension = [System.IO.Path]::GetExtension($Path).ToLowerInvariant()
    return ($script:TextExtensions -contains $extension) -and -not ($script:ScriptExtensions -contains $extension)
}

function Test-IsTextLineBudgetExempt {
    param([Parameter(Mandatory = $true)][string]$Path)

    return (ConvertTo-GovernancePath -Path $Path) -eq 'harness/package-lock.json'
}

function Test-IsGeneratedDependencyPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    $normalized = ConvertTo-GovernancePath -Path $Path
    return $normalized.StartsWith('harness/node_modules/', [System.StringComparison]::OrdinalIgnoreCase)
}

function Get-TrackedGeneratedDependencyPaths {
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    $trackedPaths = @(& git -C $RepoRoot -c core.quotepath=false ls-files 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw 'Cannot inspect the Git index for tracked Harness dependencies.'
    }
    return @(
        $trackedPaths |
            ForEach-Object { ConvertTo-GovernancePath -Path $_ } |
            Where-Object { Test-IsGeneratedDependencyPath -Path $_ } |
            Sort-Object -Unique
    )
}

function New-DirectoryMetricsFromPaths {
    param([Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Paths)

    $fileCounts = @{}
    $subdirSets = @{}
    foreach ($rawPath in $Paths) {
        $path = ConvertTo-GovernancePath -Path $rawPath
        $parts = @($path -split '/')
        if ($parts.Count -lt 2 -or $parts[0] -ne 'harness') {
            continue
        }

        $parent = ($parts[0..($parts.Count - 2)] -join '/')
        if (-not $fileCounts.ContainsKey($parent)) { $fileCounts[$parent] = 0 }
        $fileCounts[$parent]++

        for ($index = 1; $index -le $parts.Count - 2; $index++) {
            $parentDir = ($parts[0..($index - 1)] -join '/')
            $childDir = ($parts[0..$index] -join '/')
            if (-not $subdirSets.ContainsKey($parentDir)) {
                $subdirSets[$parentDir] = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
            }
            [void]$subdirSets[$parentDir].Add($childDir)
            if (-not $fileCounts.ContainsKey($childDir)) { $fileCounts[$childDir] = 0 }
        }
    }

    $keys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($key in $fileCounts.Keys) { [void]$keys.Add($key) }
    foreach ($key in $subdirSets.Keys) { [void]$keys.Add($key) }

    $metrics = @{}
    foreach ($key in $keys) {
        $metrics[$key] = [pscustomobject]@{
            Path = $key
            DirectFiles = if ($fileCounts.ContainsKey($key)) { [int]$fileCounts[$key] } else { 0 }
            DirectSubdirs = if ($subdirSets.ContainsKey($key)) { [int]$subdirSets[$key].Count } else { 0 }
        }
    }
    return $metrics
}

function New-SnapshotFromPaths {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Paths,
        [hashtable]$TextLineCounts = @{},
        [hashtable]$BlobIds = @{}
    )

    $normalizedPaths = @($Paths | ForEach-Object { ConvertTo-GovernancePath -Path $_ } | Sort-Object -Unique)
    $rootDirs = @(
        $normalizedPaths |
            Where-Object { $_ -like 'harness/*' -and @($_ -split '/').Count -ge 3 } |
            ForEach-Object { ($_ -split '/')[1] } |
            Sort-Object -Unique
    )
    return [pscustomobject]@{
        Files = $normalizedPaths
        DirectoryMetrics = New-DirectoryMetricsFromPaths -Paths $normalizedPaths
        TextLineCounts = $TextLineCounts
        BlobIds = $BlobIds
        RootDirectories = $rootDirs
    }
}

function Get-HarnessFileSnapshot {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    $harnessRoot = Join-Path $RepoRoot 'harness'
    if (-not (Test-Path -LiteralPath $harnessRoot -PathType Container)) {
        throw "Harness directory not found: $harnessRoot"
    }

    $files = @(Get-ChildItem -LiteralPath $harnessRoot -Recurse -File -Force)
    $paths = @()
    $lineCounts = @{}
    foreach ($file in $files) {
        $relative = Get-RepoRelativeGovernancePath -RepoRoot $RepoRoot -FullPath $file.FullName
        if (Test-IsGeneratedDependencyPath -Path $relative) { continue }
        $paths += $relative
        if ((Test-IsGovernanceTextFile -Path $relative) -and -not (Test-IsTextLineBudgetExempt -Path $relative)) {
            $lineCounts[$relative] = (Get-Content -LiteralPath $file.FullName | Measure-Object -Line).Lines
        }
    }
    return New-SnapshotFromPaths -Paths $paths -TextLineCounts $lineCounts
}

function Get-HarnessBaselineSnapshot {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$BaselineRef
    )

    $treeLines = @(& git -C $RepoRoot -c core.quotepath=false ls-tree -r $BaselineRef -- harness 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw "Cannot read Harness baseline ref: $BaselineRef"
    }

    $paths = @()
    $blobIds = @{}
    foreach ($line in $treeLines) {
        $tabIndex = $line.IndexOf("`t")
        if ($tabIndex -lt 0) { continue }
        $metadata = @($line.Substring(0, $tabIndex) -split '\s+')
        if ($metadata.Count -lt 3) { continue }
        $path = ConvertTo-GovernancePath -Path $line.Substring($tabIndex + 1)
        $paths += $path
        $blobIds[$path] = $metadata[2]
    }
    return New-SnapshotFromPaths -Paths $paths -BlobIds $blobIds
}

function Test-IsUnchangedLegacyArchiveBlob {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$BaselineSnapshot
    )

    if ($Path -notlike 'harness/archive/*') { return $false }
    if ($BaselineSnapshot.PSObject.Properties.Name -notcontains 'BlobIds') { return $false }
    if ($BaselineSnapshot.BlobIds.Count -eq 0) { return $false }

    $fullPath = Join-Path $RepoRoot $Path.Replace('/', '\')
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { return $false }
    $blobOutput = @(& git -C $RepoRoot hash-object -- $fullPath 2>$null)
    $hashExitCode = $LASTEXITCODE
    $blobId = if ($blobOutput.Count -gt 0) { $blobOutput[0] } else { '' }
    if ($hashExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($blobId)) {
        throw "Cannot hash archived Harness file: $Path"
    }
    return @($BaselineSnapshot.BlobIds.Values) -contains $blobId.Trim()
}

function New-GovernanceFinding {
    param(
        [string]$Code,
        [string]$Path,
        [string]$Message
    )

    return [pscustomobject]@{ Code = $Code; Path = $Path; Message = $Message }
}

function Get-DirectoryBudget {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ($Path -eq 'harness/reports') {
        return [pscustomobject]@{ FileWarning = 16; FileMax = 20; SubdirWarning = 40; SubdirMax = 50 }
    }
    return [pscustomobject]@{ FileWarning = 40; FileMax = 50; SubdirWarning = 40; SubdirMax = 50 }
}

function Compare-HarnessFileGovernance {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)]$CurrentSnapshot,
        [Parameter(Mandatory = $true)]$BaselineSnapshot,
        [AllowEmptyCollection()][string[]]$OwnedFiles = @()
    )

    $normalizedOwned = @($OwnedFiles | ForEach-Object { ConvertTo-GovernancePath -Path $_ } | Sort-Object -Unique)
    $taskPaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($path in $BaselineSnapshot.Files) { [void]$taskPaths.Add($path) }
    foreach ($path in $normalizedOwned) {
        $fullPath = Join-Path $RepoRoot $path.Replace('/', '\')
        if (Test-Path -LiteralPath $fullPath -PathType Leaf) {
            [void]$taskPaths.Add($path)
        }
        else {
            [void]$taskPaths.Remove($path)
        }
    }
    $taskSnapshot = New-SnapshotFromPaths -Paths @($taskPaths)

    $violations = @()
    $warnings = @()
    $historicalDebt = @()

    foreach ($path in @(Get-TrackedGeneratedDependencyPaths -RepoRoot $RepoRoot)) {
        $violations += New-GovernanceFinding `
            -Code 'TRACKED_GENERATED_DEPENDENCY' `
            -Path $path `
            -Message 'Generated Harness node_modules files must not be staged or tracked.'
    }

    foreach ($rootDir in $taskSnapshot.RootDirectories) {
        if ($script:AllowedRootDirs -notcontains $rootDir) {
            if ($BaselineSnapshot.RootDirectories -notcontains $rootDir) {
                $violations += New-GovernanceFinding -Code 'ROOT_DIRECTORY_NOT_ALLOWED' -Path "harness/$rootDir" -Message 'Root directory is not whitelisted.'
            }
            else {
                $historicalDebt += New-GovernanceFinding -Code 'ROOT_DIRECTORY_NOT_ALLOWED' -Path "harness/$rootDir" -Message 'Pre-existing non-whitelisted root directory.'
            }
        }
    }

    $directoryKeys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($key in $BaselineSnapshot.DirectoryMetrics.Keys) { [void]$directoryKeys.Add($key) }
    foreach ($key in $taskSnapshot.DirectoryMetrics.Keys) { [void]$directoryKeys.Add($key) }
    foreach ($path in $directoryKeys) {
        $baseline = if ($BaselineSnapshot.DirectoryMetrics.ContainsKey($path)) { $BaselineSnapshot.DirectoryMetrics[$path] } else { [pscustomobject]@{ DirectFiles = 0; DirectSubdirs = 0 } }
        $current = if ($taskSnapshot.DirectoryMetrics.ContainsKey($path)) { $taskSnapshot.DirectoryMetrics[$path] } else { [pscustomobject]@{ DirectFiles = 0; DirectSubdirs = 0 } }
        $budget = Get-DirectoryBudget -Path $path

        if ($current.DirectFiles -gt $budget.FileMax) {
            if ($baseline.DirectFiles -gt $budget.FileMax -and $current.DirectFiles -le $baseline.DirectFiles) {
                $historicalDebt += New-GovernanceFinding -Code 'DIRECT_FILE_COUNT_EXCEEDED' -Path $path -Message "Pre-existing file count $($current.DirectFiles) exceeds $($budget.FileMax)."
            }
            elseif ($baseline.DirectFiles -gt $budget.FileMax) {
                $violations += New-GovernanceFinding -Code 'DIRECT_FILE_COUNT_WORSENED' -Path $path -Message "File count increased from $($baseline.DirectFiles) to $($current.DirectFiles)."
            }
            else {
                $violations += New-GovernanceFinding -Code 'DIRECT_FILE_COUNT_EXCEEDED' -Path $path -Message "File count $($current.DirectFiles) exceeds $($budget.FileMax)."
            }
        }
        elseif ($current.DirectFiles -ge $budget.FileWarning -and $current.DirectFiles -gt $baseline.DirectFiles) {
            $warnings += New-GovernanceFinding -Code 'DIRECT_FILE_COUNT_WARNING' -Path $path -Message "File count reached warning level $($current.DirectFiles)."
        }

        if ($current.DirectSubdirs -gt $budget.SubdirMax) {
            if ($baseline.DirectSubdirs -gt $budget.SubdirMax -and $current.DirectSubdirs -le $baseline.DirectSubdirs) {
                $historicalDebt += New-GovernanceFinding -Code 'DIRECT_SUBDIR_COUNT_EXCEEDED' -Path $path -Message "Pre-existing subdir count $($current.DirectSubdirs) exceeds $($budget.SubdirMax)."
            }
            elseif ($baseline.DirectSubdirs -gt $budget.SubdirMax) {
                $violations += New-GovernanceFinding -Code 'DIRECT_SUBDIR_COUNT_WORSENED' -Path $path -Message "Subdir count increased from $($baseline.DirectSubdirs) to $($current.DirectSubdirs)."
            }
            else {
                $violations += New-GovernanceFinding -Code 'DIRECT_SUBDIR_COUNT_EXCEEDED' -Path $path -Message "Subdir count $($current.DirectSubdirs) exceeds $($budget.SubdirMax)."
            }
        }
        elseif ($current.DirectSubdirs -ge $budget.SubdirWarning -and $current.DirectSubdirs -gt $baseline.DirectSubdirs) {
            $warnings += New-GovernanceFinding -Code 'DIRECT_SUBDIR_COUNT_WARNING' -Path $path -Message "Subdir count reached warning level $($current.DirectSubdirs)."
        }
    }

    foreach ($path in $normalizedOwned) {
        $fullPath = Join-Path $RepoRoot $path.Replace('/', '\')
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { continue }

        if ($path -match '^harness/reports/(evidence|retro|content-retire)-\d{8}') {
            if ($BaselineSnapshot.Files -notcontains $path) {
                $violations += New-GovernanceFinding -Code 'TIMESTAMP_REPORT_IN_ROOT' -Path $path -Message 'New timestamp reports are forbidden in reports root.'
            }
        }

        if ((Test-IsGovernanceTextFile -Path $path) -and -not (Test-IsTextLineBudgetExempt -Path $path)) {
            $lines = (Get-Content -LiteralPath $fullPath | Measure-Object -Line).Lines
            if ($lines -gt 200) {
                if (-not (Test-IsUnchangedLegacyArchiveBlob -RepoRoot $RepoRoot -Path $path -BaselineSnapshot $BaselineSnapshot)) {
                    $violations += New-GovernanceFinding -Code 'TEXT_LINE_COUNT_EXCEEDED' -Path $path -Message "Text line count $lines exceeds 200."
                }
            }
            elseif ($lines -ge 160) {
                $warnings += New-GovernanceFinding -Code 'TEXT_LINE_COUNT_WARNING' -Path $path -Message "Text line count reached warning level $lines."
            }
        }
    }

    foreach ($path in $CurrentSnapshot.DirectoryMetrics.Keys) {
        $metric = $CurrentSnapshot.DirectoryMetrics[$path]
        $budget = Get-DirectoryBudget -Path $path
        if ($metric.DirectFiles -gt $budget.FileMax) {
            $historicalDebt += New-GovernanceFinding -Code 'DIRECT_FILE_COUNT_EXCEEDED' -Path $path -Message "Repository file count $($metric.DirectFiles) exceeds $($budget.FileMax)."
        }
        if ($metric.DirectSubdirs -gt $budget.SubdirMax) {
            $historicalDebt += New-GovernanceFinding -Code 'DIRECT_SUBDIR_COUNT_EXCEEDED' -Path $path -Message "Repository subdir count $($metric.DirectSubdirs) exceeds $($budget.SubdirMax)."
        }
    }
    foreach ($path in $CurrentSnapshot.TextLineCounts.Keys) {
        if ($CurrentSnapshot.TextLineCounts[$path] -gt 200) {
            if (-not (Test-IsUnchangedLegacyArchiveBlob -RepoRoot $RepoRoot -Path $path -BaselineSnapshot $BaselineSnapshot)) {
                $historicalDebt += New-GovernanceFinding -Code 'TEXT_LINE_COUNT_EXCEEDED' -Path $path -Message "Repository text line count $($CurrentSnapshot.TextLineCounts[$path]) exceeds 200."
            }
        }
    }

    $taskGate = if ($violations.Count -gt 0) { 'FAIL' } else { 'PASS' }
    $repositoryHealth = if ($historicalDebt.Count -gt 0 -or $warnings.Count -gt 0) { 'PARTIAL' } else { 'PASS' }
    return [pscustomobject]@{
        TaskGate = $taskGate
        RepositoryHealth = $repositoryHealth
        Violations = @($violations | Sort-Object Code, Path -Unique)
        Warnings = @($warnings | Sort-Object Code, Path -Unique)
        HistoricalDebt = @($historicalDebt | Sort-Object Code, Path -Unique)
    }
}

function Format-HarnessGovernanceReport {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)]$Result)

    $lines = @(
        '# Harness File Governance Check',
        '',
        '## Status',
        '',
        "- TASK_GATE=$($Result.TaskGate)",
        "- REPOSITORY_HEALTH=$($Result.RepositoryHealth)",
        '',
        '## Active Budgets',
        '',
        '- Active directories: warning 40, hard limit 50.',
        '- Reports root: target hard limit 20; pre-existing debt uses no-regression.',
        '- Non-script text: warning 160 lines, hard limit 200.',
        '',
        '## Task Violations'
    )
    if ($Result.Violations.Count -eq 0) { $lines += '- None' }
    foreach ($finding in $Result.Violations) { $lines += "- [$($finding.Code)] $($finding.Path): $($finding.Message)" }
    $lines += @('', '## Warnings')
    if ($Result.Warnings.Count -eq 0) { $lines += '- None' }
    foreach ($finding in $Result.Warnings) { $lines += "- [$($finding.Code)] $($finding.Path): $($finding.Message)" }
    $lines += @('', '## Historical Debt')
    if ($Result.HistoricalDebt.Count -eq 0) { $lines += '- None' }
    foreach ($finding in $Result.HistoricalDebt) { $lines += "- [$($finding.Code)] $($finding.Path): $($finding.Message)" }
    return ($lines -join "`n") + "`n"
}

Export-ModuleMember -Function @(
    'Get-HarnessFileSnapshot',
    'Get-HarnessBaselineSnapshot',
    'Compare-HarnessFileGovernance',
    'Format-HarnessGovernanceReport'
)
