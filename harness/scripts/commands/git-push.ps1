param(
    [string]$RepoRoot = '',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path }
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path

Push-Location $RepoRoot
try {
    $status = @(& git status --porcelain --untracked-files=all)
    if ($status.Count -gt 0) { throw 'Worktree must be clean before an explicit push.' }
    $branch = (& git branch --show-current).Trim()
    if ([string]::IsNullOrWhiteSpace($branch)) { throw 'Cannot determine current branch for push.' }
    $upstream = (& git for-each-ref --format='%(upstream:short)' "refs/heads/$branch").Trim()
    $remote = (& git config --get "branch.$branch.remote").Trim()
    if ([string]::IsNullOrWhiteSpace($remote) -or $remote -eq '.') { $remote = 'origin' }
    $target = "refs/heads/$branch"
    if ($DryRun) {
        Write-Output "DRY-RUN would push branch $branch to $remote/$branch."
        return
    }
    & git push --set-upstream $remote "HEAD:$target"
    if ($LASTEXITCODE -ne 0) { throw 'git push failed.' }
    Write-Host "Git push completed: $((& git rev-parse --short HEAD).Trim())" -ForegroundColor Green
}
finally { Pop-Location }
