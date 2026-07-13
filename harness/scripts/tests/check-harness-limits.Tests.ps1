$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$checker = Join-Path $repoRoot 'harness\scripts\check-harness-limits.ps1'
$allowedRootDirs = @('rules', 'tasks', 'probes', 'reports', 'scripts', 'manifests', 'archive', 'templates', 'engineering')

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

    It 'blocks a new non-whitelisted root directory' {
        $repo = New-GovernanceTestRepo -Name 'root-whitelist'
        Save-Baseline -Repo $repo
        Add-TextFile -Repo $repo -RelativePath 'harness\rogue\file.md'

        $result = Invoke-GovernanceCheck -Repo $repo -OwnedFiles @('harness/rogue/file.md')

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'ROOT_DIRECTORY_NOT_ALLOWED'
    }
}
