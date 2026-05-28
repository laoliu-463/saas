# Jenkins 后续接入方案

当前目标是先完成 Xshell 手工可重复部署，Jenkins 不是当前部署前置依赖。本文只给后续接入方案和 Jenkinsfile 草案。

## 一、接入原则

- Jenkins 复用 `docker-compose.real-pre.yml` 和服务器本地 `.env.real-pre`。
- `.env.real-pre` 由 Jenkins credentials 或服务器受控文件提供，不进入 Git。
- real-pre 必须保持 `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`。
- Jenkins 不执行 `docker compose down -v`。
- Jenkins 不删除 PostgreSQL / Redis volume。
- 部署前跑后端测试、前端构建、Compose 静态校验。
- 部署后跑 `/api/system/health` 和前端端口验活。
- P0 E2E 失败时流水线失败，不能把 `BLOCKED` / `PENDING` 记为通过。

## 二、推荐 credentials

建议在 Jenkins 中配置：

| ID | 类型 | 用途 |
| --- | --- | --- |
| `saas-real-pre-env` | Secret file | 完整 `.env.real-pre` 文件 |
| `saas-git-ssh-key` | SSH Username with private key | 拉取私有仓库时使用 |
| `douyin-client-secret` | Secret text | 如果不使用完整 env 文件，可单独注入抖音密钥 |
| `jwt-secret-real-pre` | Secret text | 如果不使用完整 env 文件，可单独注入 JWT secret |

优先使用 Secret file 管理 `.env.real-pre`，减少流水线日志泄漏风险。

## 三、Jenkinsfile 草案

以下是草案，不要求现在替换根目录 `Jenkinsfile`：

```groovy
pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  environment {
    COMPOSE_FILE = 'docker-compose.real-pre.yml'
    COMPOSE_PROJECT_NAME = 'saas-active'
    REAL_PRE_FRONTEND = 'http://127.0.0.1:3001'
    REAL_PRE_BACKEND = 'http://127.0.0.1:8081'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Prepare env') {
      steps {
        withCredentials([file(credentialsId: 'saas-real-pre-env', variable: 'REAL_PRE_ENV')]) {
          sh '''
            set -eu
            cp "$REAL_PRE_ENV" .env.real-pre
            chmod 600 .env.real-pre
          '''
        }
      }
    }

    stage('Guard real-pre env') {
      steps {
        sh '''
          set -eu
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
                  print v
                  exit
                }
              }
            ' .env.real-pre
          }

          test "$(get_env SPRING_PROFILES_ACTIVE)" = "real-pre"
          test "$(get_env DB_NAME)" = "saas_real_pre"
          test "$(get_env APP_TEST_ENABLED)" = "false"
          test "$(get_env DOUYIN_TEST_ENABLED)" = "false"
          docker compose --env-file .env.real-pre --project-name "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" config >/dev/null
        '''
      }
    }

    stage('Backend test') {
      steps {
        dir('backend') {
          sh 'mvn clean test'
        }
      }
    }

    stage('Frontend build') {
      steps {
        dir('frontend') {
          sh '''
            set -eu
            npm ci
            npm run build
          '''
        }
      }
    }

    stage('Backend package') {
      steps {
        dir('backend') {
          sh 'mvn clean package -DskipTests'
        }
      }
    }

    stage('Database backup') {
      steps {
        sh '''
          set -eu
          chmod +x scripts/backup-db.sh
          ./scripts/backup-db.sh
        '''
      }
    }

    stage('Deploy real-pre') {
      steps {
        sh '''
          set -eu
          docker compose --env-file .env.real-pre --project-name "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" up -d --build
          docker compose --env-file .env.real-pre --project-name "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" ps
        '''
      }
    }

    stage('Health check') {
      steps {
        sh '''
          set -eu
          chmod +x scripts/health-check.sh
          ./scripts/health-check.sh
        '''
      }
    }

    stage('Real-pre P0 preflight') {
      steps {
        sh '''
          set -eu
          npm ci
          E2E_BASE_URL="$REAL_PRE_FRONTEND" E2E_BACKEND_URL="$REAL_PRE_BACKEND" npm run e2e:real-pre:p0:preflight
        '''
      }
    }
  }

  post {
    always {
      sh '''
        mkdir -p runtime/qa/out/jenkins
        docker compose --env-file .env.real-pre --project-name "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" ps > runtime/qa/out/jenkins/docker-compose-ps.txt 2>&1 || true
        docker compose --env-file .env.real-pre --project-name "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=300 backend-real-pre > runtime/qa/out/jenkins/backend-real-pre.log 2>&1 || true
        docker compose --env-file .env.real-pre --project-name "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=300 frontend-real-pre > runtime/qa/out/jenkins/frontend-real-pre.log 2>&1 || true
        docker compose --env-file .env.real-pre --project-name "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=300 postgres-real-pre > runtime/qa/out/jenkins/postgres-real-pre.log 2>&1 || true
        docker compose --env-file .env.real-pre --project-name "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" logs --tail=300 redis-real-pre > runtime/qa/out/jenkins/redis-real-pre.log 2>&1 || true
      '''
      archiveArtifacts artifacts: 'runtime/qa/out/jenkins/**', allowEmptyArchive: true
    }
  }
}
```

## 四、部署流程

1. Jenkins 拉取代码。
2. Jenkins 注入 `.env.real-pre`。
3. 校验 real-pre 环境守卫。
4. 执行 `mvn clean test`。
5. 执行 `npm ci && npm run build`。
6. 执行 `mvn clean package -DskipTests`。
7. 执行 `scripts/backup-db.sh`。
8. 执行 `docker compose up -d --build`。
9. 执行 `scripts/health-check.sh`。
10. 执行 real-pre preflight 或 P0 E2E。
11. 归档 Compose 状态和四个服务日志。

## 五、回滚口径

当前项目没有完整镜像 tag 发布机制时，先使用 Git 回滚：

```bash
./scripts/rollback-real-pre.sh HEAD~1
```

后续如果建立镜像仓库和版本化 tag，可改为：

```bash
IMAGE_TAG=<previous-tag> docker compose --env-file /opt/saas/env/.env.real-pre --project-name saas-active -f docker-compose.real-pre.yml up -d
```

无论哪种方式，回滚前都必须先备份数据库，且不得删除 volume。
