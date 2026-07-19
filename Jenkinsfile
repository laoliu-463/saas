pipeline {
    agent any

    options {
        disableConcurrentBuilds(abortPrevious: false)
        buildDiscarder(logRotator(numToKeepStr: '30'))
        skipDefaultCheckout(true)
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
    }

    parameters {
        string(name: 'TARGET_GIT_SHA', defaultValue: '', description: 'CI 传入的 40 位完整 Git SHA。')
        string(name: 'BACKEND_IMAGE', defaultValue: '', description: '后端镜像仓库，不含 tag/digest。')
        string(name: 'BACKEND_IMAGE_DIGEST', defaultValue: '', description: 'CI 构建后端镜像得到的 sha256 digest。')
        string(name: 'FRONTEND_IMAGE', defaultValue: '', description: '前端镜像仓库，不含 tag/digest。')
        string(name: 'FRONTEND_IMAGE_DIGEST', defaultValue: '', description: 'CI 构建前端镜像得到的 sha256 digest。')
        booleanParam(name: 'ROLLBACK_APPROVED', defaultValue: false, description: '仅用于明确批准的回滚，普通发布必须为 false。')
    }

    environment {
        RELEASE_BRANCH = 'release/real-pre'
        CD_GIT_URL = 'https://github.com/laoliu-463/saas.git'
        RELEASE_ENV_FILE = '/opt/saas/env/.env.real-pre'
        RELEASE_ROOT = '/opt/saas/releases'
        RELEASE_LOCK = 'saas-real-pre-deploy'
        REGISTRY_CREDENTIALS_ID = 'saas-real-pre-registry'
    }

    stages {
        stage('Checkout release/real-pre') {
            steps {
                deleteDir()
                git branch: env.RELEASE_BRANCH, url: env.CD_GIT_URL
                sh '''#!/usr/bin/env bash
                set -Eeuo pipefail
                target="${TARGET_GIT_SHA:-}"
                case "$target" in
                  ''|*[!0-9a-f]* ) echo 'TARGET_GIT_SHA 必须是 40 位小写完整 SHA。'; exit 2 ;;
                esac
                [ "${#target}" -eq 40 ] || { echo '禁止短 SHA。'; exit 2; }
                [ "$(git rev-parse HEAD)" = "$target" ] || {
                  echo '目标 SHA 不是 release/real-pre 当前提交，拒绝部署。'
                  exit 2
                }
                test -f "$RELEASE_ENV_FILE"
                test -f docker-compose.real-pre.release.yml
                test -x scripts/deploy-release.sh
                command -v jq >/dev/null
                command -v docker >/dev/null
                mkdir -p runtime/qa/out/jenkins
                '''
            }
        }

        stage('Validate immutable inputs') {
            steps {
                sh '''#!/usr/bin/env bash
                set -Eeuo pipefail
                digest_pattern='^sha256:[0-9a-f]{64}$'
                [[ "${BACKEND_IMAGE_DIGEST:-}" =~ $digest_pattern ]] || { echo '后端 digest 非法。'; exit 2; }
                [[ "${FRONTEND_IMAGE_DIGEST:-}" =~ $digest_pattern ]] || { echo '前端 digest 非法。'; exit 2; }
                [ -n "${BACKEND_IMAGE:-}" ] && [ -n "${FRONTEND_IMAGE:-}" ] || { echo '镜像仓库不能为空。'; exit 2; }
                [[ "$BACKEND_IMAGE" != *@* && "$FRONTEND_IMAGE" != *@* ]] || { echo '镜像仓库参数不得自带 digest。'; exit 2; }

                migration_file='backend/src/main/resources/db/migrate-all.sql'
                migration_checksum="$(sha256sum "$migration_file" | awk '{print $1}')"
                database_version="$(basename "$migration_file")@$migration_checksum"
                flyway_file="$(find backend/src/main/resources/db/migrate -maxdepth 1 -type f -name 'V*.sql' | sort | tail -n 1)"
                [ -n "$flyway_file" ] || { echo '缺少 Flyway 迁移文件，发布门禁阻断。'; exit 2; }
                flyway_version="$(basename "$flyway_file" | sed -E 's/^V([^_]+(_[^_]+)*)__.*$/\1/; s/_/./g')"

                jq -n \
                  --arg gitSha "$TARGET_GIT_SHA" \
                  --arg sourceBranch "$RELEASE_BRANCH" \
                  --arg backendImage "$BACKEND_IMAGE:$TARGET_GIT_SHA" \
                  --arg backendDigest "$BACKEND_IMAGE_DIGEST" \
                  --arg frontendImage "$FRONTEND_IMAGE:$TARGET_GIT_SHA" \
                  --arg frontendDigest "$FRONTEND_IMAGE_DIGEST" \
                  --arg databaseMigrationVersion "$database_version" \
                  --arg flywayVersion "$flyway_version" \
                  '{schemaVersion:1,environment:"real-pre",gitSha:$gitSha,sourceBranch:$sourceBranch,
                    backend:{image:$backendImage,digest:$backendDigest},
                    frontend:{image:$frontendImage,digest:$frontendDigest},
                    databaseMigrationVersion:$databaseMigrationVersion,flywayVersion:$flywayVersion}' \
                  > runtime/qa/out/jenkins/release-manifest.json
                jq -e . runtime/qa/out/jenkins/release-manifest.json >/dev/null
                '''
            }
        }

        stage('Deploy real-pre') {
            steps {
                lock(resource: 'saas-real-pre-deploy') {
                    withCredentials([usernamePassword(
                        credentialsId: env.REGISTRY_CREDENTIALS_ID,
                        usernameVariable: 'REGISTRY_USERNAME',
                        passwordVariable: 'REGISTRY_PASSWORD'
                    )]) {
                        sh '''#!/usr/bin/env bash
                        set -Eeuo pipefail
                        backend_registry="${BACKEND_IMAGE%%/*}"
                        frontend_registry="${FRONTEND_IMAGE%%/*}"
                        [ "$backend_registry" = "$frontend_registry" ] || { echo '前后端镜像必须来自同一受控仓库。'; exit 2; }
                        printf '%s' "$REGISTRY_PASSWORD" | docker login "$backend_registry" --username "$REGISTRY_USERNAME" --password-stdin >/dev/null
                        trap 'docker logout "$backend_registry" >/dev/null 2>&1 || true' EXIT

                        docker pull "$BACKEND_IMAGE:$TARGET_GIT_SHA"
                        docker pull "$FRONTEND_IMAGE:$TARGET_GIT_SHA"

                        mapfile -t migration_paths < <(bash scripts/detect-release-db-migration.sh --paths)
                        migration_base_sha=''
                        migration_changed_paths='[]'
                        current_pointer="$RELEASE_ROOT/current.json"

                        if [[ -f "$current_pointer" ]]; then
                          migration_base_sha="$(jq -er '.gitSha' "$current_pointer")"
                        fi

                        migration_decision="$(ROLLBACK_APPROVED="$ROLLBACK_APPROVED" \
                          bash scripts/detect-release-db-migration.sh "$TARGET_GIT_SHA" "$migration_base_sha")"
                        IFS=$'\t' read -r database_migration_required database_migration_reason <<<"$migration_decision"
                        [[ "$database_migration_required" == 'true' || "$database_migration_required" == 'false' ]] \
                          || { echo '数据库迁移判定脚本返回非法结果。'; exit 2; }
                        if [[ -n "$migration_base_sha" ]]; then
                          migration_changed_paths="$(git diff --name-only "$migration_base_sha" "$TARGET_GIT_SHA" -- "${migration_paths[@]}" | jq -R -s -c 'split("\n") | map(select(length > 0))')"
                        fi

                        jq -n \
                          --argjson required "$database_migration_required" \
                          --arg reason "$database_migration_reason" \
                          --arg baseGitSha "$migration_base_sha" \
                          --argjson changedPaths "$migration_changed_paths" \
                          '{required:$required,reason:$reason,
                            baseGitSha:(if $baseGitSha == "" then null else $baseGitSha end),
                            changedPaths:$changedPaths}' \
                          > runtime/qa/out/jenkins/migration-decision.json
                        jq --slurpfile migration runtime/qa/out/jenkins/migration-decision.json \
                          '.databaseMigration = $migration[0]' \
                          runtime/qa/out/jenkins/release-manifest.json \
                          > runtime/qa/out/jenkins/release-manifest.json.tmp
                        mv runtime/qa/out/jenkins/release-manifest.json.tmp runtime/qa/out/jenkins/release-manifest.json
                        jq -e '.databaseMigration.required | type == "boolean"' \
                          runtime/qa/out/jenkins/release-manifest.json >/dev/null

                        if [[ "$database_migration_required" == 'true' ]]; then
                          REAL_PRE_ENV_FILE="$RELEASE_ENV_FILE" \
                          REAL_PRE_COMPOSE_FILE="docker-compose.real-pre.yml" \
                          REAL_PRE_COMPOSE_PROJECT="saas-active" \
                            sh scripts/run-real-pre-db-migrations.sh
                        else
                          echo "未检测到数据库迁移变更，跳过远端数据库迁移：$database_migration_reason"
                        fi

                        RELEASE_ROOT="$RELEASE_ROOT" \
                        REAL_PRE_ENV_FILE="$RELEASE_ENV_FILE" \
                        RELEASE_CONTROLLER="jenkins" \
                        ROLLBACK_APPROVED="$ROLLBACK_APPROVED" \
                          scripts/deploy-release.sh "$TARGET_GIT_SHA" runtime/qa/out/jenkins/release-manifest.json
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'runtime/qa/out/jenkins/**', allowEmptyArchive: true
        }
        success {
            echo "real-pre 发布通过：${params.TARGET_GIT_SHA}"
        }
        failure {
            echo 'real-pre 发布失败；current.json 只有在本次适用门禁全部通过后才会更新。'
        }
    }
}
