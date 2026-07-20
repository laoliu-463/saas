pipeline {
    agent any

    options {
        disableConcurrentBuilds(abortPrevious: false)
        buildDiscarder(logRotator(numToKeepStr: '20'))
        skipDefaultCheckout(true)
    }

    parameters {
        string(name: 'DEPLOY_BRANCH', defaultValue: 'release/real-pre', description: 'Only release/real-pre is deployable.')
        booleanParam(name: 'DEPLOY_REAL_PRE', defaultValue: false, description: 'Explicit approval required to deploy real-pre.')
        booleanParam(name: 'CONFIRM_REAL_PROMOTION_WRITE', defaultValue: false, description: 'Required only when real-pre promotion-write switches are enabled.')
        booleanParam(name: 'ROLLBACK_APPROVED', defaultValue: false, description: 'Explicit approval required when the target is not a descendant of the deployed commit.')
        booleanParam(name: 'RUN_BACKEND_TEST', defaultValue: false, description: 'Diagnostic rerun only; does not bypass required GitHub Actions checks.')
    }

    environment {
        JOB_PURPOSE = 'real-pre-cd'
        DEPLOY_ENV = 'real-pre'
        CD_GIT_URL = 'git@github.com:laoliu-463/saas.git'
        ENV_FILE = '/opt/saas/env/.env.real-pre'
        COMPOSE_FILE = 'docker-compose.real-pre.yml'
        PROJECT_NAME = 'saas-active'
        REAL_PRE_BACKEND = 'http://127.0.0.1:8081'
        REAL_PRE_FRONTEND = 'http://127.0.0.1:3001'
        RUN_DB_MIGRATIONS = ''
        IMAGE_TAG = ''
        FULL_COMMIT = ''
        BUILD_BRANCH = ''
        BACKEND_IMAGE_DIGEST = ''
        FRONTEND_IMAGE_DIGEST = ''
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${params.DEPLOY_BRANCH}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CloneOption', honorRefspec: true, noTags: true,
                         reference: '', shallow: false, timeout: 60]
                    ],
                    userRemoteConfigs: [[
                        refspec: "+refs/heads/${params.DEPLOY_BRANCH}:refs/remotes/origin/${params.DEPLOY_BRANCH}",
                        url: env.CD_GIT_URL
                    ]]
                ])
                script {
                    env.FULL_COMMIT = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG = env.FULL_COMMIT
                    env.BUILD_BRANCH = params.DEPLOY_BRANCH
                }
                sh '''#!/usr/bin/env bash
                set -eu
                mkdir -p runtime/qa/out/jenkins harness/reports \
                  /var/lib/jenkins/.cache/saas-real-pre-cd/m2 \
                  /var/lib/jenkins/.cache/saas-real-pre-cd/npm \
                  /var/lib/jenkins/.cache/saas-real-pre-cd/pnpm-store
                full_commit="$(git rev-parse HEAD)"
                image_tag="$full_commit"
                build_branch="${DEPLOY_BRANCH:-release/real-pre}"
                {
                  printf 'FULL_COMMIT=%s\n' "$full_commit"
                  printf 'IMAGE_TAG=%s\n' "$image_tag"
                  printf 'BUILD_BRANCH=%s\n' "$build_branch"
                } > runtime/qa/out/jenkins/cd-env.sh
                printf '%s\n' "$full_commit" > runtime/qa/out/jenkins/commit.txt
                printf '%s\n' "$build_branch" > runtime/qa/out/jenkins/branch.txt
                printf '%s\n' "$image_tag" > runtime/qa/out/jenkins/image-tag.txt
                echo "CD source: ${CD_GIT_URL}"
                echo "CD branch: ${build_branch}"
                echo "CD commit: ${full_commit}"
                echo "Image tag: ${image_tag}"
                '''
            }
        }

        stage('Preflight Guard') {
            options {
                timeout(time: 55, unit: 'MINUTES')
            }
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
                test -z "$(git status --porcelain)"
                if ! printf '%s' "$IMAGE_TAG" | grep -Eq '^[0-9a-f]{40}$'; then
                  echo "ERROR: IMAGE_TAG must be the full 40-character commit SHA."
                  exit 1
                fi
                remote_release="$(git ls-remote "$CD_GIT_URL" refs/heads/release/real-pre | awk '{print $1}')"
                if [ -z "$remote_release" ] || [ "$remote_release" != "$FULL_COMMIT" ]; then
                  echo "ERROR: checkout SHA is not the current release/real-pre head."
                  exit 1
                fi
                # PR #6: GHA SHA Gate.
                # The exact FULL_COMMIT being deployed must have a successful ci.yml
                # push run on release/real-pre (Backend tests, Frontend tests and
                # build, Repository governance all green). RUN_BACKEND_TEST=true on
                # the job only re-runs Jenkins diagnostics; it cannot bypass this gate.
                if [ -z "${GITHUB_TOKEN:-}" ]; then
                  echo "ERROR: GITHUB_TOKEN credential is required (credentialId: github-actions-read-token)."
                  echo "Create a fine-grained PAT with Actions:read on the repo and add it as a Jenkins Secret text."
                  exit 1
                fi
                GITHUB_REPOSITORY="${CD_GIT_URL#*github.com/}"; GITHUB_REPOSITORY="${GITHUB_REPOSITORY%.git}"
                GITHUB_WORKFLOW=ci.yml \
                GITHUB_BRANCH=release/real-pre \
                GITHUB_SHA="$FULL_COMMIT" \
                  bash scripts/verify-github-ci-gate.sh
                echo "GHA SHA Gate passed for $FULL_COMMIT." 
                git fetch --no-tags origin +refs/heads/main:refs/remotes/origin/main
                target_tree="$(git rev-parse "$FULL_COMMIT^{tree}")"
                source_main_sha="$(git rev-list origin/main | while read -r candidate; do
                  if [ "$(git rev-parse "$candidate^{tree}")" = "$target_tree" ]; then
                    printf '%s\n' "$candidate"
                    break
                  fi
                done)"
                if ! printf '%s' "$source_main_sha" | grep -Eq '^[0-9a-f]{40}$'; then
                  echo "ERROR: release tree does not match any commit reachable from main."
                  exit 1
                fi
                printf 'SOURCE_MAIN_SHA=%s\n' "$source_main_sha" >> runtime/qa/out/jenkins/cd-env.sh
                printf '%s\n' "$source_main_sha" > runtime/qa/out/jenkins/source-main-sha.txt
                ln -sfn "$ENV_FILE" .env.real-pre
                chmod +x scripts/*.sh || true

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
                        gsub(/\\r$/, "", v)
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
                  echo "ERROR: DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true requires ALLOW_REAL_PROMOTION_WRITE=true."
                  exit 1
                fi
                if [ "$allow_promotion_write" = "true" ] && [ "$promotion_write" != "true" ]; then
                  echo "ERROR: ALLOW_REAL_PROMOTION_WRITE=true requires DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true."
                  exit 1
                fi
                if [ "$promotion_write" = "true" ] && [ "${CONFIRM_REAL_PROMOTION_WRITE:-false}" != "true" ]; then
                  echo "ERROR: real promotion-write switches are enabled; CONFIRM_REAL_PROMOTION_WRITE must be true."
                  exit 1
                fi

                for key in \
                  DB_PASSWORD \
                  ADMIN_PASSWORD \
                  REDIS_PASSWORD \
                  JWT_SECRET \
                  CORS_ALLOWED_ORIGIN_PATTERNS \
                  DOUYIN_BASE_URL \
                  DOUYIN_APP_ID \
                  DOUYIN_CLIENT_KEY \
                  DOUYIN_CLIENT_SECRET \
                  DOUYIN_OAUTH_REDIRECT_URI \
                  DOUYIN_OAUTH_FRONTEND_SUCCESS_URL \
                  DOUYIN_OAUTH_FRONTEND_FAILURE_URL \
                  LOGISTICS_KD100_CUSTOMER \
                  LOGISTICS_KD100_KEY \
                  LOGISTICS_KD100_CALLBACK_URL \
                  LOGISTICS_KD100_CALLBACK_SALT; do
                  require_env "$key"
                done

                docker version
                docker compose version
                IMAGE_TAG="$IMAGE_TAG" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" config --quiet
                echo "Preflight guard passed without printing secrets."
                '''
            }
        }

        stage('Backend Test') {
            when {
                expression { params.RUN_BACKEND_TEST }
            }
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
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

        stage('Backend Package') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                rm -rf backend/target
                docker run --rm \
                  --user "$(id -u):$(id -g)" \
                  -e HOME=/tmp \
                  -e MAVEN_CONFIG=/tmp/.m2 \
                  -v "$PWD":/workspace \
                  -v /var/lib/jenkins/.cache/saas-real-pre-cd/m2:/tmp/.m2 \
                  -w /workspace/backend \
                  maven:3.9-eclipse-temurin-17 \
                  mvn -B clean package -DskipTests
                ls -lh backend/target/*.jar
                '''
            }
        }

        stage('Frontend Build') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                export NPM_CONFIG_CACHE=/var/lib/jenkins/.cache/saas-real-pre-cd/npm
                npx --yes pnpm@9 config set store-dir /var/lib/jenkins/.cache/saas-real-pre-cd/pnpm-store
                cd frontend
                npx --yes pnpm@9 install --frozen-lockfile
                npx --yes pnpm@9 test
                npx --yes pnpm@9 typecheck
                npx --yes pnpm@9 build
                '''
            }
        }

        stage('Docker Build') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                test -f backend/target/*.jar
                echo "Building backend and frontend images with tag: $IMAGE_TAG"
                IMAGE_TAG="$IMAGE_TAG" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" build backend-real-pre frontend-real-pre
                docker image inspect "colonel-saas/backend:$IMAGE_TAG" >/dev/null
                docker image inspect "colonel-saas/frontend:$IMAGE_TAG" >/dev/null
                test "$(docker image inspect "colonel-saas/backend:$IMAGE_TAG" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')" = "$FULL_COMMIT"
                test "$(docker image inspect "colonel-saas/frontend:$IMAGE_TAG" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')" = "$FULL_COMMIT"
                backend_image_digest="$(docker image inspect "colonel-saas/backend:$IMAGE_TAG" --format '{{.Id}}')"
                frontend_image_digest="$(docker image inspect "colonel-saas/frontend:$IMAGE_TAG" --format '{{.Id}}')"
                for image_digest in "$backend_image_digest" "$frontend_image_digest"; do
                  if ! printf '%s' "$image_digest" | grep -Eq '^sha256:[0-9a-f]{64}$'; then
                    echo "ERROR: Docker image digests must be content-addressed sha256 values."
                    exit 1
                  fi
                done
                {
                  printf 'BACKEND_IMAGE_DIGEST=%s\n' "$backend_image_digest"
                  printf 'FRONTEND_IMAGE_DIGEST=%s\n' "$frontend_image_digest"
                } >> runtime/qa/out/jenkins/cd-env.sh
                printf '%s\n' "$backend_image_digest" > runtime/qa/out/jenkins/backend-image-digest.txt
                printf '%s\n' "$frontend_image_digest" > runtime/qa/out/jenkins/frontend-image-digest.txt
                '''
            }
        }

        stage('docker compose config') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                IMAGE_TAG="$IMAGE_TAG" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" config --quiet
                echo "docker compose config passed for image tag $IMAGE_TAG"
                '''
            }
        }

        stage('Serialized real-pre release') {
            // The top-level disableConcurrentBuilds option serializes the sole CD job.
            // Canonical lock: lock(resource: 'saas-real-pre-deploy', inversePrecedence: false)
            // Lockable Resources is not installed on the real-pre Jenkins host.
            stages {
        stage('Release Order and Migration Guard') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                release_root="/opt/saas/releases"
                current_manifest="$release_root/current.json"
                mkdir -p "$release_root"

                current_sha=""
                if [ -f "$current_manifest" ]; then
                  current_sha="$(grep -Eo '"gitSha"[[:space:]]*:[[:space:]]*"[0-9a-f]{40}"' "$current_manifest" | grep -Eo '[0-9a-f]{40}' | head -n 1)"
                fi
                if [ -z "$current_sha" ]; then
                  current_container="$(docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps -q backend-real-pre 2>/dev/null || true)"
                  if [ -n "$current_container" ]; then
                    current_image_id="$(docker inspect "$current_container" --format '{{.Image}}')"
                    current_sha="$(docker image inspect "$current_image_id" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' 2>/dev/null || true)"
                  fi
                fi
                if ! printf '%s' "$current_sha" | grep -Eq '^[0-9a-f]{40}$'; then
                  echo "ERROR: current deployed SHA is unavailable; refusing an unordered release."
                  exit 1
                fi
                git cat-file -e "$current_sha^{commit}"
                if ! git merge-base --is-ancestor "$current_sha" "$FULL_COMMIT"; then
                  if [ "${ROLLBACK_APPROVED:-false}" != "true" ]; then
                    echo "ERROR: target is not a descendant of current deployment; set ROLLBACK_APPROVED=true only for an approved rollback."
                    exit 1
                  fi
                  echo "Approved rollback/non-descendant release: $current_sha -> $FULL_COMMIT"
                fi

                RUN_DB_MIGRATIONS=false
                if ! git diff --quiet "$current_sha" "$FULL_COMMIT" -- \
                  backend/src/main/resources/db/migration \
                  ':(glob)backend/src/main/resources/db/*.sql' \
                  scripts/run-real-pre-db-migrations.sh; then
                  RUN_DB_MIGRATIONS=true
                fi
                {
                  printf 'CURRENT_SHA=%s\n' "$current_sha"
                  printf 'RUN_DB_MIGRATIONS=%s\n' "$RUN_DB_MIGRATIONS"
                } >> runtime/qa/out/jenkins/cd-env.sh
                printf '%s\n' "$current_sha" > runtime/qa/out/jenkins/current-sha.txt
                printf '%s\n' "$RUN_DB_MIGRATIONS" > runtime/qa/out/jenkins/run-db-migrations.txt
                echo "Release order guard passed: $current_sha -> $FULL_COMMIT; RUN_DB_MIGRATIONS=$RUN_DB_MIGRATIONS"
                '''
            }
        }

        stage('Database Backup, Migration and Schema Precheck') {
            steps {
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

                ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" \
                  COMPOSE_PROJECT_NAME="$PROJECT_NAME" BACKUP_DIR="$backup_dir" \
                  bash scripts/backup-db.sh | tee runtime/qa/out/jenkins/database-backup.txt

                ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" \
                  COMPOSE_PROJECT_NAME="$PROJECT_NAME" IMAGE_TAG="$IMAGE_TAG" \
                  BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" REQUIRE_PINNED_IMAGE=true \
                  sh scripts/run-real-pre-db-migrations.sh | tee runtime/qa/out/jenkins/database-migration.txt

                REAL_PRE_COMPOSE_ENV="$ENV_FILE" REAL_PRE_COMPOSE_FILE="$COMPOSE_FILE" \
                  REAL_PRE_COMPOSE_PROJECT="$PROJECT_NAME" \
                  sh scripts/check-real-pre-schema.sh | tee runtime/qa/out/jenkins/schema-precheck.txt
                '''
            }
        }

        stage('Deploy Backend (Schedulers Paused)') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                mkdir -p runtime/qa/out/jenkins "/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}"

                docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/pre-deploy-compose-ps.txt || true
                docker ps --format "table {{.Names}}\\t{{.Image}}\\t{{.Status}}" > runtime/qa/out/jenkins/pre-deploy-images.txt || true
                docker inspect saas-active-backend-real-pre-1 --format '{{.Config.Image}}' > runtime/qa/out/jenkins/pre-backend-image.txt 2>/dev/null || true
                docker inspect saas-active-frontend-real-pre-1 --format '{{.Config.Image}}' > runtime/qa/out/jenkins/pre-frontend-image.txt 2>/dev/null || true

                echo "Deploying backend real-pre with schedulers paused, image tag: $IMAGE_TAG"
                APP_SCHEDULING_ENABLED=false IMAGE_TAG="$IMAGE_TAG" BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps backend-real-pre
                touch runtime/qa/out/jenkins/schedulers-paused

                docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/post-deploy-compose-ps.txt || true
                '''
            }
        }

        stage('Backend Readiness') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                ready=false
                for _ in $(seq 1 120); do
                  if curl -fsS "$REAL_PRE_BACKEND/api/actuator/health/readiness" | grep -q '"status":"UP"'; then
                    ready=true
                    break
                  fi
                  sleep 2
                done
                if [ "$ready" != "true" ]; then
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=300 backend-real-pre >&2 || true
                  exit 1
                fi
                curl -fsS "$REAL_PRE_BACKEND/api/actuator/health/readiness" > runtime/qa/out/jenkins/backend-readiness.json
                '''
            }
        }

        stage('Deploy Frontend') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                IMAGE_TAG="$IMAGE_TAG" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps frontend-real-pre
                '''
            }
        }

        stage('Core Smoke and Multi-role E2E') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                npx --yes pnpm@9 install --frozen-lockfile
                npm run e2e:real-pre:p0
                npm run e2e:real-pre:roles
                '''
            }
        }

        stage('Restore Schedulers') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                APP_SCHEDULING_ENABLED=true IMAGE_TAG="$IMAGE_TAG" BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps backend-real-pre
                for _ in $(seq 1 120); do
                  if curl -fsS "$REAL_PRE_BACKEND/api/actuator/health/readiness" | grep -q '"status":"UP"'; then
                    rm -f runtime/qa/out/jenkins/schedulers-paused
                    exit 0
                  fi
                  sleep 2
                done
                exit 1
                '''
            }
        }

        stage('Health Check') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                if ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" COMPOSE_PROJECT_NAME="$PROJECT_NAME" bash scripts/health-check.sh; then
                  echo "Health check passed."
                  exit 0
                fi

                echo "Health check failed; attempting rollback to pre-deploy image tag."
                old_backend_image="$(cat runtime/qa/out/jenkins/pre-backend-image.txt 2>/dev/null || true)"
                old_frontend_image="$(cat runtime/qa/out/jenkins/pre-frontend-image.txt 2>/dev/null || true)"
                old_backend_tag="${old_backend_image##*:}"
                old_frontend_tag="${old_frontend_image##*:}"
                old_backend_image_id="$(docker image inspect "$old_backend_image" --format '{{.Id}}' 2>/dev/null || true)"

                if [ -n "$old_backend_tag" ] && [ "$old_backend_tag" = "$old_frontend_tag" ] && [ -n "$old_backend_image_id" ]; then
                  echo "Rollback image tag: $old_backend_tag"
                  IMAGE_TAG="$old_backend_tag" BACKEND_IMAGE_DIGEST="$old_backend_image_id" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                    docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps backend-real-pre || true
                  IMAGE_TAG="$old_backend_tag" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                    docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps frontend-real-pre || true
                  ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" COMPOSE_PROJECT_NAME="$PROJECT_NAME" bash scripts/health-check.sh || true
                else
                  echo "Automatic rollback skipped: previous backend/frontend tags differ or are missing."
                fi

                exit 1
                '''
            }
        }

        stage('Evidence Report') {
            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                mkdir -p harness/reports/current
                report="harness/reports/current/latest-jenkins-cd.md"
                remote_report="/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}/latest-evidence-jenkins-cd.md"
                evidence_result="PASS"
                backend_health=""
                frontend_health=""
                frontend_version=""
                migration_versions=""
                backend_image_id=""
                frontend_image_id=""
                backend_container="$(docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps -q backend-real-pre)"
                frontend_container="$(docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps -q frontend-real-pre)"
                backend_running_image="$(docker inspect "$backend_container" --format '{{.Config.Image}}')"
                frontend_running_image="$(docker inspect "$frontend_container" --format '{{.Config.Image}}')"
                backend_running_id="$(docker inspect "$backend_container" --format '{{.Image}}')"
                frontend_running_id="$(docker inspect "$frontend_container" --format '{{.Image}}')"
                backend_revision="$(docker image inspect "$backend_running_id" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
                frontend_revision="$(docker image inspect "$frontend_running_id" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
                if ! backend_health="$(curl -fsS "$REAL_PRE_BACKEND/api/system/health")"; then evidence_result="FAIL"; fi
                if ! frontend_health="$(curl -fsS "$REAL_PRE_FRONTEND/healthz")"; then evidence_result="FAIL"; fi
                if ! frontend_version="$(curl -fsS "$REAL_PRE_FRONTEND/version.json")"; then evidence_result="FAIL"; fi
                if ! migration_versions="$(docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" exec -T postgres-real-pre sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1 -c "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank;"')"; then evidence_result="FAIL"; fi
                if ! backend_image_id="$(docker image inspect "colonel-saas/backend:$IMAGE_TAG" --format '{{.Id}}')"; then evidence_result="FAIL"; fi
                if ! frontend_image_id="$(docker image inspect "colonel-saas/frontend:$IMAGE_TAG" --format '{{.Id}}')"; then evidence_result="FAIL"; fi
                printf '%s\n' "$backend_health" | grep -q '"status":"UP"' || evidence_result="FAIL"
                printf '%s\n' "$backend_health" | grep -Eq "\"gitSha\"[[:space:]]*:[[:space:]]*\"$FULL_COMMIT\"" || evidence_result="FAIL"
                printf '%s\n' "$backend_health" | grep -Eq "\"imageDigest\"[[:space:]]*:[[:space:]]*\"$BACKEND_IMAGE_DIGEST\"" || evidence_result="FAIL"
                printf '%s\n' "$frontend_version" | grep -Eq "\"gitSha\"[[:space:]]*:[[:space:]]*\"$FULL_COMMIT\"" || evidence_result="FAIL"
                test -n "$frontend_health" || evidence_result="FAIL"
                test "$backend_running_image" = "colonel-saas/backend:$IMAGE_TAG" || evidence_result="FAIL"
                test "$frontend_running_image" = "colonel-saas/frontend:$IMAGE_TAG" || evidence_result="FAIL"
                test "$backend_running_id" = "$BACKEND_IMAGE_DIGEST" || evidence_result="FAIL"
                test "$frontend_running_id" = "$FRONTEND_IMAGE_DIGEST" || evidence_result="FAIL"
                test "$backend_image_id" = "$BACKEND_IMAGE_DIGEST" || evidence_result="FAIL"
                test "$frontend_image_id" = "$FRONTEND_IMAGE_DIGEST" || evidence_result="FAIL"
                test "$backend_revision" = "$FULL_COMMIT" || evidence_result="FAIL"
                test "$frontend_revision" = "$FULL_COMMIT" || evidence_result="FAIL"

                release_root="/opt/saas/releases"
                release_dir="$release_root/$FULL_COMMIT"
                release_candidate="runtime/qa/out/jenkins/release.json"
                release_manifest="$release_dir/release.json"
                cat > "$release_candidate" <<EOF
                {
                  "gitSha": "$FULL_COMMIT",
                  "sourceMainSha": "$SOURCE_MAIN_SHA",
                  "branch": "$BUILD_BRANCH",
                  "backend": {
                    "tag": "colonel-saas/backend:$IMAGE_TAG",
                    "digest": "$BACKEND_IMAGE_DIGEST",
                    "revision": "$backend_revision"
                  },
                  "frontend": {
                    "tag": "colonel-saas/frontend:$IMAGE_TAG",
                    "digest": "$FRONTEND_IMAGE_DIGEST",
                    "revision": "$frontend_revision"
                  }
                }
EOF

                if [ "$evidence_result" = "PASS" ]; then
                  mkdir -p "$release_dir"
                  if [ -f "$release_manifest" ]; then
                    cmp -s "$release_candidate" "$release_manifest" || {
                      echo "ERROR: immutable release manifest already exists with different content."
                      evidence_result="FAIL"
                    }
                  else
                    install -m 0444 "$release_candidate" "$release_manifest"
                  fi
                fi
                if [ "$evidence_result" = "PASS" ]; then
                  current_sha="$(cat runtime/qa/out/jenkins/current-sha.txt)"
                  if [ -f "$release_root/current.json" ] && [ "$current_sha" != "$FULL_COMMIT" ]; then
                    cp "$release_root/current.json" "$release_root/previous.json.tmp"
                    mv "$release_root/previous.json.tmp" "$release_root/previous.json"
                  fi
                  cp "$release_manifest" "$release_root/current.json.tmp"
                  mv "$release_root/current.json.tmp" "$release_root/current.json"
                fi

                {
                  echo "# Jenkins CD Evidence"
                  echo
                  echo "- Result: $evidence_result"
                  echo "- Environment: real-pre"
                  echo "- Source: $CD_GIT_URL"
                  echo "- Branch: $BUILD_BRANCH"
                  echo "- Commit: $FULL_COMMIT"
                  echo "- Source main commit: $SOURCE_MAIN_SHA"
                  echo "- Image tag: $IMAGE_TAG"
                  echo "- Jenkins job: ${JOB_NAME:-unknown}"
                  echo "- Build number: ${BUILD_NUMBER:-unknown}"
                  echo "- Build URL: ${BUILD_URL:-unknown}"
                  echo "- Time: $(date -Iseconds)"
                  echo "- Production touched: NO"
                  echo "- Database migration/write by pipeline: $RUN_DB_MIGRATIONS"
                  echo "- Backup/restore validation: $(tail -n 1 runtime/qa/out/jenkins/database-backup.txt 2>/dev/null || echo missing)"
                  echo "- Previous backend image: $(cat runtime/qa/out/jenkins/pre-backend-image.txt 2>/dev/null || echo unknown)"
                  echo "- Previous frontend image: $(cat runtime/qa/out/jenkins/pre-frontend-image.txt 2>/dev/null || echo unknown)"
                  echo "- Secret leaked: NO"
                  echo
                  echo "## Container Images"
                  echo '```'
                  docker ps --format "table {{.Names}}\\t{{.Image}}\\t{{.Status}}" | grep 'saas-active-' || true
                  echo '```'
                  echo "- Backend image ID: $backend_image_id"
                  echo "- Frontend image ID: $frontend_image_id"
                  echo "- Backend OCI revision: $backend_revision"
                  echo "- Frontend OCI revision: $frontend_revision"
                  echo "- Release manifest: $release_manifest"
                  echo
                  echo "## Database Migration Versions"
                  echo '```'
                  printf '%s\n' "$migration_versions"
                  echo '```'
                  echo
                  echo "## Health"
                  echo '```'
                  printf '%s\n' "$backend_health"
                  printf '%s\n' "$frontend_health"
                  printf '%s\n' "$frontend_version"
                  echo '```'
                } > "$report"

                cp "$report" "$remote_report"
                cat "$report"
                test "$evidence_result" = PASS
                '''
            }
        }
            }
        }
    }

    post {
        always {
            script {
                env.FINAL_BUILD_RESULT = currentBuild.currentResult ?: 'SUCCESS'
            }
            sh '''#!/usr/bin/env bash
            set +e
            if [ -f runtime/qa/out/jenkins/cd-env.sh ]; then . runtime/qa/out/jenkins/cd-env.sh; fi
            mkdir -p runtime/qa/out/jenkins harness/reports/current "/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}"
            if [ -f runtime/qa/out/jenkins/schedulers-paused ]; then
              echo "Restoring schedulers after interrupted deployment flow."
              if APP_SCHEDULING_ENABLED=true IMAGE_TAG="$IMAGE_TAG" BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps backend-real-pre; then
                rm -f runtime/qa/out/jenkins/schedulers-paused
              else
                echo "ERROR: failed to restore schedulers; manual intervention required." >&2
              fi
            fi
            docker ps --format "table {{.Names}}\\t{{.Image}}\\t{{.Status}}" > runtime/qa/out/jenkins/docker-ps-final.txt 2>&1
            docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/docker-compose-ps-final.txt 2>&1

            if [ ! -f harness/reports/current/latest-jenkins-cd.md ]; then
              {
                echo "# Jenkins CD Evidence"
                echo
                echo "- Result: ${FINAL_BUILD_RESULT:-FAIL}"
                echo "- Environment: real-pre"
                echo "- Source: ${CD_GIT_URL:-unknown}"
                echo "- Branch: ${BUILD_BRANCH:-unknown}"
                echo "- Commit: ${FULL_COMMIT:-unknown}"
                echo "- Image tag: ${IMAGE_TAG:-unknown}"
                echo "- Jenkins job: ${JOB_NAME:-unknown}"
                echo "- Build number: ${BUILD_NUMBER:-unknown}"
                echo "- Build URL: ${BUILD_URL:-unknown}"
                echo "- Time: $(date -Iseconds)"
                echo "- Production touched: NO"
                echo "- Database migration/write by pipeline: UNKNOWN; inspect archived migration evidence"
                echo "- Secret leaked: NO"
                echo
                echo "## Final Container Status"
                echo '```'
                cat runtime/qa/out/jenkins/docker-ps-final.txt
                echo '```'
              } > harness/reports/current/latest-jenkins-cd.md
            fi

            cp harness/reports/current/latest-jenkins-cd.md "/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}/latest-evidence-jenkins-cd.md" 2>/dev/null || true
            '''
            archiveArtifacts artifacts: 'harness/reports/current/latest-jenkins-cd.md,runtime/qa/out/jenkins/**,runtime/qa/out/real-pre-*/**,backend/target/surefire-reports/**,frontend/coverage/**', allowEmptyArchive: true
        }

        success {
            echo "real-pre Jenkins CD completed. image tag=${env.IMAGE_TAG}"
        }

        failure {
            echo 'real-pre Jenkins CD failed. Check Jenkins logs and archived evidence.'
        }
    }
}
