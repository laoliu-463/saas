$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$retireContent = Join-Path $projectRoot 'harness\scripts\commands\retire-content.ps1'

function New-RetireTestRepo {
    param([string]$Name)

    $repo = Join-Path $TestDrive $Name
    foreach ($dir in @('backend', 'frontend', 'harness\reports', 'harness\manifests', 'harness\archive')) {
        New-Item -ItemType Directory -Path (Join-Path $repo $dir) -Force | Out-Null
    }
    Set-Content -LiteralPath (Join-Path $repo 'AGENTS.md') -Value '# Test' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $repo 'docker-compose.test.yml') -Value 'services: {}' -Encoding UTF8
    Push-Location $repo
    try {
        git init -q
        git config user.email 'harness-tests@example.invalid'
        git config user.name 'Harness Tests'
    }
    finally { Pop-Location }
    return $repo
}

function Write-RetireFixture {
    param(
        [string]$Repo,
        [string]$ArchiveGroup
    )

    $sourceRelative = 'harness/reports/evidence-20260713-000000.md'
    Set-Content -LiteralPath (Join-Path $Repo $sourceRelative) -Value '# Evidence' -Encoding UTF8
    $manifest = @{
        items = @(@{
            path = $sourceRelative
            action = 'archive'
            category = 'report-root-debt'
            reason = 'test grouped archive'
            archiveGroup = $ArchiveGroup
        })
    } | ConvertTo-Json -Depth 5
    Set-Content -LiteralPath (Join-Path $Repo 'harness\manifests\test-retire.json') -Value $manifest -Encoding UTF8
    return $sourceRelative
}

function Invoke-RetireContent {
    param(
        [string]$Repo,
        [switch]$DryRun
    )

    $arguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-File', $retireContent,
        '-RepoRoot', $Repo,
        '-Action', 'Archive',
        '-Manifest', 'harness/manifests/test-retire.json',
        '-ArchiveRoot', 'harness/archive/by-date/2026-07-13/harness-report-root'
    )
    if ($DryRun) { $arguments += '-DryRun' }
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    Push-Location $Repo
    try {
        $output = & powershell @arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = ($output -join "`n") }
}

Describe 'retire-content grouped archive' {
    It 'plans an archive under the manifest group and stable report path' {
        $repo = New-RetireTestRepo -Name 'grouped-dry-run'
        [void](Write-RetireFixture -Repo $repo -ArchiveGroup 'evidence')

        $result = Invoke-RetireContent -Repo $repo -DryRun

        $result.ExitCode | Should Be 0
        $result.Output | Should Match 'harness.report-root.*evidence.evidence-20260713-000000.md'
        $result.Output | Should Match 'harness.reports.current.latest-content-retire.md'
    }

    It 'rejects an archive group that escapes its batch directory' {
        $repo = New-RetireTestRepo -Name 'group-traversal'
        [void](Write-RetireFixture -Repo $repo -ArchiveGroup '..\escape')

        $result = Invoke-RetireContent -Repo $repo -DryRun

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'archiveGroup'
    }

    It 'moves the file into its group and writes one stable report' {
        $repo = New-RetireTestRepo -Name 'grouped-archive'
        $sourceRelative = Write-RetireFixture -Repo $repo -ArchiveGroup 'evidence'

        $result = Invoke-RetireContent -Repo $repo

        $result.ExitCode | Should Be 0
        (Join-Path $repo $sourceRelative) | Should Not Exist
        $archived = @(Get-ChildItem -LiteralPath (Join-Path $repo 'harness\archive') -Recurse -File -Filter 'evidence-20260713-000000.md')
        $archived.Count | Should Be 1
        $archived[0].Directory.Name | Should Be 'evidence'
        (Join-Path $repo 'harness\reports\current\latest-content-retire.md') | Should Exist
    }
}
