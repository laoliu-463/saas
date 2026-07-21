# 部署文档入口

## 适用场景

本文是抖音团长 SaaS `real-pre` 运行文档入口。日常发布必须遵循 [开发与 real-pre 发布流程](../development-flow.md)，由 `main` 经 PR、`release/real-pre` 和 Jenkins 发布队列完成。

## 当前仓库实际情况

当前部署以这些仓库文件为准：

- `docker-compose.real-pre.yml`
- `.env.real-pre.example`
- `scripts/deploy-real-pre.sh`
- `scripts/backup-db.sh`
- `scripts/health-check.sh`
- `scripts/real-pre-startup-check.sh`
- `scripts/rollback-real-pre.sh`
- `package.json`
- `backend/pom.xml`
- `frontend/Dockerfile`
- `frontend/nginx/default.conf.template`

核心口径：

```text
集成分支: main
real-pre 发布分支: release/real-pre
日常发布入口: Jenkins job saas-real-pre-cd
手工 SSH: 仅限经批准的 Break-glass 紧急恢复
Compose project: saas-active
后端: backend-real-pre, 127.0.0.1:8081 -> 容器 8080
前端: frontend-real-pre, 127.0.0.1:3001 -> 容器 80
后端健康检查: /api/system/health
前端健康检查: /healthz
```

## 远端访问与 Break-glass

服务器地址、登录用户、IdentityFile、公钥指纹和环境文件位置不属于公开仓库文档，统一保存在私有 Runbook 或密码管理系统中。

手工 SSH 只用于经批准的紧急恢复。执行前必须获得 real-pre 主机锁，确认目标 SHA，完成备份、启动检查、健康检查和回滚证据，并在事后通过正常发布路径补齐变更。

## 推荐阅读顺序

1. [00-服务器部署总览.md](00-服务器部署总览.md)
2. [01-服务器初始化与宝塔配置.md](01-服务器初始化与宝塔配置.md)
3. [02-Docker手动部署real-pre.md](02-Docker手动部署real-pre.md)
4. [03-域名SSL与宝塔Nginx反向代理.md](03-域名SSL与宝塔Nginx反向代理.md)
5. [04-百应抖音授权与Token联调.md](04-百应抖音授权与Token联调.md)
6. [05-real-pre部署后验收门禁.md](05-real-pre部署后验收门禁.md)
7. [06-回滚与故障排查.md](06-回滚与故障排查.md)
8. [07-Jenkins自动化部署规划.md](07-Jenkins自动化部署规划.md)
9. [08-real-pre全过程命令清单.md](08-real-pre全过程命令清单.md)
10. [08-real-pre参数开关契约.md](08-real-pre参数开关契约.md)

旧文档仍保留：

- `00-deploy-audit.md`
- `01-xshell-manual-deploy.md`
- `02-jenkins-later.md`

如命令冲突，以本目录新文档和当前仓库实际文件为准。

## 前置条件

- 服务器已安装 Git、Docker、Docker Compose。
- 已创建 `/opt/saas/app`、`/opt/saas/env`、`/opt/saas/logs`、`/opt/saas/backups`、`/opt/saas/runtime/qa/out`。
- 已准备真实 `.env.real-pre`。
- 未把 `.env.real-pre` 提交 Git。
- 不直接复用本机 `.env.real-pre`；服务器环境文件必须重新核对域名、OAuth 回调、CORS、快递100回调和真实推广写入开关。

## 首次环境初始化

首次环境初始化和 Break-glass 细节见私有 Runbook；本仓库不再提供可直接复制的常规 SSH 部署命令。日常发布只使用 Jenkins `saas-real-pre-cd`。

环境初始化只允许由具备权限的运维人员按私有 Runbook 执行；至少要核对 real-pre 开关、数据库备份路径、Compose project 和 Jenkins 所需凭证。部署后的 readiness、前端健康、业务 smoke 和 E2E 由 Jenkins 证据记录。

## 本地更新后如何发布

本地修改必须通过 PR 合并到 `main`，再创建 `main -> release/real-pre` 发布提升 PR。不要把任务分支或 Gitee 镜像作为 real-pre 发布源。

