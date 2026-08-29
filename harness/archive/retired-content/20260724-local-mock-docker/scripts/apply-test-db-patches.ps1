param(
    [string]$ContainerName = "saas-test-postgres-1",
    [string]$Database = "colonel_saas_test",
    [string]$User = "saas",
    [int]$TimeoutSeconds = 90
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

function Assert-DockerAvailable {
    try {
        & docker.exe version | Out-Null
    }
    catch {
        throw "Docker is not available. Start Docker Desktop / Docker Engine first, then retry."
    }
}

function Wait-ContainerRunning {
    param(
        [string]$Name,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $state = (& docker.exe inspect --format "{{.State.Status}}" $Name 2>$null)
        if ($LASTEXITCODE -eq 0 -and $state -eq "running") {
            return
        }
        Start-Sleep -Seconds 3
    }

    throw "Container $Name did not become ready within $TimeoutSeconds seconds."
}

$repoRoot = Get-RepoRoot
$sqlPath = Join-Path $repoRoot "backend\src\main\resources\db\migrate-all.sql"
if (-not (Test-Path $sqlPath)) {
    throw "Migration SQL not found: $sqlPath"
}

Assert-DockerAvailable
Wait-ContainerRunning -Name $ContainerName -TimeoutSeconds $TimeoutSeconds

$sql = [System.IO.File]::ReadAllText($sqlPath, [System.Text.UTF8Encoding]::new($false))
if (-not $sql.Trim()) {
    throw "Migration SQL is empty: $sqlPath"
}

# psql `\i <file>` resolves relative paths against its own working directory.
# When SQL is piped via stdin (`docker exec -i ... psql`), the working directory
# is the container's default (`/`), so `\i alter-foo.sql` cannot find the file.
# Stage the SQL directory inside the container and run psql with `-w` so that
# relative `\i` references resolve against the staged copy.
$containerSqlDir = "/tmp/saas-db"
& docker.exe exec $ContainerName sh -c "rm -rf $containerSqlDir && mkdir -p $containerSqlDir"
Assert-LastExitCode -CommandName "docker exec reset $containerSqlDir"

$dbDir = Split-Path -Parent $sqlPath
# Copy the db directory's *contents* (the `/.` syntax) into the staged path.
# Without it, docker cp nests the source dir under $containerSqlDir, leaving
# \i references (which resolve against the working dir) unable to find files.
& docker.exe cp "$dbDir\." "$ContainerName`:$containerSqlDir"
Assert-LastExitCode -CommandName "docker cp $dbDir -> $ContainerName`:$containerSqlDir"

$sql | & docker.exe exec -i -w $containerSqlDir $ContainerName psql -U $User -d $Database -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) {
    throw "Applying test DB migrations failed."
}

Write-Host "Applied idempotent migrations from $sqlPath" -ForegroundColor Green
