# 当前执行口径

> 本文以仓库根目录 [开发与 real-pre 发布流程](../development-flow.md) 和当前 `Jenkinsfile` 为准。`release/real-pre` 是唯一发布分支；Jenkins 是日常 real-pre 发布入口，手工 SSH 仅为 Break-glass。

# Jenkins 自动化部署规划

## 适用场景

本文用于 real-pre 发布队列的自动化执行和门禁维护。服务器环境初始化可以由具备权限的运维人员按私有 Runbook 完成；日常代码发布不再通过手工 SSH 或现场构建。

## 当前仓库实际情况

- 根目录存在 `Jenkinsfile`。
- 当前 `Jenkinsfile` 已对齐 `PROJECT_NAME = 'saas-active'`，与 `docker-compose.real-pre.yml` 和 `.env.real-pre.example` 的 `COMPOSE_PROJECT_NAME=saas-active` 保持一致。
- 当前 `Jenkinsfile` 从 `release/real-pre` checkout，默认只做发布前验证；只有人工开启 `DEPLOY_REAL_PRE=true` 才执行 real-pre 部署。
- Jenkins 校验完整 40 位 SHA、`main` 可追溯性、发布顺序、迁移计划、数据库备份、readiness、smoke / E2E 和回滚证据。
- `DEPLOY_BRANCH` 固定为 `release/real-pre`，不得指向任务分支、Gitee 镜像或浮动标签。
- 当前 `Jenkinsfile` 保留 `RUN_REAL_PRE_E2E` 参数，默认 `false`；只有人工打开后才运行 preflight / roles / p0。
- 当前 `Jenkinsfile` 保留 `CONFIRM_REAL_PROMOTION_WRITE` 参数；real-pre 环境开启真实推广写双开关时，必须人工确认后才允许部署或 E2E。

## 前置条件

- 手动部署流程已成功执行至少一次。
- `/opt/saas/env/.env.real-pre` 已在服务器或 Jenkins credentials 中受控保存。
- Jenkins 节点能执行 Docker、Git、Maven、Node / npm。
- Jenkins 对 `/opt/saas/app`、`/opt/saas/runtime/qa/out` 有读写权限。

## Jenkins 职责

| 职责 | 说明 |
| --- | --- |
| 拉代码 | 获取部署分支 |
| 后端测试 | 执行 `mvn test` |
| 前端测试 / build | 执行 `npm run build` 或 `npm --prefix frontend run build` |
| env 守卫 | 校验 real-pre 不允许 mock 开关 |
| 数据库备份 | 调用部署脚本或 `scripts/backup-db.sh` |
| 部署 | 调用 `scripts/deploy-real-pre.sh` |
| 健康检查 | 调用脚本内健康检查或 `scripts/health-check.sh` |
| 留证据 | 归档 Compose config、ps、logs、测试报告 |

Jenkins 不应该直接管理真实密钥明文，不应该执行 `docker compose down -v`。

## Jenkins 容器部署示例

如果需要用 Docker 启 Jenkins，可先作为第二阶段独立服务，不放进当前 real-pre Compose。

```yaml
services:
  jenkins:
    image: jenkins/jenkins:lts-jdk17
    container_name: saas-jenkins
    user: root
    ports:
      - "18080:8080"
      - "50000:50000"
    volumes:
      - jenkins_home:/var/jenkins_home
      - /var/run/docker.sock:/var/run/docker.sock
      - /opt/saas:/opt/saas
    restart: unless-stopped

volumes:
  jenkins_home:
```

公网开放 `18080` 时必须限制来源 IP；更推荐只通过 VPN 或内网访问。

## Jenkinsfile 阶段设计

当前第一版：

```text
Checkout
-> Backend Test
-> Frontend Test / Build
-> Backend Package
-> Evidence
```

人工开启部署或 real-pre E2E 后追加：

```text
Guard Real-Pre Env
-> Deploy (DEPLOY_REAL_PRE=true)
-> Health Check
-> Real-pre Preflight
-> Real-pre Roles
-> Real-pre P0
```

