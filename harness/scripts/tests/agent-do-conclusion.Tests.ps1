$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$agentDoScript = Join-Path $repoRoot 'harness\scripts\commands\agent-do.ps1'
$content = Get-Content -Raw -LiteralPath $agentDoScript

Describe 'agent-do evidence conclusion contract' {
    It 'routes all remote deployments through the Jenkins release queue' {
        $content | Should Match 'Direct remote deployment is disabled'
        $content | Should Match 'Jenkins release queue'
        $content | Should Not Match 'deploy-remote\.ps1'
        $content | Should Not Match 'git-push-safe\.ps1'
    }

    It 'skips runtime collection for non-runtime scopes' {
        $matches = [regex]::Matches(
            $content,
            '-SkipRuntimeCollection:\(\$Scope -in @\("docs", "apifox", "deploy", "ci"\)\)'
        )

        $matches.Count | Should BeGreaterThan 1
    }
}
