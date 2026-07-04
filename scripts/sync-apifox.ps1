param(
    [string]$EnvFile = $env:APIFOX_ENV_FILE
)

$ErrorActionPreference = "Stop"

function Get-ProjectRoot {
    return (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
}

function Import-ApifoxEnv {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $allowedKeys = @(
        "APIFOX_ACCESS_TOKEN",
        "APIFOX_PROJECT_ID",
        "APIFOX_BRANCH",
        "APIFOX_OPENAPI_FILE"
    )

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -notmatch "^\s*(APIFOX_[A-Z_]+)\s*=\s*(.*)\s*$") {
            continue
        }

        $key = $matches[1]
        if ($allowedKeys -notcontains $key) {
            continue
        }

        if (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key, "Process"))) {
            continue
        }

        $value = $matches[2].Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

function Test-Placeholder {
    param([string]$Value)

    return [string]::IsNullOrWhiteSpace($Value) `
        -or $Value.StartsWith("__FILL_ME_") `
        -or ($Value.StartsWith("<") -and $Value.EndsWith(">")) `
        -or $Value.StartsWith("你的")
}

$projectRoot = Get-ProjectRoot
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $projectRoot ".env"
}

Import-ApifoxEnv -Path $EnvFile

if (Test-Placeholder -Value $env:APIFOX_ACCESS_TOKEN) {
    throw "APIFOX_ACCESS_TOKEN is required; fill the placeholder in .env or export a real token."
}

if (Test-Placeholder -Value $env:APIFOX_PROJECT_ID) {
    throw "APIFOX_PROJECT_ID is required; fill the placeholder in .env or export a real project id."
}

$openApiFile = if ([string]::IsNullOrWhiteSpace($env:APIFOX_OPENAPI_FILE)) {
    "docs/openapi/saas-openapi.json"
} else {
    $env:APIFOX_OPENAPI_FILE
}

$branch = if ([string]::IsNullOrWhiteSpace($env:APIFOX_BRANCH)) {
    "ddd-sync"
} else {
    $env:APIFOX_BRANCH
}

$openApiPath = Join-Path $projectRoot $openApiFile
if (-not (Test-Path -LiteralPath $openApiPath)) {
    throw "OpenAPI file not found: $openApiFile"
}

if (-not (Get-Command apifox -ErrorAction SilentlyContinue)) {
    throw "Apifox CLI is not installed or not in PATH."
}

apifox login --with-token $env:APIFOX_ACCESS_TOKEN
if ($LASTEXITCODE -ne 0) {
    throw "apifox login failed with exit code $LASTEXITCODE"
}

if (-not [string]::IsNullOrWhiteSpace($branch)) {
    $branchList = apifox branch list --project $env:APIFOX_PROJECT_ID --type all | ConvertFrom-Json
    $branchExists = @($branchList.data | Where-Object { $_.name -eq $branch }).Count -gt 0
    if (-not $branchExists) {
        apifox branch create --project $env:APIFOX_PROJECT_ID --type sprint --name $branch
        if ($LASTEXITCODE -ne 0) {
            throw "apifox branch create failed with exit code $LASTEXITCODE"
        }
    }
}

$args = @("import", "--project", $env:APIFOX_PROJECT_ID, "--format", "openapi", "--file", $openApiFile)
if (-not [string]::IsNullOrWhiteSpace($branch)) {
    $args += @("--branch", $branch)
}

apifox @args
if ($LASTEXITCODE -ne 0) {
    throw "apifox import failed with exit code $LASTEXITCODE"
}

Write-Host "Apifox import submitted."
Write-Host "File: $openApiFile"
Write-Host "Branch: $branch"
