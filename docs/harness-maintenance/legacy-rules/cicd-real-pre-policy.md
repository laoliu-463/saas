# CICD Real-pre Policy

## 1. Scope

- 当前 CD 标准方案固定为 Jenkins。
- 第一阶段只允许自动部署 `real-pre`。
- 生产环境不得由当前 Jenkins job 自动触碰。
- 本规范记录 Jenkins real-pre CD 的最低门禁，不替代业务验收文档。

## 2. Jenkins Job

- Job 名称：`saas-real-pre-cd`。
- 主源码来源：`https://github.com/laoliu-463/saas.git`；Gitee 仅作只读镜像。
- 唯一部署分支：`release/real-pre`；候选必须先进入 `main`，再通过发布提升 PR 串行进入该分支。
- 运行环境：服务器本机 Jenkins，仅面向 real-pre。
- 镜像来源：必须使用 `main` GitHub Actions 已推送的 `repository@sha256:digest`，镜像内写入 OCI revision label；禁止 Jenkins 在部署节点构建镜像。
- 并发：同 Job 排队且不取消旧任务；跨 Job 使用 `saas-real-pre-deploy` 全局锁。

## 3. Required Parameters

| 参数 | 要求 |
| --- | --- |
| `DEPLOY_BRANCH` | 固定为 `release/real-pre` |
| `DEPLOY_REAL_PRE` | 默认 `false`；发布人明确批准后必须为 `true` |
| `CONFIRM_REAL_PROMOTION_WRITE` | real-pre 推广写入双开关开启时必须为 `true` |
| `ROLLBACK_APPROVED` | 默认 `false`；仅批准回滚或非后继提交时设为 `true` |

## 4. Preflight Guard

Jenkins 部署前必须校验：

- `DEPLOY_ENV=real-pre`。
- 目标 SHA 等于远端 `release/real-pre` 当前 head。
- 目标必须是当前部署 SHA 的后继提交；非后继提交仅在 `ROLLBACK_APPROVED=true` 时允许。
- `RUN_DB_MIGRATIONS` 由当前 SHA 与目标 SHA 的迁移输入 diff 自动计算，不接受普通任务强制开启。
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

1. Checkout `release/real-pre`，记录发布分支头。
2. Preflight Guard：验证 `release/real-pre.json`、`sourceMainSha`、迁移输入摘要和无代码漂移。
3. 获取 `saas-container-registry` 受控凭据，拉取两个 `repository@sha256:digest` 镜像并核对 OCI revision。
4. Compose Config：只验证配置，禁止 `docker compose build`。
5. 获取全局发布锁，校验当前部署 SHA、目标顺序并计算 migration diff。
6. 仅当 `RUN_DB_MIGRATIONS=true` 时备份数据库、执行兼容迁移和 Schema 预检；否则明确记录 `SKIPPED`。
7. 以关闭调度的方式部署 backend，验证 `/api/actuator/health/readiness`。
8. 部署 frontend，执行核心业务 smoke 和多角色 E2E。
9. 恢复调度并再次验证 readiness。
10. 任一部署后阶段失败时，在本阶段锁仍持有期间执行 `scripts/cd/rollback-real-pre.sh`；超时和中止也必须经过同一 `unsuccessful` hook。
11. Evidence Report：核对后端/前端运行 SHA、镜像摘要、OCI revision、迁移版本和回滚镜像；全部通过后原子更新发布清单。

## 6. Deploy Boundary

- 只允许 `backend-real-pre` 与 `frontend-real-pre` 自动拉取和重启；不得在部署节点构建镜像。
- 只允许自动执行已入库、已通过 Testcontainers 的增量兼容迁移；禁止临时 SQL 和破坏性 DDL。
- 不允许 `docker compose down -v`。
- 不允许删除 PostgreSQL / Redis volume。
- 不允许切换到 test/mock 配置证明 real-pre 通过。
- 不允许引用 production compose 或 production env 文件。
- 不允许把 `BLOCKED`、`PENDING`、`PARTIAL` 写成 `PASS`。
- 不允许普通 Agent、任务分支或手工 SSH 绕过 Jenkins 发布队列。

## 7. Rollback

部署前必须记录当前 backend / frontend 镜像。

迁移、readiness、frontend、smoke、E2E、调度恢复或最终健康检查失败时：

1. 状态文件标记 `deployment-started` 后，禁止继续使用新版本做 post-lock 修复。
2. 在 `saas-real-pre-deploy` 锁内切回部署前 backend / frontend `repository@sha256:digest`。
3. 重新启用调度并执行健康检查；成功后写入 `rollback-completed`。
4. Jenkins build 保持失败，evidence report 记录失败、回滚动作和剩余风险。

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
- 数据库变更判定；执行时记录备份与 `pg_restore --list`，跳过时记录 `SKIPPED`。
- Flyway/兼容迁移版本和 Schema 预检结果。
- 部署前回滚镜像、部署后镜像 ID、40 位 SHA 和 OCI revision label。
- `/opt/saas/releases/<SHA>/release.json`、`current.json`、`previous.json` 状态。
- `Secret leaked: NO`。

证据文件固定写入：

- Jenkins artifact：`runtime/qa/out/latest-jenkins-cd.md`。
- 服务器归档：`/opt/saas/runtime/qa/out/jenkins-<build>/latest-evidence-jenkins-cd.md`。

## 9. Verified Baseline

历史 Jenkins CD 基线（仅供追溯，不代表本策略当前已在远端验证）：

- Job：`saas-real-pre-cd #9`。
- Commit：`e248b611698e56e1e1e924fc65e79bee0fcb8fac`。
- Image tag：`e248b611`（历史短标签，当前策略已禁止）。
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
