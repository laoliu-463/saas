pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        skipDefaultCheckout(true)
    }

    environment {
        COMPOSE_ENV = '.env.real-pre'
        COMPOSE_FILE = 'docker-compose.real-pre.yml'
        COMPOSE_PROJECT = 'saas'
        REAL_PRE_FRONTEND = 'http://127.0.0.1:3001'
        REAL_PRE_BACKEND = 'http://127.0.0.1:8081'
        IMAGE_TAG = ''
    }

    stages {
        stage('拉取代码') {
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                }
                echo "IMAGE_TAG = ${env.IMAGE_TAG}"
            }
        }

        stage('real-pre 环境守卫') {
            steps {
                sh '''
                set -eu

                if [ ! -f "$COMPOSE_ENV" ]; then
                  echo "缺少 $COMPOSE_ENV；真实凭据只能放在 Jenkins secret 或服务器未跟踪 env 文件中"
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
                        gsub(/^[[:space:]]+|[[:space:]]+$/, "", v)
                        gsub(/^"|"$/, "", v)
                        print v
                        exit
                      }
                    }
                  ' "$COMPOSE_ENV"
                }

                profile="$(get_env SPRING_PROFILES_ACTIVE)"
                db_name="$(get_env DB_NAME)"
                app_test="$(get_env APP_TEST_ENABLED | tr '[:upper:]' '[:lower:]')"
                douyin_test="$(get_env DOUYIN_TEST_ENABLED | tr '[:upper:]' '[:lower:]')"
                jwt_secret="$(get_env JWT_SECRET)"
                cors="$(get_env CORS_ALLOWED_ORIGIN_PATTERNS)"
                logistics_provider="$(get_env LOGISTICS_PROVIDER | tr '[:upper:]' '[:lower:]')"

                if [ "$profile" != "real-pre" ]; then
                  echo "SPRING_PROFILES_ACTIVE 必须是 real-pre，当前为: $profile"
                  exit 1
                fi

                if [ "$db_name" != "saas_real_pre" ]; then
                  echo "DB_NAME 必须是 saas_real_pre，当前为: $db_name"
                  exit 1
                fi

                if [ "$app_test" != "false" ]; then
                  echo "APP_TEST_ENABLED 必须是 false，当前为: $app_test"
                  exit 1
                fi

                if [ "$douyin_test" != "false" ]; then
                  echo "DOUYIN_TEST_ENABLED 必须是 false，当前为: $douyin_test"
                  exit 1
                fi

                if [ -z "$jwt_secret" ] || [ "$jwt_secret" = "MUST_CHANGE_RANDOM_64_CHAR_SECRET" ] || [ ${#jwt_secret} -lt 32 ]; then
                  echo "JWT_SECRET 必须替换为长度 >= 32 的真实密钥"
                  exit 1
                fi

                if [ -z "$cors" ]; then
                  echo "CORS_ALLOWED_ORIGIN_PATTERNS 不能为空"
                  exit 1
                fi

                if [ "$logistics_provider" = "mock" ]; then
                  echo "LOGISTICS_PROVIDER 在 real-pre 不能为 mock"
                  exit 1
                fi

                IMAGE_TAG="$IMAGE_TAG" docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" config >/dev/null
                echo "real-pre 环境守卫通过: profile=$profile db=$db_name appTest=$app_test douyinTest=$douyin_test"
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

        stage('前端构建') {
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

        stage('E2E 依赖') {
            steps {
                sh '''
                set -eu
                npm ci
                npx playwright install chromium
                '''
            }
        }

        stage('构建 real-pre 镜像') {
            steps {
                sh '''
                set -eu
                echo "构建 real-pre 镜像 IMAGE_TAG=$IMAGE_TAG"
                IMAGE_TAG="$IMAGE_TAG" docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" build backend-real-pre frontend-real-pre
                docker image inspect "colonel-saas/backend:${IMAGE_TAG}" >/dev/null
                docker image inspect "colonel-saas/frontend:${IMAGE_TAG}" >/dev/null
                docker images --filter "reference=colonel-saas/*" --format "table {{.Repository}}\\t{{.Tag}}\\t{{.Size}}"
                '''
            }
        }

        stage('部署 real-pre 生产环境') {
            steps {
                sh '''
                set -eu
                docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" down
                docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" up -d postgres-real-pre redis-real-pre

                REAL_PRE_COMPOSE_FILE="$COMPOSE_FILE" \
                REAL_PRE_COMPOSE_ENV="$COMPOSE_ENV" \
                REAL_PRE_COMPOSE_PROJECT="$COMPOSE_PROJECT" \
                  sh scripts/run-real-pre-db-migrations.sh

                IMAGE_TAG="$IMAGE_TAG" docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" up -d backend-real-pre frontend-real-pre
                docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" ps
                '''
            }
        }

        stage('real-pre 分区维护检查') {
            steps {
                sh '''
                set -eu
                docker compose \
                  --env-file "$COMPOSE_ENV" \
                  --project-name "$COMPOSE_PROJECT" \
                  -f "$COMPOSE_FILE" \
                  exec -T postgres-real-pre sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT create_next_month_partitions();"'
                '''
            }
        }

        stage('端口验活') {
            steps {
                sh '''
                set -eu

                echo "等待后端 ${REAL_PRE_BACKEND}/api/system/health ..."
                for i in $(seq 1 60); do
                  if curl -fsS "${REAL_PRE_BACKEND}/api/system/health" | grep -q '"status":"UP"'; then
                    echo "后端启动成功"
                    break
                  fi
                  if [ "$i" -eq 60 ]; then
                    echo "后端启动失败"
                    docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" logs --tail=300 backend-real-pre
                    exit 1
                  fi
                  sleep 3
                done

                echo "等待前端 ${REAL_PRE_FRONTEND}/healthz ..."
                for i in $(seq 1 40); do
                  if curl -fsS "${REAL_PRE_FRONTEND}/healthz" >/dev/null; then
                    echo "前端访问成功"
                    exit 0
                  fi
                  if [ "$i" -eq 40 ]; then
                    echo "前端启动失败"
                    docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" logs --tail=300 frontend-real-pre
                    exit 1
                  fi
                  sleep 3
                done
                '''
            }
        }

        stage('real-pre 预检') {
            steps {
                sh '''
                set -eu
                E2E_BASE_URL="$REAL_PRE_FRONTEND" E2E_BACKEND_URL="$REAL_PRE_BACKEND" npm run e2e:real-pre:p0:preflight
                '''
            }
        }

        stage('real-pre P0 E2E') {
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
            docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/docker-compose-ps.txt 2>&1
            docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" logs --tail=300 backend-real-pre > runtime/qa/out/jenkins/backend-real-pre.log 2>&1
            docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" logs --tail=300 frontend-real-pre > runtime/qa/out/jenkins/frontend-real-pre.log 2>&1
            docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" logs --tail=300 postgres-real-pre > runtime/qa/out/jenkins/postgres-real-pre.log 2>&1
            docker compose --env-file "$COMPOSE_ENV" --project-name "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" logs --tail=300 redis-real-pre > runtime/qa/out/jenkins/redis-real-pre.log 2>&1
            echo "${IMAGE_TAG:-unknown}" > runtime/qa/out/jenkins/IMAGE_TAG.txt 2>&1
            '''
            archiveArtifacts artifacts: 'runtime/qa/out/**,playwright-report/**,test-results/playwright/**,backend/target/surefire-reports/**', allowEmptyArchive: true
        }

        success {
            echo "real-pre 部署成功 IMAGE_TAG=${env.IMAGE_TAG}"
            echo "回滚: ROLLBACK_REF=<上一提交> sh scripts/rollback-real-pre.sh"
        }

        failure {
            echo '部署或验收失败，请查看 Jenkins 构建日志和归档证据'
            echo "如需回滚: ROLLBACK_REF=<上一提交> sh scripts/rollback-real-pre.sh"
        }
    }
}
