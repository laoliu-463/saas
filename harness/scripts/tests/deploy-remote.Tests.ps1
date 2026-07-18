$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$deployScript = Join-Path $repoRoot 'harness\scripts\commands\deploy-remote.ps1'
$content = Get-Content -Raw -LiteralPath $deployScript
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    $deployScript,
    [ref]$tokens,
    [ref]$parseErrors
)
$parameterNames = @($ast.ParamBlock.Parameters.Name.VariablePath.UserPath)

Describe 'remote deployment safety contract' {
    It 'binds a deployment to an expected commit before Compose changes' {
        $parseErrors.Count | Should Be 0
        ($parameterNames -contains 'ExpectedCommit') | Should Be $true
        $content | Should Match 'git rev-parse HEAD'
        $content | Should Match 'Remote commit mismatch'

        $commitGuard = $content.IndexOf('Remote commit mismatch')
        $firstComposeChange = $content.IndexOf('compose up -d')
        $commitGuard | Should BeGreaterThan -1
        $firstComposeChange | Should BeGreaterThan $commitGuard
    }

    It 'resets a clean remote worktree to the controlled feature ref before commit validation' {
        $content | Should Match 'Remote worktree is not clean'
        $content | Should Match 'git fetch gitee \+feature/auth-system'
        $content | Should Match 'git switch -C feature/auth-system gitee/feature/auth-system'

        $branchSwitch = $content.IndexOf('git switch -C feature/auth-system gitee/feature/auth-system')
        $commitGuard = $content.IndexOf('Remote commit mismatch')
        $branchSwitch | Should BeGreaterThan -1
        $commitGuard | Should BeGreaterThan $branchSwitch
    }

    It 'force-refreshes only the remote tracking ref when the mirror history is rewritten' {
        $content | Should Match 'git fetch gitee \+feature/auth-system:refs/remotes/gitee/feature/auth-system'
    }

    It 'derives the pinned image tag from the validated remote commit before migrations' {
        $commitGuard = $content.IndexOf('Remote commit mismatch')
        $imageTagAssignment = $content.IndexOf('image_tag="`$actual_commit"')
        $pinnedMigration = $content.IndexOf('REQUIRE_PINNED_IMAGE=true')

        $imageTagAssignment | Should BeGreaterThan $commitGuard
        $pinnedMigration | Should BeGreaterThan $imageTagAssignment
    }

    It 'repairs and verifies the repository real-pre env symlink before Compose' {
        $content | Should Match 'ln -sfn'
        $content | Should Match 'readlink -f'
        $content | Should Match 'Remote env link mismatch'

        $linkGuard = $content.IndexOf('Remote env link mismatch')
        $composeFunction = $content.IndexOf('compose()')
        $linkGuard | Should BeGreaterThan -1
        $composeFunction | Should BeGreaterThan $linkGuard
    }

    It 'uses the canonical project and converges both stateful services' {
        $content | Should Match "--project-name 'saas-active'"
        $content | Should Match 'compose up -d postgres-real-pre redis-real-pre'
        $content | Should Match 'com\.docker\.compose\.project\.working_dir'
        $content | Should Match 'com\.docker\.compose\.project\.environment_file'
        $content | Should Match 'Stateful service provenance guard passed'
    }

    It 'references only migration files that are present in the database manifest' {
        $manifestPath = Join-Path $repoRoot 'backend\src\main\resources\db\migrate-all.sql'
        $migrationDirectory = Split-Path -Parent $manifestPath
        $manifest = Get-Content -Raw -LiteralPath $manifestPath
        $includes = [regex]::Matches($manifest, '(?m)^\s*\\i\s+/docker-entrypoint-initdb\.d/db/([^\s]+\.sql)\s*$')
        $missing = @(
            $includes |
                ForEach-Object { $_.Groups[1].Value } |
                Where-Object { -not (Test-Path -LiteralPath (Join-Path $migrationDirectory $_)) }
        )

        $missing.Count | Should Be 0
    }
}
