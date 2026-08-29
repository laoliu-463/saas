$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$library = Join-Path $projectRoot 'harness\scripts\commands\_lib.ps1'
$jenkinsfile = Join-Path $projectRoot 'Jenkinsfile'
$releaseWorkflow = Join-Path $projectRoot '.github\workflows\release-images.yml'
$ciWorkflow = Join-Path $projectRoot '.github\workflows\ci.yml'
$deployRelease = Join-Path $projectRoot 'scripts\deploy-release.sh'
$migrationDetector = Join-Path $projectRoot 'scripts\detect-release-db-migration.sh'
$releaseCompose = Join-Path $projectRoot 'docker-compose.real-pre.release.yml'
$backendDockerfile = Join-Path $projectRoot 'backend\Dockerfile'
$frontendDockerfile = Join-Path $projectRoot 'frontend\Dockerfile'
$systemController = Join-Path $projectRoot 'backend\src\main\java\com\colonel\saas\controller\SystemEnvController.java'
$credentialProbe = Join-Path $projectRoot 'scripts\check-acr-creds.ps1'
$legacyHarnessDeploy = Join-Path $projectRoot 'harness\scripts\commands\deploy-remote.ps1'
$legacyServerDeploy = Join-Path $projectRoot 'scripts\deploy-real-pre.sh'
$legacyRollback = Join-Path $projectRoot 'scripts\rollback-real-pre.sh'

. $library

