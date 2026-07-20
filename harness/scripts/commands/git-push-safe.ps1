param(
    [Parameter(Mandatory = $true)][string]$Message,
    [AllowEmptyCollection()][string[]]$OwnedFiles = @(),
    [string]$RepoRoot = '',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_lib.ps1')

if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Get-HarnessRepoRoot }
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path

function Get-ChangedFiles {
    $lines = @(& git -c core.quotepath=false status --porcelain=v1 --untracked-files=all)
    if ($LASTEXITCODE -ne 0) { throw 'git status failed.' }
    $files = @()
    foreach ($line in $lines) {
        if ($line.Length -lt 4) { continue }
        $path = $line.Substring(3).Trim().Trim('"')
        if ($path -match '\s+->\s+') { $path = ($path -split '\s+->\s+')[-1].Trim().Trim('"') }
        if ($path) { $files += $path.Replace('\', '/') }
    }
    return @($files | Sort-Object -Unique)
}

function Assert-NoSensitiveFile {
    param([string[]]$Files)
    foreach ($file in $Files) {
        $name = Split-Path -Leaf $file
        $lower = $file.ToLowerInvariant()
        $isEnv = ($name -like '.env*' -and -not $name.EndsWith('.example'))
        if ($isEnv -or $lower -match '\.(pem|key|p12|jks)$' -or $name -match '^(credentials|secrets)') {
            throw "Sensitive file must not be committed: $file"
        }
    }
}

function Assert-NoPlainSecrets {
    param([string[]]$Files)
    # Require a quoted literal for general source/JSON assignments. Keep a separate
    # anchored pattern for unquoted config values; this avoids treating expressions
    # such as `refreshToken = getRefreshToken(...)` or Redis key names as secrets.
    $quotedPattern = '[''\"]?(password|secret|token|client_secret|jwt_secret)[''\"]?\s*[:=]\s*[''\"][A-Za-z0-9_\-/.+=]{12,}'
    $unquotedConfigPattern = '^\s*[A-Za-z0-9_.-]*(password|secret|token|client_secret|jwt_secret)[A-Za-z0-9_.-]*\s*[:=]\s*[A-Za-z0-9_\-/.+=]{12,}\s*(?:#.*)?$'
    foreach ($file in $Files) {
        $path = Join-Path $RepoRoot $file
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        $name = Split-Path -Leaf $file
        if ($name -like '*.md' -or $name -like '*.prompt.md' -or $name -like '*.skill.md') { continue }
        $hits = @(
            Select-String -LiteralPath $path -Pattern $quotedPattern -ErrorAction SilentlyContinue
            Select-String -LiteralPath $path -Pattern $unquotedConfigPattern -ErrorAction SilentlyContinue
        )
        foreach ($hit in $hits) {
            $line = $hit.Line
            if ($line -match 'REDACTED|placeholder|example|change-me|__FILL_ME_' -or $line.Contains('$' + '{')) { continue }
            throw "Potential plaintext secret in $file line $($hit.LineNumber)."
        }
    }
}

Write-HarnessStage 'Git push safe'
if ([string]::IsNullOrWhiteSpace($Message) -or $Message.Trim().Length -lt 8) {
    throw 'Commit message is required and must be readable.'
}

Push-Location $RepoRoot
try {
    & git rev-parse --is-inside-work-tree | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Not a Git worktree: $RepoRoot" }

    $changedFiles = @(Get-ChangedFiles)
    if ($changedFiles.Count -eq 0) {
        Write-Host 'No changes to commit.' -ForegroundColor Yellow
        return
    }

    $owned = @(Expand-HarnessOwnedFiles -OwnedFiles $OwnedFiles)
    if ($owned.Count -eq 0) { throw 'OwnedFiles is required when the worktree has changes.' }
    $ownedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($file in $owned) { [void]$ownedSet.Add($file) }

    $stagedBefore = @(& git -c core.quotepath=false diff --cached --name-only)
    foreach ($file in $stagedBefore) {
        if (-not $ownedSet.Contains($file.Replace('\', '/'))) {
            throw "Staged file is outside OwnedFiles: $file"
        }
    }

    $ownedChanged = @($changedFiles | Where-Object { $ownedSet.Contains($_) })
    if ($ownedChanged.Count -eq 0) {
        Write-Host 'No owned changes to commit.' -ForegroundColor Yellow
        return
    }
    Assert-NoSensitiveFile -Files $ownedChanged
    Assert-NoPlainSecrets -Files $ownedChanged

    if ($DryRun) {
        Write-Host 'DRY-RUN owned changed files:'
        foreach ($file in $ownedChanged) { Write-Host "- $file" }
        Write-Host "DRY-RUN would commit with message `"$Message`" and push the current upstream."
        return
    }

    foreach ($file in $ownedChanged) {
        & git add -- $file
        if ($LASTEXITCODE -ne 0) { throw "git staging failed: $file" }
    }

    $stagedFiles = @(& git -c core.quotepath=false diff --cached --name-only)
    foreach ($file in $stagedFiles) {
        if (-not $ownedSet.Contains($file.Replace('\', '/'))) {
            throw "Staged file is outside OwnedFiles: $file"
        }
    }
    Assert-NoSensitiveFile -Files $stagedFiles
    Assert-NoPlainSecrets -Files $stagedFiles
    & git diff --cached --check
    if ($LASTEXITCODE -ne 0) { throw 'git diff --cached --check failed.' }

    & git commit -m $Message
    if ($LASTEXITCODE -ne 0) { throw 'git commit failed.' }

    $branch = (& git branch --show-current).Trim()
    if ([string]::IsNullOrWhiteSpace($branch)) { throw 'Cannot determine current branch for push.' }
    $upstream = (& git for-each-ref --format='%(upstream:short)' "refs/heads/$branch").Trim()
    if (-not [string]::IsNullOrWhiteSpace($upstream)) {
        $upstreamRemote = (& git config --get "branch.$branch.remote").Trim()
        $upstreamMerge = (& git config --get "branch.$branch.merge").Trim()
        if ([string]::IsNullOrWhiteSpace($upstreamRemote) -or $upstreamRemote -eq '.') {
            throw "Configured upstream is not a pushable remote: branch=$branch remote=$upstreamRemote"
        }
        if ($upstreamMerge -notmatch '^refs/heads/(?<name>.+)$') {
            throw "Configured upstream branch is invalid: branch=$branch merge=$upstreamMerge"
        }
        $upstreamBranch = $matches['name']
        & git push $upstreamRemote "HEAD:refs/heads/$upstreamBranch"
    }
    else {
        & git push --set-upstream origin $branch
    }
    if ($LASTEXITCODE -ne 0) { throw 'git push failed. Check upstream and credentials.' }
    Write-Host "Git push completed: $((& git rev-parse --short HEAD).Trim())" -ForegroundColor Green
}
finally {
    Pop-Location
}
