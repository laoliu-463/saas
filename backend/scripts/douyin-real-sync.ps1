param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$Username = "",
    [string]$Password = "",
    [string]$AppId = "",
    [string]$GrantType = "",
    [string]$AuthorizationCode = "",
    [string]$RefreshToken = "",
    [switch]$SkipTokenCreate
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Load-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        return
    }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }
        $parts = $line -split "=", 2
        if ($parts.Length -ne 2) {
            return
        }
        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        $existing = [System.Environment]::GetEnvironmentVariable($name)
        if ([string]::IsNullOrWhiteSpace($existing)) {
            [System.Environment]::SetEnvironmentVariable($name, $value)
        }
    }
}

function To-JsonSafe {
    param([object]$Obj)
    return ($Obj | ConvertTo-Json -Depth 10 -Compress)
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [object]$Body = $null
    )

    $params = @{
        Method      = $Method
        Uri         = $Url
        Headers     = $Headers
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $params.Body = (To-JsonSafe -Obj $Body)
    }

    try {
        $resp = Invoke-WebRequest @params
        $raw = $resp.Content
        $json = $null
        if ($raw) {
            try { $json = $raw | ConvertFrom-Json } catch {}
        }
        return @{
            Ok         = $true
            StatusCode = [int]$resp.StatusCode
            Json       = $json
            Raw        = $raw
        }
    } catch {
        $http = $null
        if ($_.Exception -and $_.Exception.PSObject.Properties["Response"]) {
            $http = $_.Exception.Response
        }
        $statusCode = -1
        $raw = if ($_.Exception -and $_.Exception.Message) { $_.Exception.Message } else { "request failed" }
        if ($http) {
            try { $statusCode = [int]$http.StatusCode } catch {}
            try {
                $reader = New-Object System.IO.StreamReader($http.GetResponseStream())
                $raw = $reader.ReadToEnd()
            } catch {}
        }
        return @{
            Ok         = $false
            StatusCode = $statusCode
            Json       = $null
            Raw        = $raw
        }
    }
}

function Print-Step {
    param([string]$Name)
    Write-Host ""
    Write-Host ("==== " + $Name + " ====") -ForegroundColor Cyan
}

function Print-Result {
    param([string]$Name, [hashtable]$Resp)
    if ($Resp.Ok) {
        Write-Host ("[OK] " + $Name + " HTTP " + $Resp.StatusCode) -ForegroundColor Green
    } else {
        Write-Host ("[FAIL] " + $Name + " HTTP " + $Resp.StatusCode) -ForegroundColor Yellow
    }
    if ($Resp.Json) {
        $code = $Resp.Json.code
        $msg = $Resp.Json.msg
        if ($code -ne $null -or $msg) {
            Write-Host ("code=" + $code + " msg=" + $msg)
        }
    } elseif ($Resp.Raw) {
        Write-Host $Resp.Raw
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Load-DotEnv -Path (Join-Path $repoRoot ".env")

if (-not $Username) { $Username = if ($env:ADMIN_USERNAME) { $env:ADMIN_USERNAME } else { "admin" } }
if (-not $Password) { $Password = if ($env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD } else { "admin123" } }
if (-not $AppId) { $AppId = $env:DOUYIN_APP_ID }
if (-not $GrantType) {
    $GrantType = if ($env:DOUYIN_GRANT_TYPE) { $env:DOUYIN_GRANT_TYPE } else { "authorization_self" }
}
if (-not $AuthorizationCode) { $AuthorizationCode = $env:DOUYIN_AUTH_CODE }
if (-not $RefreshToken) { $RefreshToken = $env:DOUYIN_REFRESH_TOKEN }

if (-not $AppId) {
    throw "AppId is required. Set DOUYIN_APP_ID in .env or pass -AppId."
}

Print-Step "1) Login and get JWT"
$loginResp = Invoke-Api -Method "POST" -Url "$BaseUrl/auth/login" -Headers @{} -Body @{
    username = $Username
    password = $Password
}
Print-Result -Name "Login" -Resp $loginResp
if (-not $loginResp.Ok -or -not $loginResp.Json -or -not $loginResp.Json.data -or -not $loginResp.Json.data.token) {
    throw "Login failed. Stop."
}
$jwt = [string]$loginResp.Json.data.token
$headers = @{ Authorization = "Bearer $jwt" }

if (-not $SkipTokenCreate) {
    Print-Step "2) Create/refresh douyin token"
    if ($RefreshToken) {
        $tokenResp = Invoke-Api -Method "POST" -Url "$BaseUrl/douyin/token-refreshes" -Headers $headers -Body @{
            appId         = $AppId
            grantType     = "refresh_token"
            refreshToken  = $RefreshToken
        }
        Print-Result -Name "Token refresh" -Resp $tokenResp
    } else {
        if ($GrantType -eq "authorization_code" -and -not $AuthorizationCode) {
            throw "GrantType=authorization_code requires -AuthorizationCode (or DOUYIN_AUTH_CODE)."
        }
        $tokenBody = @{
            appId     = $AppId
            grantType = $GrantType
        }
        if ($AuthorizationCode) {
            $tokenBody.code = $AuthorizationCode
        }
        $tokenResp = Invoke-Api -Method "POST" -Url "$BaseUrl/douyin/tokens" -Headers $headers -Body $tokenBody
        Print-Result -Name "Token create" -Resp $tokenResp
    }
}

Print-Step "3) Query token status"
$statusResp = Invoke-Api -Method "GET" -Url "$BaseUrl/douyin/tokens?appId=$AppId" -Headers $headers
Print-Result -Name "Token status" -Resp $statusResp

Print-Step "4) Pull activities"
$activitiesResp = Invoke-Api -Method "GET" -Url "$BaseUrl/douyin/activities?appId=$AppId&page=1&pageSize=10" -Headers $headers
Print-Result -Name "Activities" -Resp $activitiesResp

Print-Step "5) Pull activity products"
$productsResp = Invoke-Api -Method "GET" -Url "$BaseUrl/douyin/activity-products?appId=$AppId&page=1&pageSize=10" -Headers $headers
Print-Result -Name "Activity products" -Resp $productsResp

$end = Get-Date
$start = $end.AddDays(-7)
$startText = $start.ToString("yyyy-MM-dd HH:mm:ss")
$endText = $end.ToString("yyyy-MM-dd HH:mm:ss")

Print-Step "6) Pull order settlements (last 7 days)"
$ordersUrl = "$BaseUrl/douyin/order-settlements?appId=$AppId&timeType=update&size=50&cursor=0&startTime=$([uri]::EscapeDataString($startText))&endTime=$([uri]::EscapeDataString($endText))"
$ordersResp = Invoke-Api -Method "GET" -Url $ordersUrl -Headers $headers
Print-Result -Name "Order settlements" -Resp $ordersResp

Print-Step "7) Trigger manual order sync"
$syncResp = Invoke-Api -Method "POST" -Url "$BaseUrl/order-sync-jobs" -Headers $headers
Print-Result -Name "Manual order sync" -Resp $syncResp

Write-Host ""
Write-Host "Done. If any step failed, check response msg/log_id and retry with a fresh auth code." -ForegroundColor Green
