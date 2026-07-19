# Harness Environment Cheatsheet

> 单一速查表。本地端口、健康检查 URL、env 含义、远端路径。
> 详细说明以 `harness/rules/environment/envs/*.md` 为准；本表只做快速索引。

## 1. 本地端口与服务

| 服务 | 端口 | Compose 服务名 | 健康检查 URL |
| --- | --- | --- | --- |
| backend | 8081 | `backend-real-pre` | `http://localhost:8081/api/system/health` |
| frontend | 3001 | `frontend-real-pre` | `http://localhost:3001/healthz` |
| PostgreSQL | 5432 | `postgres-real-pre` | `pg_isready -h localhost -p 5432` |
| Redis | 6379 | `redis-real-pre` | `redis-cli -h localhost -p 6379 ping` |
| test backend | 8080 | `backend-test` | `http://localhost:8080/api/system/health` |
| test frontend | 3000 | `frontend-test` | `http://localhost:3000/healthz` |

## 2. Env 变量速查

| 变量 | 含义 | 默认 | 禁止 |
| --- | --- | --- | --- |
| `APP_TEST_ENABLED` | test 模式开关 | `false` | real-pre 严禁 `true` |
| `DOUYIN_TEST_ENABLED` | 抖音 mock 开关 | `false` | real-pre 严禁 `true` |
| `DOUYIN_REAL_UPSTREAM_MODE` | 抖音真实上游模式 | `live` | 禁止改 `mock` / `dry-run` |
| `PRODUCT_ACTIVITY_SYNC_ENABLED` | 商品活动同步开关 | `false` | 启用前需 P-FIX-002B 修复完成 |
| `PRODUCT_ACTIVITY_SYNC_CRON` | 商品活动同步周期 | 5min | 至少 5min |
| `REDIS_HOST` / `REDIS_PORT` | Redis 连接 | real-pre 默认 | 不允许指向 test redis |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `real-pre` | docs-only / 调试可临时改 |

## 3. 本地操作命令

```powershell
# 查看容器
docker compose -f docker-compose.real-pre.yml ps

# 重启后端
docker compose -f docker-compose.real-pre.yml restart backend-real-pre

# 后端日志
docker compose -f docker-compose.real-pre.yml logs backend --tail=200

# 跑所有 harness 验证
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey task-key -OwnedFiles 'path1;path2' -Message "task"
```

## 4. 远端信息（受控）

| 项 | 默认值 | 备注 |
| --- | --- | --- |
| SSH alias | `saas` | 实际值见用户私有配置；非必要不在文档写出 |
| 发布目录 | `/opt/saas/releases/<完整SHA>` | 不可变清单与 Compose |
| 固定 env | `/opt/saas/env/.env.real-pre` | Jenkins 只读使用 |
| 远端 backend | `backend-real-pre` | 容器名与本地一致 |
| 远端 frontend | `frontend-real-pre` | 容器名与本地一致 |
| 远端发布入口 | `release/real-pre` → CI → Jenkins | 普通任务禁止 SSH/部署 |

## 5. test vs real-pre 差异

| 维度 | test | real-pre |
| --- | --- | --- |
| 上游数据 | mock / 抖店 sandbox | 真实抖音 |
| 订单 | 不存在或全 mock | 必须有真实订单样本 |
| `APP_TEST_ENABLED` | `true` | `false` |
| `DOUYIN_TEST_ENABLED` | `true` | `false` |
| 用途 | 回归 / 单元 / E2E mock | 上线前联调 / 真实样本验证 |
| 启动 | `docker compose -f docker-compose.test.yml up -d` | `docker compose -f docker-compose.real-pre.yml up -d` |

## 6. 安全检查命令

```powershell
# docs-only
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun

# 部署前
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\verify-local.ps1 -Env real-pre -Scope full
```

## 7. 禁止命令

| 禁止 | 原因 |
| --- | --- |
| `docker compose down -v` | 会清空 volume |
| `rm -rf backend/src/main` | 业务代码 |
| `git add .` / `-A` / `<dir>/` | 大块引入 dirty |
| `git commit --amend` | 改写历史 |
| `git push --force` to main/master | 危险 |
| 修改 `.env.real-pre` 真实文件 | 密钥 |
| `-DeployRemote true` 或旧部署脚本 | 绕过 Jenkins 唯一发布队列 |

## 8. 关联文档

- `harness/rules/environment/README.md`
- `harness/rules/environment/envs/test-env.md`
- `harness/rules/environment/envs/real-pre-env.md`
- `harness/rules/environment/envs/remote-real-pre-env.md`
- `harness/rules/environment/envs/docker-compose-map.md`
- `harness/rules/environment/envs/local-dev-env.md`
- `harness/rules/runbooks/governance/scope-command-matrix.md`
- `harness/rules/runbooks/remote-deploy.md`
