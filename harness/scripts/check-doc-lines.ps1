# Harness Document Line Count Checker
# Usage: powershell -ExecutionPolicy Bypass -File harness/scripts/check-doc-lines.ps1
# Exit 0 = PASS, Exit 1 = FAIL (violations found)

$limit = 200
$files = Get-ChildItem -Path "harness" -Recurse -Filter "*.md" |
  Where-Object { $_.FullName -notmatch "\\harness\\archive\\" -and $_.FullName -notmatch "\\harness\\reports\\archive\\" }

$violations = @()

foreach ($file in $files) {
  $lines = (Get-Content $file.FullName).Count
  if ($lines -gt $limit) {
    $violations += [PSCustomObject]@{
      Path  = $file.FullName.Replace((Get-Location).Path + "\", "")
      Lines = $lines
    }
  }
}

if ($violations.Count -gt 0) {
  Write-Host "FAIL: $($violations.Count) file(s) exceed $limit lines:" -ForegroundColor Red
  $violations | Format-Table -AutoSize
  exit 1
}

Write-Host "PASS: all current harness markdown files <= $limit lines" -ForegroundColor Green
exit 0
