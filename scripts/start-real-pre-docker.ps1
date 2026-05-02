param(
    [string]$EnvFile = ".env.real",
    [switch]$Detach
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $scriptPath = $PSCommandPath
    if (-not $scriptPath) {
        $scriptPath = $MyInvocation.PSCommandPath
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
        $map[$parts[0].Trim()] = $parts[1].Trim()
    }
    return $map
}

$repoRoot = Get-RepoRoot
$envPath = Join-Path $repoRoot $EnvFile
$composePath = Join-Path $repoRoot "docker-compose.real-pre.yml"
$envMap = Read-EnvFile -Path $envPath

if ($envMap["DOUYIN_TEST_ENABLED"] -ne "false") {
    throw "DOUYIN_TEST_ENABLED must be false in ${EnvFile}."
}
if ($envMap["DB_NAME"] -eq "colonel_saas_test") {
    throw "DB_NAME cannot be colonel_saas_test in ${EnvFile}."
}
if ($envMap["REDIS_DATABASE"] -eq "1") {
    throw "REDIS_DATABASE cannot be 1 in ${EnvFile}."
}

$args = @(
    "compose",
    "--project-name", "saas-real-pre",
    "--env-file", $envPath,
    "-f", $composePath,
    "up",
    "--build"
)
if ($Detach) {
    $args += "-d"
}

Push-Location $repoRoot
try {
    & docker.exe @args
}
finally {
    Pop-Location
}
