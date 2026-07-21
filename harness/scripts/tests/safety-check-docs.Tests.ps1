$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$safetyCheck = Join-Path $repoRoot 'harness\scripts\commands\safety-check.ps1'
$powerShellHost = (Get-Process -Id $PID).Path

Describe 'docs scope safety check contract' {
    It 'does not require a runtime env file for docs and governance changes' {
        $trackedEnv = @(& git -C $repoRoot ls-files -- '.env.real-pre' 2>$null)
        $trackedEnv.Count | Should Be 0

        & git -C $repoRoot check-ignore --quiet --no-index '.env.real-pre' 2>$null
        $ignoreExitCode = $LASTEXITCODE
        $ignoreExitCode | Should Be 0

        $output = & $powerShellHost -NoProfile -ExecutionPolicy Bypass -File $safetyCheck `
            -Env real-pre -Scope docs 2>&1
        $exitCode = $LASTEXITCODE

        $exitCode | Should Be 0
        ($output -join "`n") | Should Match 'Runtime environment config: not required for Scope=docs'
        ($output -join "`n") | Should Match 'Safety check passed'
    }
}
