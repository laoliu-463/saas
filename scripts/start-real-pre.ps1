param(
    [string]$EnvFile = ".env.real-pre",
    [int]$BackendPort = 8081,
    [int]$FrontendPort = 3001,
    [switch]$ForceRestartFrontend
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $scriptPath = $PSCommandPath
    if (-not $scriptPath) {
        $scriptPath = $MyInvocation.PSCommandPath
    }
    if (-not $scriptPath) {
        throw "Unable to resolve script path."
    }
    $scriptDir = Split-Path -Parent $scriptPath
    return (Resolve-Path (Join-Path $scriptDir "..")).Path
}

function Read-EnvFile {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Env file not found: $Path"
    }

    $map = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
            continue
        }

        $parts = $trimmed -split "=", 2
        if ($parts.Count -ne 2) {
            continue
        }

        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        $map[$key] = $value
    }

    return $map
}

function Assert-RequiredEnv {
    param(
        [hashtable]$EnvMap,
        [string[]]$Keys
    )

    $missing = @()
    foreach ($key in $Keys) {
        if (-not $EnvMap.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($EnvMap[$key])) {
            $missing += $key
        }
    }

    if ($missing.Count -gt 0) {
        throw "Missing required env values in ${EnvFile}: $($missing -join ', ')"
    }
}

function Test-PortFree {
    param([int]$Port)

    $listening = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return $null -eq $listening
}

function Start-ManagedProcess {
    param(
        [string]$Name,
        [string]$WorkDir,
        [hashtable]$EnvMap,
        [string]$Command,
        [string]$StdOut,
        [string]$StdErr,
        [string]$PidFile
    )

    $envAssignments = @()
    foreach ($key in $EnvMap.Keys) {
        $escapedValue = $EnvMap[$key].Replace("'", "''")
        $envAssignments += "`$env:$key='$escapedValue'"
    }

    $psScript = @(
        '$ErrorActionPreference = ''Stop'''
        "Set-Location '$($WorkDir.Replace("'", "''"))'"
        $envAssignments
        $Command
    ) -join "; "

    $process = Start-Process -FilePath "powershell.exe" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $psScript) `
        -WorkingDirectory $WorkDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput $StdOut `
        -RedirectStandardError $StdErr `
        -PassThru

    Set-Content -LiteralPath $PidFile -Value $process.Id -Encoding UTF8
    return $process
}

$repoRoot = Get-RepoRoot
$envPath = Join-Path $repoRoot $EnvFile
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"
$runtimeDir = Join-Path $repoRoot "runtime\real-pre"
$logDir = Join-Path $runtimeDir "logs"

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$envMap = Read-EnvFile -Path $envPath

Assert-RequiredEnv -EnvMap $envMap -Keys @(
    "SPRING_PROFILES_ACTIVE",
    "DOUYIN_TEST_ENABLED",
    "DB_HOST",
    "DB_PORT",
    "DB_NAME",
    "DB_USER",
    "DB_PASSWORD",
    "REDIS_HOST",
    "REDIS_PORT",
    "REDIS_DATABASE",
    "JWT_SECRET",
    "DOUYIN_BASE_URL",
    "DOUYIN_APP_ID",
    "DOUYIN_CLIENT_KEY",
    "DOUYIN_CLIENT_SECRET"
)

if ($envMap["DOUYIN_TEST_ENABLED"] -ne "false") {
    throw "DOUYIN_TEST_ENABLED must be false for real/pre startup. Current value: $($envMap["DOUYIN_TEST_ENABLED"])"
}

if ($envMap["APP_TEST_ENABLED"] -ne "false") {
    throw "APP_TEST_ENABLED must be false for real/pre startup. Current value: $($envMap["APP_TEST_ENABLED"])"
}

if ($envMap["DB_NAME"] -eq "colonel_saas_test") {
    throw "DB_NAME cannot be colonel_saas_test for real/pre startup."
}

if ($envMap["REDIS_DATABASE"] -eq "1") {
    throw "REDIS_DATABASE cannot be 1 for real/pre startup."
}

if (-not (Test-PortFree -Port $BackendPort)) {
    throw "Backend port $BackendPort is already in use."
}

$reuseFrontend = $false
if (-not (Test-PortFree -Port $FrontendPort)) {
    if ($ForceRestartFrontend) {
        throw "Frontend port $FrontendPort is already in use. Stop it first or rerun without -ForceRestartFrontend."
    }
    $reuseFrontend = $true
}

$backendEnv = @{}
foreach ($key in $envMap.Keys) {
    $backendEnv[$key] = $envMap[$key]
}
$backendEnv["SERVER_PORT"] = "$BackendPort"
$backendEnv["DEBUG"] = "false"
$backendEnv["SPRING_DEVTOOLS_ADD_PROPERTIES"] = "false"
$backendEnv["LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB_CLIENT_RESTTEMPLATE"] = "INFO"
$backendEnv["LOGGING_LEVEL_COM_DOUDIAN_OPEN_CORE"] = "WARN"

$frontendEnv = @{
    "VITE_API_BASE_URL" = "http://localhost:$BackendPort/api"
    "VITE_PROXY_TARGET" = "http://localhost:$BackendPort"
    "VITE_ENV_LABEL" = "REAL-PRE"
}

$backendStdOut = Join-Path $logDir "backend.stdout.log"
$backendStdErr = Join-Path $logDir "backend.stderr.log"
$frontendStdOut = Join-Path $logDir "frontend.stdout.log"
$frontendStdErr = Join-Path $logDir "frontend.stderr.log"
$backendPidFile = Join-Path $runtimeDir "backend.pid"
$frontendPidFile = Join-Path $runtimeDir "frontend.pid"

$backendCommand = "mvn '-Dmaven.test.skip=true' spring-boot:run"
$frontendCommand = "npm run dev -- --host 0.0.0.0 --port $FrontendPort --strictPort"

$backendProcess = Start-ManagedProcess `
    -Name "backend" `
    -WorkDir $backendDir `
    -EnvMap $backendEnv `
    -Command $backendCommand `
    -StdOut $backendStdOut `
    -StdErr $backendStdErr `
    -PidFile $backendPidFile

Start-Sleep -Seconds 5

if (-not $reuseFrontend) {
    $frontendProcess = Start-ManagedProcess `
        -Name "frontend" `
        -WorkDir $frontendDir `
        -EnvMap $frontendEnv `
        -Command $frontendCommand `
        -StdOut $frontendStdOut `
        -StdErr $frontendStdErr `
        -PidFile $frontendPidFile
} else {
    $frontendProcess = $null
}

Start-Sleep -Seconds 2

Write-Host ""
Write-Host "real/pre startup requested." -ForegroundColor Green
Write-Host "Backend PID : $($backendProcess.Id)"
if ($frontendProcess -ne $null) {
    Write-Host "Frontend PID: $($frontendProcess.Id)"
} else {
    Write-Host "Frontend PID: reused existing process on port $FrontendPort"
}
Write-Host "Backend URL : http://localhost:$BackendPort/api"
Write-Host "Frontend URL: http://localhost:$FrontendPort"
Write-Host "Env file    : $envPath"
Write-Host "Logs dir    : $logDir"
Write-Host ""
Write-Host "Next checks:" -ForegroundColor Yellow
Write-Host "1. Get-Content '$backendStdOut' -Tail 50"
Write-Host "2. Get-Content '$backendStdErr' -Tail 50"
Write-Host "3. Get-Content '$frontendStdOut' -Tail 50"
Write-Host "4. Open http://localhost:$FrontendPort"
