$ErrorActionPreference = "Stop"

function Get-HarnessRepoRoot {
    return (Get-Item -LiteralPath (Join-Path $PSScriptRoot "..\..\..")).FullName
}

function Get-HarnessBashPath {
    $bashCommand = Get-Command bash -ErrorAction SilentlyContinue
    if ($bashCommand -and $bashCommand.Source -and ($bashCommand.Source -notmatch "\\System32\\bash\.exe$")) {
        return $bashCommand.Source
    }

    $gitExecPath = Get-HarnessGitValue -Arguments @("--exec-path")
    if (-not [string]::IsNullOrWhiteSpace($gitExecPath)) {
        $gitCore = Resolve-Path -LiteralPath $gitExecPath -ErrorAction SilentlyContinue
        if ($gitCore) {
            $gitRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $gitCore.Path))
            $candidate = Join-Path $gitRoot "bin\bash.exe"
            if (Test-Path -LiteralPath $candidate) {
                return $candidate
            }
        }
    }

    foreach ($candidate in @(
        "D:\DevTools\Git\Git\bin\bash.exe",
        "C:\Program Files\Git\bin\bash.exe",
        "C:\Program Files (x86)\Git\bin\bash.exe"
    )) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    throw "Git Bash was not found. Install Git Bash or set PATH so bash resolves to Git Bash, not WSL."
}

function Convert-HarnessPathToMsys {
    param([Parameter(Mandatory = $true)][string]$Path)

    $resolved = (Resolve-Path -LiteralPath $Path).Path
    if ($resolved -match "^([A-Za-z]):\\(.*)$") {
        $drive = $matches[1].ToLowerInvariant()
        $rest = $matches[2] -replace "\\", "/"
        return "/$drive/$rest"
    }
    return ($resolved -replace "\\", "/")
}

function Write-HarnessStage {
    param([Parameter(Mandatory = $true)][string]$Title)
    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

function Assert-HarnessRepoRoot {
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    $current = (Get-Item -LiteralPath (Get-Location).Path).FullName
    $expected = (Get-Item -LiteralPath $RepoRoot).FullName
    if (-not $current.Equals($expected, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Current path must be project root. current=$current expected=$expected"
    }
    foreach ($path in @("backend", "frontend")) {
        if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $path))) {
            throw "Required directory not found: $path"
        }
    }
    foreach ($path in @("AGENTS.md", "harness")) {
        if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $path))) {
            throw "Required harness entry not found: $path"
        }
    }
    if (-not (Get-ChildItem -LiteralPath $RepoRoot -Filter "docker-compose*.yml" -ErrorAction SilentlyContinue)) {
        throw "No docker-compose*.yml file found in project root."
    }
}

function Get-HarnessChangedFiles {
    $statusLines = & git -c core.quotepath=false status --porcelain=v1 2>$null
    if ($LASTEXITCODE -ne 0 -or $null -eq $statusLines -or $statusLines.Count -eq 0) {
        return @()
    }

    $files = @()
    foreach ($line in $statusLines) {
        if ($line.Length -lt 4) {
            continue
        }
        $path = $line.Substring(3).Trim().Trim('"')
        if ($path -match "\s+->\s+") {
            $path = ($path -split "\s+->\s+")[-1].Trim().Trim('"')
        }
        if ($path) {
            $files += $path
        }
    }
    return $files | Sort-Object -Unique
}

function Assert-HarnessNoSensitiveChangedFiles {
    $files = @(Get-HarnessChangedFiles)
    foreach ($file in $files) {
        $name = Split-Path -Leaf $file
        $lower = $file.ToLowerInvariant()
        $isEnv = ($name -like ".env*" -and -not $name.EndsWith(".example"))
        $blocked = $isEnv `
            -or $lower.EndsWith(".pem") `
            -or $lower.EndsWith(".key") `
            -or $lower.EndsWith(".p12") `
            -or $lower.EndsWith(".jks") `
            -or $name.ToLowerInvariant().StartsWith("credentials") `
            -or $name.ToLowerInvariant().StartsWith("secrets")
        if ($blocked) {
            throw "Sensitive changed file is blocked: $file"
        }
    }
}

function Get-HarnessPortFromCompose {
    param(
        [Parameter(Mandatory = $true)][string]$ComposeFile,
        [Parameter(Mandatory = $true)][string]$EnvKey,
        [Parameter(Mandatory = $true)][string]$Default
    )

    if (-not (Test-Path -LiteralPath $ComposeFile)) {
        return $Default
    }
    $content = Get-Content -LiteralPath $ComposeFile -Raw
    $escaped = [regex]::Escape($EnvKey)
    $patternWithDefault = "\$\{${escaped}:-(?<port>\d+)\}:"
    $match = [regex]::Match($content, $patternWithDefault)
    if ($match.Success) {
        return $match.Groups["port"].Value
    }
    return $Default
}

function Read-HarnessEnvFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    $map = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $map
    }

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
        $value = $trimmed.Substring($eqIndex + 1).Trim().Trim("'`"")
        $map[$key] = $value
    }
    return $map
}

