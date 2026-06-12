param()

$ErrorActionPreference = "Continue"
$harnessPath = "D:\Projects\SAAS\harness"
$reportPath = "$harnessPath\reports\latest-harness-limits-check.md"

$violations = @()

$rootDirs = Get-ChildItem -Path $harnessPath -Directory
$allowedDirs = @('rules', 'tasks', 'probes', 'reports', 'scripts', 'manifests', 'archive', 'templates')
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
    if ($subdirs.Count -gt 10) {
        $violations += @{ Type="Directory Limits"; Path=$dir; Issue="Subdir count over 10 ($($subdirs.Count))"; Suggestion="Archive" }
    }

    $files = Get-ChildItem -Path $dir -File
    if ($files.Count -gt 10) {
        $violations += @{ Type="File Limits"; Path=$dir; Issue="File count over 10 ($($files.Count))"; Suggestion="Archive" }
    }
}

$allNonScripts = Get-ChildItem -Path $harnessPath -Recurse -File | Where-Object { $_.Extension -match '^\.(md|txt|json|yaml|yml|csv)$' }
foreach ($file in $allNonScripts) {
    try {
        $lines = (Get-Content -Path $file.FullName -ErrorAction SilentlyContinue | Measure-Object -Line).Lines
        if ($lines -gt 200) {
            $violations += @{ Type="Line Limits"; Path=$file.FullName; Issue="Line count over 200 ($lines)"; Suggestion="Split or truncate" }
        }
    } catch {}
}

$isPass = ($violations.Count -eq 0)

$content = "# Harness Limits Check`n`n"
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
$content += "Please maintain the harness folders according to the check results."

$content | Set-Content -Path $reportPath -Encoding UTF8 -Force

if ($isPass) {
    Write-Host "PASS"
} else {
    Write-Host "FAIL"
}
