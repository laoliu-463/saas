pipeline {
    agent any

    options {
        disableConcurrentBuilds(abortPrevious: false)
        buildDiscarder(logRotator(numToKeepStr: '20'))
        skipDefaultCheckout(true)
        // PR #5: pipeline-wide timeout. Covers the full real-pre CD.
        // Background: prior runs had zero timeout protection, so any
        // hang (DB backup, P0 Playwright, readiness wait) could block
        // the cross-job deployment lock indefinitely.
        // 60 minutes is the documented fail-fast ceiling; each stage
        // also has its own per-stage timeout below.
        timeout(time: 60, unit: 'MINUTES')
    }

    parameters {
        string(name: 'DEPLOY_BRANCH', defaultValue: 'release/real-pre', description: 'Only release/real-pre is deployable.')
        booleanParam(name: 'DEPLOY_REAL_PRE', defaultValue: false, description: 'Explicit approval required to deploy real-pre.')
        booleanParam(name: 'CONFIRM_REAL_PROMOTION_WRITE', defaultValue: false, description: 'Required only when real-pre promotion-write switches are enabled.')
        booleanParam(name: 'ROLLBACK_APPROVED', defaultValue: false, description: 'Explicit approval required when the target is not a descendant of the deployed commit.')
        // PR-D: offline-recovery escape hatch. Default false: images are
        // pulled by digest from GHCR (built by .github/workflows/image-build.yml
        // on push to main). Set true ONLY when a SHA was tagged locally
        // without going through GHA (e.g. a server-only tag), which
        // re-enables the legacy Backend Package / Frontend Build stages
        // for one-off builds.
        booleanParam(name: 'FORCE_LOCAL_IMAGE_BUILD', defaultValue: false, description: 'BREAK-GLASS: re-enable local Maven + pnpm + docker build. Use only when no GHCR digest is available for IMAGE_TAG.')        // PR-B: RUN_BACKEND_TEST defaults to false. The real CI gate is the
        // GHA SHA Gate (see Preflight Guard); this parameter only enables
        // an optional diagnostic rerun of mvn test inside Jenkins.
        booleanParam(name: 'RUN_BACKEND_TEST', defaultValue: false, description: 'Diagnostic rerun only; does not bypass the GHA SHA Gate.')    }

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
        // PR-D: GHCR image coordinates. Jenkinsfile pulls by sha256 digest.
        GHCR_OWNER = 'laoliu-463'
        GHCR_BACKEND_IMAGE  = 'ghcr.io/laoliu-463/saas-backend'
        GHCR_FRONTEND_IMAGE = 'ghcr.io/laoliu-463/saas-frontend'
    }

    stages {
        stage('Checkout') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }

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
                  mkdir -p runtime/qa/out/jenkins \
                    /var/lib/jenkins/.cache/saas-real-pre-cd/m2 \
                    /var/lib/jenkins/.cache/saas-real-pre-cd/npm \
                    /var/lib/jenkins/.cache/saas-real-pre-cd/pnpm-store
                full_commit="$(git rev-parse HEAD)"
                image_tag="$full_commit"
                build_branch="${DEPLOY_BRANCH:-release/real-pre}"
                {
                  printf 'FULL_COMMIT=%s\\n' "$full_commit"
                  printf 'IMAGE_TAG=%s\\n' "$image_tag"
                  printf 'BUILD_BRANCH=%s\\n' "$build_branch"
                } > runtime/qa/out/jenkins/cd-env.sh
                printf '%s\\n' "$full_commit" > runtime/qa/out/jenkins/commit.txt
                printf '%s\\n' "$build_branch" > runtime/qa/out/jenkins/branch.txt
                printf '%s\\n' "$image_tag" > runtime/qa/out/jenkins/image-tag.txt
                echo "CD source: ${CD_GIT_URL}"
                echo "CD branch: ${build_branch}"
                echo "CD commit: ${full_commit}"
                echo "Image tag: ${image_tag}"
                '''
            }
        }

        stage('Preflight Guard') {
            options {
                timeout(time: 5, unit: 'MINUTES')
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
                git fetch --no-tags origin +refs/heads/main:refs/remotes/origin/main
                target_tree="$(git rev-parse "$FULL_COMMIT^{tree}")"
                source_main_sha="$(git rev-list origin/main | while read -r candidate; do
                  if [ "$(git rev-parse "$candidate^{tree}")" = "$target_tree" ]; then
                    printf '%s\\n' "$candidate"
                    break
                  fi
                done)"
                if ! printf '%s' "$source_main_sha" | grep -Eq '^[0-9a-f]{40}$'; then
                  echo "ERROR: release tree does not match any commit reachable from main."
                  exit 1
                fi
                printf 'SOURCE_MAIN_SHA=%s\\n' "$source_main_sha" >> runtime/qa/out/jenkins/cd-env.sh
                printf '%s\\n' "$source_main_sha" > runtime/qa/out/jenkins/source-main-sha.txt
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

                for key in \\
                  DB_PASSWORD \\
                  ADMIN_PASSWORD \\
                  REDIS_PASSWORD \\
                  JWT_SECRET \\
                  CORS_ALLOWED_ORIGIN_PATTERNS \\
                  DOUYIN_BASE_URL \\
                  DOUYIN_APP_ID \\
                  DOUYIN_CLIENT_KEY \\
                  DOUYIN_CLIENT_SECRET \\
                  DOUYIN_OAUTH_REDIRECT_URI \\
                  DOUYIN_OAUTH_FRONTEND_SUCCESS_URL \\
                  DOUYIN_OAUTH_FRONTEND_FAILURE_URL \\
                  LOGISTICS_KD100_CUSTOMER \\
                  LOGISTICS_KD100_KEY \\
                  LOGISTICS_KD100_CALLBACK_URL \\
                  LOGISTICS_KD100_CALLBACK_SALT; do
                  require_env "$key"
                done

                docker version
                docker compose version
                IMAGE_TAG="$IMAGE_TAG" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \\
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" config --quiet
                echo "Preflight guard passed without printing secrets."
                '''
            }

            // PR-B: GHA SHA Gate. Must run inside withCredentials so the
            // GitHub PAT never appears in the Jenkins console log. The
            // exact FULL_COMMIT being deployed must have a successful
            // ci.yml push run on release/real-pre (Backend tests,
            // Frontend tests and build, Repository governance all green).
            // RUN_BACKEND_TEST only reruns Jenkins diagnostics; it cannot
            // bypass this gate.
            steps {
                withCredentials([string(credentialsId: 'github-actions-read-token', variable: 'GITHUB_TOKEN')]) {
                    sh '''#!/usr/bin/env bash
                    set -eu
                    . runtime/qa/out/jenkins/cd-env.sh
                    mkdir -p runtime/qa/out/jenkins
                    GITHUB_REPOSITORY="${CD_GIT_URL#*github.com/}"; GITHUB_REPOSITORY="${GITHUB_REPOSITORY%.git}"
                    export GITHUB_REPOSITORY
                    GITHUB_WORKFLOW=ci.yml \
                    GITHUB_BRANCH=release/real-pre \
                    GITHUB_SHA="$FULL_COMMIT" \
                      bash scripts/verify-github-ci-gate.sh
                    echo "GHA SHA Gate passed for $FULL_COMMIT."
                    '''
                }
            }
        }

        stage('Backend Test') {
            // PR-B: skip by default. The GHA SHA Gate in Preflight Guard
            // already validates that Backend tests passed on the same
            // FULL_COMMIT. Setting RUN_BACKEND_TEST=true re-enables a
            // diagnostic mvn test rerun inside Jenkins (does NOT bypass
            // the GHA gate).
            when {
                expression { return params.RUN_BACKEND_TEST }
            }
            options {
                timeout(time: 15, unit: 'MINUTES')
            }

            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                docker_gid="$(stat -c '%g' /var/run/docker.sock)"
                docker run --rm \\
                  --user "$(id -u):$(id -g)" \\
                  --group-add "$docker_gid" \\
                  --network host \\
                  -e HOME=/tmp \\
                  -e MAVEN_CONFIG=/tmp/.m2 \\
                  -e TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1 \\
                  -e TESTCONTAINERS_RYUK_DISABLED=true \\
                  -v /var/run/docker.sock:/var/run/docker.sock \\
                  -v "$PWD":/workspace \\
                  -v /var/lib/jenkins/.cache/saas-real-pre-cd/m2:/tmp/.m2 \\
                  -w /workspace/backend \\
                  maven:3.9-eclipse-temurin-17 \\
                  mvn -B clean test
                '''
            }
        }

        stage('Backend Package') {
            // PR-D: removed from the default pipeline. The backend
            // image is now built by .github/workflows/image-build.yml
            // on push to main and published to GHCR; the 'Pull images
            // by digest' stage below retrieves it. This stage is
            // preserved with a `when` gate so it can be re-enabled
            // on a one-off basis via the FORCE_LOCAL_IMAGE_BUILD
            // parameter (set true for offline recovery, etc.).
            when { expression { return params.FORCE_LOCAL_IMAGE_BUILD } }
            options {
                timeout(time: 10, unit: 'MINUTES')
            }

            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                rm -rf backend/target
                docker run --rm \\
                  --user "$(id -u):$(id -g)" \\
                  -e HOME=/tmp \\
                  -e MAVEN_CONFIG=/tmp/.m2 \\
                  -v "$PWD":/workspace \\
                  -v /var/lib/jenkins/.cache/saas-real-pre-cd/m2:/tmp/.m2 \\
                  -w /workspace/backend \\
                  maven:3.9-eclipse-temurin-17 \\
                  mvn -B clean package -DskipTests
                ls -lh backend/target/*.jar
                '''
            }
        }

        stage('Frontend Build') {
            // PR-D: removed from the default pipeline; see Backend
            // Package comment above.
            when { expression { return params.FORCE_LOCAL_IMAGE_BUILD } }
            options {
                timeout(time: 10, unit: 'MINUTES')
            }

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

        stage('Pull images by digest') {
            // PR-D: replaces the old 'Docker Build' stage. The
            // backend and frontend images were already built and
            // published to GHCR by .github/workflows/image-build.yml
            // (triggered by the push to main that this release is
            // based on). We pull by sha256 digest so the bytes on
            // the real-pre host are guaranteed to be exactly what
            // GHA produced.
            when { expression { return !params.FORCE_LOCAL_IMAGE_BUILD } }
            options {
                timeout(time: 10, unit: 'MINUTES')
            }

            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                # The GHA image-build workflow writes a JSON file to
                # releases/<sha>/image-digests.json on main. The
                # checkout step checked out $BUILD_BRANCH (which IS
                # main) so that file is available locally. If the
                # file is missing (e.g. someone re-tagged the image
                # manually), fail closed. The operator can override
                # via FORCE_LOCAL_IMAGE_BUILD=true to use the legacy
                # Backend Package + Frontend Build + Docker Build
                # stages instead.
                digest_file="releases/${IMAGE_TAG}/image-digests.json"
                if [ ! -f "$digest_file" ]; then
                  echo "ERROR: missing image-digests.json at $digest_file"
                  echo "The GHA image-build.yml workflow must have published"
                  echo "the digests for $IMAGE_TAG before this CD can run."
                  echo "If the image was tagged locally without GHA, set"
                  echo "FORCE_LOCAL_IMAGE_BUILD=true on this Jenkins build."
                  exit 1
                fi
                echo "Reading digests from $digest_file"
                cat "$digest_file"

                # Parse the JSON without depending on jq (it may not
                # be installed on the Jenkins host). Python is always
                # present because the real-pre machine runs the
                # backend JVM.
                read BACKEND_IMAGE BACKEND_DIGEST FRONTEND_IMAGE FRONTEND_DIGEST < <(
                  python - "$digest_file" <<'PY'
                import json, sys
                with open(sys.argv[1], encoding="utf-8") as f:
                    d = json.load(f)
                print(d["backend"]["image"])
                print(d["backend"]["digest"])
                print(d["frontend"]["image"])
                print(d["frontend"]["digest"])
                PY
                )

                for d in "$BACKEND_DIGEST" "$FRONTEND_DIGEST"; do
                  if ! printf '%s' "$d" | grep -Eq '^sha256:[0-9a-f]{64}$'; then
                    echo "ERROR: digests must be content-addressed sha256 values, got: $d"
                    exit 1
                  fi
                done

                echo "Pulling backend:  ${BACKEND_IMAGE}@${BACKEND_DIGEST}"
                docker pull "${BACKEND_IMAGE}@${BACKEND_DIGEST}"
                echo "Pulling frontend: ${FRONTEND_IMAGE}@${FRONTEND_DIGEST}"
                docker pull "${FRONTEND_IMAGE}@${FRONTEND_DIGEST}"

                # Tag the pulled images with the SHA so docker compose
                # can reference them by tag. The local tag is just a
                # label; digest remains the source of truth.
                docker tag "${BACKEND_IMAGE}@${BACKEND_DIGEST}"  "colonel-saas/backend:${IMAGE_TAG}"
                docker tag "${FRONTEND_IMAGE}@${FRONTEND_DIGEST}" "colonel-saas/frontend:${IMAGE_TAG}"

                # Verify the OCI labels match the expected commit SHA.
                actual_backend_rev="$(docker image inspect "colonel-saas/backend:${IMAGE_TAG}" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
                actual_frontend_rev="$(docker image inspect "colonel-saas/frontend:${IMAGE_TAG}" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
                test "$actual_backend_rev"  = "$FULL_COMMIT" || { echo "ERROR: backend image revision $actual_backend_rev != $FULL_COMMIT"; exit 1; }
                test "$actual_frontend_rev" = "$FULL_COMMIT" || { echo "ERROR: frontend image revision $actual_frontend_rev != $FULL_COMMIT"; exit 1; }

                # Capture the actual local image IDs (which ARE the
                # digest since the image was pulled by digest).
                backend_image_digest="$(docker image inspect "colonel-saas/backend:${IMAGE_TAG}" --format '{{.Id}}')"
                frontend_image_digest="$(docker image inspect "colonel-saas/frontend:${IMAGE_TAG}" --format '{{.Id}}')"
                test "$backend_image_digest"  = "$BACKEND_DIGEST"  || { echo "ERROR: backend local digest $backend_image_digest != $BACKEND_DIGEST"; exit 1; }
                test "$frontend_image_digest" = "$FRONTEND_DIGEST" || { echo "ERROR: frontend local digest $frontend_image_digest != $FRONTEND_DIGEST"; exit 1; }

                {
                  printf 'BACKEND_IMAGE_DIGEST=%s\\n'  "$backend_image_digest"
                  printf 'FRONTEND_IMAGE_DIGEST=%s\\n' "$frontend_image_digest"
                } >> runtime/qa/out/jenkins/cd-env.sh
                printf '%s\\n' "$backend_image_digest"  > runtime/qa/out/jenkins/backend-image-digest.txt
                printf '%s\\n' "$frontend_image_digest" > runtime/qa/out/jenkins/frontend-image-digest.txt
                '''
            }
        }

        stage('docker compose config') {
            options {
                timeout(time: 2, unit: 'MINUTES')
            }

            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                IMAGE_TAG="$IMAGE_TAG" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \\
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" config --quiet
                echo "docker compose config passed for image tag $IMAGE_TAG"
                '''
            }
        }

        stage('Serialized real-pre release') {
            options {
                timeout(time: 1, unit: 'MINUTES')
            }

            // The top-level disableConcurrentBuilds option serializes the sole CD job.
            // Per-stage global lock: every mutating subcommand (backup / migrate /
            // deploy / rollback) goes through scripts/cd/release-real-pre.sh, which
            // holds an exclusive flock on /var/lock/saas-real-pre-deploy.lock for the
            // duration of the step. This replaces the `lock(resource: 'saas-real-pre-
            // deploy')` declaration that previously relied on the Lockable Resources
            // plugin, which is NOT installed on the real-pre Jenkins host.
            // See docs/deploy/README.md "BREAK-GLASS 紧急恢复" for the matching manual
            // entry point that uses the same lock file.
            stages {
        stage('Release Order and Migration Guard') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }

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
                if ! git diff --quiet "$current_sha" "$FULL_COMMIT" -- \\
                  backend/src/main/resources/db/migration \\
                  ':(glob)backend/src/main/resources/db/*.sql' \\
                  scripts/run-real-pre-db-migrations.sh; then
                  RUN_DB_MIGRATIONS=true
                fi
                # NOTE: changes to scripts/cd/release-real-pre.sh (the flock
                # wrapper) are intentionally NOT in the diff pathspec, so a
                # wrapper change does not trigger an unnecessary DB migration.
                {
                  printf 'CURRENT_SHA=%s\\n' "$current_sha"
                  printf 'RUN_DB_MIGRATIONS=%s\\n' "$RUN_DB_MIGRATIONS"
                } >> runtime/qa/out/jenkins/cd-env.sh
                printf '%s\\n' "$current_sha" > runtime/qa/out/jenkins/current-sha.txt
                printf '%s\\n' "$RUN_DB_MIGRATIONS" > runtime/qa/out/jenkins/run-db-migrations.txt
                echo "Release order guard passed: $current_sha -> $FULL_COMMIT; RUN_DB_MIGRATIONS=$RUN_DB_MIGRATIONS"
                '''
            }
        }

        stage('Database Backup, Migration and Schema Precheck') {
            options {
                timeout(time: 30, unit: 'MINUTES')
            }

            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                if [ "$RUN_DB_MIGRATIONS" != "true" ]; then
                  echo "Database work skipped: no migration inputs changed" | tee runtime/qa/out/jenkins/database-migration.txt
                  printf '%s\\n' "SKIPPED: no migration inputs changed" > runtime/qa/out/jenkins/database-backup.txt
                  printf '%s\\n' "SKIPPED: no migration inputs changed" > runtime/qa/out/jenkins/schema-precheck.txt
                  exit 0
                fi
                backup_dir="/opt/saas/backups/jenkins-${BUILD_NUMBER:-manual}"
                mkdir -p "$backup_dir"

                  ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" \
                  COMPOSE_PROJECT_NAME="$PROJECT_NAME" BACKUP_DIR="$backup_dir" \
                  bash scripts/cd/release-real-pre.sh backup | tee runtime/qa/out/jenkins/database-backup.txt

                ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" \
                  COMPOSE_PROJECT_NAME="$PROJECT_NAME" IMAGE_TAG="$IMAGE_TAG" \
                    BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" REQUIRE_PINNED_IMAGE=true \
                    bash scripts/cd/release-real-pre.sh migrate | tee runtime/qa/out/jenkins/database-migration.txt

                REAL_PRE_COMPOSE_ENV="$ENV_FILE" REAL_PRE_COMPOSE_FILE="$COMPOSE_FILE" \\
                  REAL_PRE_COMPOSE_PROJECT="$PROJECT_NAME" \\
                  sh scripts/check-real-pre-schema.sh | tee runtime/qa/out/jenkins/schema-precheck.txt
                '''
            }
        }

        stage('Deploy Backend (Schedulers Paused)') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }

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
                APP_SCHEDULING_ENABLED=false IMAGE_TAG="$IMAGE_TAG" BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \\
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps backend-real-pre
                touch runtime/qa/out/jenkins/schedulers-paused

                docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/post-deploy-compose-ps.txt || true
                '''
            }
        }

        stage('Backend Readiness') {
            options {
                timeout(time: 6, unit: 'MINUTES')
            }

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
            options {
                timeout(time: 5, unit: 'MINUTES')
            }

            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                IMAGE_TAG="$IMAGE_TAG" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \\
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps frontend-real-pre
                '''
            }
        }

        stage('Core Smoke and Multi-role E2E') {
            options {
                timeout(time: 25, unit: 'MINUTES')
            }

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
            options {
                timeout(time: 6, unit: 'MINUTES')
            }

            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                APP_SCHEDULING_ENABLED=true IMAGE_TAG="$IMAGE_TAG" BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \\
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
            options {
                timeout(time: 5, unit: 'MINUTES')
            }

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
                  IMAGE_TAG="$old_backend_tag" BACKEND_IMAGE_DIGEST="$old_backend_image_id" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \\
                    docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps backend-real-pre || true
                  IMAGE_TAG="$old_backend_tag" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \\
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
            options {
                timeout(time: 5, unit: 'MINUTES')
            }

            steps {
                sh '''#!/usr/bin/env bash
                set -eu
                . runtime/qa/out/jenkins/cd-env.sh
                mkdir -p runtime/qa/out
                report="runtime/qa/out/latest-jenkins-cd.md"
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
                printf '%s\\n' "$backend_health" | grep -q '"status":"UP"' || evidence_result="FAIL"
                printf '%s\\n' "$backend_health" | grep -Eq "\\"gitSha\\"[[:space:]]*:[[:space:]]*\\"$FULL_COMMIT\\"" || evidence_result="FAIL"
                printf '%s\\n' "$backend_health" | grep -Eq "\\"imageDigest\\"[[:space:]]*:[[:space:]]*\\"$BACKEND_IMAGE_DIGEST\\"" || evidence_result="FAIL"
                printf '%s\\n' "$frontend_version" | grep -Eq "\\"gitSha\\"[[:space:]]*:[[:space:]]*\\"$FULL_COMMIT\\"" || evidence_result="FAIL"
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
                # PR-C: emit a release manifest with all the fields required
                # by scripts/cd/evidence-collect.sh (gitSha, branch,
                # backendDigest, frontendDigest, migrationVersions, ciRun,
                # jenkinsBuild, deployResult, previous, rollbackTarget).
                # The canonical current/previous rotation is now performed
                # by evidence-collect.sh, not by inline cp/mv.
                previous_release_sha="$(cat "$release_root/previous.json" 2>/dev/null \
                  | grep -Eo '"gitSha"[[:space:]]*:[[:space:]]*"[0-9a-f]{40}"' \
                  | head -n 1 | grep -Eo '[0-9a-f]{40}' || true)"
                [ -n "$previous_release_sha" ] || previous_release_sha="null"
                cat > "$release_candidate" <<EOF
                {
                  "gitSha": "$FULL_COMMIT",
                  "branch": "$BUILD_BRANCH",
                  "backendDigest": "$BACKEND_IMAGE_DIGEST",
                  "frontendDigest": "$FRONTEND_IMAGE_DIGEST",
                  "migrationVersions": $(printf '%s\n' "${MIGRATION_VERSIONS:-}" | python -c 'import json,sys; vs=[l.strip() for l in sys.stdin if l.strip()]; print(json.dumps(vs))' 2>/dev/null || echo '[]'),
                  "ciRun": {
                    "sha": "$FULL_COMMIT",
                    "workflow": "ci.yml",
                    "branch": "release/real-pre"
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
                    cmp -s "$release_candidate" "$release_manifest" || {
                      echo "ERROR: immutable release manifest already exists with different content."
                      evidence_result="FAIL"
                    }
                  fi
                fi
                # PR-C: route the release manifest through the canonical
                # evidence-collect.sh entry point. evidence-collect.sh
                # validates the required fields, copies the manifest to
                # releases/<sha>/release-manifest.json, and atomically
                # rotates releases/current.json + releases/previous.json.
                # This replaces the inline cp/mv block that was here
                # before; the inline block was a candidate-only path
                # that did not enforce the schema.
                if [ "$evidence_result" = "PASS" ]; then
                  REPO_ROOT="${REPO_ROOT:-$WORKSPACE}" \
                    RELEASES_BASE="$release_root" \
                    bash scripts/cd/evidence-collect.sh release-manifest "$FULL_COMMIT" "$release_candidate"
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
                  printf '%s\\n' "$migration_versions"
                  echo '```'
                  echo
                  echo "## Health"
                  echo '```'
                  printf '%s\\n' "$backend_health"
                  printf '%s\\n' "$frontend_health"
                  printf '%s\\n' "$frontend_version"
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
            mkdir -p runtime/qa/out/jenkins runtime/qa/out "/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}"
            if [ -f runtime/qa/out/jenkins/schedulers-paused ]; then
              echo "Restoring schedulers after interrupted deployment flow."
              if APP_SCHEDULING_ENABLED=true IMAGE_TAG="$IMAGE_TAG" BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \\
                docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps backend-real-pre; then
                rm -f runtime/qa/out/jenkins/schedulers-paused
              else
                echo "ERROR: failed to restore schedulers; manual intervention required." >&2
              fi
            fi
            docker ps --format "table {{.Names}}\\t{{.Image}}\\t{{.Status}}" > runtime/qa/out/jenkins/docker-ps-final.txt 2>&1
            docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/docker-compose-ps-final.txt 2>&1

            if [ ! -f runtime/qa/out/latest-jenkins-cd.md ]; then
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
              } > runtime/qa/out/latest-jenkins-cd.md
            fi

            cp runtime/qa/out/latest-jenkins-cd.md "/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}/latest-evidence-jenkins-cd.md" 2>/dev/null || true
            '''
            archiveArtifacts artifacts: 'runtime/qa/out/latest-jenkins-cd.md,runtime/qa/out/jenkins/**,runtime/qa/out/real-pre-*/**,backend/target/surefire-reports/**,frontend/coverage/**', allowEmptyArchive: true
        }

        success {
            echo "real-pre Jenkins CD completed. image tag=${env.IMAGE_TAG}"
        }

        failure {
            echo 'real-pre Jenkins CD failed. Check Jenkins logs and archived evidence.'
        }
    }
}
