$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path

Describe 'selective release flow' {
    It 'keeps isolated application scopes in the Node verifier without default business E2E' {
        $agentDo = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\commands\agent-do.ps1')
        $agentDo | Should Match '\$scopedSkipBusinessValidation = \$SkipBusinessValidation'
        $agentDo | Should Match '\$Scope -in @\("backend", "frontend"\)'
        $agentDo | Should Match 'New-HarnessNodeVerifyArguments'
        $workflow = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\lib\node\workflows\verify.ts')
        $workflow | Should Match 'if \(scope !== "full"\) return buildNodes'
    }

    It 'supports deployment and CI scopes without application build or restart' {
        $agentDo = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\commands\agent-do.ps1')
        $agentDo | Should Match 'deploy", "ci'
        $agentDo | Should Match 'release-queue-governance\.Tests\.ps1'
        $agentDo | Should Match 'CI-only verification'
        $agentDo | Should Match 'application/Docker build, restart and business validation skipped'
        $agentDo | Should Match 'Get-Command pwsh'
        $agentDo | Should Match '\$harnessPowerShell'
    }

    It 'does not duplicate safety checks or dependencies on isolated restarts' {
        $restart = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\commands\restart-compose.ps1')
        $restart | Should Match '\[switch\]\$SkipSafetyCheck'
        $restart | Should Match '\$upArgs \+= "--no-deps"'
    }

    It 'does not run a standalone frontend production build in CI or the Node frontend plan' {
        $ci = Get-Content -Raw -LiteralPath (Join-Path $repoRoot '.github\workflows\ci.yml')
        $frontend = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\lib\node\checks\frontend.ts')
        $ci | Should Not Match 'run: pnpm build'
        $ci | Should Not Match 'run: npm --prefix frontend run build'
        $frontend | Should Not Match 'frontend-build'
        $frontend | Should Match 'Docker 镜像阶段'
    }

    It 'keeps Jenkins deployment on immutable images' {
        $jenkins = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'Jenkinsfile')
        $jenkins | Should Match "stage\('Pull Immutable Images'\)"
        $jenkins | Should Match '--no-build --no-deps'
        $jenkins | Should Not Match 'docker compose.*build backend-real-pre frontend-real-pre'
    }
}
