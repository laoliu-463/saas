$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$safetyCheck = Join-Path $repoRoot 'harness\scripts\commands\safety-check.ps1'
$realPreEnv = Join-Path $repoRoot '.env.real-pre'
$powerShellHost = (Get-Process -Id $PID).Path

Describe 'docs scope safety check contract' {
    It 'does not require a runtime env file for docs and governance changes' {
        (Test-Path -LiteralPath $realPreEnv) | Should Be $false

        $output = & $powerShellHost -NoProfile -ExecutionPolicy Bypass -File $safetyCheck `
            -Env real-pre -Scope docs 2>&1
        $exitCode = $LASTEXITCODE

        $exitCode | Should Be 0
        ($output -join "`n") | Should Match 'Runtime environment config: not required for Scope=docs'
        ($output -join "`n") | Should Match 'Safety check passed'
    }
}
