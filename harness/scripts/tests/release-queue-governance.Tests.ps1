$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$jenkinsfile = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'Jenkinsfile')
$rollbackScript = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'scripts\cd\rollback-real-pre.sh')
$releaseWrapper = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'scripts\cd\release-real-pre.sh')
$immutablePullScript = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'scripts\cd\pull-immutable-images.sh')
$agentDo = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\commands\agent-do.ps1')
$deployRemote = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'harness\scripts\commands\deploy-remote.ps1')
$ci = Get-Content -Raw -LiteralPath (Join-Path $repoRoot '.github\workflows\ci.yml')
$compose = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'docker-compose.real-pre.yml')
$backendService = [regex]::Match(
    $compose,
    '(?ms)^  backend-real-pre:\s*\r?\n(?<body>.*?)(?=^  frontend-real-pre:\s*\r?\n)'
).Groups['body'].Value
$frontendDockerfile = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'frontend\Dockerfile')

# Canonical release entry script: every mutating real-pre subcommand MUST
# route through this wrapper, which holds an exclusive flock(1) lock.
$releaseScriptPath = Join-Path $repoRoot 'scripts\cd\release-real-pre.sh'
$releaseScriptExists = (Test-Path -LiteralPath $releaseScriptPath -PathType Leaf)
$releaseScript = if ($releaseScriptExists) { Get-Content -Raw -LiteralPath $releaseScriptPath } else { '' }

