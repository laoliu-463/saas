$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$agentDoScript = Join-Path $repoRoot 'harness\scripts\commands\agent-do.ps1'
$content = Get-Content -Raw -LiteralPath $agentDoScript

Describe 'agent-do real-pre business validation contract' {
    It 'injects the canonical admin credential only for the validation process and restores it' {
        $content | Should Match 'Read-HarnessEnvFile -Path \$config\.EnvFile'
        $content | Should Match "\['ADMIN_PASSWORD'\]"
        $content | Should Match '\$env:QA_ADMIN_PASSWORD'
        $content | Should Match 'Remove-Item Env:QA_ADMIN_PASSWORD'
        $content | Should Match 'value redacted'

        $credentialInjection = $content.IndexOf('$env:QA_ADMIN_PASSWORD')
        $businessInvocation = $content.IndexOf('powershell -NoProfile -ExecutionPolicy Bypass -Command $effectiveBusinessCommand')
        $credentialInjection | Should BeGreaterThan -1
        $businessInvocation | Should BeGreaterThan $credentialInjection
    }
}
