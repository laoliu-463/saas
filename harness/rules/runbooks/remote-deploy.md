# Runbook：远端 real-pre 发布

## 适用场景

用户明确要求把已合并变更发布到远端 `real-pre` 时使用。普通 Codex/Agent 只准备候选、验证和记录，不直接 SSH 修改或部署服务器。

## 唯一发布链路

```text
任务分支 -> PR/CI -> main -> 发布提升 PR -> release/real-pre -> Jenkins saas-real-pre-cd
```

- `main` 是唯一集成主线。
- `release/real-pre` 是唯一可部署分支。
- Jenkins 是唯一发布控制器。
- `agent-do.ps1 -DeployRemote true` 与 `deploy-remote.ps1` 直接部署入口均已停用。

## 发布前条件

1. 候选提交已经进入 `main`，禁止发布未合并的任务分支。
2. `main` 的 `Backend tests`、`Frontend tests and build`、`Repository governance` 全部通过。
3. 通过独立 PR 将目标提交串行提升到 `release/real-pre`。
4. 发布人确认目标是 40 位完整 SHA，并检查回滚版本。
5. 真实推广写开关开启时，必须显式确认 `CONFIRM_REAL_PROMOTION_WRITE=true`。

## Jenkins 参数

| 参数 | 值 / 规则 |
| --- | --- |
| `DEPLOY_BRANCH` | 固定 `release/real-pre` |
| `DEPLOY_REAL_PRE` | 发布人批准后设为 `true` |
| `CONFIRM_REAL_PROMOTION_WRITE` | 真实推广写开启时必须为 `true` |
| `ROLLBACK_APPROVED` | 仅批准回滚或非后继发布时为 `true` |

## 串行与顺序门禁

- 同 Job 使用 `disableConcurrentBuilds(abortPrevious: false)` 排队，不取消运行中的发布。
- 所有 Job 共享 `saas-real-pre-deploy` 全局锁。
- Jenkins 比较 `/opt/saas/releases/current.json` 或当前运行镜像的 SHA。
- 目标不是当前版本后继提交时默认拒绝；只有 `ROLLBACK_APPROVED=true` 才允许。

## 数据库边界

- Jenkins 比较当前 SHA 与目标 SHA 的迁移输入。
- 只有数据库 migration / 聚合迁移入口变化时，才执行备份、迁移和 Schema 预检。
- 纯文档、Harness、前端或无迁移的后端变更记录 `RUN_DB_MIGRATIONS=false`，不得强制远端数据库操作。
- 禁止临时 SQL、清库、删除 volume 和 `docker compose down -v`。

## 发布成功条件

以下证据全部一致后才允许更新 `current.json`：

1. Jenkins 目标完整 SHA。
2. 后端 `/api/system/health` 的 `gitSha` 与 `imageDigest`。
3. 前端 `/version.json` 的 `gitSha`。
4. 运行容器镜像 tag、Docker 内容摘要与 OCI revision。
5. Flyway 成功版本查询。
6. health、smoke 与多角色 E2E。

发布清单位置：

```text
/opt/saas/releases/<完整SHA>/release.json
/opt/saas/releases/current.json
/opt/saas/releases/previous.json
```

只有全部验证通过后才原子更新 `current.json`。失败时保持当前发布指针不变，并使用 `previous.json` / 部署前镜像执行受控回滚。

## Evidence

- Jenkins artifact：`harness/reports/current/latest-jenkins-cd.md`
- 服务器归档：`/opt/saas/runtime/qa/out/jenkins-<build>/latest-evidence-jenkins-cd.md`
- 未执行数据库操作必须明确记录为 `false` / `SKIPPED`，不得写成已迁移。

## 禁止事项

- 禁止 Agent 直接 SSH 部署或修改 `/opt/saas/app`。
- 禁止从任务分支部署。
- 禁止 `latest`、短 SHA、本地临时 tag。
- 禁止修改共享 `/opt/saas/env/.env.real-pre`。
- 禁止并行合并、并行迁移、并行远端 E2E 或并行部署。
