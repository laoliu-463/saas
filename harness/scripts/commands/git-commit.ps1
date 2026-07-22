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
if ([string]::IsNullOrWhiteSpace($Message) -or $Message.Trim().Length -lt 8) { throw 'Commit message is required and must be readable.' }

$owned = @(Expand-HarnessOwnedFiles -OwnedFiles $OwnedFiles)
if ($owned.Count -eq 0) { throw 'OwnedFiles is required for an explicit commit.' }

Push-Location $RepoRoot
try {
    # Reuse the existing secret/path scanner in read-only mode. This command
    # deliberately stops before the legacy helper can commit or push anything.
    & (Join-Path $PSScriptRoot 'git-push-safe.ps1') -Message $Message -OwnedFiles $owned -RepoRoot $RepoRoot -DryRun
    if ($DryRun) { return }
    foreach ($file in $owned) {
        & git add -- $file
        if ($LASTEXITCODE -ne 0) { throw "git add failed: $file" }
    }
    & git diff --cached --check
    if ($LASTEXITCODE -ne 0) { throw 'git diff --cached --check failed.' }
    & git commit -m $Message
    if ($LASTEXITCODE -ne 0) { throw 'git commit failed.' }
    Write-Host "Git commit created: $((& git rev-parse --short HEAD).Trim())" -ForegroundColor Green
}
finally { Pop-Location }
