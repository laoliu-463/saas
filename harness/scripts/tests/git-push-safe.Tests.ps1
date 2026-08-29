$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$gitPushSafeScript = Join-Path $repoRoot 'harness\scripts\commands\git-push-safe.ps1'
$content = Get-Content -Raw -LiteralPath $gitPushSafeScript

Describe 'git-push-safe upstream contract' {
    It 'pushes HEAD to the configured upstream branch when local and remote names differ' {
        $content | Should Match 'branch\.\$branch\.remote'
        $content | Should Match 'branch\.\$branch\.merge'
        $content | Should Match 'HEAD:refs/heads/'
        $content | Should Not Match 'git push[^\r\n]*--force'
    }
}
