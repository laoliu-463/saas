param(
    [string]$RepoRoot = '',
    [string]$BaselineRef = 'HEAD',
    [AllowEmptyCollection()][string[]]$OwnedFiles = @(),
    [switch]$NoReport
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
}
else {
    $RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
}

$modulePath = Join-Path $PSScriptRoot 'modules\HarnessFileGovernance.psm1'
Import-Module $modulePath -Force

$expandedOwnedFiles = @()
foreach ($ownedFile in $OwnedFiles) {
    if ([string]::IsNullOrWhiteSpace($ownedFile)) { continue }
    $expandedOwnedFiles += @($ownedFile -split ';' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}
$OwnedFiles = @($expandedOwnedFiles | Sort-Object -Unique)

if ($OwnedFiles.Count -eq 0) {
    $statusLines = @(& git -C $RepoRoot -c core.quotepath=false status --porcelain=v1 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'git status failed while deriving owned files.' }
    $derived = @()
    foreach ($line in $statusLines) {
        if ($line.Length -lt 4) { continue }
        $rawPath = $line.Substring(3).Trim()
        $candidates = if ($rawPath -match '\s+->\s+') {
            @($rawPath -split '\s+->\s+' | ForEach-Object { $_.Trim().Trim('"') })
        }
        else {
            @($rawPath.Trim('"'))
        }

        foreach ($candidate in $candidates) {
            $normalized = $candidate.Replace('\', '/')
            if ($normalized -notlike 'harness/*') { continue }
            $fullPath = Join-Path $RepoRoot $normalized.Replace('/', '\')
            if (Test-Path -LiteralPath $fullPath -PathType Container) {
                $canonicalRoot = (Get-Item -LiteralPath $RepoRoot).FullName.TrimEnd('\') + '\'
                foreach ($file in @(Get-ChildItem -LiteralPath $fullPath -Recurse -File -Force)) {
                    $derived += $file.FullName.Substring($canonicalRoot.Length).Replace('\', '/')
                }
            }
            else {
                $derived += $normalized
            }
        }
    }
    $OwnedFiles = @($derived | Sort-Object -Unique)
}

$current = Get-HarnessFileSnapshot -RepoRoot $RepoRoot
$baseline = Get-HarnessBaselineSnapshot -RepoRoot $RepoRoot -BaselineRef $BaselineRef
$result = Compare-HarnessFileGovernance -RepoRoot $RepoRoot -CurrentSnapshot $current -BaselineSnapshot $baseline -OwnedFiles $OwnedFiles
$content = Format-HarnessGovernanceReport -Result $result

Write-Output $content

if (-not $NoReport) {
    $reportDir = Join-Path $RepoRoot 'harness\reports\current'
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
    $reportPath = Join-Path $reportDir 'latest-harness-limits-check.md'
    $existing = if (Test-Path -LiteralPath $reportPath) { Get-Content -Raw -LiteralPath $reportPath } else { '' }
    if ($existing -ne $content) {
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($reportPath, $content, $utf8NoBom)
    }
}

if ($result.TaskGate -eq 'FAIL') { exit 1 }
exit 0
