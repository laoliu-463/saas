$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$checker = Join-Path $repoRoot 'harness\scripts\check-harness-limits.ps1'
$allowedRootDirs = @(
    'rules', 'tasks', 'probes', 'reports', 'scripts', 'manifests', 'archive', 'templates', 'engineering',
    'src', 'contracts', 'state', 'tests'
)

function New-GovernanceTestRepo {
    param([string]$Name)

    $path = Join-Path $TestDrive $Name
    New-Item -ItemType Directory -Path $path -Force | Out-Null
    foreach ($dir in $allowedRootDirs) {
        New-Item -ItemType Directory -Path (Join-Path $path "harness\$dir") -Force | Out-Null
    }
    Set-Content -LiteralPath (Join-Path $path 'harness\README.md') -Value '# Harness' -Encoding UTF8

    Push-Location $path
    try {
        git init -q
        git config user.email 'harness-tests@example.invalid'
        git config user.name 'Harness Tests'
    }
    finally {
        Pop-Location
    }
    return $path
}

function Add-TextFile {
    param(
        [string]$Repo,
        [string]$RelativePath,
        [int]$Lines = 1
    )

    $fullPath = Join-Path $Repo $RelativePath
    New-Item -ItemType Directory -Path (Split-Path -Parent $fullPath) -Force | Out-Null
    $content = for ($i = 1; $i -le $Lines; $i++) { "line-$i" }
    Set-Content -LiteralPath $fullPath -Value $content -Encoding UTF8
}

function Save-Baseline {
    param([string]$Repo)

    Push-Location $Repo
    try {
        git add -- harness
        git commit -q -m 'test: baseline'
    }
    finally {
        Pop-Location
    }
}

function Add-LegacyRootReports {
    param(
        [string]$Repo,
        [int]$Count
    )

    for ($i = 1; $i -le $Count; $i++) {
        Add-TextFile -Repo $Repo -RelativePath ("harness\reports\latest-legacy-{0:D3}.md" -f $i)
    }
}

function Invoke-GovernanceCheck {
    param(
        [string]$Repo,
        [string[]]$OwnedFiles = @()
    )

    $arguments = @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', $checker,
        '-RepoRoot', $Repo,
        '-BaselineRef', 'HEAD',
        '-NoReport'
    )
    if ($OwnedFiles.Count -gt 0) {
        $arguments += '-OwnedFiles'
        $arguments += ($OwnedFiles -join ';')
    }

    $output = & powershell @arguments 2>&1
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = ($output -join "`n")
    }
}

