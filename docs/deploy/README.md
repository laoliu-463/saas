# 部署文档入口

## 适用场景

本文是抖音团长 SaaS `real-pre` 服务器受控部署的入口。当前目标是先完成 Docker 手动部署和真实联调，再沉淀脚本，最后接 Jenkins 自动化。

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
当前开发分支: feature/auth-system
当前优先同步仓库: https://gitee.com/cao-jianing463/saas.git
Compose project: saas-active
后端: backend-real-pre, 127.0.0.1:8081 -> 容器 8080
前端: frontend-real-pre, 127.0.0.1:3001 -> 容器 80
后端健康检查: /api/system/health
前端健康检查: /healthz
```

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

## 新手第一次部署步骤

完整过程命令见 [08-real-pre全过程命令清单.md](08-real-pre全过程命令清单.md)。第一次只做最小部署时按下面命令执行：

```bash
sudo mkdir -p /opt/saas/app /opt/saas/env /opt/saas/logs /opt/saas/backups /opt/saas/runtime/qa/out
sudo chown -R "$USER":"$USER" /opt/saas

cd /opt/saas
git clone -o gitee -b feature/auth-system https://gitee.com/cao-jianing463/saas.git app
cd /opt/saas/app
git pull --ff-only

cp .env.real-pre.example /opt/saas/env/.env.real-pre
chmod 600 /opt/saas/env/.env.real-pre
vi /opt/saas/env/.env.real-pre
ln -sfn /opt/saas/env/.env.real-pre /opt/saas/app/.env.real-pre

# 部署前自检：验证 .env.real-pre 开关基线（参考 docs/deploy/08-real-pre参数开关契约.md）
ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/real-pre-startup-check.sh

ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/deploy-real-pre.sh
```

部署后检查：

```bash
curl -fsS http://127.0.0.1:8081/api/system/health
curl -fsS http://127.0.0.1:3001/healthz
```

## 本地更新后如何同步到服务器

本地修改必须先提交并推送，服务器再拉取。未提交的工作区改动不会被服务器获取。

本地执行：

```bash
cd D:/Projects/SAAS
git status
git add <本次修改文件>
git commit -m "描述本次修改"
git push gitee feature/auth-system
```

服务器执行：

```bash
ssh saas
cd /opt/saas/app
git remote get-url gitee >/dev/null 2>&1 || git remote add gitee https://gitee.com/cao-jianing463/saas.git
git fetch gitee
git pull --ff-only
ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/real-pre-startup-check.sh
ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/deploy-real-pre.sh
```

如服务器没有 `saas` SSH 别名，则使用实际登录方式，例如 `ssh root@服务器IP`。

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
- real-pre 受控部署默认关闭真实推广写入：`DOUYIN_REAL_PROMOTION_WRITE_ENABLED=false` 且 `ALLOW_REAL_PROMOTION_WRITE=false`。
- 当验收目标包含商品库复制简介携带推广链接、真实转链或 `pick_source` 归因时，必须人工批准并同时设置 `DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true` 与 `ALLOW_REAL_PROMOTION_WRITE=true`；验收结束后按窗口结论恢复。
- 三组门禁已执行并归档。

最终结论口径：

```text
服务器 real-pre 受控部署完成。
环境健康检查通过。
real-pre 测试开关关闭。
真实 upstream 模式开启。
真实推广写开关保持关闭；如本轮验收包含商品库复制简介携带推广链接、真实转链或 pick_source 归因，再进入单独人工批准的写窗口，同时开启两个开关并记录批准证据。
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
| 能不能直接开真实推广写入 | 不行，必须人工确认后再改 |
| 复制简介不含推广链接 | 如本轮要验收真实转链，人工批准后同时开启 `DOUYIN_REAL_PROMOTION_WRITE_ENABLED` 与 `ALLOW_REAL_PROMOTION_WRITE`，重启 backend 后复验 |
