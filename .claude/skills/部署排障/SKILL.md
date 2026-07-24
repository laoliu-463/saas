---
name: deployment-troubleshooting
description: 排查本地 Docker Compose、real-pre 启动、健康、版本、迁移和 Jenkins 发布问题，保持生产环境可回滚、可追溯。
---

# 部署排障

## 触发场景

- 容器启动失败、健康检查失败、端口冲突、profile 或环境变量错误。
- 后端、前端、PostgreSQL、Redis 版本不一致，或 real-pre 发布、回滚、迁移失败。

## 输入

- 目标环境：`test`、本地 `real-pre` 或远端 Jenkins `real-pre`。
- 现象、首次出现时间、最近一次变更、构建号、目标 SHA 和发布清单（如适用）。
- 现有证据：Compose 状态、脱敏日志、健康接口、`current.json` / `previous.json`、Jenkins artifact。

## 必读依据

- `harness/policy/safety.md`、`harness/policy/real-pre.md`。
- `harness/runbooks/deployment.md`、`harness/runbooks/rollback.md` 和对应环境 runbook。
- `docs/10-部署运行总览.md`、`.claude/lsp/诊断规则.md`。

## 安全边界

- 先读、后判断、再处理；不得同时混起 `test` 和 `real-pre` 的后端或前端。
- 本地可以检查 Compose；远端禁止普通 Agent SSH、服务器现场构建、共享工作树 `checkout/pull/reset` 和 `-DeployRemote true`。
- 远端唯一入口是 `release/real-pre` → Jenkins `saas-real-pre-cd` → `saas-real-pre-deploy` 锁；不要用手工命令绕过队列止血。
- 禁止 `docker compose down -v`、清库、删除 volume、修改服务器 `.env`、切换 mock 或关闭真实上游后宣称通过。
- 不输出 Token、密码、OAuth code、`.env` 内容或完整连接串。

## 步骤

1. 先做只读检查并确认目标环境：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 inspect -TargetEnv real-pre
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify -TargetEnv real-pre
```

2. 本地 Compose 仅在目标环境配置已存在时检查：

```powershell
docker compose --env-file .env.real-pre --project-name saas-active -f docker-compose.real-pre.yml config --quiet
docker compose --env-file .env.real-pre --project-name saas-active -f docker-compose.real-pre.yml ps
```

核对四个服务、profile、端口 `3001/8081`、数据库 `saas_real_pre`、Redis 连接和 `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`。不确定配置时停止，不猜值。

3. 按故障类型收集最小证据：Compose 状态和脱敏日志、公开探针 `/api/system/health`、前端 `/login`、启动 profile、数据库迁移结果、最近一次变更。不要用需要鉴权的 `/api/actuator/health` 代替公开探针。

4. 远端发布故障只核对 Jenkins：构建号、`release/real-pre` SHA、发布清单、后端 `gitSha/imageDigest`、前端 `/version.json`、运行镜像 digest、Flyway 版本、`current.json` / `previous.json` 和锁状态。若已创建 `deployment-started` 而未完成，等待锁内统一回滚结果；回滚失败时停止重试并升级值班负责人。

5. 数据库迁移只按 Jenkins 根据 SHA 差异决定是否执行；无迁移输入时记录跳过，有迁移失败时保留备份、日志和版本，不手工反向改库。

## 输出

用短表说明：现象、证据、根因或当前推论、影响范围、处理动作、回滚入口、状态和下一步。区分：

- `LOCAL_HEALTHY`：本地容器健康，不代表远端发布成功。
- `REMOTE_HEALTHY`：Jenkins 发布、版本、镜像、迁移、健康和业务证据齐全。
- `BLOCKED`：权限、Token、外部服务或 Jenkins 队列阻塞。
- `FAIL`：系统内可复现的部署或运行失败。
- `PARTIAL`：部分证据通过，仍有关键项未验证。

## 验证

- 结论必须能回到命令、日志、Jenkins artifact 或发布指针；不能只写“容器启动成功”。
- 远端成功必须同时核对 Jenkins SHA、运行镜像摘要、健康检查、迁移结果和业务验收。
- `BLOCKED`、`PENDING`、`PARTIAL` 不得改写成 `PASS`；回滚未完成不得宣称环境已恢复。
- 生成 evidence，记录时间、环境、版本、容器状态、健康检查、业务验证、远端状态和 retro 结论。
