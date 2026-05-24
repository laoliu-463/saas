# 本地前后端热更新启动（本机进程，非 Docker 内编译）
# 后端：Spring DevTools 类路径重启（3s 轮询 + 2s 静默，合并连续保存）
# 前端：Vite HMR
param(
    [int]$FrontendPort = 0,
    [int]$BackendPort = 0,
    [switch]$BackendOnly,
    [switch]$FrontendOnly
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Import-DotEnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $eq = $line.IndexOf("=")
        if ($eq -lt 1) { return }
        $name = $line.Substring(0, $eq).Trim()
        $value = $line.Substring($eq + 1).Trim()
        if ($name) { Set-Item -Path "Env:$name" -Value $value }
    }
}

foreach ($envFile in @(
    (Join-Path $RepoRoot ".env.local-dev"),
    (Join-Path $RepoRoot ".env")
)) {
    Import-DotEnvFile $envFile
}

if (-not $env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE = "local-mock" }
if (-not $env:SPRING_DEVTOOLS_RESTART_ENABLED) { $env:SPRING_DEVTOOLS_RESTART_ENABLED = "true" }
if (-not $env:SPRING_DEVTOOLS_POLL_INTERVAL) { $env:SPRING_DEVTOOLS_POLL_INTERVAL = "3s" }
if (-not $env:SPRING_DEVTOOLS_QUIET_PERIOD) { $env:SPRING_DEVTOOLS_QUIET_PERIOD = "2s" }

if ($FrontendPort -gt 0) { $env:VITE_DEV_PORT = "$FrontendPort" }
elseif (-not $env:VITE_DEV_PORT) { $env:VITE_DEV_PORT = "3000" }

if ($BackendPort -gt 0) { $backendBase = "http://localhost:$BackendPort" }
elseif ($env:VITE_PROXY_TARGET) { $backendBase = $env:VITE_PROXY_TARGET }
else { $backendBase = "http://localhost:8080" }
$env:VITE_PROXY_TARGET = $backendBase

function Start-DevWindow {
    param(
        [string]$Title,
        [string]$WorkingDirectory,
        [string[]]$CommandLines
    )
    $scriptPath = Join-Path $env:TEMP "colonel-saas-dev-$Title-$PID.ps1"
    $content = @(
        "`$ErrorActionPreference = 'Continue'",
        "Set-Location -LiteralPath '$WorkingDirectory'",
        "Write-Host '[dev:$Title] starting in' (Get-Location)",
        $CommandLines
    ) -join "`r`n"
    Set-Content -Path $scriptPath -Value $content -Encoding UTF8
    Start-Process powershell -ArgumentList @("-NoExit", "-NoProfile", "-File", $scriptPath) -WorkingDirectory $WorkingDirectory
    Write-Host "[dev] $Title window -> $WorkingDirectory"
}

Write-Host "==> Colonel SaaS local dev (hot reload)"
Write-Host "    Frontend: http://localhost:$($env:VITE_DEV_PORT)"
Write-Host "    Backend:  $backendBase/api"
Write-Host "    Disable backend hot restart: `$env:SPRING_DEVTOOLS_RESTART_ENABLED='false'"

if (-not $FrontendOnly) {
    $backendDir = Join-Path $RepoRoot "backend"
    Start-DevWindow -Title "backend" -WorkingDirectory $backendDir -CommandLines @(
        "`$env:SPRING_PROFILES_ACTIVE='$($env:SPRING_PROFILES_ACTIVE)'",
        "`$env:SPRING_DEVTOOLS_RESTART_ENABLED='$($env:SPRING_DEVTOOLS_RESTART_ENABLED)'",
        "`$env:SPRING_DEVTOOLS_POLL_INTERVAL='$($env:SPRING_DEVTOOLS_POLL_INTERVAL)'",
        "`$env:SPRING_DEVTOOLS_QUIET_PERIOD='$($env:SPRING_DEVTOOLS_QUIET_PERIOD)'",
        "mvn -DskipTests spring-boot:run `"-Dspring-boot.run.fork=true`""
    )
}

if (-not $BackendOnly) {
    $frontendDir = Join-Path $RepoRoot "frontend"
    Start-DevWindow -Title "frontend" -WorkingDirectory $frontendDir -CommandLines @(
        "`$env:VITE_DEV_PORT='$($env:VITE_DEV_PORT)'",
        "`$env:VITE_PROXY_TARGET='$backendBase'",
        "npm run dev"
    )
}
