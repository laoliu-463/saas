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
        BackendHealthPath = "/api/system/health"
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
        $content = if ($null -eq $response.Content) { "" } else { [string]$response.Content }
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
            $expanded += $path.TrimStart('./')
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

function Get-HarnessAgentDoExecutionMode {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("backend", "frontend", "full", "docs", "apifox")]
        [string]$Scope
    )

    if ($Scope -in @("backend", "frontend", "full")) {
        return "NODE"
    }
    return "LEGACY"
}

function New-HarnessNodeVerifyArguments {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("test", "real-pre")]
        [string]$Env,
        [Parameter(Mandatory = $true)]
        [ValidateSet("backend", "frontend", "full")]
        [string]$Scope,
        [Parameter(Mandatory = $true)]
        [string]$ReportKey,
        [string]$BusinessCommand = "",
        [switch]$SkipBusinessValidation,
        [switch]$DryRun
    )

    $key = ConvertTo-HarnessReportKey -ReportKey $ReportKey
    $arguments = @(
        "run", "harness:verify", "--",
        "--env", $Env,
        "--scope", $Scope,
        "--report-key", $key
    )
    if (-not [string]::IsNullOrWhiteSpace($BusinessCommand)) {
        $arguments += @("--business-command", $BusinessCommand)
    }
    if ($SkipBusinessValidation) {
        $arguments += "--skip-business-validation"
    }
    if ($DryRun) {
        $arguments += "--dry-run"
    }
    return @($arguments)
}

function Get-HarnessSha256Text {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)

    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        return (($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join "")
    }
    finally {
        $sha.Dispose()
    }
}

