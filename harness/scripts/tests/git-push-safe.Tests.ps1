$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$gitPushSafeScript = Join-Path $repoRoot 'harness\scripts\commands\git-push-safe.ps1'
$libraryScript = Join-Path $repoRoot 'harness\scripts\commands\_lib.ps1'
$content = Get-Content -Raw -LiteralPath $gitPushSafeScript
. $libraryScript

Describe 'git-push-safe upstream contract' {
    It 'pushes HEAD to the configured upstream branch when local and remote names differ' {
        $content | Should Match 'branch\.\$branch\.remote'
        $content | Should Match 'branch\.\$branch\.merge'
        $content | Should Match 'HEAD:refs/heads/'
        $content | Should Not Match 'git push[^\r\n]*--force'
    }
}

Describe 'git-push-safe owned path contract' {
    It 'preserves dot-prefixed repository directories' {
        $expanded = @(Expand-HarnessOwnedFiles -OwnedFiles @('.github/CODEOWNERS'))

        $expanded.Count | Should Be 1
        $expanded[0] | Should Be '.github/CODEOWNERS'
    }

    It 'enumerates files inside untracked directories' {
        $content | Should Match 'status --porcelain=v1 --untracked-files=all'
    }
}
