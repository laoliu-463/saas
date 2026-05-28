$ErrorActionPreference = "Stop"

function Read-EnvFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Env file not found: $Path"
    }

    $map = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }
        $eqIndex = $trimmed.IndexOf("=")
        if ($eqIndex -lt 1) {
            continue
        }
        $key = $trimmed.Substring(0, $eqIndex).Trim()
        $value = $trimmed.Substring($eqIndex + 1).Trim()
        $map[$key] = $value
    }
    return $map
}

function Assert-LastExitCode {
    param(
        [Parameter(Mandatory = $true)][string]$CommandName
    )

    if ($LASTEXITCODE -ne 0) {
        throw "$CommandName failed with exit code $LASTEXITCODE."
    }
}

function Convert-HttpContentToString {
    param(
        [Parameter(Mandatory = $false)]$Content
    )

    if ($null -eq $Content) {
        return ""
    }
    if ($Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Content)
    }
    return [string]$Content
}

function Get-BackendHealthUrl {
    param(
        [Parameter(Mandatory = $true)][hashtable]$EnvMap
    )

    $port = if ($EnvMap.ContainsKey("BACKEND_HOST_PORT") -and $EnvMap["BACKEND_HOST_PORT"]) {
        $EnvMap["BACKEND_HOST_PORT"]
    } else {
        "8080"
    }
    return "http://127.0.0.1:$port/api/system/health"
}

function Get-FrontendBaseUrl {
    param(
        [Parameter(Mandatory = $true)][hashtable]$EnvMap
    )

    $port = if ($EnvMap.ContainsKey("FRONTEND_HOST_PORT") -and $EnvMap["FRONTEND_HOST_PORT"]) {
        $EnvMap["FRONTEND_HOST_PORT"]
    } else {
        "3000"
    }
    return "http://127.0.0.1:$port"
}

function Wait-Until {
    param(
        [Parameter(Mandatory = $true)][string]$Description,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [int]$PollIntervalMilliseconds = 2000,
        [Parameter(Mandatory = $true)][scriptblock]$CheckScript
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (& $CheckScript) {
            return $true
        }
        Start-Sleep -Milliseconds $PollIntervalMilliseconds
    }
    throw "$Description timed out after $TimeoutSeconds seconds."
}

function Wait-ContainersStopped {
    param(
        [int]$TimeoutSeconds = 120,
        [int]$PollIntervalMilliseconds = 2000,
        [scriptblock]$CheckScript
    )

    if (-not $CheckScript) {
        $CheckScript = {
            $names = docker ps -a --filter "name=saas" --format "{{.Names}}"
            return -not $names
        }
    }

    Wait-Until -Description "Waiting for old SAAS containers to stop" `
        -TimeoutSeconds $TimeoutSeconds `
        -PollIntervalMilliseconds $PollIntervalMilliseconds `
        -CheckScript $CheckScript | Out-Null
    return $true
}

function Wait-HttpHealth {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$TimeoutSeconds = 120,
        [int]$PollIntervalMilliseconds = 2000,
        [scriptblock]$ProbeScript
    )

    if (-not $ProbeScript) {
        $ProbeScript = {
            param($ProbeUrl)
            try {
                $response = Invoke-WebRequest -Uri $ProbeUrl -UseBasicParsing -TimeoutSec 10
                $body = Convert-HttpContentToString -Content $response.Content
                $json = $body | ConvertFrom-Json
                return @{
                    ok = ($response.StatusCode -eq 200 -and $json.status -eq "UP")
                    status = $json.status
                    body = $body
                }
            } catch {
                return @{
                    ok = $false
                    status = "UNREACHABLE"
                    body = ($_ | Out-String).Trim()
                }
            }
        }
    }

    $last = @{ ok = $false; status = "UNREACHABLE"; body = "" }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $last = & $ProbeScript $Url
        if ($last.ok) {
            return $last
        }
        Start-Sleep -Milliseconds $PollIntervalMilliseconds
    }

    throw "Health check timed out after $TimeoutSeconds seconds. url=$Url lastStatus=$($last.status)"
}

function Wait-HttpOk {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$TimeoutSeconds = 120,
        [int]$PollIntervalMilliseconds = 2000,
        [scriptblock]$ProbeScript
    )

    if (-not $ProbeScript) {
        $ProbeScript = {
            param($ProbeUrl)
            try {
                $response = Invoke-WebRequest -Uri $ProbeUrl -UseBasicParsing -TimeoutSec 10
                return @{
                    ok = ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400)
                    statusCode = $response.StatusCode
                    body = Convert-HttpContentToString -Content $response.Content
                }
            } catch {
                return @{
                    ok = $false
                    statusCode = 0
                    body = ($_ | Out-String).Trim()
                }
            }
        }
    }

    $last = @{ ok = $false; statusCode = 0; body = "" }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $last = & $ProbeScript $Url
        if ($last.ok) {
            return $last
        }
        Start-Sleep -Milliseconds $PollIntervalMilliseconds
    }

    throw "HTTP ready check timed out after $TimeoutSeconds seconds. url=$Url lastStatusCode=$($last.statusCode)"
}

function Show-StartupDiagnostics {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$ComposeFile,
        [Parameter(Mandatory = $true)][string]$EnvFile,
        [string]$ProjectName = "saas-active"
    )

    Write-Host ""
    Write-Host "=== docker ps ===" -ForegroundColor Yellow
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

    Write-Host ""
    Write-Host "=== docker compose ps ($ProjectName) ===" -ForegroundColor Yellow
    Push-Location $RepoRoot
    try {
        docker compose --env-file $EnvFile --project-name $ProjectName -f $ComposeFile ps
    } finally {
        Pop-Location
    }

    Write-Host ""
    Write-Host "=== backend logs (tail 100) ===" -ForegroundColor Yellow
    $backendName = docker ps -a `
        --filter "label=com.docker.compose.project=$ProjectName" `
        --filter "label=com.docker.compose.service=backend" `
        --format "{{.Names}}" |
        Select-Object -First 1
    if ($backendName) {
        docker logs $backendName --tail 100 2>&1
    } else {
        Write-Host "backend container is not present for compose project '$ProjectName'."
    }
}
