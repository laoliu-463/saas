param()

$ErrorActionPreference = "Continue"
$harnessPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$reportPath = Join-Path $harnessPath "reports\latest-harness-limits-check.md"
$maxDirectFiles = 50
$maxDirectSubdirs = 50
$maxNonScriptLines = 200

$violations = @()

$rootDirs = Get-ChildItem -Path $harnessPath -Directory
$allowedDirs = @(
    'rules',
    'tasks',
    'probes',
    'reports',
    'scripts',
    'manifests',
    'archive',
    'templates',
    'engineering'
)
$extraDirs = @()
foreach ($d in $rootDirs) {
    if ($allowedDirs -notcontains $d.Name) {
        $extraDirs += $d
    }
}
if ($extraDirs.Count -gt 0) {
    $violations += @{ Type="Root Directory Limits"; Path=($extraDirs.Name -join ','); Issue="Found disallowed root dir"; Suggestion="Delete or merge" }
}

$allDirs = Get-ChildItem -Path $harnessPath -Recurse -Directory
$checkDirs = @($harnessPath) + @($allDirs | Select-Object -ExpandProperty FullName)

foreach ($dir in $checkDirs) {
    $subdirs = Get-ChildItem -Path $dir -Directory
    if ($subdirs.Count -gt $maxDirectSubdirs) {
        $violations += @{ Type="Directory Limits"; Path=$dir; Issue="Subdir count over $maxDirectSubdirs ($($subdirs.Count))"; Suggestion="Archive" }
    }

    $files = Get-ChildItem -Path $dir -File
    if ($files.Count -gt $maxDirectFiles) {
        $violations += @{ Type="File Limits"; Path=$dir; Issue="File count over $maxDirectFiles ($($files.Count))"; Suggestion="Archive" }
    }
}

$allNonScripts = Get-ChildItem -Path $harnessPath -Recurse -File | Where-Object { $_.Extension -match '^\.(md|txt|json|yaml|yml|csv)$' }
foreach ($file in $allNonScripts) {
    try {
        $lines = (Get-Content -Path $file.FullName -ErrorAction SilentlyContinue | Measure-Object -Line).Lines
        if ($lines -gt $maxNonScriptLines) {
            $violations += @{ Type="Line Limits"; Path=$file.FullName; Issue="Line count over $maxNonScriptLines ($lines)"; Suggestion="Split or truncate" }
        }
    } catch {}
}

$isPass = ($violations.Count -eq 0)

$content = "# Harness Limits Check`n`n"
$content += "## Active Limits`n"
$content += "- Direct files per directory: <= $maxDirectFiles`n"
$content += "- Direct subdirectories per directory: <= $maxDirectSubdirs`n"
$content += "- Non-script text lines per file: <= $maxNonScriptLines`n`n"
$content += "## Conclusion`n"
if ($isPass) {
    $content += "PASS`n`n"
} else {
    $content += "FAIL`n`n"
}

$content += "## Violations`n"
$content += "| Path | Issue | Suggestion |`n"
$content += "| --- | --- | --- |`n"

if ($violations.Count -gt 0) {
    foreach ($v in $violations) {
        $content += "| $($v.Path) | $($v.Issue) | $($v.Suggestion) |`n"
    }
} else {
    $content += "| None | None | None |`n"
}

$content += "`n## Next Steps`n"
$content += "Run this check after each task and during weekly or iteration-start cleanup reviews."

$content | Set-Content -Path $reportPath -Encoding UTF8 -Force

if ($isPass) {
    Write-Host "PASS"
} else {
    Write-Host "FAIL"
}