Describe 'check-harness-limits baseline-aware governance' {
    It 'does not block unchanged historical report debt' {
        $repo = New-GovernanceTestRepo -Name 'unchanged-debt'
        Add-LegacyRootReports -Repo $repo -Count 89
        Save-Baseline -Repo $repo

        $result = Invoke-GovernanceCheck -Repo $repo

        $result.ExitCode | Should Be 0
        $result.Output | Should Match 'TASK_GATE=PASS'
        $result.Output | Should Match 'REPOSITORY_HEALTH=PARTIAL'
        $result.Output | Should Not Match 'ROOT_DIRECTORY_NOT_ALLOWED'
    }

    It 'blocks a directory count increase above an over-limit baseline' {
        $repo = New-GovernanceTestRepo -Name 'worsened-debt'
        Add-LegacyRootReports -Repo $repo -Count 89
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\reports\latest-new.md'

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/reports/latest-new.md')

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'TASK_GATE=FAIL'
        $result.Output | Should Match 'DIRECT_FILE_COUNT_WORSENED'
    }

    It 'allows debt reduction while repository health remains partial' {
        $repo = New-GovernanceTestRepo -Name 'reduced-debt'
        Add-LegacyRootReports -Repo $repo -Count 89
        Save-Baseline -Repo $repo
        Remove-Item -LiteralPath (Join-Path $repo 'harness\reports\latest-legacy-089.md')

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/reports/latest-legacy-089.md')

        $result.ExitCode | Should Be 0
        $result.Output | Should Match 'TASK_GATE=PASS'
        $result.Output | Should Match 'REPOSITORY_HEALTH=PARTIAL'
    }

    It 'warns at forty direct files without failing' {
        $repo = New-GovernanceTestRepo -Name 'file-warning'
        for ($i = 1; $i -le 39; $i++) {
            Add-TextFile -Repo $repo -RelativePath ("harness\rules\rule-{0:D2}.md" -f $i)
        }
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\rules\rule-40.md'

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/rules/rule-40.md')

        $result.ExitCode | Should Be 0
        $result.Output | Should Match 'DIRECT_FILE_COUNT_WARNING'
    }

    It 'blocks the fifty-first direct file' {
        $repo = New-GovernanceTestRepo -Name 'file-hard-limit'
        for ($i = 1; $i -le 50; $i++) {
            Add-TextFile -Repo $repo -RelativePath ("harness\rules\rule-{0:D2}.md" -f $i)
        }
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\rules\rule-51.md'

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/rules/rule-51.md')

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'DIRECT_FILE_COUNT_EXCEEDED'
    }

    It 'warns at one hundred sixty lines and blocks at two hundred one' {
        $repo = New-GovernanceTestRepo -Name 'line-budgets'
        Add-TextFile -Repo $repo -RelativePath 'harness\rules\warning.md' -Lines 160
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\rules\too-long.md' -Lines 201

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/rules/warning.md', 'harness/rules/too-long.md')

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'TEXT_LINE_COUNT_WARNING'
        $result.Output | Should Match 'TEXT_LINE_COUNT_EXCEEDED'
    }

    It 'exempts only the root Harness package lock from the text line budget' {
        $repo = New-GovernanceTestRepo -Name 'root-package-lock'
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\package-lock.json' -Lines 201

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/package-lock.json')

        $result.ExitCode | Should Be 0
        $result.Output | Should Match 'TASK_GATE=PASS'
        $result.Output | Should Match 'REPOSITORY_HEALTH=PASS'
        $result.Output | Should Not Match 'TEXT_LINE_COUNT_EXCEEDED'
    }

    It 'exempts a project root package lock from the text line budget' {
        $repo = New-GovernanceTestRepo -Name 'project-package-lock'
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'frontend\package-lock.json' -Lines 201

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('frontend/package-lock.json')

        $result.ExitCode | Should Be 0
        $result.Output | Should Match 'TASK_GATE=PASS'
        $result.Output | Should Not Match 'TEXT_LINE_COUNT_EXCEEDED'
    }

    It 'does not exempt another package lock from the text line budget' {
        $repo = New-GovernanceTestRepo -Name 'nested-package-lock'
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\contracts\package-lock.json' -Lines 201

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/contracts/package-lock.json')

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'TEXT_LINE_COUNT_EXCEEDED'
    }

    It 'blocks a new root timestamp report even when total count does not grow' {
        $repo = New-GovernanceTestRepo -Name 'timestamp-report'
        Add-LegacyRootReports -Repo $repo -Count 20
        Save-Baseline -Repo $repo
        Remove-Item -LiteralPath (Join-Path $repo 'harness\reports\latest-legacy-020.md')
        Add-TextFile -Repo $repo -RelativePath 'harness\reports\evidence-20260713-000000.md'

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @(
            'harness/reports/latest-legacy-020.md',
            'harness/reports/evidence-20260713-000000.md'
        )

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'TIMESTAMP_REPORT_IN_ROOT'
    }

    It 'does not apply the text line limit to scripts' {
        $repo = New-GovernanceTestRepo -Name 'script-lines'
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\scripts\large.ps1' -Lines 250

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/scripts/large.ps1')

        $result.ExitCode | Should Be 0
        $result.Output | Should Not Match 'TEXT_LINE_COUNT_EXCEEDED'
    }

    It 'allows an unchanged legacy text blob to move into archive' {
        $repo = New-GovernanceTestRepo -Name 'legacy-archive-move'
        Add-TextFile -Repo $repo -RelativePath 'harness\reports\evidence-legacy.md' -Lines 221
        Save-Baseline -Repo $repo

        $archivePath = Join-Path $repo 'harness\archive\by-date\2026-07-13\evidence\evidence-legacy.md'
        New-Item -ItemType Directory -Path (Split-Path -Parent $archivePath) -Force | Out-Null
        Move-Item -LiteralPath (Join-Path $repo 'harness\reports\evidence-legacy.md') -Destination $archivePath

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @(
            'harness/reports/evidence-legacy.md',
            'harness/archive/by-date/2026-07-13/evidence/evidence-legacy.md'
        )

        $result.ExitCode | Should Be 0
        $result.Output | Should Match 'TASK_GATE=PASS'
        $result.Output | Should Match 'REPOSITORY_HEALTH=PASS'
        $result.Output | Should Not Match 'TEXT_LINE_COUNT_EXCEEDED'
    }

    It 'allows the src root directory' {
        $repo = New-GovernanceTestRepo -Name 'root-src'
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\src\entry.ts'

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/src/entry.ts')

        $result.ExitCode | Should Be 0
        $result.Output | Should Not Match 'ROOT_DIRECTORY_NOT_ALLOWED'
    }

    It 'allows the contracts root directory' {
        $repo = New-GovernanceTestRepo -Name 'root-contracts'
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\contracts\result.schema.json'

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/contracts/result.schema.json')

        $result.ExitCode | Should Be 0
        $result.Output | Should Not Match 'ROOT_DIRECTORY_NOT_ALLOWED'
    }

    It 'allows the state root directory' {
        $repo = New-GovernanceTestRepo -Name 'root-state'
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\state\release-baseline.json'

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/state/release-baseline.json')

        $result.ExitCode | Should Be 0
        $result.Output | Should Not Match 'ROOT_DIRECTORY_NOT_ALLOWED'
    }

    It 'allows the tests root directory' {
        $repo = New-GovernanceTestRepo -Name 'root-tests'
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\tests\governance.test.ts'

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/tests/governance.test.ts')

        $result.ExitCode | Should Be 0
        $result.Output | Should Not Match 'ROOT_DIRECTORY_NOT_ALLOWED'
    }

    It 'blocks an unknown fourteenth root directory' {
        $repo = New-GovernanceTestRepo -Name 'root-fourteenth'
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\unknown-root\file.md'

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/unknown-root/file.md')

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'ROOT_DIRECTORY_NOT_ALLOWED'
    }

    It 'ignores generated Harness node_modules without allowing another root directory' {
        $repo = New-GovernanceTestRepo -Name 'ignored-node-modules'
        Set-Content -LiteralPath (Join-Path $repo '.gitignore') -Value 'node_modules/' -Encoding UTF8
        Save-Baseline -Repo $repo
        for ($i = 1; $i -le 51; $i++) {
            Add-TextFile -Repo $repo -RelativePath ("harness\node_modules\dependency\file-{0:D2}.json" -f $i)
        }
        Add-TextFile -Repo $repo -RelativePath 'harness\node_modules\large.json' -Lines 201

        $result = Invoke-GovernanceCheck -Repo $repo

        $result.ExitCode | Should Be 0
        $result.Output | Should Match 'TASK_GATE=PASS'
        $result.Output | Should Match 'REPOSITORY_HEALTH=PASS'
        $result.Output | Should Not Match 'node_modules'
    }

    It 'blocks a force-added tracked Harness node_modules file regardless of owned files' {
        $repo = New-GovernanceTestRepo -Name 'tracked-node-modules'
        Set-Content -LiteralPath (Join-Path $repo '.gitignore') -Value 'node_modules/' -Encoding UTF8
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\node_modules\dependency\index.json'
        Push-Location $repo
        try {
            git add -f -- 'harness/node_modules/dependency/index.json'
        }
        finally {
            Pop-Location
        }

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/README.md')

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'TASK_GATE=FAIL'
        $result.Output | Should Match 'TRACKED_GENERATED_DEPENDENCY'
        $result.Output | Should Match 'harness/node_modules/dependency/index.json'
    }

    It 'expands an untracked directory when owned files are derived' {
        $repo = New-GovernanceTestRepo -Name 'untracked-directory'
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\rules\new-topic\too-long.md' -Lines 201

        $result = Invoke-GovernanceCheck -Repo $repo

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'TASK_GATE=FAIL'
        $result.Output | Should Match 'harness/rules/new-topic/too-long.md'
        $result.Output | Should Match 'TEXT_LINE_COUNT_EXCEEDED'
    }

    It 'writes a stable report with exactly one trailing newline' {
        $repo = New-GovernanceTestRepo -Name 'stable-report-newline'
        Save-Baseline -Repo $repo

        $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $checker `
            -RepoRoot $repo -BaselineRef HEAD 2>&1
        $exitCode = $LASTEXITCODE
        $reportPath = Join-Path $repo 'harness\reports\current\latest-harness-limits-check.md'
        $bytes = [System.IO.File]::ReadAllBytes($reportPath)

        $exitCode | Should Be 0
        ($output -join "`n") | Should Match 'TASK_GATE=PASS'
        $bytes[$bytes.Length - 1] | Should Be 10
        $bytes[$bytes.Length - 2] | Should Not Be 10
    }
}