function Get-HarnessNodeGitSnapshotJson {
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    $headSha = (Get-HarnessGitValue -Arguments @("rev-parse", "--verify", "HEAD")).ToLowerInvariant()
    if ($headSha -notmatch '^(?:[a-f0-9]{40}|[a-f0-9]{64})$') {
        throw "无法为 Node 验证采集可信 Git HEAD。"
    }
    $branchOutput = & git symbolic-ref --quiet --short HEAD 2>$null
    $branchExit = $LASTEXITCODE
    if ($branchExit -eq 0) {
        $branch = ($branchOutput -join "`n").Trim()
        if ([string]::IsNullOrWhiteSpace($branch)) { throw "Git 分支名称为空。" }
    }
    elseif ($branchExit -eq 1) {
        $branch = "DETACHED"
    }
    else {
        throw "无法为 Node 验证采集可信 Git 分支。"
    }
    $statusProbe = & git -c core.quotepath=false status --porcelain=v1 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "无法为 Node 验证采集可信 Git 工作区状态。"
    }
    $changedFiles = @(Get-HarnessChangedFiles | ForEach-Object { ([string]$_).Replace('\', '/') } | Sort-Object -Unique)
    foreach ($file in $changedFiles) {
        if ([System.IO.Path]::IsPathRooted($file) -or $file -match '(^|/)\.\.(/|$)') {
            throw "Git 变更路径不符合仓库相对路径契约。"
        }
    }

    if ($changedFiles.Count -eq 0) {
        return (@{
            headSha = $headSha
            branch = $branch
            clean = $true
            changedFiles = @()
            identity = @{ kind = "COMMIT"; commitSha = $headSha }
        } | ConvertTo-Json -Depth 8 -Compress)
    }

    $fingerprintParts = @("HARNESS_POWERSHELL_WORKTREE_V1", $headSha, $branch)
    foreach ($file in $changedFiles) {
        $absolute = Join-Path $RepoRoot ($file.Replace('/', '\'))
        if (Test-Path -LiteralPath $absolute -PathType Leaf) {
            $digest = (Get-FileHash -LiteralPath $absolute -Algorithm SHA256).Hash.ToLowerInvariant()
            $fingerprintParts += "$file|FILE|$digest"
        }
        elseif (Test-Path -LiteralPath $absolute -PathType Container) {
            $fingerprintParts += "$file|DIRECTORY"
        }
        else {
            $fingerprintParts += "$file|MISSING"
        }
    }
    $fingerprint = "sha256:$(Get-HarnessSha256Text -Text ($fingerprintParts -join "`n"))"
    return (@{
        headSha = $headSha
        branch = $branch
        clean = $false
        changedFiles = @($changedFiles)
        identity = @{
            kind = "WORKTREE"
            headSha = $headSha
            changedFiles = @($changedFiles)
            patchFingerprint = $fingerprint
        }
    } | ConvertTo-Json -Depth 8 -Compress)
}

function Get-HarnessNodeVerifyReceipt {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Output,
        [Parameter(Mandatory = $true)][string]$ExpectedInvocationId,
        [Parameter(Mandatory = $true)][ValidateSet("test", "real-pre")][string]$ExpectedEnv,
        [Parameter(Mandatory = $true)][ValidateSet("backend", "frontend", "full")][string]$ExpectedScope,
        [Parameter(Mandatory = $true)][string]$ExpectedReportKey
    )

    $prefix = "HARNESS_VERIFY_RECEIPT_V1:"
    $receiptLines = @($Output | ForEach-Object { [string]$_ } | Where-Object { $_.StartsWith($prefix) })
    if ($receiptLines.Count -ne 1) {
        throw "Node 验证未返回唯一的本次运行回执，禁止 Git 候选收尾。"
    }
    try {
        $encoded = $receiptLines[0].Substring($prefix.Length).Replace('-', '+').Replace('_', '/')
        while (($encoded.Length % 4) -ne 0) { $encoded += '=' }
        $json = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($encoded))
        $receipt = $json | ConvertFrom-Json
        $valid = $receipt.schemaVersion -eq "1.0.0" `
            -and $receipt.invocationId -eq $ExpectedInvocationId `
            -and $receipt.environment -eq $ExpectedEnv `
            -and $receipt.scope -eq $ExpectedScope `
            -and $receipt.reportKey -eq $ExpectedReportKey `
            -and ([string]$receipt.runId) -match '^[a-z0-9]+(?:[._-][a-z0-9]+)*$' `
            -and $receipt.status -in @("PASS", "FAIL", "BLOCKED", "PARTIAL") `
            -and $receipt.evidencePaths.rawJson -eq "runtime/qa/out/$($receipt.runId)/run.json" `
            -and $receipt.evidencePaths.stableJson -eq "harness/reports/current/latest-$ExpectedReportKey.json" `
            -and $receipt.evidencePaths.stableMarkdown -eq "harness/reports/current/latest-$ExpectedReportKey.md" `
            -and $receipt.evidenceDigests.rawJson -match '^sha256:[a-f0-9]{64}$' `
            -and $receipt.evidenceDigests.stableJson -match '^sha256:[a-f0-9]{64}$' `
            -and $receipt.evidenceDigests.stableMarkdown -match '^sha256:[a-f0-9]{64}$'
        if (-not $valid) { throw "invalid receipt" }
        return $receipt
    }
    catch {
        throw "Node 验证回执与本次调用不一致，禁止 Git 候选收尾。"
    }
}

function New-HarnessNodeVerifyDecision {
    param(
        [bool]$AllowGit,
        [string]$Conclusion,
        [string]$Reason
    )
    return [pscustomobject]@{
        AllowGit = $AllowGit
        Conclusion = $Conclusion
        Reason = $Reason
    }
}

function Resolve-HarnessNodeVerifyDecision {
    param(
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [Parameter(Mandatory = $true)][string]$StableJsonPath,
        [Parameter(Mandatory = $true)][string]$StableMarkdownPath,
        [Parameter(Mandatory = $true)][ValidateSet("test", "real-pre")][string]$ExpectedEnv,
        [Parameter(Mandatory = $true)][ValidateSet("backend", "frontend", "full")][string]$ExpectedScope,
        [Parameter(Mandatory = $true)][string]$ExpectedReportKey,
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [string]$ExpectedRunId = "",
        [string]$ExpectedReceiptStatus = "",
        [string]$ExpectedRawJsonDigest = "",
        [string]$ExpectedStableJsonDigest = "",
        [string]$ExpectedStableMarkdownDigest = "",
        [string]$PreviousRunId = ""
    )

    if ($ExitCode -eq 1) {
        return New-HarnessNodeVerifyDecision -AllowGit $false -Conclusion "FAIL" -Reason "Node 验证确定性失败，禁止 Git 候选收尾。"
    }
    if ($ExitCode -eq 3) {
        return New-HarnessNodeVerifyDecision -AllowGit $false -Conclusion "FAIL" -Reason "Node 参数或契约错误，禁止 Git 候选收尾。"
    }
    if ($ExitCode -notin @(0, 2)) {
        return New-HarnessNodeVerifyDecision -AllowGit $false -Conclusion "FAIL" -Reason "Node 返回未知退出码 $ExitCode，禁止 Git 候选收尾。"
    }
    if (-not (Test-Path -LiteralPath $StableJsonPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $StableMarkdownPath -PathType Leaf)) {
        return New-HarnessNodeVerifyDecision -AllowGit $false -Conclusion "BLOCKED" -Reason "Node 稳定 JSON 或 Markdown 证据缺失，禁止 Git 候选收尾。"
    }

    try {
        $stableJson = Get-Content -Raw -Encoding UTF8 -LiteralPath $StableJsonPath
        $report = $stableJson | ConvertFrom-Json
        $markdown = Get-Content -Raw -Encoding UTF8 -LiteralPath $StableMarkdownPath
        $runId = [string]$report.runId
        $status = [string]$report.result.status
        $expectedRaw = "runtime/qa/out/$runId/run.json"
        $expectedStableJson = "harness/reports/current/latest-$ExpectedReportKey.json"
        $expectedStableMarkdown = "harness/reports/current/latest-$ExpectedReportKey.md"
        $rawPath = Join-Path $RepoRoot ($expectedRaw.Replace('/', '\'))
        if (-not (Test-Path -LiteralPath $rawPath -PathType Leaf)) {
            return New-HarnessNodeVerifyDecision -AllowGit $false -Conclusion "BLOCKED" -Reason "Node 原始 JSON 证据缺失，禁止 Git 候选收尾。"
        }
        $rawJson = Get-Content -Raw -Encoding UTF8 -LiteralPath $rawPath
        $actualRawJsonDigest = "sha256:$((Get-FileHash -LiteralPath $rawPath -Algorithm SHA256).Hash.ToLowerInvariant())"
        $actualStableJsonDigest = "sha256:$((Get-FileHash -LiteralPath $StableJsonPath -Algorithm SHA256).Hash.ToLowerInvariant())"
        $actualStableMarkdownDigest = "sha256:$((Get-FileHash -LiteralPath $StableMarkdownPath -Algorithm SHA256).Hash.ToLowerInvariant())"
        $checks = @($report.result.checks)
        $statusLabels = @{ PASS = "通过"; FAIL = "失败"; BLOCKED = "阻塞"; PARTIAL = "部分完成" }
        $identityKind = [string]$report.git.identity.kind
        $gitValid = $false
        if ($identityKind -eq "COMMIT") {
            $gitValid = $report.git.clean -eq $true `
                -and @($report.git.changedFiles).Count -eq 0 `
                -and $report.git.headSha -match '^(?:[a-f0-9]{40}|[a-f0-9]{64})$' `
                -and $report.git.identity.commitSha -eq $report.git.headSha
        }
        elseif ($identityKind -eq "WORKTREE") {
            $gitValid = $report.git.clean -eq $false `
                -and @($report.git.changedFiles).Count -gt 0 `
                -and $report.git.headSha -match '^(?:[a-f0-9]{40}|[a-f0-9]{64})$' `
                -and $report.git.identity.headSha -eq $report.git.headSha `
                -and (($report.git.identity.changedFiles | ConvertTo-Json -Compress) -eq ($report.git.changedFiles | ConvertTo-Json -Compress)) `
                -and $report.git.identity.patchFingerprint -match '^sha256:[a-f0-9]{64}$'
        }
        $checksValid = $checks.Count -gt 0
        foreach ($check in $checks) {
            $checksValid = $checksValid `
                -and $check.schemaVersion -eq "1.0.0" `
                -and -not [string]::IsNullOrWhiteSpace([string]$check.checkId) `
                -and -not [string]::IsNullOrWhiteSpace([string]$check.title) `
                -and $check.status -in @("PASS", "FAIL", "BLOCKED", "WARN", "SKIPPED", "NOT_COLLECTED") `
                -and $null -ne $check.blocking `
                -and -not [string]::IsNullOrWhiteSpace([string]$check.summary) `
                -and $null -ne $check.nextActions `
                -and $null -ne $check.artifacts
        }
        $consistent = $report.schemaVersion -eq "1.0.0" `
            -and $report.result.schemaVersion -eq "1.0.0" `
            -and $report.reportKey -eq $ExpectedReportKey `
            -and -not [string]::IsNullOrWhiteSpace($runId) `
            -and $runId -eq $ExpectedRunId `
            -and ([string]::IsNullOrWhiteSpace($PreviousRunId) -or $runId -ne $PreviousRunId) `
            -and $report.environment -eq $ExpectedEnv `
            -and $report.scope -eq $ExpectedScope `
            -and $status -eq $ExpectedReceiptStatus `
            -and $report.result.statusLabel -eq $statusLabels[$status] `
            -and -not [string]::IsNullOrWhiteSpace([string]$report.result.summary) `
            -and $report.evidencePaths.rawJson -eq $expectedRaw `
            -and $report.evidencePaths.stableJson -eq $expectedStableJson `
            -and $report.evidencePaths.stableMarkdown -eq $expectedStableMarkdown `
            -and $rawJson -ceq $stableJson `
            -and $actualRawJsonDigest -eq $ExpectedRawJsonDigest `
            -and $actualStableJsonDigest -eq $ExpectedStableJsonDigest `
            -and $actualStableMarkdownDigest -eq $ExpectedStableMarkdownDigest `
            -and $gitValid `
            -and $checksValid `
            -and $markdown.Contains($runId) `
            -and $markdown.Contains($status) `
            -and $markdown.Contains($expectedRaw) `
            -and $markdown.Contains($expectedStableJson) `
            -and $markdown.Contains($expectedStableMarkdown)
        if (-not $consistent) {
            return New-HarnessNodeVerifyDecision -AllowGit $false -Conclusion "BLOCKED" -Reason "Node 稳定证据身份不一致，禁止 Git 候选收尾。"
        }
        if ($ExitCode -eq 0 -and $status -eq "PASS") {
            return New-HarnessNodeVerifyDecision -AllowGit $true -Conclusion "PASS" -Reason "Node PASS 证据一致，可提交候选。"
        }
        if ($ExitCode -eq 2 -and $status -in @("BLOCKED", "PARTIAL")) {
            return New-HarnessNodeVerifyDecision -AllowGit $true -Conclusion $status -Reason "Node 非 PASS 证据一致，可保存候选但不得升级结论。"
        }
        return New-HarnessNodeVerifyDecision -AllowGit $false -Conclusion "BLOCKED" -Reason "Node 退出码与证据结论不一致，禁止 Git 候选收尾。"
    }
    catch {
        return New-HarnessNodeVerifyDecision -AllowGit $false -Conclusion "BLOCKED" -Reason "Node 稳定证据无法可信解析，禁止 Git 候选收尾。"
    }
}
