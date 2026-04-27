param(
    [string]$Root = "."
)

$ErrorActionPreference = "Stop"

$extensions = @(".md", ".java", ".yml", ".yaml", ".sql", ".ts", ".tsx", ".js", ".jsx", ".vue", ".json", ".ps1", ".sh")
$skipPatterns = @("\\target\\", "\\dist\\", "\\node_modules\\", "\\.git\\", "\\out\\")

$files = Get-ChildItem -Path $Root -Recurse -File | Where-Object {
    $extensions -contains $_.Extension -and
    -not ($skipPatterns | Where-Object { $_ -and $_ -as [string] -and $_ -match [regex]::Escape($_) })
}

$issues = @()

foreach ($file in $files) {
    $path = $file.FullName
    if ($skipPatterns | Where-Object { $path -match $_ }) {
        continue
    }

    $bytes = [System.IO.File]::ReadAllBytes($path)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191
    if ($hasBom) {
        $issues += [pscustomobject]@{
            Type = "BOM"
            File = $path
            Detail = "UTF-8 BOM detected"
        }
    }

    $text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    if ($text.Contains("`r`n")) {
        $issues += [pscustomobject]@{
            Type = "CRLF"
            File = $path
            Detail = "CRLF line ending detected"
        }
    }
}

if ($issues.Count -eq 0) {
    Write-Host "Encoding check passed: no BOM or CRLF issues found." -ForegroundColor Green
    exit 0
}

$issues | Format-Table -AutoSize
Write-Error "Encoding check failed: found $($issues.Count) issue(s)."
