# Frontend View API Alignment Audit (DDD boundary check)
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

foreach ($d in $domainMap.Keys) {
    $dir = Join-Path $path $d
    if (-not (Test-Path $dir)) { continue }
    Write-Host "[domain: $d]"
    $allFiles = Get-ChildItem -Path $dir -Recurse -Include *.vue,*.ts
    $apiCounts = @{}
    foreach ($f in $allFiles) {
        $lines = Select-String -Path $f.FullName -Pattern "^import.*from.*api/([a-zA-Z]+)"
        foreach ($line in $lines) {
            $apiName = $line.Matches[0].Groups[1].Value
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