Describe 'real-pre 唯一发布通道契约' {
    It '让 Jenkins 作唯一串行发布控制器' {
        $content = Get-Content -Raw -LiteralPath $jenkinsfile

        $content | Should Match 'disableConcurrentBuilds\s*\(\s*abortPrevious\s*:\s*false\s*\)'
        $content | Should Match "RELEASE_BRANCH\s*=\s*'release/real-pre'"
        $content | Should Match "lock\s*\(\s*resource\s*:\s*'saas-real-pre-deploy'\s*\)"
        $content | Should Match 'scripts/deploy-release\.sh'
        $content | Should Not Match 'git\.rev-parse --short'
        $content | Should Not Match '(?m)^\s*(mvn|pnpm|npm|docker build|docker compose build)\b'
    }

    It '仅在发布差异包含数据库迁移时执行远端迁移' {
        $jenkins = Get-Content -Raw -LiteralPath $jenkinsfile
        $deploy = Get-Content -Raw -LiteralPath $deployRelease
        $detector = Get-Content -Raw -LiteralPath $migrationDetector

        $jenkins | Should Match 'mapfile -t migration_paths'
        $jenkins | Should Match 'detect-release-db-migration\.sh'
        $detector | Should Match 'backend/src/main/resources/db'
        $jenkins | Should Match 'git diff --name-only "\$migration_base_sha" "\$TARGET_GIT_SHA" -- "\$\{migration_paths\[@\]\}"'
        $detector | Should Match 'git diff --quiet "\$BASE_SHA" "\$TARGET_SHA"'
        $jenkins | Should Match '\.databaseMigration\s*='
        $jenkins | Should Match 'if \[\[ "\$database_migration_required" == ''true'' \]\]; then[\s\S]*run-real-pre-db-migrations\.sh[\s\S]*未检测到数据库迁移变更'
        $deploy | Should Match '\.databaseMigration\.required'
        $deploy | Should Match 'DATABASE_MIGRATION_REQUIRED'
        $deploy | Should Match 'databaseMigrationRequired'
        $deploy | Should Match 'if \[\[ "\$DATABASE_MIGRATION_REQUIRED" == ''true'' \]\]; then[\s\S]*\.databaseMigrationVersion == \$migration[\s\S]*else[\s\S]*\(\.databaseMigrationVersion \| type == "string"\)'
        $deploy | Should Match 'OBSERVED_DATABASE_MIGRATION_VERSION'
    }

    It '用真实 Git 差异区分 Harness 变更、迁移变更、首发和回滚' {
        $repo = Join-Path $TestDrive 'migration-decision-repo'
        New-Item -ItemType Directory -Path (Join-Path $repo 'backend\src\main\resources\db') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $repo 'harness\rules') -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $repo 'backend\src\main\resources\db\migrate-all.sql') -Value '-- baseline' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $repo 'harness\rules\policy.md') -Value 'baseline' -Encoding UTF8
        Push-Location $repo
        try {
            git init -q
            git config user.email 'harness-tests@example.invalid'
            git config user.name 'Harness Tests'
            git add .
            git commit -q -m 'test: baseline'
            $baselineSha = (& git rev-parse HEAD).Trim()

            Set-Content -LiteralPath (Join-Path $repo 'harness\rules\policy.md') -Value 'harness only' -Encoding UTF8
            git add .
            git commit -q -m 'test: harness only'
            $harnessSha = (& git rev-parse HEAD).Trim()

            Set-Content -LiteralPath (Join-Path $repo 'backend\src\main\resources\db\migrate-all.sql') -Value '-- migration changed' -Encoding UTF8
            git add .
            git commit -q -m 'test: migration changed'
            $migrationSha = (& git rev-parse HEAD).Trim()
        }
        finally {
            Pop-Location
        }

        $bash = Get-HarnessBashPath
        $repoForBash = Convert-HarnessPathToMsys -Path $repo
        $detectorForBash = Convert-HarnessPathToMsys -Path $migrationDetector
        function Invoke-Decision([string]$Target, [string]$Base, [string]$Rollback = 'false') {
            $command = "cd '$repoForBash' && ROLLBACK_APPROVED='$Rollback' bash '$detectorForBash' '$Target' '$Base'"
            $output = (& $bash -lc $command).Trim()
            [void]($LASTEXITCODE | Should Be 0)
            return @($output -split "`t")
        }

        $harnessOnly = Invoke-Decision -Target $harnessSha -Base $baselineSha
        $harnessOnly[0] | Should Be 'false'
        $harnessOnly[1] | Should Be 'NO_MIGRATION_CHANGE'

        $migration = Invoke-Decision -Target $migrationSha -Base $harnessSha
        $migration[0] | Should Be 'true'
        $migration[1] | Should Be 'MIGRATION_PATH_CHANGED'

        $firstRelease = Invoke-Decision -Target $baselineSha -Base ''
        $firstRelease[0] | Should Be 'true'
        $firstRelease[1] | Should Be 'FIRST_RELEASE'

        $rollback = Invoke-Decision -Target $baselineSha -Base $migrationSha -Rollback 'true'
        $rollback[0] | Should Be 'false'
        $rollback[1] | Should Be 'ROLLBACK_FORWARD_ONLY'
    }

    It '只允许 CI 为 release real-pre 构建完整 SHA 镜像并把 digest 交给 Jenkins' {
        $content = Get-Content -Raw -LiteralPath $releaseWorkflow

        $content | Should Match 'release/real-pre'
        $content | Should Match 'org\.opencontainers\.image\.revision'
        $content | Should Match 'tags:\s*\|[\s\S]*\$\{\{ github\.sha \}\}'
        $content | Should Match 'backend_digest'
        $content | Should Match 'frontend_digest'
        $content | Should Match 'buildWithParameters'
        $content | Should Not Match '(?i)(:latest|--tag\s+latest)'
    }

    It '让必需 CI 在 merge queue 合并组上重新验证' {
        $content = Get-Content -Raw -LiteralPath $ciWorkflow

        $content | Should Match 'merge_group:'
        $content | Should Match 'types:\s*\[checks_requested\]'
    }

    It '部署脚本拒绝短 SHA 与旧任务并只写不可变发布目录' {
        $content = Get-Content -Raw -LiteralPath $deployRelease

        $content | Should Match '\^\[0-9a-f\]\{40\}\$'
        $content | Should Match 'sha256:\[0-9a-f\]\{64\}'
        $content | Should Match 'git merge-base --is-ancestor'
        $content | Should Match 'ROLLBACK_APPROVED'
        $content | Should Match 'RELEASE_CONTROLLER'
        $content | Should Match '/opt/saas/releases'
        $content | Should Match 'current\.json'
        $content | Should Match 'previous\.json'
        $content | Should Match 'org\.opencontainers\.image\.revision'
        $content | Should Not Match '(?m)git (pull|checkout|reset)\b'
        $content | Should Not Match '/opt/saas/app'

        $healthIndex = $content.IndexOf('verify_runtime_versions')
        $currentIndex = $content.LastIndexOf('current.json')
        $healthIndex | Should BeGreaterThan -1
        $currentIndex | Should BeGreaterThan $healthIndex
    }

    It '发布 Compose 只接受 digest 固定镜像且禁止现场构建' {
        $content = Get-Content -Raw -LiteralPath $releaseCompose

        $content | Should Match 'BACKEND_IMAGE_REF'
        $content | Should Match 'FRONTEND_IMAGE_REF'
        $content | Should Match 'APP_GIT_SHA'
        $content | Should Match 'APP_IMAGE_DIGEST'
        $content | Should Not Match '(?m)^\s*build:'
        $content | Should Not Match '(?i)(latest|:-real-pre)'
    }

    It '镜像与运行接口暴露可核验的版本身份' {
        $backendImage = Get-Content -Raw -LiteralPath $backendDockerfile
        $frontendImage = Get-Content -Raw -LiteralPath $frontendDockerfile
        $controller = Get-Content -Raw -LiteralPath $systemController

        $backendImage | Should Match 'org\.opencontainers\.image\.revision'
        $frontendImage | Should Match 'org\.opencontainers\.image\.revision'
        $frontendImage | Should Match 'version\.json'
        $controller | Should Match 'gitSha'
        $controller | Should Match 'imageDigest'
        $controller | Should Match 'databaseMigrationVersion'
        $controller | Should Match 'flywayVersion'
    }

    It '凭证探针不打印或落盘 registry 密码' {
        $content = Get-Content -Raw -LiteralPath $credentialProbe

        $content | Should Not Match 'FromBase64String'
        $content | Should Not Match 'Auth found:'
        $content | Should Not Match 'Password written to'
    }

    It '关闭旧 SSH 现场构建和手工回滚旁路' {
        foreach ($path in @($legacyHarnessDeploy, $legacyServerDeploy, $legacyRollback)) {
            $content = Get-Content -Raw -LiteralPath $path
            $content | Should Match 'Jenkins'
            $content | Should Match '(禁止|已停用|BLOCKED)'
            $content | Should Not Match '(?m)^\s*(ssh|git pull|git checkout|git reset|docker compose .*build)\b'
        }
    }
}
