pipeline {
    agent any

    options {
        disableConcurrentBuilds(abortPrevious: false)
        buildDiscarder(logRotator(numToKeepStr: '20'))
        skipDefaultCheckout(true)
        timeout(time: 150, unit: 'MINUTES')
    }

    parameters {
        string(name: 'DEPLOY_BRANCH', defaultValue: 'release/real-pre', description: 'Only release/real-pre is deployable.')
        booleanParam(name: 'DEPLOY_REAL_PRE', defaultValue: false, description: 'Explicit approval required to deploy real-pre.')
        booleanParam(name: 'CONFIRM_REAL_PROMOTION_WRITE', defaultValue: false, description: 'Required only when real-pre promotion-write switches are enabled.')
        booleanParam(name: 'ROLLBACK_APPROVED', defaultValue: false, description: 'Explicit approval required when the target is not a descendant of the deployed commit.')
        booleanParam(name: 'RUN_BACKEND_TEST', defaultValue: false, description: 'Diagnostic rerun only; does not bypass the GHA SHA Gate.')
    }

    environment {
        JOB_PURPOSE = 'real-pre-cd'
        DEPLOY_ENV = 'real-pre'
        CD_GIT_URL = 'git@github.com:laoliu-463/saas.git'
        CD_GIT_REFERENCE = '/var/lib/jenkins/caches/saas-real-pre-git-reference.git'
        ENV_FILE = '/opt/saas/env/.env.real-pre'
        COMPOSE_FILE = 'docker-compose.real-pre.yml'
        PROJECT_NAME = 'saas-active'
        RELEASE_MANIFEST = 'release/real-pre.json'
        REAL_PRE_BACKEND = 'http://127.0.0.1:8081'
        REAL_PRE_FRONTEND = 'http://127.0.0.1:3001'
        RELEASE_HEAD_SHA = ''
        SOURCE_MAIN_SHA = ''
        IMAGE_TAG = ''
        FULL_COMMIT = ''
        BUILD_BRANCH = ''
        BACKEND_IMAGE = ''
        FRONTEND_IMAGE = ''
        BACKEND_IMAGE_DIGEST = ''
        FRONTEND_IMAGE_DIGEST = ''
        MIGRATION_VERSION = ''
        MIGRATION_INPUT_SHA256 = ''
        RUN_DB_MIGRATIONS = ''
        RELEASE_STATE_DIR = 'runtime/qa/out/jenkins/release-state'
        // The release manifest remains canonical ghcr.io@sha256. This host is
        // transport-only; the pull helper re-tags and verifies the exact
        // canonical digest before Compose is allowed to use it.
        IMAGE_PULL_REGISTRY = 'ghcr.1ms.run'
        // Digest evidence uses range/println templates for Docker 29.5 compatibility.
    }

    stages {
        stage('Checkout') {
            options { timeout(time: 15, unit: 'MINUTES') }
            steps {
                deleteDir()
                sh '''#!/usr/bin/env bash
                set -eu
                test -d "$CD_GIT_REFERENCE"
                test ! -e "$CD_GIT_REFERENCE/shallow"
                test "$(git --git-dir="$CD_GIT_REFERENCE" rev-parse --is-bare-repository)" = true
                '''
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${params.DEPLOY_BRANCH}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true,
                         reference: env.CD_GIT_REFERENCE, shallow: true, timeout: 60]
                    ],
                    userRemoteConfigs: [[
                        refspec: "+refs/heads/${params.DEPLOY_BRANCH}:refs/remotes/origin/${params.DEPLOY_BRANCH}",
                        url: env.CD_GIT_URL
                    ]]
                ])
                sh '''#!/usr/bin/env bash
                set -eu
                mkdir -p runtime/qa/out/jenkins \
                  /var/lib/jenkins/.cache/saas-real-pre-cd/m2 \
                  /var/lib/jenkins/.cache/saas-real-pre-cd/npm \
                  /var/lib/jenkins/.cache/saas-real-pre-cd/pnpm-store
                release_head_sha="$(git rev-parse HEAD)"
                build_branch="${DEPLOY_BRANCH:-release/real-pre}"
                {
                  printf 'RELEASE_HEAD_SHA=%s\n' "$release_head_sha"
                  printf 'BUILD_BRANCH=%s\n' "$build_branch"
                } > runtime/qa/out/jenkins/cd-env.sh
                printf '%s\n' "$release_head_sha" > runtime/qa/out/jenkins/release-head-sha.txt
                printf '%s\n' "$build_branch" > runtime/qa/out/jenkins/branch.txt
                echo "CD source: ${CD_GIT_URL}"
                echo "CD branch: ${build_branch}"
                echo "Release branch head: ${release_head_sha}"
                '''
            }
        }

        stage('Preflight Guard') {
            options { timeout(time: 10, unit: 'MINUTES') }
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh

                if [ "$DEPLOY_ENV" != "real-pre" ]; then
                  echo "ERROR: this Jenkinsfile only supports real-pre CD."
                  exit 1
                fi
                if [ "${DEPLOY_REAL_PRE:-false}" != "true" ]; then
                  echo "ERROR: DEPLOY_REAL_PRE must be true for this real-pre CD job."
                  exit 1
                fi
                if [ "$BUILD_BRANCH" != "release/real-pre" ]; then
                  echo "ERROR: only release/real-pre may deploy real-pre."
                  exit 1
                fi
                if [ "$COMPOSE_FILE" != "docker-compose.real-pre.yml" ]; then
                  echo "ERROR: production compose files are not allowed."
                  exit 1
                fi
                test -f "$ENV_FILE"
                test -f "$COMPOSE_FILE"
                test -f "$RELEASE_MANIFEST"
                # Compose keeps the local real-pre contract at .env.real-pre;
                # Jenkins injects the protected file outside the workspace.
                ln -sfn "$ENV_FILE" .env.real-pre
                test -f .env.real-pre
                test -z "$(git status --porcelain)"

                remote_release="$(git ls-remote "$CD_GIT_URL" refs/heads/release/real-pre | awk '{print $1}')"
                if [ -z "$remote_release" ] || [ "$remote_release" != "$RELEASE_HEAD_SHA" ]; then
                  echo "ERROR: checkout is not the current release/real-pre head."
                  exit 1
                fi

                git fetch --no-tags origin +refs/heads/main:refs/remotes/origin/main
                python3 scripts/verify-real-pre-release.py "$RELEASE_MANIFEST" --format shell > runtime/qa/out/jenkins/release-env.sh
                . runtime/qa/out/jenkins/release-env.sh
                if ! printf '%s' "$SOURCE_MAIN_SHA" | grep -Eq '^[0-9a-f]{40}$'; then
                  echo "ERROR: sourceMainSha must be a full 40-character SHA."
                  exit 1
                fi
                if ! printf '%s' "$IMAGE_TAG" | grep -Eq '^[0-9a-f]{40}$'; then
                  echo "ERROR: IMAGE_TAG must be the full 40-character commit SHA."
                  exit 1
                fi
                if ! git merge-base --is-ancestor "$SOURCE_MAIN_SHA" origin/main; then
                  echo "ERROR: release sourceMainSha is not reachable from main."
                  exit 1
                fi
                # release/real-pre may carry release-controller and operator
                # documentation updates without rebuilding application images.
                # Keep those paths explicit; all runtime code/configuration
                # must still match sourceMainSha exactly.
                git diff --check "$SOURCE_MAIN_SHA" "$RELEASE_HEAD_SHA" -- . \
                  ':(exclude)release/real-pre.json' \
                  ':(exclude)Jenkinsfile' \
                  ':(exclude).github/workflows/**' \
                  ':(exclude)docs/deploy/**' \
                  ':(exclude)scripts/verify-github-ci-gate.sh' \
                  ':(exclude)harness/scripts/tests/release-queue-governance.Tests.ps1'
                git diff --exit-code "$SOURCE_MAIN_SHA" "$RELEASE_HEAD_SHA" -- . \
                  ':(exclude)release/real-pre.json' \
                  ':(exclude)Jenkinsfile' \
                  ':(exclude).github/workflows/**' \
                  ':(exclude)docs/deploy/**' \
                  ':(exclude)scripts/verify-github-ci-gate.sh' \
                  ':(exclude)harness/scripts/tests/release-queue-governance.Tests.ps1'
                computed_migration_input_sha="$(python3 scripts/hash-real-pre-migration-inputs.py --ref "$SOURCE_MAIN_SHA")"
                if [ "$computed_migration_input_sha" != "$MIGRATION_INPUT_SHA256" ]; then
                  echo "ERROR: release migration input digest does not match sourceMainSha."
                  exit 1
                fi

                FULL_COMMIT="$SOURCE_MAIN_SHA"
                IMAGE_TAG="$SOURCE_MAIN_SHA"
                {
                  cat runtime/qa/out/jenkins/release-env.sh
                  printf 'RELEASE_HEAD_SHA=%s\n' "$RELEASE_HEAD_SHA"
                  printf 'FULL_COMMIT=%s\n' "$FULL_COMMIT"
                  printf 'IMAGE_TAG=%s\n' "$IMAGE_TAG"
                  printf 'BUILD_BRANCH=%s\n' "$BUILD_BRANCH"
                } > runtime/qa/out/jenkins/cd-env.sh
                printf '%s\n' "$SOURCE_MAIN_SHA" > runtime/qa/out/jenkins/source-main-sha.txt
                printf '%s\n' "$IMAGE_TAG" > runtime/qa/out/jenkins/image-tag.txt

                get_env() {
                  key="$1"
                  default_value="${2:-}"
                  value="$(awk -F= -v key="$key" '
                    /^[[:space:]]*#/ || /^[[:space:]]*$/ { next }
                    {
                      k=$1
                      gsub(/^[[:space:]]+|[[:space:]]+$/, "", k)
                      if (k == key) {
                        v=$0
                        sub(/^[^=]*=/, "", v)
                        gsub(/\r$/, "", v)
                        gsub(/^[[:space:]]+|[[:space:]]+$/, "", v)
                        gsub(/^"|"$/, "", v)
                        print v
                        exit
                      }
                    }
                  ' "$ENV_FILE")"
                  printf '%s' "${value:-$default_value}"
                }
                expect_env() {
                  key="$1"
                  expected="$2"
                  actual="$(get_env "$key")"
                  if [ "$actual" != "$expected" ]; then
                    echo "ERROR: $key must be $expected, got ${actual:-<empty>}."
                    exit 1
                  fi
                }
                require_env() {
                  key="$1"
                  value="$(get_env "$key")"
                  if [ -z "$value" ] || [ "${value#MUST_CHANGE}" != "$value" ] || [ "${value#*YOUR_}" != "$value" ] || [ "${value#*PLACEHOLDER}" != "$value" ]; then
                    echo "ERROR: $key is empty or still uses a placeholder."
                    exit 1
                  fi
                }
                expect_env COMPOSE_PROJECT_NAME saas-active
                expect_env SPRING_PROFILES_ACTIVE real-pre
                expect_env DB_NAME saas_real_pre
                expect_env APP_TEST_ENABLED false
                expect_env DOUYIN_TEST_ENABLED false
                expect_env DOUYIN_REAL_UPSTREAM_MODE live
                expect_env ORDER_SYNC_ENABLED true
                expect_env TALENT_COLLECT_MODE api
                expect_env TALENT_COLLECT_API_ENABLED true
                expect_env TALENT_PUBLIC_PAGE_CRAWL_ENABLED false
                expect_env LOGISTICS_PROVIDER kuaidi100
                expect_env LOGISTICS_KD100_ENABLED true
                expect_env LOGISTICS_KD100_SUBSCRIBE_ENABLED true
                expect_env LOGISTICS_SYNC_ENABLED true
                expect_env EXCLUSIVE_ENABLED false

                promotion_write="$(get_env DOUYIN_REAL_PROMOTION_WRITE_ENABLED false | tr '[:upper:]' '[:lower:]')"
                allow_promotion_write="$(get_env ALLOW_REAL_PROMOTION_WRITE false | tr '[:upper:]' '[:lower:]')"
                if [ "$promotion_write" = "true" ] && [ "$allow_promotion_write" != "true" ]; then
                  echo "ERROR: real promotion-write switches must be enabled together."
                  exit 1
                fi
                if [ "$allow_promotion_write" = "true" ] && [ "$promotion_write" != "true" ]; then
                  echo "ERROR: real promotion-write switches must be enabled together."
                  exit 1
                fi
                if [ "$promotion_write" = "true" ] && [ "${CONFIRM_REAL_PROMOTION_WRITE:-false}" != "true" ]; then
                  echo "ERROR: CONFIRM_REAL_PROMOTION_WRITE must be true for real promotion writes."
                  exit 1
                fi
                for key in DB_PASSWORD ADMIN_PASSWORD REDIS_PASSWORD JWT_SECRET CORS_ALLOWED_ORIGIN_PATTERNS \
                  DOUYIN_BASE_URL DOUYIN_APP_ID DOUYIN_CLIENT_KEY DOUYIN_CLIENT_SECRET \
                  DOUYIN_OAUTH_REDIRECT_URI DOUYIN_OAUTH_FRONTEND_SUCCESS_URL DOUYIN_OAUTH_FRONTEND_FAILURE_URL \
                  LOGISTICS_KD100_CUSTOMER LOGISTICS_KD100_KEY LOGISTICS_KD100_CALLBACK_URL LOGISTICS_KD100_CALLBACK_SALT; do
                  require_env "$key"
                done

                for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
                  if ! printf '%s' "$image" | grep -Eq '^[^@[:space:]]+@sha256:[0-9a-f]{64}$'; then
                    echo "ERROR: deployment images must use repository@sha256:digest."
                    exit 1
                  fi
                done
                docker version
                docker compose version
                BACKEND_IMAGE="$BACKEND_IMAGE" FRONTEND_IMAGE="$FRONTEND_IMAGE" IMAGE_TAG="$IMAGE_TAG" \
                  COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" config --quiet
                echo "Preflight guard passed without printing secrets."
                '''
            }
        }

        stage('GitHub CI SHA Gate') {
            options { timeout(time: 55, unit: 'MINUTES') }
            steps {
                withCredentials([string(credentialsId: 'github-actions-read-token', variable: 'GITHUB_TOKEN')]) {
                    sh '''#!/usr/bin/env bash
                    set -eu
                    . runtime/qa/out/jenkins/cd-env.sh
                    GITHUB_REPOSITORY="$CD_GIT_URL"
                    GITHUB_REPOSITORY="${GITHUB_REPOSITORY#git@github.com:}"
                    GITHUB_REPOSITORY="${GITHUB_REPOSITORY#https://github.com/}"
                    GITHUB_REPOSITORY="${GITHUB_REPOSITORY#ssh://git@github.com/}"
                    GITHUB_REPOSITORY="${GITHUB_REPOSITORY%.git}"
                    export GITHUB_REPOSITORY
                    # ci.yml push runs are produced on main. release/real-pre
                    # is the deployment branch and intentionally has no push
                    # workflow, so query the sourceMainSha on main instead.
                    GITHUB_WORKFLOW=ci.yml \
                      GITHUB_BRANCH=main \
                      GITHUB_SHA="$FULL_COMMIT" \
                      bash scripts/verify-github-ci-gate.sh
                    '''
                }
            }
        }

        stage('Backend Test') {
            when { expression { return params.RUN_BACKEND_TEST } }
            options { timeout(time: 15, unit: 'MINUTES') }
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                docker_gid="$(stat -c '%g' /var/run/docker.sock)"
                docker run --rm \
                  --user "$(id -u):$(id -g)" \
                  --group-add "$docker_gid" \
                  --network host \
                  -e HOME=/tmp \
                  -e MAVEN_CONFIG=/tmp/.m2 \
                  -e TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1 \
                  -e TESTCONTAINERS_RYUK_DISABLED=true \
                  -v /var/run/docker.sock:/var/run/docker.sock \
                  -v "$PWD":/workspace \
                  -v /var/lib/jenkins/.cache/saas-real-pre-cd/m2:/tmp/.m2 \
                  -w /workspace/backend \
                  maven:3.9-eclipse-temurin-17 \
                  mvn -B clean test
                '''
            }
        }

        stage('Pull Immutable Images') {
            options { timeout(time: 70, unit: 'MINUTES') }
            steps {
                withCredentials([usernamePassword(credentialsId: 'saas-container-registry', usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD')]) {
                    sh '''#!/usr/bin/env bash
                    set -eu
                    . runtime/qa/out/jenkins/cd-env.sh
                    registry_host="${BACKEND_IMAGE%%/*}"
                    printf '%s' "$REGISTRY_PASSWORD" | docker login "$registry_host" --username "$REGISTRY_USERNAME" --password-stdin
                    cleanup_registry_login() { docker logout "$registry_host" >/dev/null 2>&1 || true; }
                    trap cleanup_registry_login EXIT
                    export BACKEND_IMAGE FRONTEND_IMAGE FULL_COMMIT IMAGE_PULL_REGISTRY
                    bash scripts/cd/pull-immutable-images.sh
                    '''
                }
            }
        }

        stage('Compose Config') {
            options { timeout(time: 2, unit: 'MINUTES') }
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                BACKEND_IMAGE="$BACKEND_IMAGE" FRONTEND_IMAGE="$FRONTEND_IMAGE" IMAGE_TAG="$IMAGE_TAG" \
                  COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" config --quiet
                echo "docker compose config passed with immutable image references."
                '''
            }
        }

        stage('Serialized real-pre release') {
            options {
                timeout(time: 35, unit: 'MINUTES')
            }
            steps {
                script {
                    lock(resource: 'saas-real-pre-deploy', inversePrecedence: false) {
                        try {
            stage('Release Order and Migration Guard') {
                        sh '''#!/usr/bin/env bash
                        set -eu
                        . runtime/qa/out/jenkins/cd-env.sh
                        release_root="/opt/saas/releases"
                        current_manifest="$release_root/current.json"
                        mkdir -p "$release_root"

                        current_sha=""
                        if [ -f "$current_manifest" ]; then
                          current_sha="$(python3 - "$current_manifest" <<'PY'
import json
import sys
data = json.load(open(sys.argv[1], encoding='utf-8'))
print(data.get('sourceMainSha') or data.get('gitSha') or '')
PY
                          )"
                        fi
                        current_container="$(docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps -q backend-real-pre 2>/dev/null || true)"
                        if [ -n "$current_container" ]; then
                          docker inspect "$current_container" --format '{{.Config.Image}}' > runtime/qa/out/jenkins/pre-backend-image.txt
                          docker inspect "$current_container" --format '{{.Image}}' > runtime/qa/out/jenkins/pre-backend-local-image-id.txt
                          container_sha="$(docker image inspect "$(cat runtime/qa/out/jenkins/pre-backend-local-image-id.txt)" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' 2>/dev/null || true)"
                          if [ -z "$current_sha" ]; then current_sha="$container_sha"; fi
                        fi
                        current_frontend_container="$(docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps -q frontend-real-pre 2>/dev/null || true)"
                        if [ -n "$current_frontend_container" ]; then
                          docker inspect "$current_frontend_container" --format '{{.Config.Image}}' > runtime/qa/out/jenkins/pre-frontend-image.txt
                          docker inspect "$current_frontend_container" --format '{{.Image}}' > runtime/qa/out/jenkins/pre-frontend-local-image-id.txt
                        fi
                        rollback_backend_image="${PREVIOUS_BACKEND_IMAGE:-}"
                        rollback_frontend_image="${PREVIOUS_FRONTEND_IMAGE:-}"
                        if [ -z "$rollback_backend_image" ]; then rollback_backend_image="$(cat runtime/qa/out/jenkins/pre-backend-image.txt 2>/dev/null || true)"; fi
                        if [ -z "$rollback_frontend_image" ]; then rollback_frontend_image="$(cat runtime/qa/out/jenkins/pre-frontend-image.txt 2>/dev/null || true)"; fi
                        if ! printf '%s' "$rollback_backend_image" | grep -Eq '^[^@[:space:]]+@sha256:[0-9a-f]{64}$'; then
                          echo "ERROR: previous backend image must be an immutable repository@sha256:digest before deployment."
                          exit 1
                        fi
                        if ! printf '%s' "$rollback_frontend_image" | grep -Eq '^[^@[:space:]]+@sha256:[0-9a-f]{64}$'; then
                          echo "ERROR: previous frontend image must be an immutable repository@sha256:digest before deployment."
                          exit 1
                        fi
                        if [ -n "$current_container" ] && [ -n "${PREVIOUS_BACKEND_IMAGE:-}" ]; then
                          current_backend_repo_digests="$(docker image inspect "$(cat runtime/qa/out/jenkins/pre-backend-local-image-id.txt)" --format '{{range .RepoDigests}}{{println .}}{{end}}')"
                          printf '%s\n' "$current_backend_repo_digests" | grep -Fx "$rollback_backend_image" >/dev/null || {
                            echo "ERROR: release manifest previous backend image does not match the running backend content."
                            exit 1
                          }
                        fi
                        if [ -n "$current_frontend_container" ] && [ -n "${PREVIOUS_FRONTEND_IMAGE:-}" ]; then
                          current_frontend_repo_digests="$(docker image inspect "$(cat runtime/qa/out/jenkins/pre-frontend-local-image-id.txt)" --format '{{range .RepoDigests}}{{println .}}{{end}}')"
                          printf '%s\n' "$current_frontend_repo_digests" | grep -Fx "$rollback_frontend_image" >/dev/null || {
                            echo "ERROR: release manifest previous frontend image does not match the running frontend content."
                            exit 1
                          }
                        fi
                        if ! printf '%s' "$current_sha" | grep -Eq '^[0-9a-f]{40}$'; then
                          echo "ERROR: current deployed SHA is unavailable; refusing an unordered release."
                          exit 1
                        fi
                        git cat-file -e "$current_sha^{commit}"
                        if ! git merge-base --is-ancestor "$current_sha" "$SOURCE_MAIN_SHA"; then
                          if [ "${ROLLBACK_APPROVED:-false}" != "true" ]; then
                            echo "ERROR: target source is not a descendant of current deployment."
                            exit 1
                          fi
                          echo "Approved rollback/non-descendant release: $current_sha -> $SOURCE_MAIN_SHA"
                        fi
                        if [ -n "${PREVIOUS_SOURCE_MAIN_SHA:-}" ] && [ "$PREVIOUS_SOURCE_MAIN_SHA" != "$current_sha" ]; then
                          echo "ERROR: release manifest previous.sourceMainSha is not the currently deployed SHA."
                          exit 1
                        fi

                        RUN_DB_MIGRATIONS=false
                        if ! git diff --quiet "$current_sha" "$SOURCE_MAIN_SHA" -- \
                          backend/src/main/resources/db/migration \
                          ':(glob)backend/src/main/resources/db/*.sql' \
                          scripts/run-real-pre-db-migrations.sh scripts/check-real-pre-schema.sh; then
                          RUN_DB_MIGRATIONS=true
                        fi
                        {
                          printf 'CURRENT_SHA=%s\n' "$current_sha"
                          printf 'RUN_DB_MIGRATIONS=%s\n' "$RUN_DB_MIGRATIONS"
                          printf 'ROLLBACK_SOURCE_MAIN_SHA=%s\n' "$current_sha"
                          printf 'ROLLBACK_BACKEND_IMAGE=%s\n' "$rollback_backend_image"
                          printf 'ROLLBACK_FRONTEND_IMAGE=%s\n' "$rollback_frontend_image"
                        } >> runtime/qa/out/jenkins/cd-env.sh
                        mkdir -p "$RELEASE_STATE_DIR"
                        rm -f "$RELEASE_STATE_DIR/deployment-started" "$RELEASE_STATE_DIR/rollback-started" \
                          "$RELEASE_STATE_DIR/rollback-completed" "$RELEASE_STATE_DIR/release-completed" \
                          "$RELEASE_STATE_DIR/schedulers-restored" "$RELEASE_STATE_DIR/schedulers-paused"
                        printf '%s\n' "$current_sha" > runtime/qa/out/jenkins/current-sha.txt
                        printf '%s\n' "$RUN_DB_MIGRATIONS" > runtime/qa/out/jenkins/run-db-migrations.txt
                        echo "Release order guard passed: $current_sha -> $SOURCE_MAIN_SHA; RUN_DB_MIGRATIONS=$RUN_DB_MIGRATIONS"
                        '''
                }

                stage('Database Backup, Migration and Schema Precheck') {
                        sh '''#!/usr/bin/env bash
                        set -eu
                        . runtime/qa/out/jenkins/cd-env.sh
                        if [ "$RUN_DB_MIGRATIONS" != "true" ]; then
                          echo "Database work skipped: no migration inputs changed" | tee runtime/qa/out/jenkins/database-migration.txt
                          printf '%s\n' "SKIPPED: no migration inputs changed" > runtime/qa/out/jenkins/database-backup.txt
                          printf '%s\n' "SKIPPED: no migration inputs changed" > runtime/qa/out/jenkins/schema-precheck.txt
                          exit 0
                        fi
                        backup_dir="/opt/saas/backups/jenkins-${BUILD_NUMBER:-manual}"
                        mkdir -p "$backup_dir"
                        ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" COMPOSE_PROJECT_NAME="$PROJECT_NAME" BACKUP_DIR="$backup_dir" \
                          BACKEND_IMAGE="$BACKEND_IMAGE" FRONTEND_IMAGE="$FRONTEND_IMAGE" bash scripts/cd/release-real-pre.sh backup | tee runtime/qa/out/jenkins/database-backup.txt
                        ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                          IMAGE_TAG="$IMAGE_TAG" BACKEND_IMAGE="$BACKEND_IMAGE" FRONTEND_IMAGE="$FRONTEND_IMAGE" \
                          BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" REQUIRE_PINNED_IMAGE=true \
                          bash scripts/cd/release-real-pre.sh migrate | tee runtime/qa/out/jenkins/database-migration.txt
                        REAL_PRE_COMPOSE_ENV="$ENV_FILE" REAL_PRE_COMPOSE_FILE="$COMPOSE_FILE" REAL_PRE_COMPOSE_PROJECT="$PROJECT_NAME" \
                          sh scripts/check-real-pre-schema.sh | tee runtime/qa/out/jenkins/schema-precheck.txt
                        '''
                }

                stage('Deploy Backend (Schedulers Paused)') {
                        sh '''#!/usr/bin/env bash
                        set -eu
                        . runtime/qa/out/jenkins/cd-env.sh
                        touch "$RELEASE_STATE_DIR/deployment-started" "$RELEASE_STATE_DIR/schedulers-paused" runtime/qa/out/jenkins/schedulers-paused
                        APP_SCHEDULING_ENABLED=false IMAGE_TAG="$IMAGE_TAG" BACKEND_IMAGE="$BACKEND_IMAGE" FRONTEND_IMAGE="$FRONTEND_IMAGE" \
                          BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                          docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps backend-real-pre
                        docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/post-backend-compose-ps.txt
                        '''
                }

                stage('Backend Readiness') {
                        sh '''#!/usr/bin/env bash
                        set -eu
                        for _ in $(seq 1 120); do
                          if curl -fsS "$REAL_PRE_BACKEND/api/actuator/health/readiness" | grep -q '"status":"UP"'; then
                            curl -fsS "$REAL_PRE_BACKEND/api/actuator/health/readiness" > runtime/qa/out/jenkins/backend-readiness.json
                            exit 0
                          fi
                          sleep 2
                        done
                        docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=300 backend-real-pre >&2 || true
                        exit 1
                        '''
                }

                stage('Deploy Frontend') {
                        sh '''#!/usr/bin/env bash
                        set -eu
                        . runtime/qa/out/jenkins/cd-env.sh
                        IMAGE_TAG="$IMAGE_TAG" BACKEND_IMAGE="$BACKEND_IMAGE" FRONTEND_IMAGE="$FRONTEND_IMAGE" \
                          COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                          docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps frontend-real-pre
                        '''
                }

                stage('Core Smoke and Multi-role E2E') {
                        sh '''#!/usr/bin/env bash
                        set -eu
                        npx --yes pnpm@9 install --frozen-lockfile
                        npm run e2e:real-pre:p0
                        npm run e2e:real-pre:roles
                        '''
                }

                stage('Restore Schedulers') {
                        sh '''#!/usr/bin/env bash
                        set -eu
                        . runtime/qa/out/jenkins/cd-env.sh
                        APP_SCHEDULING_ENABLED=true IMAGE_TAG="$IMAGE_TAG" BACKEND_IMAGE="$BACKEND_IMAGE" FRONTEND_IMAGE="$FRONTEND_IMAGE" \
                          BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                          docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps backend-real-pre
                        for _ in $(seq 1 120); do
                          if curl -fsS "$REAL_PRE_BACKEND/api/actuator/health/readiness" | grep -q '"status":"UP"'; then
                            touch "$RELEASE_STATE_DIR/schedulers-restored"
                            rm -f "$RELEASE_STATE_DIR/schedulers-paused" runtime/qa/out/jenkins/schedulers-paused
                            exit 0
                          fi
                          sleep 2
                        done
                        exit 1
                        '''
                }

                stage('Health Check') {
                        sh '''#!/usr/bin/env bash
                        set -eu
                        . runtime/qa/out/jenkins/cd-env.sh
                        ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" COMPOSE_PROJECT_NAME="$PROJECT_NAME" bash scripts/health-check.sh
                        '''
                }

                stage('Evidence Report') {
                        sh '''#!/usr/bin/env bash
                        set -eu
                        . runtime/qa/out/jenkins/cd-env.sh
                        report="runtime/qa/out/latest-jenkins-cd.md"
                        remote_report="/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}/latest-evidence-jenkins-cd.md"
                        evidence_result="PASS"
                        backend_container="$(docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps -q backend-real-pre)"
                        frontend_container="$(docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps -q frontend-real-pre)"
                        backend_running_image="$(docker inspect "$backend_container" --format '{{.Config.Image}}')"
                        frontend_running_image="$(docker inspect "$frontend_container" --format '{{.Config.Image}}')"
                        backend_running_id="$(docker inspect "$backend_container" --format '{{.Image}}')"
                        frontend_running_id="$(docker inspect "$frontend_container" --format '{{.Image}}')"
                        backend_revision="$(docker image inspect "$backend_running_id" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
                        frontend_revision="$(docker image inspect "$frontend_running_id" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
                        backend_repo_digests="$(docker image inspect "$backend_running_id" --format '{{range .RepoDigests}}{{println .}}{{end}}')"
                        frontend_repo_digests="$(docker image inspect "$frontend_running_id" --format '{{range .RepoDigests}}{{println .}}{{end}}')"
                        backend_health="$(curl -fsS "$REAL_PRE_BACKEND/api/system/health" || true)"
                        frontend_health="$(curl -fsS "$REAL_PRE_FRONTEND/healthz" || true)"
                        frontend_version="$(curl -fsS "$REAL_PRE_FRONTEND/version.json" || true)"
                        migration_versions="$(docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" exec -T postgres-real-pre sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1 -c "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank;"' || true)"
                        printf '%s\n' "$backend_health" | grep -q '"status":"UP"' || evidence_result="FAIL"
                        printf '%s\n' "$backend_health" | grep -Eq "\"gitSha\"[[:space:]]*:[[:space:]]*\"$FULL_COMMIT\"" || evidence_result="FAIL"
                        printf '%s\n' "$backend_health" | grep -Eq "\"imageDigest\"[[:space:]]*:[[:space:]]*\"$BACKEND_IMAGE_DIGEST\"" || evidence_result="FAIL"
                        printf '%s\n' "$frontend_version" | grep -Eq "\"gitSha\"[[:space:]]*:[[:space:]]*\"$FULL_COMMIT\"" || evidence_result="FAIL"
                        test "$backend_running_image" = "$BACKEND_IMAGE" || evidence_result="FAIL"
                        test "$frontend_running_image" = "$FRONTEND_IMAGE" || evidence_result="FAIL"
                        printf '%s\n' "$backend_repo_digests" | grep -Fx "$BACKEND_IMAGE" >/dev/null || evidence_result="FAIL"
                        printf '%s\n' "$frontend_repo_digests" | grep -Fx "$FRONTEND_IMAGE" >/dev/null || evidence_result="FAIL"
                        test "$backend_revision" = "$FULL_COMMIT" || evidence_result="FAIL"
                        test "$frontend_revision" = "$FULL_COMMIT" || evidence_result="FAIL"
                        if [ "$RUN_DB_MIGRATIONS" = "true" ]; then
                          printf '%s\n' "$migration_versions" | grep -F "$MIGRATION_VERSION" >/dev/null || evidence_result="FAIL"
                        fi

                        release_root="/opt/saas/releases"
                        release_dir="$release_root/$FULL_COMMIT"
                        release_candidate="runtime/qa/out/jenkins/release.json"
                        release_manifest="$release_dir/release.json"
                        previous_release_sha="$(cat "$release_root/previous.json" 2>/dev/null \
                          | grep -Eo '"gitSha"[[:space:]]*:[[:space:]]*"[0-9a-f]{40}"' \
                          | head -n 1 | grep -Eo '[0-9a-f]{40}' || true)"
                        [ -n "$previous_release_sha" ] || previous_release_sha="null"
                        ci_run_id="$(cat runtime/qa/out/jenkins/github-ci-run-id.txt 2>/dev/null || true)"
                        ci_run_url="$(cat runtime/qa/out/jenkins/github-ci-run-url.txt 2>/dev/null || true)"
                        [ -n "$ci_run_id" ] || ci_run_id="unknown"
                        [ -n "$ci_run_url" ] || ci_run_url="unknown"
                        migration_versions_json="$(printf '%s\n' "$migration_versions" | python3 -c 'import json,sys; print(json.dumps([line.strip() for line in sys.stdin if line.strip()]))')"
                        cat > "$release_candidate" <<EOF
                        {
                          "gitSha": "$FULL_COMMIT",
                          "branch": "$BUILD_BRANCH",
                          "backendDigest": "$BACKEND_IMAGE_DIGEST",
                          "frontendDigest": "$FRONTEND_IMAGE_DIGEST",
                          "migrationVersions": $migration_versions_json,
                          "ciRun": {
                            "id": "$ci_run_id",
                            "url": "$ci_run_url",
                            "workflow": "ci.yml",
                            "branch": "main",
                            "sha": "$FULL_COMMIT"
                          },
                          "jenkinsBuild": {
                            "job": "${JOB_NAME:-unknown}",
                            "number": ${BUILD_NUMBER:-0},
                            "url": "${BUILD_URL:-unknown}"
                          },
                          "deployResult": "$evidence_result",
                          "previous": $previous_release_sha,
                          "rollbackTarget": $previous_release_sha,
                          "sourceMainSha": "$SOURCE_MAIN_SHA",
                          "imageTag": "$IMAGE_TAG"
                        }
EOF
                        if [ "$evidence_result" = "PASS" ]; then
                          mkdir -p "$release_dir"
                          if [ -f "$release_manifest" ]; then
                            cmp -s "$release_candidate" "$release_manifest" || { echo "ERROR: immutable release manifest differs."; evidence_result="FAIL"; }
                          else
                            install -m 0444 "$release_candidate" "$release_manifest"
                          fi
                        fi
                        if [ "$evidence_result" = "PASS" ]; then
                          REPO_ROOT="${WORKSPACE:-$PWD}" \
                            RELEASES_BASE="$release_root" \
                            bash scripts/cd/evidence-collect.sh release-manifest "$FULL_COMMIT" "$release_candidate"
                        fi

                        mkdir -p "$(dirname "$remote_report")"
                        {
                          echo "# Jenkins CD Evidence"
                          echo
                          echo "- Result: $evidence_result"
                          echo "- Environment: real-pre"
                          echo "- Source: $CD_GIT_URL"
                          echo "- Release branch head: $RELEASE_HEAD_SHA"
                          echo "- Source main commit: $SOURCE_MAIN_SHA"
                          echo "- Backend image: $BACKEND_IMAGE"
                          echo "- Frontend image: $FRONTEND_IMAGE"
                          echo "- Jenkins job: ${JOB_NAME:-unknown}"
                          echo "- Build number: ${BUILD_NUMBER:-unknown}"
                          echo "- Time: $(date -Iseconds)"
                          echo "- Production touched: NO"
                          echo "- Database migration/write by pipeline: $RUN_DB_MIGRATIONS"
                          echo "- Migration version: $MIGRATION_VERSION"
                          echo "- Migration input digest: $MIGRATION_INPUT_SHA256"
                          echo "- Previous backend image: ${ROLLBACK_BACKEND_IMAGE:-unknown}"
                          echo "- Previous frontend image: ${ROLLBACK_FRONTEND_IMAGE:-unknown}"
                          echo "- Secret leaked: NO"
                          echo
                          echo "## Health"
                          echo '```'
                          printf '%s\n' "$backend_health" "$frontend_health" "$frontend_version"
                          echo '```'
                          echo
                          echo "## Container image evidence"
                          echo '```'
                          docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}" | grep 'saas-active-' || true
                          echo '```'
                          echo "- Backend local image ID: $backend_running_id"
                          echo "- Frontend local image ID: $frontend_running_id"
                          echo "- Backend OCI revision: $backend_revision"
                          echo "- Frontend OCI revision: $frontend_revision"
                          echo "- Release manifest: $release_manifest"
                          echo
                          echo "## Flyway versions"
                          echo '```'
                          printf '%s\n' "$migration_versions"
                          echo '```'
                        } > "$report"
                        cp "$report" "$remote_report"
                        cat "$report"
                        test "$evidence_result" = PASS
                        touch "$RELEASE_STATE_DIR/release-completed"
                        '''
                        }
                        } finally {
                            def rollbackStatus = sh(
                                script: '''#!/usr/bin/env bash
                                set +e
                                if [ -f runtime/qa/out/jenkins/cd-env.sh ]; then . runtime/qa/out/jenkins/cd-env.sh; fi
                                state_dir="${RELEASE_STATE_DIR:-runtime/qa/out/jenkins/release-state}"
                                if [ -f "$state_dir/deployment-started" ] && [ ! -f "$state_dir/rollback-completed" ] && [ ! -f "$state_dir/release-completed" ]; then
                                  echo "Release stage failed after deployment started; rolling back while saas-real-pre-deploy lock is still held."
                                  ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                                    RELEASE_STATE_DIR="$state_dir" \
                                    ROLLBACK_SOURCE_MAIN_SHA="${ROLLBACK_SOURCE_MAIN_SHA:-}" \
                                    ROLLBACK_BACKEND_IMAGE="${ROLLBACK_BACKEND_IMAGE:-}" \
                                    ROLLBACK_FRONTEND_IMAGE="${ROLLBACK_FRONTEND_IMAGE:-}" \
                                    bash scripts/cd/release-real-pre.sh rollback-immutable
                                fi
                                exit 0
                                ''',
                                returnStatus: true
                            )
            if (rollbackStatus != 0) {
                echo "Lock-scoped rollback command failed with status ${rollbackStatus}."
            }
                }
            }
        }
    }
    post {
        unsuccessful {
            echo 'Serialized release failed; rollback was attempted inside the deployment lock.'
        }
    }
    }
    }

    post {
        always {
            script { env.FINAL_BUILD_RESULT = currentBuild.currentResult ?: 'SUCCESS' }
            sh '''#!/usr/bin/env bash
            set +e
            if [ -f runtime/qa/out/jenkins/cd-env.sh ]; then . runtime/qa/out/jenkins/cd-env.sh; fi
            mkdir -p runtime/qa/out/jenkins "/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}"
            state_dir="${RELEASE_STATE_DIR:-runtime/qa/out/jenkins/release-state}"
            if [ -f "$state_dir/deployment-started" ] && [ ! -f "$state_dir/rollback-completed" ] && [ ! -f "$state_dir/release-completed" ]; then
              echo "ERROR: lock-scoped rollback did not complete; refusing post-lock mutation of real-pre." >&2
            fi
            if [ -f "$state_dir/rollback-completed" ] || [ -f "$state_dir/release-completed" ]; then
              rm -f runtime/qa/out/jenkins/schedulers-paused
            fi
            docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}" > runtime/qa/out/jenkins/docker-ps-final.txt 2>&1
            docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/docker-compose-ps-final.txt 2>&1
            if [ ! -f runtime/qa/out/latest-jenkins-cd.md ]; then
              {
                echo "# Jenkins CD Evidence"
                echo
                echo "- Result: ${FINAL_BUILD_RESULT:-FAIL}"
                echo "- Environment: real-pre"
                echo "- Release branch head: ${RELEASE_HEAD_SHA:-unknown}"
                echo "- Source main commit: ${SOURCE_MAIN_SHA:-unknown}"
                echo "- Production touched: NO"
                echo "- Evidence: deployment stopped before the final evidence stage; inspect archived Jenkins logs."
                echo "- Secret leaked: NO"
              } > runtime/qa/out/latest-jenkins-cd.md
            fi
            cp runtime/qa/out/latest-jenkins-cd.md "/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}/latest-evidence-jenkins-cd.md" 2>/dev/null || true
            '''
            archiveArtifacts artifacts: 'runtime/qa/out/latest-jenkins-cd.md,runtime/qa/out/jenkins/**,runtime/qa/out/real-pre-*/**,backend/target/surefire-reports/**,frontend/coverage/**', allowEmptyArchive: true
        }
        success { echo "real-pre Jenkins CD completed. sourceMainSha=${env.SOURCE_MAIN_SHA}" }
        failure { echo 'real-pre Jenkins CD failed. Check Jenkins logs and archived evidence.' }
    }
}
