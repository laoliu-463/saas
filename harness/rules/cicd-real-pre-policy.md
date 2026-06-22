# CICD Real-pre Policy

## 1. Scope

- 当前 CD 标准方案固定为 Jenkins。
- 第一阶段只允许自动部署 `real-pre`。
- 生产环境不得由当前 Jenkins job 自动触碰。
- 本规范记录 Jenkins real-pre CD 的最低门禁，不替代业务验收文档。

## 2. Jenkins Job

- Job 名称：`saas-real-pre-cd`。
- 源码来源：`https://gitee.com/cao-jianing463/saas.git`。
- 默认分支：`feature/ddd/DDD-VERIFY-001`。
- 运行环境：服务器本机 Jenkins，仅面向 real-pre。
- 镜像标签：使用 Git short commit，例如 `e248b611`。

## 3. Required Parameters

| 参数 | 要求 |
| --- | --- |
| `DEPLOY_BRANCH` | 必须指向受控 real-pre CD 分支 |
| `DEPLOY_REAL_PRE` | 必须为 `true` |
| `CONFIRM_REAL_PROMOTION_WRITE` | real-pre 推广写入双开关开启时必须为 `true` |

## 4. Preflight Guard

Jenkins 部署前必须校验：

- `DEPLOY_ENV=real-pre`。
- `RUN_DB_MIGRATIONS=false`。
- Compose 文件只能是 `docker-compose.real-pre.yml`。
- Compose project 固定为 `saas-active`。
- 服务器环境文件固定为 `/opt/saas/env/.env.real-pre`。
- `APP_TEST_ENABLED=false`。
- `DOUYIN_TEST_ENABLED=false`。
- `DOUYIN_REAL_UPSTREAM_MODE=live`。
- `SPRING_PROFILES_ACTIVE=real-pre`。
- `DB_NAME=saas_real_pre`。
- PostgreSQL / Redis 不对公网暴露。
- 不输出 `.env.real-pre` 中的密钥值。

## 5. Pipeline Stages

Jenkins real-pre CD 必须按以下顺序执行：

1. Checkout 指定分支并记录 commit / image tag。
2. Preflight Guard。
3. Backend Test：执行 `mvn clean test`，不得跳过测试阶段。
4. Backend Package：执行 `mvn clean package -DskipTests`，只复用已通过的测试结果。
5. Frontend Build：执行依赖安装、`test`、`typecheck`、`build`。
6. Docker Build：构建 `backend-real-pre` 与 `frontend-real-pre` 镜像。
7. Compose Config：输出并校验 `docker compose config`。
8. Deploy real-pre：只更新 backend / frontend，不重建 DB / Redis。
9. Health Check：验证后端 `/api/system/health` 与前端 `/healthz`。
10. Evidence Report：归档 Jenkins CD 证据。

## 6. Deploy Boundary

- 只允许 `backend-real-pre` 与 `frontend-real-pre` 自动重建和重启。
- 不允许自动执行数据库迁移。
- 不允许 `docker compose down -v`。
- 不允许删除 PostgreSQL / Redis volume。
- 不允许切换到 test/mock 配置证明 real-pre 通过。
- 不允许引用 production compose 或 production env 文件。
- 不允许把 `BLOCKED`、`PENDING`、`PARTIAL` 写成 `PASS`。

## 7. Rollback

部署前必须记录当前 backend / frontend 镜像。

Health Check 失败时：

1. 尝试切回部署前 backend / frontend 镜像标签。
2. 重新执行健康检查。
3. Jenkins build 标记为失败。
4. evidence report 记录失败、回滚动作和剩余风险。

## 8. Evidence

每次 Jenkins CD 必须生成：

- Jenkins build number 和 result。
- source、branch、commit、image tag。
- 后端测试汇总。
- 前端测试 / typecheck / build 结果。
- Docker image 和 container 状态。
- `docker compose config` 结果。
- 后端 / 前端健康检查结果。
- `Production touched: NO`。
- `Database migration/write by pipeline: NO`。
- `Secret leaked: NO`。

证据文件固定写入：

- Jenkins artifact：`harness/reports/latest-evidence-jenkins-cd.md`。
- 服务器归档：`/opt/saas/runtime/qa/out/jenkins-<build>/latest-evidence-jenkins-cd.md`。

## 9. Verified Baseline

最近已验证的 Jenkins CD 基线：

- Job：`saas-real-pre-cd #9`。
- Commit：`e248b611698e56e1e1e924fc65e79bee0fcb8fac`。
- Image tag：`e248b611`。
- Result：`SUCCESS`。
- 后端测试：`2511` run，`0` failures，`0` errors，`3` skipped。
- 后端健康：`{"status":"UP"}`。
- 前端健康：`ok`。
- real-pre 容器：backend / frontend 已切到 `colonel-saas/*:e248b611`。

## 10. Production Rule

生产环境后续如需接入 Jenkins，必须另建审批型 job，并满足：

- 默认不自动触发。
- 必须人工确认部署窗口、commit、回滚镜像和数据库迁移策略。
- 必须先有 real-pre PASS 证据。
- 必须单独生成生产 evidence report。
- 不得复用 `saas-real-pre-cd` 直接部署生产。
