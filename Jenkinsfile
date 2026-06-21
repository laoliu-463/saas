pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        skipDefaultCheckout(true)
    }

    parameters {
        string(name: 'DEPLOY_BRANCH', defaultValue: 'feature/ddd/DDD-VERIFY-001', description: 'Single real-pre CD branch.')
        booleanParam(name: 'DEPLOY_REAL_PRE', defaultValue: true, description: 'Deploy only the real-pre environment.')
        booleanParam(name: 'CONFIRM_REAL_PROMOTION_WRITE', defaultValue: false, description: 'Required only when real-pre promotion-write switches are enabled.')
    }

    environment {
        JOB_PURPOSE = 'real-pre-cd'
        DEPLOY_ENV = 'real-pre'
        CD_GIT_URL = 'https://github.com/laoliu-463/saas.git'
        ENV_FILE = '/opt/saas/env/.env.real-pre'
        COMPOSE_FILE = 'docker-compose.real-pre.yml'
        PROJECT_NAME = 'saas-active'
        REAL_PRE_BACKEND = 'http://127.0.0.1:8081'
        REAL_PRE_FRONTEND = 'http://127.0.0.1:3001'
        RUN_DB_MIGRATIONS = 'false'
        IMAGE_TAG = ''
        FULL_COMMIT = ''
        BUILD_BRANCH = ''
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                git branch: params.DEPLOY_BRANCH, url: env.CD_GIT_URL
                script {
                    env.FULL_COMMIT = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG = sh(script: 'git rev-parse --short=8 HEAD', returnStdout: true).trim()
                    env.BUILD_BRANCH = params.DEPLOY_BRANCH
                }
                sh '''
                set -eu
                mkdir -p runtime/qa/out/jenkins harness/reports .jenkins-cache/m2 .jenkins-cache/npm .jenkins-cache/pnpm-store
                printf '%s\n' "$FULL_COMMIT" > runtime/qa/out/jenkins/commit.txt
                printf '%s\n' "$BUILD_BRANCH" > runtime/qa/out/jenkins/branch.txt
                printf '%s\n' "$IMAGE_TAG" > runtime/qa/out/jenkins/image-tag.txt
                echo "CD source: ${CD_GIT_URL}"
                echo "CD branch: ${BUILD_BRANCH}"
                echo "CD commit: ${FULL_COMMIT}"
                echo "Image tag: ${IMAGE_TAG}"
                '''
            }
        }

        stage('Preflight Guard') {
            steps {
                sh '''
                set -eu

                if [ "$DEPLOY_ENV" != "real-pre" ]; then
                  echo "ERROR: this Jenkinsfile only supports real-pre CD."
                  exit 1
                fi
                if [ "${DEPLOY_REAL_PRE:-false}" != "true" ]; then
                  echo "ERROR: DEPLOY_REAL_PRE must be true for this real-pre CD job."
                  exit 1
                fi
                if [ "$RUN_DB_MIGRATIONS" != "false" ]; then
                  echo "ERROR: database migrations are disabled for this CD closure."
                  exit 1
                fi
                if [ "$COMPOSE_FILE" != "docker-compose.real-pre.yml" ]; then
                  echo "ERROR: production compose files are not allowed."
                  exit 1
                fi

                test -f "$ENV_FILE"
                test -f "$COMPOSE_FILE"
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
            steps {
                sh '''
                set -eu
                docker_gid="$(stat -c '%g' /var/run/docker.sock)"
                docker run --rm \
                  --user "$(id -u):$(id -g)" \
                  --group-add "$docker_gid" \
                  -e HOME=/tmp \
                  -e MAVEN_CONFIG=/tmp/.m2 \
                  -v /var/run/docker.sock:/var/run/docker.sock \
                  -v "$PWD":/workspace \
                  -v "$PWD/.jenkins-cache/m2":/tmp/.m2 \
                  -w /workspace/backend \
                  maven:3.9-eclipse-temurin-17 \
                  mvn -B clean test
                '''
            }
        }

        stage('Backend Package') {
            steps {
                sh '''
                set -eu
                rm -rf backend/target
                docker run --rm \
                  --user "$(id -u):$(id -g)" \
                  -e HOME=/tmp \
                  -e MAVEN_CONFIG=/tmp/.m2 \
                  -v "$PWD":/workspace \
                  -v "$PWD/.jenkins-cache/m2":/tmp/.m2 \
                  -w /workspace/backend \
                  maven:3.9-eclipse-temurin-17 \
                  mvn -B clean package -DskipTests
                ls -lh backend/target/*.jar
                '''
            }
        }

        stage('Frontend Build') {
            steps {
                sh '''
                set -eu
                docker run --rm \
                  --user "$(id -u):$(id -g)" \
                  -e HOME=/tmp \
                  -e NPM_CONFIG_CACHE=/tmp/.npm \
                  -v "$PWD/frontend":/workspace \
                  -v "$PWD/.jenkins-cache/npm":/tmp/.npm \
                  -v "$PWD/.jenkins-cache/pnpm-store":/tmp/pnpm-store \
                  -w /workspace \
                  node:20-bookworm \
                  sh -lc 'npx --yes pnpm@9 config set store-dir /tmp/pnpm-store && npx --yes pnpm@9 install --frozen-lockfile && npx --yes pnpm@9 test && npx --yes pnpm@9 typecheck && npx --yes pnpm@9 build'
                '''
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                set -eu
                test -f backend/target/*.jar
                echo "Building backend and frontend images with tag: $IMAGE_TAG"
                IMAGE_TAG="$IMAGE_TAG" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" build backend-real-pre frontend-real-pre
                docker image inspect "colonel-saas/backend:$IMAGE_TAG" >/dev/null
                docker image inspect "colonel-saas/frontend:$IMAGE_TAG" >/dev/null
                '''
            }
        }

        stage('docker compose config') {
            steps {
                sh '''
                set -eu
                IMAGE_TAG="$IMAGE_TAG" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" config > runtime/qa/out/jenkins/docker-compose.config.yml
                echo "docker compose config passed for image tag $IMAGE_TAG"
                '''
            }
        }

        stage('Deploy real-pre') {
            steps {
                sh '''
                set -eu
                mkdir -p runtime/qa/out/jenkins "/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}"

                docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/pre-deploy-compose-ps.txt || true
                docker ps --format "table {{.Names}}\\t{{.Image}}\\t{{.Status}}" > runtime/qa/out/jenkins/pre-deploy-images.txt || true
                docker inspect saas-active-backend-real-pre-1 --format '{{.Config.Image}}' > runtime/qa/out/jenkins/pre-backend-image.txt 2>/dev/null || true
                docker inspect saas-active-frontend-real-pre-1 --format '{{.Config.Image}}' > runtime/qa/out/jenkins/pre-frontend-image.txt 2>/dev/null || true

                echo "Deploying backend/frontend real-pre with image tag: $IMAGE_TAG"
                IMAGE_TAG="$IMAGE_TAG" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps backend-real-pre
                IMAGE_TAG="$IMAGE_TAG" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d --no-build --no-deps frontend-real-pre

                docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/post-deploy-compose-ps.txt || true
                '''
            }
        }

        stage('Health Check') {
            steps {
                sh '''
                set -eu
                if ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" COMPOSE_PROJECT_NAME="$PROJECT_NAME" bash scripts/health-check.sh; then
                  echo "Health check passed."
                  exit 0
                fi

                echo "Health check failed; attempting rollback to pre-deploy image tag."
                old_backend_image="$(cat runtime/qa/out/jenkins/pre-backend-image.txt 2>/dev/null || true)"
                old_frontend_image="$(cat runtime/qa/out/jenkins/pre-frontend-image.txt 2>/dev/null || true)"
                old_backend_tag="${old_backend_image##*:}"
                old_frontend_tag="${old_frontend_image##*:}"

                if [ -n "$old_backend_tag" ] && [ "$old_backend_tag" = "$old_frontend_tag" ]; then
                  echo "Rollback image tag: $old_backend_tag"
                  IMAGE_TAG="$old_backend_tag" COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
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
                sh '''
                set -eu
                report="harness/reports/latest-evidence-jenkins-cd.md"
                remote_report="/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}/latest-evidence-jenkins-cd.md"
                backend_health="$(curl -fsS "$REAL_PRE_BACKEND/api/system/health" || true)"
                frontend_health="$(curl -fsS "$REAL_PRE_FRONTEND/healthz" || true)"

                {
                  echo "# Jenkins CD Evidence"
                  echo
                  echo "- Result: PASS"
                  echo "- Environment: real-pre"
                  echo "- Source: $CD_GIT_URL"
                  echo "- Branch: $BUILD_BRANCH"
                  echo "- Commit: $FULL_COMMIT"
                  echo "- Image tag: $IMAGE_TAG"
                  echo "- Jenkins job: ${JOB_NAME:-unknown}"
                  echo "- Build number: ${BUILD_NUMBER:-unknown}"
                  echo "- Build URL: ${BUILD_URL:-unknown}"
                  echo "- Time: $(date -Iseconds)"
                  echo "- Production touched: NO"
                  echo "- Database migration/write by pipeline: NO"
                  echo "- Secret leaked: NO"
                  echo
                  echo "## Container Images"
                  echo '```'
                  docker ps --format "table {{.Names}}\\t{{.Image}}\\t{{.Status}}" | grep 'saas-active-' || true
                  echo '```'
                  echo
                  echo "## Health"
                  echo '```'
                  printf '%s\n' "$backend_health"
                  printf '%s\n' "$frontend_health"
                  echo '```'
                } > "$report"

                cp "$report" "$remote_report"
                cat "$report"
                '''
            }
        }
    }

    post {
        always {
            script {
                env.FINAL_BUILD_RESULT = currentBuild.currentResult ?: 'SUCCESS'
            }
            sh '''
            set +e
            mkdir -p runtime/qa/out/jenkins harness/reports "/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}"
            docker ps --format "table {{.Names}}\\t{{.Image}}\\t{{.Status}}" > runtime/qa/out/jenkins/docker-ps-final.txt 2>&1
            docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/docker-compose-ps-final.txt 2>&1

            if [ ! -f harness/reports/latest-evidence-jenkins-cd.md ]; then
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
                echo "- Database migration/write by pipeline: NO"
                echo "- Secret leaked: NO"
                echo
                echo "## Final Container Status"
                echo '```'
                cat runtime/qa/out/jenkins/docker-ps-final.txt
                echo '```'
              } > harness/reports/latest-evidence-jenkins-cd.md
            fi

            cp harness/reports/latest-evidence-jenkins-cd.md "/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER:-manual}/latest-evidence-jenkins-cd.md" 2>/dev/null || true
            '''
            archiveArtifacts artifacts: 'harness/reports/latest-evidence-jenkins-cd.md,runtime/qa/out/jenkins/**,backend/target/surefire-reports/**,frontend/coverage/**', allowEmptyArchive: true
        }

        success {
            echo "real-pre Jenkins CD completed. image tag=${env.IMAGE_TAG}"
        }

        failure {
            echo 'real-pre Jenkins CD failed. Check Jenkins logs and archived evidence.'
        }
    }
}
