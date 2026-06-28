$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$path = "D:\Projects\SAAS\frontend\src\views"
$domainMap = @{
    "orders"    = "order"
    "dashboard" = "dashboard"
    "data"      = "data"
    "ops"       = "ops"
    "product"   = "product"
    "sample"    = "sample"
    "talent"    = "talent"
    "system"    = "sys"
    "profile"   = "sys"
}

Write-Host "=== Frontend View API Alignment Audit ==="
Write-Host ""

# Use line-by-line parsing to handle multi-line imports
foreach ($d in $domainMap.Keys) {
    $dir = Join-Path $path $d
    if (-not (Test-Path $dir)) { continue }
    Write-Host "[domain: $d]"
    $allFiles = Get-ChildItem -Path $dir -Recurse -Include *.vue,*.ts
    $apiCounts = @{}

    foreach ($f in $allFiles) {
        $content = Get-Content $f.FullName -Raw
        # Match imports from api/ - both single-line and multi-line
        $regex = 'import\s+(?:\*\s+as\s+\w+|{[^}]+}|\w+)\s+from\s+["\047][^"\047]*api/([a-zA-Z]+)["\047]'
        $matches = [regex]::Matches($content, $regex)
        foreach ($m in $matches) {
            $apiName = $m.Groups[1].Value
            if ($apiCounts.ContainsKey($apiName)) {
                $apiCounts[$apiName]++
            } else {
                $apiCounts[$apiName] = 1
            }
        }
    }

    $sorted = $apiCounts.GetEnumerator() | Sort-Object Value -Descending
    foreach ($entry in $sorted) {
        $marker = if ($entry.Key -ne $domainMap[$d]) { " [cross-domain]" } else { "" }
        Write-Host ("  api/{0,-20} {1,3} imports{2}" -f $entry.Key, $entry.Value, $marker)
    }
    Write-Host ""
}