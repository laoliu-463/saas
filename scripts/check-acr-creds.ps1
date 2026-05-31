$cfg = Get-Content "$env:USERPROFILE\.docker\config.json" | ConvertFrom-Json
$registry = "crpi-5yw4kk2bxbk3nj6k.cn-hangzhou.personal.cr.aliyuncs.com"
if ($cfg.auths.$registry) {
    $auth = $cfg.auths.$registry.PSObject.Properties | Where-Object { $_.Name -eq 'auth' } | Select-Object -ExpandProperty Value
    if ($auth) {
        $decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($auth))
        Write-Output "Auth found: $decoded"
    } else {
        Write-Output "Auth is empty (using credential helper)"
    }
} else {
    Write-Output "No entry for registry"
}

# Try to get credentials from Windows Credential Manager
$out = cmdkey /list:"$registry" 2>&1
Write-Output "CMDKEY output:"
Write-Output $out
