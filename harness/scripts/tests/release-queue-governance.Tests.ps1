$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$jenkinsfile = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'Jenkinsfile')
$rollbackScript = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'scripts\cd\rollback-real-pre.sh')
$releaseWrapper = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'scripts\cd\release-real-pre.sh')
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
