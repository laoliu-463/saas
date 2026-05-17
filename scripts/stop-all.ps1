$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
. (Join-Path $scriptDir "stack-utils.ps1")

function Invoke-ComposeDown {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectName,
        [Parameter(Mandatory = $true)][string]$ComposeFile,
        [string]$EnvFile
    )

    if (Test-Path -LiteralPath $ComposeFile) {
        Write-Host "Stopping compose project '$ProjectName' with $ComposeFile"
        $args = @("compose", "--project-name", $ProjectName)
        if ($EnvFile -and (Test-Path -LiteralPath $EnvFile)) {
            $args += @("--env-file", $EnvFile)
        }
        $args += @("-f", $ComposeFile, "down", "--remove-orphans")
        docker @args
        Assert-LastExitCode -CommandName "docker $($args -join ' ')"
    }
}

function Remove-ContainerIfExists {
    param([Parameter(Mandatory = $true)][string]$Name)

    $ids = docker ps -aq --filter "name=^/$Name$"
    Assert-LastExitCode -CommandName "docker ps -aq --filter name=^/$Name$"
    if ($ids) {
        Write-Host "Removing leftover container $Name"
        docker rm -f $ids | Out-Null
        Assert-LastExitCode -CommandName "docker rm -f $Name"
    }
}

Push-Location $repoRoot
try {
    Invoke-ComposeDown -ProjectName "saas-active" -ComposeFile (Join-Path $repoRoot "docker-compose.yml") -EnvFile (Join-Path $repoRoot ".env.test")
    Invoke-ComposeDown -ProjectName "saas-test" -ComposeFile (Join-Path $repoRoot "archive\docker-compose\docker-compose.test.yml.bak") -EnvFile (Join-Path $repoRoot ".env.test")
    Invoke-ComposeDown -ProjectName "saas" -ComposeFile (Join-Path $repoRoot "archive\docker-compose\docker-compose.real-pre.yml.bak") -EnvFile (Join-Path $repoRoot ".env.real-pre")
    Invoke-ComposeDown -ProjectName "saas-prod" -ComposeFile (Join-Path $repoRoot "docker-compose.prod.yml") -EnvFile (Join-Path $repoRoot ".env.prod")
    Invoke-ComposeDown -ProjectName "saas" -ComposeFile (Join-Path $repoRoot "docker-compose.local-mock.yml.bak") -EnvFile (Join-Path $repoRoot ".env")

    @(
        "saas-frontend",
        "saas-backend",
        "saas-postgres",
        "saas-redis",
        "saas-test-frontend-1",
        "saas-test-backend-1",
        "saas-test-postgres-1",
        "saas-test-redis-1",
        "saas-frontend-real-pre-1",
        "saas-backend-real-pre-1",
        "saas-postgres-real-pre-1",
        "saas-redis-real-pre-1",
        "saas-frontend-1",
        "saas-backend-1",
        "saas-postgres-1",
        "saas-redis-1",
        "saas-prod-frontend-1",
        "saas-prod-backend-1",
        "saas-prod-postgres-1",
        "saas-prod-redis-1"
    ) | ForEach-Object { Remove-ContainerIfExists -Name $_ }

    Wait-ContainersStopped -TimeoutSeconds 120 -PollIntervalMilliseconds 2000 -CheckScript {
        $names = docker ps -a --filter "name=saas" --format "{{.Names}}"
        return -not $names
    } | Out-Null
}
finally {
    Pop-Location
}

Write-Host "All SAAS containers are stopped. Volumes were kept." -ForegroundColor Green
Write-Host ""
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
Assert-LastExitCode -CommandName "docker ps"