Describe 'real-pre single release queue contract' {
    It 'only accepts the protected release branch' {
        $jenkinsfile | Should Match "defaultValue:\s*'release/real-pre'"
        $jenkinsfile | Should Match 'BUILD_BRANCH"?\s*!=\s*"release/real-pre"'
        $jenkinsfile | Should Match 'refs/heads/release/real-pre'
        $jenkinsfile | Should Match 'git fetch --no-tags origin \+refs/heads/main:refs/remotes/origin/main'
        $jenkinsfile | Should Match 'release/real-pre\.json'
        $jenkinsfile | Should Match 'sourceMainSha'
    }

    It 'queues builds without aborting and holds one cross-job deployment lock' {
        $jenkinsfile | Should Match 'disableConcurrentBuilds\(abortPrevious:\s*false\)'
        $jenkinsfile | Should Match "lock\(resource:\s*'saas-real-pre-deploy'"
        ([regex]::Matches($jenkinsfile, "lock\(resource:\s*'saas-real-pre-deploy'").Count) | Should Be 1
    }

    It 'rejects stale releases unless rollback is explicitly approved' {
        $jenkinsfile | Should Match "booleanParam\(name:\s*'ROLLBACK_APPROVED',\s*defaultValue:\s*false"
        $jenkinsfile | Should Match 'git merge-base --is-ancestor "\$current_sha" "\$SOURCE_MAIN_SHA"'
        $jenkinsfile | Should Match 'ROLLBACK_APPROVED'
    }

    It 'runs database work only when migration inputs changed' {
        $jenkinsfile | Should Match 'backend/src/main/resources/db/migration'
        $jenkinsfile | Should Match 'RUN_DB_MIGRATIONS=false'
        $jenkinsfile | Should Match 'RUN_DB_MIGRATIONS=true'
        $jenkinsfile | Should Match 'Database work skipped: no migration inputs changed'
        $jenkinsfile | Should Not Match "RUN_DB_MIGRATIONS\s*=\s*'true'"
        $jenkinsfile | Should Not Match 'compatible database migrations are mandatory'
    }

    It 'enforces a GHA SHA Gate on the exact FULL_COMMIT before deploying (PR-B)' {
        # The SHA Gate must (a) live in scripts/verify-github-ci-gate.sh,
        # (b) be invoked from Jenkinsfile via withCredentials, and (c)
        # target the same FULL_COMMIT that is being deployed.
        $shaGateScript = Join-Path $repoRoot 'scripts\verify-github-ci-gate.sh'
        (Test-Path -LiteralPath $shaGateScript -PathType Leaf) | Should Be $true

        $shaGate = Get-Content -Raw -LiteralPath $shaGateScript
        $shaGate | Should Match 'GITHUB_SHA'
        $shaGate | Should Match 'workflow_runs'
        $shaGate | Should Match 'conclusion'

        $jenkinsfile | Should Match 'github-actions-read-token'
        $jenkinsfile | Should Match 'verify-github-ci-gate\.sh'
        $jenkinsfile | Should Match 'GITHUB_SHA="\$FULL_COMMIT"'
        $jenkinsfile | Should Match 'GITHUB_BRANCH=main'
        $jenkinsfile | Should Match 'GITHUB_REPOSITORY="\$CD_GIT_URL"'
        $jenkinsfile | Should Match 'git@github\.com:'
        $shaGate | Should Match 'REQUIRED_JOBS=\("CI Gate"\)'

        $ciWorkflow = Get-Content -Raw -LiteralPath (Join-Path $repoRoot '.github\workflows\ci.yml')
        foreach ($allowedPath in @(
            '\(exclude\)release/real-pre\.json',
            '\(exclude\)Jenkinsfile',
            '\(exclude\)\.github/workflows/\*\*',
            '\(exclude\)docs/deploy/\*\*',
            '\(exclude\)scripts/verify-github-ci-gate\.sh',
            '\(exclude\)harness/scripts/tests/release-queue-governance\.Tests\.ps1'
        )) {
            $ciWorkflow | Should Match $allowedPath
        }

        # RUN_BACKEND_TEST must default to false so the SHA Gate is the
        # single source of truth for the Backend tests signal.
        $jenkinsfile | Should Match "booleanParam\(name:\s*'RUN_BACKEND_TEST',\s*defaultValue:\s*false"
    }

    It 'records immutable release manifests and atomically promotes current state' {
        $jenkinsfile | Should Match '/opt/saas/releases'
        $jenkinsfile | Should Match 'release\.json'
        $jenkinsfile | Should Match 'current\.json'
        $jenkinsfile | Should Match 'previous\.json'
        $jenkinsfile | Should Match 'BACKEND_IMAGE_DIGEST'
        $jenkinsfile | Should Match 'FRONTEND_IMAGE_DIGEST'
        $jenkinsfile | Should Match 'repository@sha256:digest'
        $jenkinsfile | Should Not Match 'docker compose[^\r\n]+\sbuild'
    }

    It 'installs E2E dependencies from the repository lockfile' {
        $jenkinsfile | Should Match 'npm ci --no-audit --no-fund'
        $jenkinsfile | Should Not Match 'pnpm@9 install --frozen-lockfile'
        (Test-Path -LiteralPath (Join-Path $repoRoot 'package-lock.json') -PathType Leaf) | Should Be $true
    }

    It 'passes the protected real-pre admin credential to E2E without printing it' {
        $jenkinsfile | Should Match 'dotenv\.parse'
        $jenkinsfile | Should Match 'values\.ADMIN_PASSWORD'
        $jenkinsfile | Should Match 'export QA_ADMIN_PASSWORD'
        $jenkinsfile | Should Match 'unset qa_admin_password'
        $jenkinsfile | Should Not Match 'echo "\$qa_admin_password"'
    }

    It 'bounds and retries immutable image pulls without mutable fallback' {
        $jenkinsfile | Should Match "stage\('Pull Immutable Images'\)"
        $jenkinsfile | Should Match "timeout\(time:\s*70,\s*unit:\s*'MINUTES'\)"
        $jenkinsfile | Should Match 'pull-immutable-images\.sh'
        $immutablePullScript | Should Match 'timeout --foreground --kill-after=30s'
        $immutablePullScript | Should Match 'PULL_TIMEOUT_SECONDS:-900'
        $immutablePullScript | Should Match 'PULL_ATTEMPTS'
        $immutablePullScript | Should Match 'Retrying with Docker''s partially downloaded layer cache'
        $immutablePullScript | Should Match 'image_is_ready'
        $immutablePullScript | Should Match 'pull_image_ref'
        $immutablePullScript | Should Match 'repository="\$\{repository%@\*\}"'
        $immutablePullScript | Should Match 'canonicalize_image_ref'
        $immutablePullScript | Should Match 'IMAGE_PULL_REGISTRY'
        $immutablePullScript | Should Match 'repository@sha256:digest'
        $immutablePullScript | Should Match 'org\.opencontainers\.image\.revision'
        $immutablePullScript | Should Match 'range \.RepoDigests'
        $immutablePullScript | Should Match 'docker-system-df\.txt'
        $jenkinsfile | Should Match 'export BACKEND_IMAGE FRONTEND_IMAGE FULL_COMMIT'
        $jenkinsfile | Should Match "IMAGE_PULL_REGISTRY = 'ghcr\.1ms\.run'"
        $immutablePullScript | Should Not Match 'docker pull[^\r\n]*:latest'
    }

    It 'routes the release manifest through scripts/cd/evidence-collect.sh (PR-C)' {
        # PR-C moves the current/previous rotation out of inline cp/mv and
        # into the canonical evidence-collect.sh entry point. The
        # manifest schema is now enforced by evidence-collect.sh's
        # python validator rather than by ad-hoc heredoc JSON.
        $evidenceCollect = Join-Path $repoRoot 'scripts\cd\evidence-collect.sh'
        (Test-Path -LiteralPath $evidenceCollect -PathType Leaf) | Should Be $true

        $ec = Get-Content -Raw -LiteralPath $evidenceCollect
        # The script must define the release-manifest subcommand and
        # enforce every required field of the manifest schema.
        $ec | Should Match 'release-manifest\)'
        foreach ($field in 'gitSha','branch','backendDigest','frontendDigest','migrationVersions','ciRun','jenkinsBuild','deployResult','previous','rollbackTarget') {
            $ec | Should Match $field
        }

        # The Jenkinsfile must call the script as the final release step.
        $jenkinsfile | Should Match 'evidence-collect\.sh\s+release-manifest'
        $jenkinsfile | Should Match 'RELEASES_BASE='
        # The old inline cp/mv block must be gone so there is no second
        # writer competing with evidence-collect.sh.
        $jenkinsfile | Should Not Match 'release_root/current\.json\.tmp'
    }

    It 'verifies backend, frontend, image, and source revisions before PASS' {
        $jenkinsfile | Should Match '/api/system/health'
        $jenkinsfile | Should Match '/version\.json'
        $jenkinsfile | Should Match 'org\.opencontainers\.image\.revision'
        $jenkinsfile | Should Match 'gitSha'
        $jenkinsfile | Should Match 'imageDigest'
    }
}

