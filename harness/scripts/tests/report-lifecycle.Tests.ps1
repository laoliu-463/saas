$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$library = Join-Path $projectRoot 'harness\scripts\commands\_lib.ps1'
$gitPush = Join-Path $projectRoot 'harness\scripts\commands\git-push-safe.ps1'
$collectEvidence = Join-Path $projectRoot 'harness\scripts\commands\collect-evidence.ps1'
$newRetro = Join-Path $projectRoot 'harness\scripts\commands\new-retro.ps1'
$agentDo = Join-Path $projectRoot 'harness\scripts\commands\agent-do.ps1'

. $library

function New-ReportTestRepo {
    param([string]$Name)

    $path = Join-Path $TestDrive $Name
    New-Item -ItemType Directory -Path $path -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $path 'harness\reports\current') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $path 'owned.md') -Value 'baseline-owned' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $path 'unrelated.md') -Value 'baseline-unrelated' -Encoding UTF8
    Push-Location $path
    try {
        git init -q
        git config user.email 'harness-tests@example.invalid'
        git config user.name 'Harness Tests'
        git add -- owned.md unrelated.md
        git commit -q -m 'test: baseline'
    }
    finally {
        Pop-Location
    }
    return $path
}

function Invoke-GitPushDryRun {
    param(
        [string]$Repo,
        [string[]]$OwnedFiles
    )

    $arguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-File', $gitPush,
        '-RepoRoot', $Repo,
        '-Message', 'test: owned files',
        '-DryRun'
    )
    if ($null -ne $OwnedFiles -and $OwnedFiles.Count -gt 0) {
        $arguments += '-OwnedFiles'
        $arguments += ($OwnedFiles -join ';')
    }
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & powershell @arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = ($output -join "`n") }
}

Describe 'stable Harness report lifecycle' {
    It 'builds a stable current report path' {
        $repo = New-ReportTestRepo -Name 'stable-path'

        $actual = New-HarnessReportPath -RepoRoot $repo -ReportKey 'file-governance'

        $actual | Should Be (Join-Path $repo 'harness\reports\current\latest-file-governance.md')
    }

    It 'rejects report key path traversal' {
        $repo = New-ReportTestRepo -Name 'path-traversal'

        $thrown = $false
        try {
            [void](New-HarnessReportPath -RepoRoot $repo -ReportKey '..\escape')
        }
        catch {
            $thrown = $true
            $_.Exception.Message | Should Match 'must not contain path segments'
        }
        $thrown | Should Be $true
    }

    It 'lists only owned changed files in git dry-run' {
        $repo = New-ReportTestRepo -Name 'owned-only'
        Set-Content -LiteralPath (Join-Path $repo 'owned.md') -Value 'changed-owned' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $repo 'unrelated.md') -Value 'changed-unrelated' -Encoding UTF8

        $result = Invoke-GitPushDryRun -Repo $repo -OwnedFiles @('owned.md')

        $result.ExitCode | Should Be 0
        $result.Output | Should Match 'owned.md'
        $result.Output | Should Not Match 'unrelated.md'
    }

    It 'rejects an empty owned set when changes exist' {
        $repo = New-ReportTestRepo -Name 'empty-owned'
        Set-Content -LiteralPath (Join-Path $repo 'owned.md') -Value 'changed-owned' -Encoding UTF8

        $result = Invoke-GitPushDryRun -Repo $repo -OwnedFiles @()

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'OwnedFiles is required'
    }

    It 'rejects a pre-staged file outside the owned set' {
        $repo = New-ReportTestRepo -Name 'staged-outside'
        Set-Content -LiteralPath (Join-Path $repo 'owned.md') -Value 'changed-owned' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $repo 'unrelated.md') -Value 'changed-unrelated' -Encoding UTF8
        Push-Location $repo
        try { git add -- unrelated.md } finally { Pop-Location }

        $result = Invoke-GitPushDryRun -Repo $repo -OwnedFiles @('owned.md')

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'Staged file is outside OwnedFiles'
    }

    It 'writes one stable evidence report with owned files and inline retro' {
        $repo = New-ReportTestRepo -Name 'evidence'
        Set-Content -LiteralPath (Join-Path $repo 'owned.md') -Value 'changed-owned' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $repo 'unrelated.md') -Value 'changed-unrelated' -Encoding UTF8

        $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $collectEvidence `
            -RepoRoot $repo `
            -Env real-pre `
            -Scope docs `
            -ReportKey file-governance `
            -OwnedFiles 'owned.md' `
            -RetroSummary 'No separate retro required.' `
            -SkipRuntimeCollection 2>&1
        $exitCode = $LASTEXITCODE
        $reportPath = Join-Path $repo 'harness\reports\current\latest-file-governance.md'

        $exitCode | Should Be 0
        $reportPath | Should Exist
        $content = Get-Content -Raw -LiteralPath $reportPath
        $content | Should Match 'owned.md'
        $content | Should Not Match 'unrelated.md'
        $content | Should Match 'No separate retro required.'
        ($output -join "`n") | Should Match ([regex]::Escape($reportPath))
    }

    It 'does not create a standalone retro without an actionable improvement' {
        $repo = New-ReportTestRepo -Name 'retro-inline-only'

        $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $newRetro `
            -RepoRoot $repo -ReportKey file-governance 2>&1
        $exitCode = $LASTEXITCODE

        $exitCode | Should Be 0
        ($output -join "`n") | Should Match 'Retro not generated'
        (Join-Path $repo 'harness\reports\current\latest-retro-file-governance.md') | Should Not Exist
    }

    It 'writes a stable standalone retro only for an actionable improvement' {
        $repo = New-ReportTestRepo -Name 'retro-actionable'

        $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $newRetro `
            -RepoRoot $repo `
            -ReportKey file-governance `
            -Actionable `
            -Owner harness-team `
            -NextAction 'Run the regression suite.' `
            -Verification 'Pester passes.' 2>&1
        $exitCode = $LASTEXITCODE
        $reportPath = Join-Path $repo 'harness\reports\current\latest-retro-file-governance.md'

        $exitCode | Should Be 0
        $reportPath | Should Exist
        $content = Get-Content -Raw -LiteralPath $reportPath
        $content | Should Match 'harness-team'
        $content | Should Match 'Run the regression suite.'
        $content | Should Match 'Pester passes.'
        ($output -join "`n") | Should Match ([regex]::Escape($reportPath))
    }

    It 'wires owned files and one inline retro through agent-do' {
        $content = Get-Content -Raw -LiteralPath $agentDo
        $tokens = $null
        $parseErrors = $null
        $ast = [System.Management.Automation.Language.Parser]::ParseFile($agentDo, [ref]$tokens, [ref]$parseErrors)
        $parameterNames = @($ast.ParamBlock.Parameters.Name.VariablePath.UserPath)

        $parseErrors.Count | Should Be 0
        ($parameterNames -contains 'OwnedFiles') | Should Be $true
        ($parameterNames -contains 'ReportKey') | Should Be $true
        $content | Should Match 'check-harness-limits\.ps1'
        $content | Should Match 'collect-evidence\.ps1[\s\S]+-OwnedFiles'
        $content | Should Match 'git-push-safe\.ps1[\s\S]+-OwnedFiles'
        $content | Should Not Match 'new-retro\.ps1'
    }
}
