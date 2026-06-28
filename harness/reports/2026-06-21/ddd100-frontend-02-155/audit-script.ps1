# Frontend Hardcoded Business Rule Audit
$path = "D:\Projects\SAAS\frontend\src"

Write-Host "=== 1. Hardcoded permission strings ==="
$permMatches = Get-ChildItem -Path $path -Recurse -Include *.ts,*.vue |
    Select-String -Pattern 'permission[\\":\\s]+(ROLE_|PERM_|ADMIN|CHANNEL|BIZ_|SAMPLE_|TALENT_)'
Write-Host "Count: $($permMatches.Count)"

Write-Host "=== 2. role_code references (central RBAC) ==="
$roleMatches = Get-ChildItem -Path $path -Recurse -Include *.ts,*.vue |
    Select-String -Pattern 'role_code|roleCode'
Write-Host "Total role_code refs: $($roleMatches.Count)"

Write-Host "=== 3. Hardcoded status numbers in views/ ==="
$statusMatches = Get-ChildItem -Path "$path\views" -Recurse -Include *.vue,*.ts |
    Select-String -Pattern '(status|state)[\\":\\s]*=*[\\":\\s]*[0-9]'
Write-Host "Count: $($statusMatches.Count)"

Write-Host "=== 4. selectAll hardcoded ==="
$selMatches = Get-ChildItem -Path $path -Recurse -Include *.ts,*.vue |
    Select-String -Pattern 'selectAll|SELECT_ALL|select_all'
Write-Host "Count: $($selMatches.Count)"
