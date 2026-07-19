$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$ciPath = Join-Path $repoRoot '.github\workflows\ci.yml'

Describe 'GitHub collaboration governance contract' {
    $requiredFiles = @(
        '.github/CODEOWNERS',
        '.github/pull_request_template.md',
        '.github/ISSUE_TEMPLATE/bug.yml',
        '.github/ISSUE_TEMPLATE/feature.yml',
        '.github/ISSUE_TEMPLATE/config.yml',
        '.github/dependabot.yml',
        'CONTRIBUTING.md',
        'SECURITY.md'
    )

    It 'provides the required collaboration entry points' {
        foreach ($relativePath in $requiredFiles) {
            $fullPath = Join-Path $repoRoot $relativePath
            (Test-Path -LiteralPath $fullPath -PathType Leaf) | Should Be $true
        }
    }

    It 'assigns sensitive repository paths to an explicit owner' {
        $content = Get-Content -Raw -LiteralPath (Join-Path $repoRoot '.github\CODEOWNERS')

        $content | Should Match '(?m)^/\.github/\s+@laoliu-463\s*$'
        $content | Should Match '(?m)^/Jenkinsfile\s+@laoliu-463\s*$'
        $content | Should Match '(?m)^/backend/src/main/resources/db/\s+@laoliu-463\s*$'
        $content | Should Match '(?m)^/harness/\s+@laoliu-463\s*$'
    }

    It 'requires PRs to identify issue, database, deployment, validation, and evidence scope' {
        $content = Get-Content -Raw -LiteralPath (Join-Path $repoRoot '.github\pull_request_template.md')

        $content | Should Match 'Closes #'
        $content | Should Match 'Database change'
        $content | Should Match 'Deployment'
        $content | Should Match 'Validation'
        $content | Should Match 'Evidence|证据'
    }

    It 'configures dependency updates for Actions, Maven, and pnpm' {
        $content = Get-Content -Raw -LiteralPath (Join-Path $repoRoot '.github\dependabot.yml')

        $content | Should Match 'package-ecosystem:\s*"github-actions"'
        $content | Should Match 'package-ecosystem:\s*"maven"'
        $content | Should Match 'package-ecosystem:\s*"npm"'
        $content | Should Match 'directory:\s*"/backend"'
        $content | Should Match 'directory:\s*"/frontend"'
    }

    It 'directs security reports to a private channel' {
        $content = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'SECURITY.md')

        $content | Should Match 'Security Advisories'
        $content | Should Match 'public Issue'
    }
}

Describe 'GitHub Actions CI contract' {
    It 'runs for pull requests and merge queue validation' {
        $content = Get-Content -Raw -LiteralPath $ciPath

        $content | Should Match '(?m)^  pull_request:\s*$'
        $content | Should Match '(?m)^  merge_group:\s*$'
        $content | Should Not Match 'paths-ignore:\s*[\r\n]+\s*-\s*"?harness/reports/'
    }

    It 'uses least privilege, bounded jobs, and a supported Node line' {
        $content = Get-Content -Raw -LiteralPath $ciPath
        $timeoutCount = [regex]::Matches($content, '(?m)^\s{4}timeout-minutes:\s*\d+\s*$').Count

        $content | Should Match '(?ms)^permissions:\s*\r?\n\s{2}contents:\s*read\s*$'
        $timeoutCount | Should BeGreaterThan 2
        $content | Should Match 'node-version:\s*"20"'
    }

    It 'pins every third-party Action to a full commit SHA' {
        $usesLines = @(Get-Content -LiteralPath $ciPath | Where-Object { $_ -match '^\s*-?\s*uses:' })

        $usesLines.Count | Should BeGreaterThan 0
        foreach ($line in $usesLines) {
            $line | Should Match '@[0-9a-f]{40}(?:\s+#.*)?$'
            $line | Should Not Match '@v\d+(?:\s|$)'
        }
    }

    It 'declares isolated PostgreSQL and Redis service containers without repository migrations' {
        $content = Get-Content -Raw -LiteralPath $ciPath

        $content | Should Match '(?ms)^\s{4}services:\s*\r?\n\s{6}postgres:.*?\r?\n\s{6}redis:'
        $content | Should Match 'image:\s*postgres:15-alpine'
        $content | Should Match 'image:\s*redis:7-alpine'
        $content | Should Not Match 'docker-compose\.test\.yml'
        $content | Should Not Match 'migrate-all\.sql'
        $content | Should Match 'mvn -B test'
    }

    It 'always exposes a repository governance result' {
        $content = Get-Content -Raw -LiteralPath $ciPath

        $content | Should Match '(?m)^  governance:\s*$'
        $content | Should Match 'check-harness-limits\.ps1'
        $content | Should Match 'git diff --check'
    }
}
