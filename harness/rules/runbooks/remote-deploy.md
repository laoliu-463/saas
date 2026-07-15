# Runbook：real-pre 远端发布

## 适用场景

用户要求把已验证变更发布到远端 real-pre 时使用。本 Runbook 不授予普通 Codex 任务 SSH 或部署权限。

## 普通 Codex 任务职责

- SSH alias：`saas`
- 远端目录：`/opt/saas/app`
- Compose 文件：`docker-compose.real-pre.yml`
- 远端 env：`/opt/saas/env/.env.real-pre`
- 预期 commit：默认取执行机当前工作树的完整 `HEAD`，也可通过 `-ExpectedCommit` 显式传入

禁止直接 SSH、修改 `/opt/saas/app`、修改 `/opt/saas/env/.env.real-pre`、现场构建镜像或部署未合并分支。

## 唯一发布入口

```text
release/real-pre push
→ .github/workflows/release-images.yml
→ Jenkins saas-real-pre-cd 队列
→ lock('saas-real-pre-deploy')
→ scripts/deploy-release.sh
```

Jenkins 参数由 CI 自动提交，不接受人工改成其他分支或短 SHA。发布控制器配置见 `../cicd-real-pre-policy.md`。

## 发布结果判定

- `PASS`：应用/镜像身份一致；如本次包含迁移，数据库版本也一致，`current.json` 已原子更新。
- `FAIL`：构建、digest、迁移、健康或版本校验失败；不得更新 current。
- `BLOCKED`：缺凭证、目标非后继、外部环境不可用，或本次需要迁移但 Flyway/数据库门禁不满足。
- `ROLLBACK`：必须重新进入同一 Jenkins 队列并显式设置 `ROLLBACK_APPROVED=true`。

1. `ssh` 进入远端目录并执行 `git pull --ff-only`。
2. 校验远端 `HEAD` 必须等于预期 commit；不一致立即失败，禁止部署并发推进后的未知提交。
3. 将仓库 `.env.real-pre` 安全收敛为 `/opt/saas/env/.env.real-pre` 的符号链接并校验 canonical target；普通文件不会被覆盖。
4. 固定 Compose project 为 `saas-active`，先收敛 `postgres-real-pre`、`redis-real-pre`，再校验容器 working directory、Compose 文件和 env 来源。
5. 执行幂等 schema guard，构建并更新 `backend-real-pre`、`frontend-real-pre`。
6. 执行 Compose、后端和前端健康检查。

## 部署前检查（同步参数）

部署前必须确认远端 env 文件包含同步相关参数：

```bash
ssh saas "grep PRODUCT_ACTIVITY_SYNC /opt/saas/env/.env.real-pre"
ssh saas "cd /opt/saas/app && docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml config | grep PRODUCT_ACTIVITY"
```

期望输出：

```env
PRODUCT_ACTIVITY_SYNC_ENABLED=true
PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?
```

如果 env 文件缺失，必须在部署前补充到远端 env 文件。compose 与 real-pre profile 仍提供默认值，但远端受控部署必须显式记录 `PRODUCT_ACTIVITY_SYNC_ENABLED=true` 和 5 分钟周期，避免环境漂移。

远端启用前必须确认 `P-FIX-002B` 已修复 `uk_pos_one_displaying_per_product` 唯一索引冲突；否则 5 分钟同步会放大失败频率。

## 部署后检查（同步任务）

部署后必须验证同步任务配置已加载：

```bash
ssh saas "docker exec backend-real-pre printenv | grep PRODUCT_ACTIVITY"
ssh saas "docker logs --tail=200 backend-real-pre 2>&1 | grep ProductActivitySyncJob"
ssh saas "curl -s http://127.0.0.1:8081/api/system/health"
ssh saas "curl -s http://127.0.0.1:3001/healthz"
```

期望日志：

```
ProductActivitySyncJob config: enabled=true, cron=0 */5 * * * ?, batchSize=20, whitelist=(all active)
```

远端启用后必须复核商品库数量，至少对账 `product_snapshot`、`product_operation_state`、`DISPLAYING` 数量和商品库 API total。

## 禁止事项

- 不执行 `down -v`。
- 不删除 PostgreSQL / Redis volume。
- 不把本机 `.env.real-pre` 复制到远端。
- 不输出密钥或 Token。

## 常见失败处理

| 失败 | 处理 |
| --- | --- |
| SSH 不通 | 检查 `RemoteHost`、网络和密钥，不猜测密码 |
| `git pull` 失败 | 检查远端工作区和分支，不强制 reset |
| `Remote commit mismatch` | 重新确认本地提交已推送到 `feature/auth-system`，不得跳过 commit 门禁 |
| `Remote env link mismatch` | 检查 `/opt/saas/env/.env.real-pre` 与仓库链接，不复制本机 env |
| `Stateful service provenance mismatch` | 检查 `saas-active` 项目、固定 volume 和当前 Compose 来源，禁止删除 volume |
| compose 构建失败 | 保留构建日志，标记 `FAIL` |
| 健康检查失败 | 查容器日志和 env guard，标记 `FAIL` 或 `BLOCKED` |
