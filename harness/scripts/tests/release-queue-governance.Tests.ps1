$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$jenkinsfile = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'Jenkinsfile')
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
        $jenkinsfile | Should Match 'release tree does not match any commit reachable from main'
    }

    It 'queues builds without aborting and holds one cross-job deployment lock' {
        $jenkinsfile | Should Match 'disableConcurrentBuilds\(abortPrevious:\s*false\)'
          # The previous `lock(resource: 'saas-real-pre-deploy')` directive
        # relied on the Lockable Resources plugin, which is not installed.
        # The contract is now enforced via flock(1) inside the canonical
        # release entry script, so we check for the wrapper rather than
        # the obsolete Groovy lock directive.
        $releaseScriptExists | Should Be $true
        $jenkinsfile | Should Match ([regex]::Escape('scripts/cd/release-real-pre.sh'))
        $releaseScript | Should Match '\bflock\b'
        # saas-real-pre-deploy must appear in at least the wrapper (lock file
          # name) and the Jenkinsfile comment that explains the wrapper.
          ([regex]::Matches($releaseScript, 'saas-real-pre-deploy').Count) | Should BeGreaterThan 0
    }

    It 'rejects stale releases unless rollback is explicitly approved' {
        $jenkinsfile | Should Match "booleanParam\(name:\s*'ROLLBACK_APPROVED',\s*defaultValue:\s*false"
        $jenkinsfile | Should Match 'git merge-base --is-ancestor "\$current_sha" "\$FULL_COMMIT"'
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
        $ci | Should Not Match 'deploy script must validate IMAGE_TAG'
    }
}
