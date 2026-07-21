# 部署文档入口

> **⚠️ 2026-07-21 更新：手工 SSH 部署已退役。real-pre 的唯一发布入口是 Jenkins real-pre CD。**
>
> 本文档以前记录的"开发分支 / Gitee 优先 / 本机 SSH 别名 / `root@` 登录 / git pull + docker compose up"等流程已不再是常规部署路径。
>
> **普通任务请直接走 PR + Jenkins CD**：参考根目录 [README.md](../../README.md) 的"同步到服务器"一节。

---

## 适用场景

本文档保留历史部署知识，供以下场景使用：

1. **首次初始化一台新的 real-pre 服务器**（基线安装、目录创建、`.env.real-pre.example` → `.env.real-pre`）。
2. **BREAK-GLASS 紧急恢复**：Jenkins 不可用、服务器环境损坏、需要手工回滚到上一份 release manifest。
3. **审计与回溯**：查看过去部署报告、Compose 参数契约、门禁清单。

任何常规代码改动 → 部署的发布动作请通过 Jenkins（`release/real-pre` 提升 PR 触发），不要按本文档手工执行。

---

## 当前仓库实际文件（与 Jenkins 共用同一份）

- `docker-compose.real-pre.yml`
- `.env.real-pre.example`
- `scripts/deploy-real-pre.sh`
- `scripts/backup-db.sh`
- `scripts/health-check.sh`
- `scripts/real-pre-startup-check.sh`
- `scripts/rollback-real-pre.sh`
- `Jenkinsfile`
- `backend/pom.xml`
- `frontend/Dockerfile`
- `frontend/nginx/default.conf.template`

Jenkins 与 BREAK-GLASS 流程都调用同一份 `scripts/deploy-real-pre.sh`，不接受两套命令口径。

---

## 当前发布黄金路径

```text
main 合并 → 镜像构建（push:main 触发）
    ↓
release/real-pre 提升 PR
    ↓
Jenkins real-pre CD
    ├─ 校验精确 SHA
    ├─ 校验 GitHub required checks
    ├─ 获取全局锁（/var/lock/saas-real-pre-deploy.lock）
    ├─ 数据库备份 + 兼容迁移
    ├─ 暂停 scheduler → 部署 backend → readiness → 部署 frontend
    ├─ P0 smoke + 多角色 E2E
    └─ 恢复 scheduler + 更新 release manifest
```

详细规范见：

- [07-Jenkins自动化部署规划.md](07-Jenkins自动化部署规划.md)（历史规划文档；现状以 Jenkinsfile 为准）
- [08-real-pre参数开关契约.md](08-real-pre参数开关契约.md)（生产参数基线）
- 根目录 [Jenkinsfile](../../Jenkinsfile)（实际编排）

---

## ⚠️ BREAK-GLASS 紧急恢复

仅在以下情况启动 BREAK-GLASS：

- Jenkins 长时间不可用且无法在 30 分钟内恢复。
- 服务器环境被破坏且无法通过 Jenkins 回滚到 `previous` release。
- 真实上游紧急关闭（订单、风控、合规）需要立即降级部署。

BREAK-GLASS 启动前必须：

1. 在团队频道声明 `BREAK-GLASS-START <YYYY-MM-DD HH:mm>`。
2. 在服务器侧获取 `/var/lock/saas-real-pre-deploy.lock`（与 Jenkins 共用同一把锁）：
   ```bash
   flock -n /var/lock/saas-real-pre-deploy.lock -c '
     cd /opt/saas/app
     git fetch origin
     git checkout release/real-pre
     ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/real-pre-startup-check.sh
     ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/deploy-real-pre.sh
   '
   ```
3. BREAK-GLASS 完成后必须：
   - 把所用 SHA 写入 `releases/<sha>/break-glass-YYYYMMDD.md`（含原因、影响范围、回滚点）。
   - 把 `current.json` 同步更新到 BREAK-GLASS 部署的版本。
   - 在团队频道声明 `BREAK-GLASS-END`。

**真实服务器 IP、SSH 用户名、私钥路径、SSH 公钥指纹、`.env.real-pre` 实际内容均不得在公开 Git 仓库中保留**。这些信息应放在私有 Runbook（如 Bitwarden / Confluence 私有空间 / 服务器本地 `/root/saas-private-notes/`），由运维负责人按需查阅。

