$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$agentDoScript = Join-Path $repoRoot 'harness\scripts\commands\agent-do.ps1'
$content = Get-Content -Raw -LiteralPath $agentDoScript

Describe 'agent-do evidence conclusion contract' {
    It 'keeps remote deployment partial when business validation is skipped' {
        $content | Should Match '\$remoteConclusion = if \(\$SkipBusinessValidation'
        $content | Should Match '-Conclusion \$remoteConclusion'
        $content | Should Match '\$conclusion = \$remoteConclusion'
    }

    It 'skips runtime collection for docs and apifox evidence' {
        $matches = [regex]::Matches(
            $content,
            '-SkipRuntimeCollection:\(\$Scope -eq "docs" -or \$Scope -eq "apifox"\)'
        )

        $matches.Count | Should BeGreaterThan 2
    }
}
