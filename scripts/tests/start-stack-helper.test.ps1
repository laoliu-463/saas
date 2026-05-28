$ErrorActionPreference = "Stop"

$helperPath = Join-Path $PSScriptRoot "..\stack-utils.ps1"
. $helperPath

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Assert-Equal {
    param(
        $Actual,
        $Expected,
        [string]$Message
    )

    if ($Actual -ne $Expected) {
        throw "$Message`nExpected: $Expected`nActual: $Actual"
    }
}

$tempEnv = Join-Path $env:TEMP "saas-start-test-env.txt"
@(
    "BACKEND_HOST_PORT=8080"
    "FRONTEND_HOST_PORT=3000"
) | Set-Content -LiteralPath $tempEnv -Encoding UTF8

try {
    $envMap = Read-EnvFile -Path $tempEnv
    Assert-Equal -Actual $envMap["BACKEND_HOST_PORT"] -Expected "8080" -Message "Read-EnvFile should parse BACKEND_HOST_PORT"

    $healthUrl = Get-BackendHealthUrl -EnvMap $envMap
    Assert-Equal -Actual $healthUrl -Expected "http://127.0.0.1:8080/api/system/health" -Message "Get-BackendHealthUrl should respect .env backend port"

    $frontendUrl = Get-FrontendBaseUrl -EnvMap $envMap
    Assert-Equal -Actual $frontendUrl -Expected "http://127.0.0.1:3000" -Message "Get-FrontendBaseUrl should respect .env frontend port"

    $decoded = Convert-HttpContentToString -Content ([System.Text.Encoding]::UTF8.GetBytes('{"status":"UP"}'))
    Assert-Equal -Actual $decoded -Expected '{"status":"UP"}' -Message "Convert-HttpContentToString should decode byte arrays"

    $containerChecks = 0
    Wait-ContainersStopped -TimeoutSeconds 2 -PollIntervalMilliseconds 10 -CheckScript {
        $script:containerChecks++
        return $script:containerChecks -ge 3
    } | Out-Null
    Assert-True -Condition ($containerChecks -ge 3) -Message "Wait-ContainersStopped should keep polling until containers stop"

    $healthChecks = 0
    $health = Wait-HttpHealth -Url "http://localhost:8080/api/system/health" -TimeoutSeconds 2 -PollIntervalMilliseconds 10 -ProbeScript {
        $script:healthChecks++
        if ($script:healthChecks -lt 2) {
            return @{ ok = $false; status = "DOWN" }
        }
        return @{ ok = $true; status = "UP"; body = '{"status":"UP"}' }
    }
    Assert-Equal -Actual $health.status -Expected "UP" -Message "Wait-HttpHealth should return the final UP status"

    $httpChecks = 0
    $httpOk = Wait-HttpOk -Url "http://localhost:3000" -TimeoutSeconds 2 -PollIntervalMilliseconds 10 -ProbeScript {
        $script:httpChecks++
        if ($script:httpChecks -lt 2) {
            return @{ ok = $false; statusCode = 503; body = "" }
        }
        return @{ ok = $true; statusCode = 200; body = "<html></html>" }
    }
    Assert-Equal -Actual $httpOk.statusCode -Expected 200 -Message "Wait-HttpOk should return the final 200 status"

    Write-Host "start-stack helper tests passed" -ForegroundColor Green
}
finally {
    if (Test-Path -LiteralPath $tempEnv) {
        Remove-Item -LiteralPath $tempEnv -Force
    }
}