```bash
git switch -c codex/<short-task-name> main
git push origin codex/<short-task-name>
# 创建 PR 到 main；发布时再通过 PR 提升到 release/real-pre
```

Jenkins 发布队列负责 checkout、SHA 校验、迁移、部署、健康检查、业务验收和回滚证据。

## 只有服务器无域名时

走 IP + 端口测试：

```text
http://服务器IP:3001
http://服务器IP:8081/api/system/health
```

`.env.real-pre` 中临时配置：

```dotenv
CORS_ALLOWED_ORIGIN_PATTERNS=http://服务器IP:3001
DOUYIN_OAUTH_REDIRECT_URI=http://服务器IP:8081/api/douyin/oauth/callback
DOUYIN_OAUTH_FRONTEND_SUCCESS_URL=http://服务器IP:3001/system/douyin?oauth=success
DOUYIN_OAUTH_FRONTEND_FAILURE_URL=http://服务器IP:3001/system/douyin?oauth=failed
```

限制：不能完整验证 HTTPS 百应授权跳转体验。

## 有域名 SSL 时

走完整联调：

```text
https://real-pre.xxx.com
https://real-pre.xxx.com/api/system/health
https://real-pre.xxx.com/api/douyin/oauth/callback
```

`.env.real-pre` 中配置：

```dotenv
CORS_ALLOWED_ORIGIN_PATTERNS=https://real-pre.xxx.com
DOUYIN_OAUTH_REDIRECT_URI=https://real-pre.xxx.com/api/douyin/oauth/callback
DOUYIN_OAUTH_FRONTEND_SUCCESS_URL=https://real-pre.xxx.com/system/douyin?oauth=success
DOUYIN_OAUTH_FRONTEND_FAILURE_URL=https://real-pre.xxx.com/system/douyin?oauth=failed
```

宝塔 Nginx：

```text
/api/ -> http://127.0.0.1:8081/api/
/     -> http://127.0.0.1:3001/
```

实际 Nginx 配置见 [03-域名SSL与宝塔Nginx反向代理.md](03-域名SSL与宝塔Nginx反向代理.md)。

## Jenkins 自动化什么时候做

Jenkins 放在第二阶段：

1. 手动部署成功。
2. OAuth 和真实 Token 能跑通。
3. 健康检查和门禁结果能稳定归档。
4. 再让 Jenkins 调用同一套 `scripts/deploy-real-pre.sh`。

第一版 Jenkins 不强制接入 P0 E2E；第二版再逐步加入 preflight / roles / p0。

## 执行步骤

最小执行顺序：

```text
服务器初始化
-> Docker 手动部署
-> 健康检查
-> 如有域名则配置 SSL / Nginx
-> 百应 OAuth 授权
-> E2E 门禁
-> 观察真实订单
-> 输出部署验收报告
```

## 验收标准

部署成功不等于正式上线。最低验收：

- Docker 四个服务运行。
- `/api/system/health` 返回 `UP`。
- `/healthz` 返回 `ok`。
- `APP_TEST_ENABLED=false`。
- `DOUYIN_TEST_ENABLED=false`。
- `DOUYIN_REAL_UPSTREAM_MODE=live`。
- real-pre 涉及上游的读、同步、刷新、回调和写入类开关默认开启；真实推广写入保持 `DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true` 且 `ALLOW_REAL_PROMOTION_WRITE=true`。
- 如因风控、上游冻结或只读排障临时关闭真实写入，必须记录关闭原因、影响范围和恢复计划；相关验收项只能记为 `BLOCKED` / `PENDING`，不能记为通过。
- 三组门禁已执行并归档。

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

## 常见问题

| 问题 | 处理 |
| --- | --- |
| 不知道先看哪篇 | 从 `00-服务器部署总览.md` 开始 |
| 没域名能不能部署 | 可以端口测试，但不能完整验证百应授权体验 |
| Jenkins 要不要先上 | 不要，先手动部署跑通 |
| PENDING 算不算成功 | 不算 PASS，也不算代码硬失败，需要按样本不足记录 |
| 真实推广写入默认是否开启 | 开启；real-pre 是真实上游 / 生产形态环境 |
| 复制简介不含推广链接 | 先查两个真实推广写开关是否被临时关闭；若关闭，按降级原因记录并恢复后复验 |
