param(
    [string]$RepoRoot = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_lib.ps1')
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Get-HarnessRepoRoot }
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path

Push-Location $RepoRoot
try {
    $branch = (& git branch --show-current).Trim()
    $commit = (& git rev-parse HEAD).Trim()
    $status = @(& git -c core.quotepath=false status --short --untracked-files=all)
    $changed = @(& git -c core.quotepath=false diff --name-only HEAD)
    Write-Output "Branch=$branch"
    Write-Output "Commit=$commit"
    Write-Output "Worktree=$(if ($status.Count -eq 0) { 'clean' } else { 'dirty' })"
    Write-Output 'ChangedFiles:'
    if ($changed.Count -eq 0) { Write-Output '(none)' }
    else { $changed | ForEach-Object { Write-Output $_ } }
}
finally { Pop-Location }
