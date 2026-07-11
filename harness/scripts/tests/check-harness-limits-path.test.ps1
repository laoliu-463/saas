$ErrorActionPreference = "Stop"

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

$sourceScript = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\check-harness-limits.ps1")).Path
$tempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd("\") + "\"
$tempRoot = Join-Path $tempBase ("saas-harness-limit-test-" + [guid]::NewGuid().ToString("N"))
$tempHarness = Join-Path $tempRoot "harness"
$tempScripts = Join-Path $tempHarness "scripts"
$tempReports = Join-Path $tempHarness "reports"
$tempScript = Join-Path $tempScripts "check-harness-limits.ps1"
$tempReport = Join-Path $tempReports "latest-harness-limits-check.md"

try {
    New-Item -ItemType Directory -Force -Path $tempScripts, $tempReports | Out-Null
    Copy-Item -LiteralPath $sourceScript -Destination $tempScript

    1..51 | ForEach-Object {
        [System.IO.File]::WriteAllText((Join-Path $tempReports ("fixture-{0:D2}.md" -f $_)), "fixture")
    }

    $output = (& powershell -NoProfile -ExecutionPolicy Bypass -File $tempScript 2>&1 | Out-String).Trim()

    Assert-True -Condition (Test-Path -LiteralPath $tempReport) -Message "The limits report must be written beside the invoked script's harness root. Output: $output"

    $reportContent = Get-Content -LiteralPath $tempReport -Raw
    Assert-True -Condition ($output -match "FAIL") -Message "A temporary harness with 51 direct report files must fail. Output: $output"
    Assert-True -Condition ($reportContent -match "## Conclusion\s+FAIL") -Message "The temporary harness report must record FAIL."
    Assert-True -Condition ($reportContent -match "File count over 50 \(51\)") -Message "The report must identify the temporary directory's 51-file overflow."

    Write-Host "check-harness-limits worktree path test passed" -ForegroundColor Green
}
finally {
    if (Test-Path -LiteralPath $tempRoot) {
        $resolvedTempRoot = (Resolve-Path -LiteralPath $tempRoot).Path
        $isExpectedTempPath = $resolvedTempRoot.StartsWith($tempBase, [System.StringComparison]::OrdinalIgnoreCase) `
            -and (Split-Path -Leaf $resolvedTempRoot).StartsWith("saas-harness-limit-test-", [System.StringComparison]::OrdinalIgnoreCase)
        if (-not $isExpectedTempPath) {
            throw "Refusing to remove unexpected test path: $resolvedTempRoot"
        }
        Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force
    }
}
