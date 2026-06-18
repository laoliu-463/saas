pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        skipDefaultCheckout(true)
    }

    parameters {
        string(name: 'DEPLOY_BRANCH', defaultValue: '', description: '留空时使用 Jenkins SCM 当前分支/提交；DEPLOY_REAL_PRE=true 时必填')
        booleanParam(name: 'DEPLOY_REAL_PRE', defaultValue: false, description: '人工确认后执行 real-pre 受控部署；默认只跑 CI')
        booleanParam(name: 'CONFIRM_REAL_PROMOTION_WRITE', defaultValue: false, description: 'real-pre 开启真实推广写双开关时，必须人工确认后才允许部署')
        booleanParam(name: 'RUN_REAL_PRE_E2E', defaultValue: false, description: '第二阶段开启：运行 preflight / roles / p0 E2E 门禁')
    }

    environment {
        ENV_FILE = '/opt/saas/env/.env.real-pre'
        COMPOSE_FILE = 'docker-compose.real-pre.yml'
        PROJECT_NAME = 'saas-active'
        REAL_PRE_FRONTEND = 'http://127.0.0.1:3001'
        REAL_PRE_BACKEND = 'http://127.0.0.1:8081'
        IMAGE_TAG = ''
        TARGET_REF = ''
    }

    stages {
        stage('拉取代码') {
            steps {
                checkout scm
                sh '''
                set -eu
                mkdir -p runtime/qa/out/jenkins

                deploy_real_pre="$(printf '%s' "${DEPLOY_REAL_PRE:-false}" | tr '[:upper:]' '[:lower:]')"
                if [ "$deploy_real_pre" = "true" ] && [ -z "${DEPLOY_BRANCH:-}" ]; then
                  echo "DEPLOY_REAL_PRE=true 时必须填写 DEPLOY_BRANCH，避免 detached HEAD 部署和 git pull --ff-only 失败"
                  exit 1
                fi

                if [ -n "${DEPLOY_BRANCH:-}" ]; then
                  git fetch origin "$DEPLOY_BRANCH"
                  git checkout -B "$DEPLOY_BRANCH" "origin/$DEPLOY_BRANCH"
                  git branch --set-upstream-to="origin/$DEPLOY_BRANCH" "$DEPLOY_BRANCH" || true
                  printf '%s\n' "$DEPLOY_BRANCH" > runtime/qa/out/jenkins/TARGET_REF.txt
                else
                  current_ref="$(git rev-parse --abbrev-ref HEAD)"
                  if [ "$current_ref" = "HEAD" ]; then
                    current_ref="$(git rev-parse --short HEAD)"
                  fi
                  printf '%s\n' "$current_ref" > runtime/qa/out/jenkins/TARGET_REF.txt
                fi
                '''
                script {
                    env.TARGET_REF = readFile('runtime/qa/out/jenkins/TARGET_REF.txt').trim()
                    env.IMAGE_TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                }
                sh '''
                set -eu
                printf '%s\n' "$IMAGE_TAG" > runtime/qa/out/jenkins/IMAGE_TAG.txt
                git status --short > runtime/qa/out/jenkins/git-status-before-deploy.txt
                '''
                echo "TARGET_REF = ${env.TARGET_REF}"
                echo "IMAGE_TAG = ${env.IMAGE_TAG}"
            }
        }

        stage('real-pre 环境守卫') {
            when {
                anyOf {
                    expression { return params.DEPLOY_REAL_PRE }
                    expression { return params.RUN_REAL_PRE_E2E }
                }
            }
            steps {
                sh '''
                set -eu

                if [ ! -f "$ENV_FILE" ]; then
                  echo "缺少 $ENV_FILE；真实凭据只能放在服务器未跟踪 env 文件或 Jenkins Secret file 中"
                  exit 1
                fi

                get_env() {
                  awk -F= -v key="$1" '
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
                  ' "$ENV_FILE"
                }

                require_env() {
                  key="$1"
                  value="$(get_env "$key")"
                  if [ -z "$value" ] || [ "${value#MUST_CHANGE}" != "$value" ] || [ "${value#*YOUR_}" != "$value" ] || [ "${value#*PLACEHOLDER}" != "$value" ]; then
                    echo "$key 为空或仍是占位值"
                    exit 1
                  fi
                }

                expect_env() {
                  key="$1"
                  expected="$2"
                  actual="$(get_env "$key")"
                  if [ "$actual" != "$expected" ]; then
                    echo "$key 必须是 $expected，当前为: ${actual:-<empty>}"
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

                promotion_write="$(get_env DOUYIN_REAL_PROMOTION_WRITE_ENABLED | tr '[:upper:]' '[:lower:]')"
                allow_promotion_write="$(get_env ALLOW_REAL_PROMOTION_WRITE | tr '[:upper:]' '[:lower:]')"
                if [ "$promotion_write" = "true" ] && [ "$allow_promotion_write" != "true" ]; then
                  echo "DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true 需要同时配置 ALLOW_REAL_PROMOTION_WRITE=true"
                  exit 1
                fi
                if [ "$allow_promotion_write" = "true" ] && [ "$promotion_write" != "true" ]; then
                  echo "ALLOW_REAL_PROMOTION_WRITE=true 需要同时配置 DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true"
                  exit 1
                fi
                if [ "$promotion_write" = "true" ]; then
                  confirm_write="$(printf '%s' "${CONFIRM_REAL_PROMOTION_WRITE:-false}" | tr '[:upper:]' '[:lower:]')"
                  if [ "$confirm_write" != "true" ]; then
                    echo "real-pre 已开启真实推广写双开关；必须勾选 CONFIRM_REAL_PROMOTION_WRITE 后才允许进入受控部署或 E2E"
                    exit 1
                  fi
                  echo "真实推广写双开关已开启，且本次 Jenkins 运行已人工确认。"
                else
                  expect_env DOUYIN_REAL_PROMOTION_WRITE_ENABLED false
                  expect_env ALLOW_REAL_PROMOTION_WRITE false
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
                  DOUYIN_OAUTH_FRONTEND_FAILURE_URL; do
                  require_env "$key"
                done

                jwt_secret="$(get_env JWT_SECRET)"
                if [ ${#jwt_secret} -lt 32 ]; then
                  echo "JWT_SECRET 长度必须 >= 32"
                  exit 1
                fi

                if [ "$(get_env LOGISTICS_KD100_ENABLED | tr '[:upper:]' '[:lower:]')" = "true" ]; then
                  require_env LOGISTICS_KD100_CUSTOMER
                  require_env LOGISTICS_KD100_KEY
                fi

                if [ "$(get_env LOGISTICS_KD100_SUBSCRIBE_ENABLED | tr '[:upper:]' '[:lower:]')" = "true" ]; then
                  require_env LOGISTICS_KD100_CALLBACK_URL
                  require_env LOGISTICS_KD100_CALLBACK_SALT
                fi

                IMAGE_TAG="$IMAGE_TAG" \
                COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" config > runtime/qa/out/jenkins/docker-compose.config.yml

                echo "real-pre 环境守卫通过: project=$PROJECT_NAME profile=real-pre appTest=false douyinTest=false upstream=live"
                '''
            }
        }

        stage('后端测试') {
            steps {
                dir('backend') {
                    sh 'mvn clean test'
                }
            }
        }

        stage('前端测试与构建') {
            steps {
                dir('frontend') {
                    sh '''
                    set -eu
                    if command -v pnpm >/dev/null 2>&1; then
                      PNPM="pnpm"
                    else
                      corepack enable || true
                      corepack prepare pnpm@9 --activate || true
                      if command -v pnpm >/dev/null 2>&1; then
                        PNPM="pnpm"
                      else
                        PNPM="npx --yes pnpm@9"
                      fi
                    fi
                    CI=true $PNPM install --frozen-lockfile
                    $PNPM test
                    $PNPM build
                    '''
                }
            }
        }

        stage('后端打包') {
            steps {
                dir('backend') {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('部署 real-pre 受控环境') {
            when {
                expression { return params.DEPLOY_REAL_PRE }
            }
            steps {
                sh '''
                set -eu
                chmod +x scripts/*.sh
                APP_DIR="$PWD" \
                ENV_FILE="$ENV_FILE" \
                COMPOSE_FILE="$COMPOSE_FILE" \
                PROJECT_NAME="$PROJECT_NAME" \
                EVIDENCE_ROOT="/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER}" \
                IMAGE_TAG="$IMAGE_TAG" \
                REAL_PROMOTION_WRITE_CONFIRMED="${CONFIRM_REAL_PROMOTION_WRITE:-false}" \
                  ./scripts/deploy-real-pre.sh
                '''
            }
        }

        stage('端口健康检查') {
            when {
                anyOf {
                    expression { return params.DEPLOY_REAL_PRE }
                    expression { return params.RUN_REAL_PRE_E2E }
                }
            }
            steps {
                sh '''
                set -eu
                ENV_FILE="$ENV_FILE" \
                COMPOSE_FILE="$COMPOSE_FILE" \
                COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
                  ./scripts/health-check.sh

                curl -fsS "${REAL_PRE_BACKEND}/api/system/health" | tee runtime/qa/out/jenkins/backend-health.json
                curl -fsS "${REAL_PRE_FRONTEND}/healthz" | tee runtime/qa/out/jenkins/frontend-health.txt
                '''
            }
        }

        stage('E2E 依赖') {
            when {
                expression { return params.RUN_REAL_PRE_E2E }
            }
            steps {
                sh '''
                set -eu
                npm ci
                npx playwright install chromium
                '''
            }
        }

        stage('real-pre 预检') {
            when {
                expression { return params.RUN_REAL_PRE_E2E }
            }
            steps {
                sh '''
                set -eu
                E2E_BASE_URL="$REAL_PRE_FRONTEND" E2E_BACKEND_URL="$REAL_PRE_BACKEND" npm run e2e:real-pre:p0:preflight
                '''
            }
        }

        stage('real-pre 角色门禁') {
            when {
                expression { return params.RUN_REAL_PRE_E2E }
            }
            steps {
                sh '''
                set -eu
                E2E_BASE_URL="$REAL_PRE_FRONTEND" E2E_BACKEND_URL="$REAL_PRE_BACKEND" npm run e2e:real-pre:roles
                '''
            }
        }

        stage('real-pre P0 E2E') {
            when {
                expression { return params.RUN_REAL_PRE_E2E }
            }
            steps {
                sh '''
                set -eu
                E2E_BASE_URL="$REAL_PRE_FRONTEND" E2E_BACKEND_URL="$REAL_PRE_BACKEND" npm run e2e:real-pre:p0
                '''
            }
        }
    }

    post {
        always {
            sh '''
            set +e
            mkdir -p runtime/qa/out/jenkins
            {
              echo "DEPLOY_BRANCH=${DEPLOY_BRANCH:-}"
              echo "TARGET_REF=${TARGET_REF:-}"
              echo "IMAGE_TAG=${IMAGE_TAG:-}"
              echo "DEPLOY_REAL_PRE=${DEPLOY_REAL_PRE:-false}"
              echo "RUN_REAL_PRE_E2E=${RUN_REAL_PRE_E2E:-false}"
              echo "CONFIRM_REAL_PROMOTION_WRITE=${CONFIRM_REAL_PROMOTION_WRITE:-false}"
            } > runtime/qa/out/jenkins/pipeline-params.txt
            if [ -f "$ENV_FILE" ] && [ -f "$COMPOSE_FILE" ]; then
              docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/docker-compose-ps.txt 2>&1
              docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=300 > runtime/qa/out/jenkins/docker-compose.logs.txt 2>&1
              docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=300 backend-real-pre > runtime/qa/out/jenkins/backend-real-pre.log 2>&1
              docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=300 frontend-real-pre > runtime/qa/out/jenkins/frontend-real-pre.log 2>&1
              docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=300 postgres-real-pre > runtime/qa/out/jenkins/postgres-real-pre.log 2>&1
              docker compose --env-file "$ENV_FILE" --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=300 redis-real-pre > runtime/qa/out/jenkins/redis-real-pre.log 2>&1
            else
              echo "Skip docker compose evidence: ENV_FILE or COMPOSE_FILE is unavailable for this CI-only run." > runtime/qa/out/jenkins/docker-compose-collection-skipped.txt
            fi
            git rev-parse --short HEAD > runtime/qa/out/jenkins/commit.txt 2>&1
            '''
            archiveArtifacts artifacts: 'runtime/qa/out/**,playwright-report/**,test-results/playwright/**,backend/target/surefire-reports/**,frontend/coverage/**', allowEmptyArchive: true
        }

        success {
            echo "流水线完成：CI 已通过；DEPLOY_REAL_PRE=${params.DEPLOY_REAL_PRE}，RUN_REAL_PRE_E2E=${params.RUN_REAL_PRE_E2E}。这不等于正式生产全量上线。IMAGE_TAG=${env.IMAGE_TAG}"
        }

        failure {
            echo 'real-pre 受控部署流水线失败，请查看 Jenkins 日志和归档证据。'
            echo '如需回滚：在服务器执行 ROLLBACK_REF=<上一提交> ./scripts/rollback-real-pre.sh'
        }
    }
}