function Get-HarnessEnvConfig {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("test", "real-pre")]
        [string]$Env
    )

    $repoRoot = Get-HarnessRepoRoot
    if ($Env -eq "real-pre") {
        return [pscustomobject]@{
            Env = "real-pre"
            RepoRoot = $repoRoot
            ComposeFile = Join-Path $repoRoot "docker-compose.real-pre.yml"
            EnvFile = Join-Path $repoRoot ".env.real-pre"
            ProjectName = "saas-active"
            BackendService = "backend-real-pre"
            FrontendService = "frontend-real-pre"
            BackendPort = "8081"
            FrontendPort = "3001"
            BackendHealthPath = "/api/system/health"
            FrontendHealthCandidates = @("/healthz", "/login", "/")
        }
    }

    return [pscustomobject]@{
        Env = "test"
        RepoRoot = $repoRoot
        ComposeFile = Join-Path $repoRoot "docker-compose.test.yml"
        EnvFile = Join-Path $repoRoot ".env.test"
        ProjectName = "saas-test"
        BackendService = "backend"
        FrontendService = "frontend"
        BackendPort = "8080"
        FrontendPort = "3000"
        BackendHealthPath = "/api/actuator/health/readiness"
        FrontendHealthCandidates = @("/healthz", "/favicon.svg", "/")
    }
}

function Get-HarnessPort {
    param(
        [Parameter(Mandatory = $true)][hashtable]$EnvMap,
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$Default
    )

    if ($EnvMap.ContainsKey($Key) -and $EnvMap[$Key]) {
        return $EnvMap[$Key]
    }
    return $Default
}

function Invoke-HarnessExternal {
    param(
        [Parameter(Mandatory = $true)][string]$CommandName,
        [Parameter(Mandatory = $true)][scriptblock]$Script
    )

    & $Script
    if ($LASTEXITCODE -ne 0) {
        throw "$CommandName failed with exit code $LASTEXITCODE."
    }
}

function Invoke-HarnessHttp {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$TimeoutSec = 10
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        if ($null -eq $response.Content) {
            $content = ""
        }
        elseif ($response.Content -is [byte[]]) {
            # Windows PowerShell can expose text responses as byte[]; casting
            # directly to string produces "123 34 ..." and breaks JSON probes.
            $content = [Text.Encoding]::UTF8.GetString($response.Content)
        }
        else {
            $content = [string]$response.Content
        }
        return [pscustomobject]@{
            Ok = ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400)
            StatusCode = $response.StatusCode
            Body = $content
            Error = ""
        }
    }
    catch {
        return [pscustomobject]@{
            Ok = $false
            StatusCode = 0
            Body = ""
            Error = ($_.Exception.Message)
        }
    }
}

function Get-HarnessGitValue {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    try {
        $value = & git @Arguments 2>$null
        if ($LASTEXITCODE -eq 0) {
            return ($value -join "`n").Trim()
        }
    }
    catch {
        return ""
    }
    return ""
}

function ConvertTo-HarnessReportKey {
    param([Parameter(Mandatory = $true)][string]$ReportKey)

    $key = $ReportKey.Trim().ToLowerInvariant()
    if ($key.Contains('..') -or $key.Contains('/') -or $key.Contains('\')) {
        throw "ReportKey must not contain path segments: $ReportKey"
    }
    if ($key -notmatch '^[a-z0-9][a-z0-9-]{0,63}$') {
        throw "ReportKey must use lowercase letters, digits, and hyphens with a maximum length of 64: $ReportKey"
    }
    return $key
}

function Expand-HarnessOwnedFiles {
    param([AllowEmptyCollection()][string[]]$OwnedFiles = @())

    $expanded = @()
    foreach ($item in $OwnedFiles) {
        if ([string]::IsNullOrWhiteSpace($item)) { continue }
        foreach ($candidate in @($item -split ';')) {
            $path = $candidate.Trim().Trim('"').Replace('\', '/')
            if ([string]::IsNullOrWhiteSpace($path)) { continue }
            if ([System.IO.Path]::IsPathRooted($path) -or $path -match '(^|/)\.\.(/|$)' -or $path -match '[*?]') {
                throw "Owned file must be a repository-relative literal path: $candidate"
            }
            if ($path.StartsWith('./')) {
                $path = $path.Substring(2)
            }
            $expanded += $path
        }
    }
    return @($expanded | Sort-Object -Unique)
}

function Get-HarnessRepoRelativePath {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $root = (Get-Item -LiteralPath $RepoRoot).FullName.TrimEnd('\')
    $resolved = (Get-Item -LiteralPath $Path).FullName
    $prefix = $root + '\'
    if (-not $resolved.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside repository. path=$resolved root=$root"
    }
    return $resolved.Substring($prefix.Length).Replace('\', '/')
}

function Write-HarnessFileIfChanged {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Content
    )

    $existing = if (Test-Path -LiteralPath $Path) { Get-Content -Raw -LiteralPath $Path } else { $null }
    if ($existing -eq $Content) { return $false }
    New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
    Set-Content -LiteralPath $Path -Value $Content -Encoding UTF8
    return $true
}

function Get-HarnessComposeArgs {
    param([Parameter(Mandatory = $true)]$Config)

    $args = @("compose")
    if (Test-Path -LiteralPath $Config.EnvFile) {
        $args += @("--env-file", $Config.EnvFile)
    }
    $args += @("-f", $Config.ComposeFile)
    return $args
}

function New-HarnessReportPath {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$ReportKey
    )

    $key = ConvertTo-HarnessReportKey -ReportKey $ReportKey
    $reportsDir = Join-Path $RepoRoot "harness\reports\current"
    if (-not (Test-Path -LiteralPath $reportsDir)) {
        New-Item -ItemType Directory -Force -Path $reportsDir | Out-Null
    }
    return Join-Path $reportsDir "latest-$key.md"
}

function Convert-HarnessBool {
    param([Parameter(Mandatory = $false)]$Value)

    if ($null -eq $Value) {
        return $false
    }
    if ($Value -is [bool]) {
        return [bool]$Value
    }
    $text = ([string]$Value).Trim().ToLowerInvariant()
    if ($text -in @("true", "1", "yes", "y")) {
        return $true
    }
    if ($text -in @("false", "0", "no", "n", "")) {
        return $false
    }
    throw "Cannot convert to bool: $Value"
}