历史 SSH 别名示例已从本文档移除。如需重建 SSH 访问，请使用你个人本机的 `~/.ssh/config`，不要把含本机用户名或私钥路径的示例提交回仓库。

---

## 推荐阅读顺序（首次初始化服务器时）

1. [00-服务器部署总览.md](00-服务器部署总览.md)
2. [01-服务器初始化与宝塔配置.md](01-服务器初始化与宝塔配置.md)
3. [02-Docker手动部署real-pre.md](02-Docker手动部署real-pre.md)（**仅初始化时使用，后续不再手工执行**）
4. [03-域名SSL与宝塔Nginx反向代理.md](03-域名SSL与宝塔Nginx反向代理.md)
5. [04-百应抖音授权与Token联调.md](04-百应抖音授权与Token联调.md)
6. [05-real-pre部署后验收门禁.md](05-real-pre部署后验收门禁.md)
7. [06-回滚与故障排查.md](06-回滚与故障排查.md)
8. [07-Jenkins自动化部署规划.md](07-Jenkins自动化部署规划.md)
9. [08-real-pre全过程命令清单.md](08-real-pre全过程命令清单.md)（**仅 BREAK-GLASS 参考**）
10. [08-real-pre参数开关契约.md](08-real-pre参数开关契约.md)

旧文档仍保留（仅作审计背景，不应再作为操作步骤）：

- `00-deploy-audit.md`
- `01-xshell-manual-deploy.md`
- `02-jenkins-later.md`

如命令冲突，以 `Jenkinsfile` + 本目录新文档为准。

---

## 前置条件

- 服务器已安装 Git、Docker、Docker Compose。
- 已创建 `/opt/saas/app`、`/opt/saas/env`、`/opt/saas/logs`、`/opt/saas/backups`、`/opt/saas/runtime/qa/out`。
- 已准备真实 `.env.real-pre`（**仅在服务器本地**，不要复制本机版本）。
- 未把 `.env.real-pre` 提交 Git。
- Jenkins 已经能访问该服务器（见 [Jenkinsfile](../../Jenkinsfile) 中的 SSH 与 lock 配置）。

## 验收标准

部署成功不等于正式上线。最低验收：

- Docker 服务运行（由 Jenkins 或 BREAK-GLASS 启动）。
- `/api/system/health` 返回 `UP`。
- `/healthz` 返回 `ok`。
- `APP_TEST_ENABLED=false`。
- `DOUYIN_TEST_ENABLED=false`。
- `DOUYIN_REAL_UPSTREAM_MODE=live`。
- real-pre 涉及上游的读、同步、刷新、回调和写入类开关默认开启；真实推广写入保持 `DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true` 且 `ALLOW_REAL_PROMOTION_WRITE=true`。
- 如因风控、上游冻结或只读排障临时关闭真实写入，必须记录关闭原因、影响范围和恢复计划；相关验收项只能记为 `BLOCKED` / `PENDING`，不能记为通过。
- 三组门禁已执行并归档（preflight / roles / p0）。

最终结论口径：

```text
服务器 real-pre 受控部署完成。
环境健康检查通过。
real-pre 测试开关关闭。
真实 upstream 模式开启。
真实上游读、同步、刷新、回调和写入类开关默认开启；真实推广写双开关保持开启。
E2E preflight / roles / p0 已执行。
若仍有 PENDING，原因归类为真实订单样本不足，不定义为代码硬失败。
```

---

## 常见问题

| 问题 | 处理 |
| --- | --- |
| 不知道先看哪篇 | 从 [00-服务器部署总览.md](00-服务器部署总览.md) 开始 |
| 没域名能不能部署 | 可以端口测试，但不能完整验证百应授权体验 |
| Jenkins 挂了怎么办 | 走 BREAK-GLASS（见本文"⚠️ BREAK-GLASS 紧急恢复"一节） |
| PENDING 算不算成功 | 不算 PASS，也不算代码硬失败，需要按样本不足记录 |
| 真实推广写入默认是否开启 | 开启；real-pre 是真实上游 / 生产形态环境 |
| 复制简介不含推广链接 | 先查两个真实推广写开关是否被临时关闭；若关闭，按降级原因记录并恢复后复验 |
| 怎么把代码推到服务器 | **不要推**。提 PR → Jenkins 会从 GitHub 拉取并部署 |