Describe 'runtime version contract' {
    It 'injects the release SHA and backend image digest into the backend container' {
        $compose | Should Match 'image:\s*\$\{BACKEND_IMAGE:-colonel-saas/backend:real-pre\}'
        $compose | Should Match 'APP_GIT_SHA:\s*\$\{IMAGE_TAG:-unknown\}'
        $compose | Should Match 'APP_IMAGE_DIGEST:\s*\$\{BACKEND_IMAGE_DIGEST:-unknown\}'
    }

    It 'publishes a static frontend version document from the immutable build' {
        $frontendDockerfile | Should Match '/app/dist/version\.json'
        $frontendDockerfile | Should Match '"gitSha"'
    }

    It 'restarts the backend instead of leaving a half-dead JVM after heap exhaustion' {
        $backendService | Should Match '-XX:\+ExitOnOutOfMemoryError'
        $backendService | Should Match '(?m)^    restart:\s*always\s*$'
    }
}

Describe 'agent deployment boundary contract' {
    It 'does not let the general agent entry point invoke direct SSH deployment' {
        $agentDo | Should Not Match 'deploy-remote\.ps1'
        $agentDo | Should Match 'Jenkins release queue'
        $deployRemote | Should Match 'Direct SSH deployment is retired'
        $deployRemote | Should Not Match '\bssh\s+\$RemoteHost'
    }

    It 'keeps CI governance aligned with Jenkins as the only release controller' {
        $ci | Should Match 'Check Jenkins release identity and retired SSH deploy path'
        $ci | Should Match 'Jenkins must reject floating image tags'
        $ci | Should Match 'direct SSH deploy path must remain retired'
        $ci | Should Match 'Jenkins must not build images on the deployment host'
        $ci | Should Not Match 'deploy script must validate IMAGE_TAG'
        $jenkinsfile | Should Match 'scripts/cd/release-real-pre\.sh'
        $releaseWrapper | Should Match '\bflock\b'
    }
}

Describe 'lock-scoped failure rollback contract' {
    It 'rolls back when backend readiness fails' {
        $jenkinsfile | Should Match "stage\('Backend Readiness'\)"
        $rollbackScript | Should Match 'backend-real-pre'
        $rollbackScript | Should Match 'BACKEND_IMAGE="\$\{OLD_BACKEND_IMAGE\}"'
    }

    It 'rolls back when frontend deployment fails' {
        $jenkinsfile | Should Match "stage\('Deploy Frontend'\)"
        $rollbackScript | Should Match 'frontend-real-pre'
        $rollbackScript | Should Match 'FRONTEND_IMAGE="\$\{OLD_FRONTEND_IMAGE\}"'
    }

    It 'rolls back when P0 or role E2E fails' {
        $jenkinsfile | Should Match "stage\('Core Smoke and Multi-role E2E'\)"
        $jenkinsfile | Should Match '(?s)stage\(''Serialized real-pre release''\).*?post\s*\{\s*unsuccessful'
        $jenkinsfile | Should Match 'scripts/cd/release-real-pre\.sh rollback-immutable'
        $releaseWrapper | Should Match 'scripts/cd/rollback-real-pre\.sh'
    }

    It 'rolls back when the final health check fails' {
        $jenkinsfile | Should Match "stage\('Health Check'\)"
        $jenkinsfile | Should Match '(?s)stage\(''Health Check''\).*?scripts/health-check\.sh'
        $rollbackScript | Should Match 'health check after rollback'
    }

    It 'handles timeout or abort without post-lock scheduler mutation' {
        $jenkinsfile | Should Match 'deployment-started'
        $jenkinsfile | Should Match 'rollback-completed'
        $jenkinsfile | Should Match 'release-completed'
        $jenkinsfile | Should Match 'schedulers-restored'
        $jenkinsfile | Should Match 'refusing post-lock mutation of real-pre'
        $jenkinsfile | Should Not Match 'Restoring schedulers after interrupted deployment flow'
    }
}