后续版本再逐步强化：

```text
Real-pre Preflight
-> Real-pre Roles
-> Real-pre P0
-> Evidence Summary Parsing
-> Rollback Drill
```

`PENDING` 不应直接让 Jenkins 判定为代码失败，除非报告明确是 `FAIL` 或硬失败。

## 执行步骤

### 1. Jenkins 凭据

建议配置：

| ID | 类型 | 用途 |
| --- | --- | --- |
| `saas-real-pre-env` | Secret file | 完整 `.env.real-pre` |
| `saas-git-ssh-key` | SSH private key | 私有仓库拉代码时使用 |
| `jwt-secret-real-pre` | Secret text | 如拆分 env 时使用 |
| `douyin-client-secret` | Secret text | 如拆分 env 时使用 |

优先用 Secret file 管理完整 `.env.real-pre`，减少拼接错误。

### 2. 当前 Jenkinsfile 执行口径

当前仓库根目录 `Jenkinsfile` 已落地第一版自动化，核心参数如下：

```groovy
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
}
```

`DEPLOY_REAL_PRE=true` 时，部署阶段固定调用：

```bash
APP_DIR="$PWD" \
ENV_FILE="$ENV_FILE" \
COMPOSE_FILE="$COMPOSE_FILE" \
PROJECT_NAME="$PROJECT_NAME" \
EVIDENCE_ROOT="/opt/saas/runtime/qa/out/jenkins-${BUILD_NUMBER}" \
IMAGE_TAG="$IMAGE_TAG" \
REAL_PROMOTION_WRITE_CONFIRMED="$CONFIRM_REAL_PROMOTION_WRITE" \
  ./scripts/deploy-real-pre.sh
```

说明：Jenkins 仍然是第二阶段入口，不替代第一次手动部署。第一次服务器部署应先用 `scripts/deploy-real-pre.sh` 直接验证端口、健康检查、Token 和真实 API。默认构建不会部署 real-pre，也不会重启容器。

## 验收标准

- Jenkins 不参与第一次手动部署。
- Jenkins 调用同一套 `scripts/deploy-real-pre.sh`。
- Jenkins 默认只跑 CI；`DEPLOY_REAL_PRE=true` 才允许执行部署和重启。
- Jenkins 部署时必须保持 `DEPLOY_BRANCH=release/real-pre`，不依赖服务器上的 `git pull`。
- Jenkins 环境守卫能阻断 `APP_TEST_ENABLED=true`、`DOUYIN_TEST_ENABLED=true`、`DOUYIN_REAL_UPSTREAM_MODE` 非 `live`、真实推广双开关不一致、快递100或物流同步未开启等上线配置缺口。
- Jenkins 在真实推广写双开关为 `true` 时，必须要求 `CONFIRM_REAL_PROMOTION_WRITE=true`，否则阻断部署或 real-pre E2E。
- Jenkins 归档部署证据和日志。
- 第一版 Jenkins 不强制接入 P0 E2E，`RUN_REAL_PRE_E2E` 默认关闭。
- 第二版接入 preflight / roles / p0 时，不能把 `PENDING` 直接当 PASS 或代码失败。

## 常见问题

| 问题 | 判断方法 | 处理 |
| --- | --- | --- |
| Jenkins 和手动部署行为不一致 | 对比 Jenkinsfile 与 `deploy-real-pre.sh` | 以脚本为准，删除重复流程 |
| Jenkins 使用旧 project | 搜索 `PROJECT_NAME = 'saas-active'` 和 `.env.real-pre` 中的 `COMPOSE_PROJECT_NAME=saas-active` | 两处必须一致 |
| Jenkins 泄漏 env | 查看构建日志 | 使用 Secret file，不 echo 密钥 |
| PENDING 使流水线失败 | 查看 E2E exit code 和 summary | 第二版再设计 PENDING 处理策略 |
| Docker 权限不足 | Jenkins 日志显示 permission denied | 将 Jenkins 用户纳入 docker 组或使用受控 root 容器 |
