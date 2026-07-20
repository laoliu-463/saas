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
        $jenkinsfile | Should Match "lock\(resource:\s*'saas-real-pre-deploy'"
        ([regex]::Matches($jenkinsfile, "saas-real-pre-deploy").Count) | Should Be 1
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

    It 'records immutable release manifests and atomically promotes current state' {
        $jenkinsfile | Should Match '/opt/saas/releases'
        $jenkinsfile | Should Match 'release\.json'
        $jenkinsfile | Should Match 'current\.json'
        $jenkinsfile | Should Match 'previous\.json'
        $jenkinsfile | Should Match 'BACKEND_IMAGE_DIGEST'
        $jenkinsfile | Should Match 'FRONTEND_IMAGE_DIGEST'
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

Describe 'PR #6: GHA SHA Gate + RUN_BACKEND_TEST default-skip' {
    It 'verify-github-ci-gate.sh exists at scripts/' {
        $scriptPath = Join-Path $repoRoot 'scripts' 'verify-github-ci-gate.sh'
        Test-Path -LiteralPath $scriptPath | Should Be $true
    }

    It 'Jenkinsfile references the GHA SHA Gate script from Preflight Guard' {
        $jenkinsfile | Should Match "bash\s+scripts/verify-github-ci-gate\.sh"
    }

    It 'Jenkinsfile GHA SHA Gate binds GITHUB_SHA=$FULL_COMMIT' {
        $jenkinsfile | Should Match 'GITHUB_SHA=\"\$FULL_COMMIT\"'
    }

    It 'Jenkinsfile exposes RUN_BACKEND_TEST parameter defaulting to false' {
        $jenkinsfile | Should Match "booleanParam\(name:\s*'RUN_BACKEND_TEST',\s*defaultValue:\s*false"
    }

    It 'Backend Test stage runs only when params.RUN_BACKEND_TEST is true' {
        $body = (([regex]::Matches($jenkinsfile, "stage\('Backend Test'\)\s*\{(.+?)\n        \}", 'Singleline')) | ForEach-Object { $_.Groups[1].Value }) | Select-Object -First 1
        ($body -match 'when\s*\{') | Should Be $true
        ($body -match 'params\.RUN_BACKEND_TEST') | Should Be $true
    }

    $scriptPath = Join-Path $repoRoot 'scripts' 'verify-github-ci-gate.sh'
    It 'verify-github-ci-gate.sh uses fail-closed error codes (2/3/4/5)' {
        $script = Get-Content -Raw -LiteralPath $scriptPath
        ($script -match 'GITHUB_CI_GATE_API_ERROR') | Should Be $true
        ($script -match 'GITHUB_CI_GATE_RUN_FAILED') | Should Be $true
        ($script -match 'GITHUB_CI_GATE_TIMEOUT') | Should Be $true
        ($script -match 'GITHUB_CI_GATE_JOB_FAILED') | Should Be $true
    }

    It 'verify-github-ci-gate.sh requires 40-character GITHUB_SHA' {
        $script = Get-Content -Raw -LiteralPath $scriptPath
        ($script -match "\[0-9a-f\]\{40\}") | Should Be $true
    }

    It 'verify-github-ci-gate.sh checks the three required job names' {
        $script = Get-Content -Raw -LiteralPath $scriptPath
        ($script -match 'Backend tests') | Should Be $true
        ($script -match 'Frontend tests and build') | Should Be $true
        ($script -match 'Repository governance') | Should Be $true
    }
}
