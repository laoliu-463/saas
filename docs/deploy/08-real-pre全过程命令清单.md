# real-pre 全过程命令清单

> **⚠️ BREAK-GLASS ONLY — 2026-07-21**
>
> 本文用于**首次初始化一台新的 real-pre 服务器**，或 Jenkins 不可用时的人工恢复。
> **常规发布已统一由 Jenkins real-pre CD 完成**，请勿按本文档做常规部署。
>
> 真实服务器 IP、SSH 用户名（`root@…`）、本机 `C:\Users\…\.ssh\` 私钥路径、SSH 公钥指纹均不得在公开仓库保留。
> 详见 [docs/deploy/README.md](./README.md) 中"⚠️ BREAK-GLASS 紧急恢复"一节。
>
> Jenkins 与 BREAK-GLASS 流程调用的是同一份 `scripts/deploy-real-pre.sh`；命令口径以该脚本与 `Jenkinsfile` 为准。

## 适用场景

本文把 real-pre 服务器受控部署过程中需要执行的命令按顺序集中到一处，便于第一次手动部署、端口测试、域名 SSL 联调、百应 / 抖音授权、E2E 门禁、真实订单观察、回滚和后续 Jenkins 自动化接入。

当前目标仍是 `real-pre` 受控部署验证，不是正式生产全量上线。`PENDING` 只能表示真实样本不足或外部条件未满足，不能写成 `P0 PASS`。

## 当前仓库实际情况

以当前仓库文件为准：

| 项目 | 当前值 |
| --- | --- |
| 仓库 | GitHub 主仓：`https://github.com/laoliu-463/saas.git`（已不再以 Gitee 为优先同步源） |
| 部署分支 | `release/real-pre`（由 Jenkins CD 自动部署；BREAK-GLASS 时手工 `git checkout` 同名分支） |
| Compose 文件 | `docker-compose.real-pre.yml` |
| Compose project | `saas-active` |
| 服务器 env | `/opt/saas/env/.env.real-pre` |
| 后端服务 | `backend-real-pre` |
| 前端服务 | `frontend-real-pre` |
| PostgreSQL 服务 | `postgres-real-pre` |
| Redis 服务 | `redis-real-pre` |
| 后端端口 | `127.0.0.1:8081 -> 容器 8080` |
| 前端端口 | `127.0.0.1:3001 -> 容器 80` |
| 后端健康检查 | `http://127.0.0.1:8081/api/system/health` |
| 前端健康检查 | `http://127.0.0.1:3001/healthz` |
| 部署脚本 | `scripts/deploy-real-pre.sh` |
| 备份脚本 | `scripts/backup-db.sh` |
| 健康检查脚本 | `scripts/health-check.sh` |
| 回滚脚本 | `scripts/rollback-real-pre.sh` |

## 前置条件

- 可以 SSH 登录服务器。
- 服务器可以执行 `git`、`docker`、`docker compose`。
- 已拿到真实抖音 / 抖店配置，但不要写入 Git、文档或截图。
- 如果要完整验证 OAuth 授权跳转，已准备域名和 HTTPS。
- 如果只有服务器 IP，可以先做端口测试，但不能完整验证百应授权跳转体验。

## 执行步骤

### 0. 本地最终检查

在本地项目根目录执行，只做仓库静态检查，不使用真实密钥：

```bash
git status --short
git diff --check
bash -n scripts/deploy-real-pre.sh scripts/backup-db.sh scripts/health-check.sh scripts/run-real-pre-db-migrations.sh scripts/rollback-real-pre.sh
docker compose --env-file .env.real-pre.example -f docker-compose.real-pre.yml config --quiet
```

如需要在本地先跑构建和测试：

```bash
cd backend
mvn clean test

cd ../frontend
npm install
npm run test
npm run build
```

根目录 E2E 命令只在服务已经启动后执行：

```bash
npm ci
npx playwright install chromium
npm run e2e:real-pre:p0:preflight
npm run e2e:real-pre:roles
npm run e2e:real-pre:p0
```

### 1. SSH 登录服务器（BREAK-GLASS）

如果本机已配置 SSH 别名（**别名只在本机 `~/.ssh/config` 内有效**，不得提交到仓库）：

```bash
ssh <你的本机别名>
```

未配置别名时：

```bash
ssh <服务器SSH用户名>@<服务器IP>
```

真实服务器 IP、SSH 用户名、本机私钥路径、SSH 公钥指纹均不在公开仓库保留。

如果不使用 `root`，用实际部署用户登录：

```bash
ssh 部署用户@服务器IP
```

### 2. 创建服务器目录

```bash
sudo mkdir -p /opt/saas/app
sudo mkdir -p /opt/saas/env
sudo mkdir -p /opt/saas/logs
sudo mkdir -p /opt/saas/backups
sudo mkdir -p /opt/saas/runtime/qa/out
sudo chown -R "$USER":"$USER" /opt/saas
```

检查目录：

```bash
ls -la /opt/saas
```

### 3. 检查服务器基础组件

```bash
git --version
docker --version
docker compose version
```

检查 Docker 服务：

```bash
systemctl status docker
```

启动 Docker：

```bash
sudo systemctl enable --now docker
```

Ubuntu / Debian 安装提示：

```bash
sudo apt-get update
sudo apt-get install -y git ca-certificates curl gnupg
```

CentOS / Rocky 安装提示：

```bash
sudo dnf install -y git ca-certificates curl
```

Docker 安装以服务器发行版和云厂商镜像源为准；安装后必须重新执行：

```bash
docker --version
docker compose version
```

### 4. 拉取代码（BREAK-GLASS）

首次部署（BREAK-GLASS 初始化）：

```bash
cd /opt/saas
git clone -o origin -b release/real-pre https://github.com/laoliu-463/saas.git app
cd /opt/saas/app
git pull --ff-only
git rev-parse --short HEAD
```

目录已存在时（BREAK-GLASS 同步到 `release/real-pre` HEAD）：

```bash
cd /opt/saas/app
git remote get-url origin >/dev/null 2>&1 || git remote add origin https://github.com/laoliu-463/saas.git
git fetch origin
git checkout release/real-pre
git pull --ff-only
git rev-parse --short HEAD
```

> BREAK-GLASS 不要在本机 `D:/Projects/SAAS` 推任何"开发分支" — 所有常规发布流程请走 PR + Jenkins CD。

查看最近提交，记录可回滚版本：

```bash
git log --oneline -5
```

### 5. 创建 real-pre 环境文件

```bash
cd /opt/saas/app
cp .env.real-pre.example /opt/saas/env/.env.real-pre
chmod 600 /opt/saas/env/.env.real-pre
vi /opt/saas/env/.env.real-pre
```

必须人工替换但不能写入文档或 Git 的值：

```text
DB_PASSWORD
ADMIN_PASSWORD
REDIS_PASSWORD
JWT_SECRET
DOUYIN_APP_ID
DOUYIN_CLIENT_KEY
DOUYIN_CLIENT_SECRET
CORS_ALLOWED_ORIGIN_PATTERNS
DOUYIN_OAUTH_REDIRECT_URI
DOUYIN_OAUTH_FRONTEND_SUCCESS_URL
DOUYIN_OAUTH_FRONTEND_FAILURE_URL
```

必须保持的 real-pre 开关：

```dotenv
COMPOSE_PROJECT_NAME=saas-active
SPRING_PROFILES_ACTIVE=real-pre
DB_NAME=saas_real_pre
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_UPSTREAM_MODE=live
DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true
ALLOW_REAL_PROMOTION_WRITE=true
ORDER_SYNC_ENABLED=true
TALENT_COLLECT_MODE=api
TALENT_COLLECT_API_ENABLED=true
TALENT_REFRESH_ENABLED=true
TALENT_PUBLIC_PAGE_CRAWL_ENABLED=false
LOGISTICS_PROVIDER=kuaidi100
LOGISTICS_KD100_ENABLED=true
LOGISTICS_KD100_SUBSCRIBE_ENABLED=true
LOGISTICS_SYNC_ENABLED=true
EXCLUSIVE_ENABLED=false
```

real-pre 默认开启真实 `instPickSourceConvert` 与 `pick_source` 写入，两个真实推广写开关必须同时为 `true`。若因风控、上游冻结或只读排障临时关闭，需在验收报告中记录关闭原因、影响范围、恢复时间和责任人。只改其中一个开关时，后端仍会拒绝真实转链。

如果只做 IP + 端口测试，临时配置示例：

```dotenv
CORS_ALLOWED_ORIGIN_PATTERNS=http://服务器IP:3001
DOUYIN_OAUTH_REDIRECT_URI=http://服务器IP:8081/api/douyin/oauth/callback
DOUYIN_OAUTH_FRONTEND_SUCCESS_URL=http://服务器IP:3001/system/douyin?oauth=success
DOUYIN_OAUTH_FRONTEND_FAILURE_URL=http://服务器IP:3001/system/douyin?oauth=failed
```

如果做域名 + SSL 完整联调，配置示例：

```dotenv
CORS_ALLOWED_ORIGIN_PATTERNS=https://real-pre.xxx.com
DOUYIN_OAUTH_REDIRECT_URI=https://real-pre.xxx.com/api/douyin/oauth/callback
DOUYIN_OAUTH_FRONTEND_SUCCESS_URL=https://real-pre.xxx.com/system/douyin?oauth=success
DOUYIN_OAUTH_FRONTEND_FAILURE_URL=https://real-pre.xxx.com/system/douyin?oauth=failed
```

确认 `.env.real-pre` 不会进 Git：

```bash
cd /opt/saas/app
git status --short
git check-ignore -q .env.real-pre && echo "local .env.real-pre is ignored"
```

### 6. 部署前环境守卫检查

```bash
grep -E '^(COMPOSE_PROJECT_NAME|SPRING_PROFILES_ACTIVE|DB_NAME|APP_TEST_ENABLED|DOUYIN_TEST_ENABLED|DOUYIN_REAL_UPSTREAM_MODE|DOUYIN_REAL_PROMOTION_WRITE_ENABLED|ALLOW_REAL_PROMOTION_WRITE|ORDER_SYNC_ENABLED|TALENT_COLLECT_MODE|TALENT_COLLECT_API_ENABLED|TALENT_REFRESH_ENABLED|TALENT_PUBLIC_PAGE_CRAWL_ENABLED|LOGISTICS_PROVIDER|LOGISTICS_KD100_ENABLED|LOGISTICS_KD100_SUBSCRIBE_ENABLED|LOGISTICS_SYNC_ENABLED|EXCLUSIVE_ENABLED)=' /opt/saas/env/.env.real-pre
```

期望看到：

```text
COMPOSE_PROJECT_NAME=saas-active
SPRING_PROFILES_ACTIVE=real-pre
DB_NAME=saas_real_pre
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_UPSTREAM_MODE=live
DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true
ALLOW_REAL_PROMOTION_WRITE=true
ORDER_SYNC_ENABLED=true
TALENT_COLLECT_MODE=api
TALENT_COLLECT_API_ENABLED=true
TALENT_REFRESH_ENABLED=true
TALENT_PUBLIC_PAGE_CRAWL_ENABLED=false
LOGISTICS_PROVIDER=kuaidi100
LOGISTICS_KD100_ENABLED=true
LOGISTICS_KD100_SUBSCRIBE_ENABLED=true
LOGISTICS_SYNC_ENABLED=true
EXCLUSIVE_ENABLED=false
```

检查是否仍有占位值：

```bash
grep -nE 'MUST_CHANGE|YOUR_DOMAIN|PLACEHOLDER' /opt/saas/env/.env.real-pre || true
```

检查快递 100 物流授权配置：

```bash
grep -E '^(LOGISTICS_KD100_ENABLED|LOGISTICS_KD100_SUBSCRIBE_ENABLED|LOGISTICS_KD100_CUSTOMER|LOGISTICS_KD100_CALLBACK_URL)=' /opt/saas/env/.env.real-pre
```

不要在终端截图里展示真实 `LOGISTICS_KD100_KEY`、`LOGISTICS_KD100_CALLBACK_SALT`。

### 7. Compose 配置渲染

```bash
cd /opt/saas/app
export REAL_PRE_ENV_FILE=/opt/saas/env/.env.real-pre
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  -f docker-compose.real-pre.yml \
  config >/opt/saas/runtime/qa/out/docker-compose.real-pre.rendered.yml
```

只验证配置是否可渲染：

```bash
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  -f docker-compose.real-pre.yml \
  config --quiet
```

### 8. 执行受控部署脚本

推荐使用脚本部署，它会执行环境守卫、备份、迁移、构建、健康检查和证据留存：

```bash
cd /opt/saas/app
chmod +x scripts/deploy-real-pre.sh scripts/backup-db.sh scripts/health-check.sh scripts/run-real-pre-db-migrations.sh scripts/rollback-real-pre.sh

APP_DIR=/opt/saas/app \
ENV_FILE=/opt/saas/env/.env.real-pre \
COMPOSE_FILE=docker-compose.real-pre.yml \
PROJECT_NAME=saas-active \
BACKUP_DIR=/opt/saas/backups \
EVIDENCE_ROOT=/opt/saas/runtime/qa/out/deploy-real-pre-$(date +%Y%m%d-%H%M%S) \
  ./scripts/deploy-real-pre.sh
```

如果需要直接用 Docker Compose 手动启动：

```bash
cd /opt/saas/app
export REAL_PRE_ENV_FILE=/opt/saas/env/.env.real-pre
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  -f docker-compose.real-pre.yml \
  up -d --build
```

### 9. 查看容器状态

```bash
cd /opt/saas/app
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  -f docker-compose.real-pre.yml \
  ps
```

查看宿主机端口：

```bash
ss -lntp | grep -E '3001|8081|80|443' || true
```

查看 Docker 运行状态：

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
docker stats --no-stream
```

### 10. 健康检查

使用脚本：

```bash
cd /opt/saas/app
ENV_FILE=/opt/saas/env/.env.real-pre \
COMPOSE_FILE=docker-compose.real-pre.yml \
COMPOSE_PROJECT_NAME=saas-active \
  ./scripts/health-check.sh
```

直接检查：

```bash
curl -fsS http://127.0.0.1:8081/api/system/health
curl -fsS http://127.0.0.1:3001/healthz
```

期望后端返回：

```json
{"status":"UP"}
```

期望前端返回：

```text
ok
```

### 11. 查看日志

```bash
cd /opt/saas/app
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml logs --tail=300 backend-real-pre
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml logs --tail=300 frontend-real-pre
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml logs --tail=300 postgres-real-pre
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml logs --tail=300 redis-real-pre
```

持续观察后端日志：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml logs -f --tail=300 backend-real-pre
```

重点看：

```text
profile 是否 real-pre
APP_TEST_ENABLED 是否 false
DOUYIN_TEST_ENABLED 是否 false
DOUYIN_REAL_UPSTREAM_MODE 是否 live
订单同步任务是否启动
抖音 API 调用是否成功
OAuth callback / TokenCreateResponse 相关日志
```

### 12. IP + 端口访问验证

只做端口测试时，在浏览器访问：

```text
http://服务器IP:3001
http://服务器IP:8081/api/system/health
```

命令验证：

```bash
curl -I http://服务器IP:3001
curl -fsS http://服务器IP:8081/api/system/health
```

端口测试完成后，如果已经配置 Nginx 反代，应关闭公网 `3001/8081`，只保留本机访问。

### 13. 域名解析与 HTTPS 验证

域名解析在域名服务商控制台配置：

```text
类型：A
主机记录：real-pre
记录值：服务器公网 IP
```

本地或服务器验证 DNS：

```bash
nslookup real-pre.xxx.com
ping -c 3 real-pre.xxx.com
```

申请 SSL 后验证：

```bash
curl -I http://real-pre.xxx.com
curl -I https://real-pre.xxx.com
curl -fsS https://real-pre.xxx.com/api/system/health
```

期望：

```text
HTTP 自动跳 HTTPS
HTTPS 前端返回 200 或 302
/api/system/health 返回 UP
```

### 14. 宝塔 Nginx 反向代理验证

宝塔站点反代目标：

```text
/api/ -> http://127.0.0.1:8081/api/
/     -> http://127.0.0.1:3001/
```

如果需要在服务器命令行检查 Nginx：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

宝塔常见日志路径按实际站点为准，可先查：

```bash
ls -la /www/wwwlogs | grep real-pre || true
```

查看站点错误日志：

```bash
tail -n 200 /www/wwwlogs/real-pre.xxx.com.error.log
```

反代成功后再次验证：

```bash
curl -I https://real-pre.xxx.com
curl -fsS https://real-pre.xxx.com/api/system/health
```

### 15. 百应 / 抖音 OAuth 授权验证

百应后台配置示例：

```text
去使用地址：https://real-pre.xxx.com/system/douyin
OAuth 回调地址：https://real-pre.xxx.com/api/douyin/oauth/callback
```

后端 callback 路径连通性验证：

```bash
curl -I https://real-pre.xxx.com/api/douyin/oauth/callback
```

没有 `code` 和 `state` 时返回失败或跳转失败页是正常的；这里主要确认不是 Nginx `404/502`。

授权过程中观察后端日志：

```bash
cd /opt/saas/app
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml logs -f --tail=300 backend-real-pre | grep -Ei "oauth|TokenCreateResponse|Douyin|token"
```

授权成功后，浏览器最终应跳到：

```text
https://real-pre.xxx.com/system/douyin?oauth=success
```

### 16. 数据库检查

进入 PostgreSQL：

```bash
cd /opt/saas/app
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre
```

用单条命令检查表：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "\\dt"
```

检查配置 seed：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "select count(*) from system_configs;"
```

检查订单同步：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "select count(*) from colonelsettlement_order;"
```

最近订单：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "select order_id, product_id, activity_id, pick_source, channel_user_id, pay_time, settle_time from colonelsettlement_order order by created_at desc limit 20;"
```

归因映射：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "select * from pick_source_mapping order by created_at desc limit 20;"
```

寄样自动完成：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "select id, status, channel_user_id, talent_uid, product_id, homework_time from sample_request order by created_at desc limit 20;"
```

业绩记录：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "select * from performance_record order by created_at desc limit 20;"
```

如果表名和当前数据库不一致，先查表：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "\\dt *order*"
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "\\dt *sample*"
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "\\dt *performance*"
```

### 17. 部署后 E2E 门禁

在能访问服务器的本地项目根目录执行：

```bash
npm ci
npx playwright install chromium
```

IP + 端口模式：

```bash
E2E_BASE_URL=http://服务器IP:3001 E2E_BACKEND_URL=http://服务器IP:8081 npm run e2e:real-pre:p0:preflight
E2E_BASE_URL=http://服务器IP:3001 E2E_BACKEND_URL=http://服务器IP:8081 npm run e2e:real-pre:roles
E2E_BASE_URL=http://服务器IP:3001 E2E_BACKEND_URL=http://服务器IP:8081 npm run e2e:real-pre:p0
```

域名 HTTPS 模式：

```bash
E2E_BASE_URL=https://real-pre.xxx.com E2E_BACKEND_URL=https://real-pre.xxx.com npm run e2e:real-pre:p0:preflight
E2E_BASE_URL=https://real-pre.xxx.com E2E_BACKEND_URL=https://real-pre.xxx.com npm run e2e:real-pre:roles
E2E_BASE_URL=https://real-pre.xxx.com E2E_BACKEND_URL=https://real-pre.xxx.com npm run e2e:real-pre:p0
```

结果判定：

```text
preflight FAIL：配置或部署失败，必须阻断。
roles FAIL：权限链路失败，必须阻断。
p0 FAIL：核心链路硬失败，必须阻断。
p0 PENDING：真实订单样本不足，不等于代码硬失败。
p0 PASS：具备进一步验收条件，但仍不是正式全量上线。
```

### 18. 真实订单回流观察

订单同步不是启动后立刻必然有样本。部署后按 5-10 分钟观察一轮：

```bash
date
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml logs --tail=500 backend-real-pre | grep -Ei "order|settlement|colonel|pick_source|buyin"
```

重复检查订单数量：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "select count(*) from colonelsettlement_order;"
```

如果没有真实订单，结论只能写：

```text
real-pre 环境部署成功，P0 因真实样本不足保持 PENDING。
```

不能写：

```text
real-pre P0 完全通过。
```

### 19. 备份

部署前或关键变更前执行：

```bash
cd /opt/saas/app
ENV_FILE=/opt/saas/env/.env.real-pre BACKUP_DIR=/opt/saas/backups ./scripts/backup-db.sh
```

查看备份：

```bash
ls -lh /opt/saas/backups | tail
```

手动备份：

```bash
cd /opt/saas/app
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre pg_dump -U saas -d saas_real_pre -F c > /opt/saas/backups/saas_real_pre-$(date +%Y%m%d-%H%M%S).dump
```

### 20. 重启和重建

重启后端：

```bash
cd /opt/saas/app
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml restart backend-real-pre
```

重启前端：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml restart frontend-real-pre
```

重建并启动：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml up -d --build
```

停止应用容器但保留数据卷：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml stop backend-real-pre frontend-real-pre
```

禁止无确认执行：

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml down -v
```

### 21. 回滚

查看可回滚提交：

```bash
cd /opt/saas/app
git log --oneline -10
```

使用脚本回滚：

```bash
cd /opt/saas/app
ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/rollback-real-pre.sh 上一个稳定commit
```

使用环境变量回滚：

```bash
cd /opt/saas/app
ROLLBACK_REF=上一个稳定commit ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/rollback-real-pre.sh
```

手动应用回滚：

```bash
cd /opt/saas/app
git remote get-url origin >/dev/null 2>&1 || git remote add origin https://github.com/laoliu-463/saas.git
git fetch origin
git checkout 上一个稳定commit
ENV_FILE=/opt/saas/env/.env.real-pre PROJECT_NAME=saas-active ./scripts/deploy-real-pre.sh
```

回滚后验活：

```bash
curl -fsS http://127.0.0.1:8081/api/system/health
curl -fsS http://127.0.0.1:3001/healthz
```

### 22. Jenkins 第二阶段命令

> 截至 2026-07-21，Jenkins 已**正式接管** real-pre CD，不再属于"第二阶段"。本节保留作为历史背景；现状以仓库根 [Jenkinsfile](../../Jenkinsfile) 为准。

Jenkins 不参与第一次手动部署。手动部署稳定后，先在服务器验证脚本可用：

```bash
cd /opt/saas/app
bash -n scripts/deploy-real-pre.sh scripts/health-check.sh scripts/backup-db.sh scripts/rollback-real-pre.sh
ENV_FILE=/opt/saas/env/.env.real-pre PROJECT_NAME=saas-active ./scripts/health-check.sh
```

Jenkins 默认参数（实际以 `Jenkinsfile` 为准；以下为历史参考）：

```text
DEPLOY_BRANCH=release/real-pre
RUN_REAL_PRE_E2E=false
```

Jenkins 节点需要能执行：

```bash
git --version
mvn -version
node --version
npm --version
docker --version
docker compose version
```

Jenkins 第一版不强制跑 P0。如果人工打开 `RUN_REAL_PRE_E2E=true`，流水线会执行：

```bash
npm ci
npx playwright install chromium
E2E_BASE_URL=http://127.0.0.1:3001 E2E_BACKEND_URL=http://127.0.0.1:8081 npm run e2e:real-pre:p0:preflight
E2E_BASE_URL=http://127.0.0.1:3001 E2E_BACKEND_URL=http://127.0.0.1:8081 npm run e2e:real-pre:roles
E2E_BASE_URL=http://127.0.0.1:3001 E2E_BACKEND_URL=http://127.0.0.1:8081 npm run e2e:real-pre:p0
```

### 23. 证据归档

查看部署脚本证据目录：

```bash
ls -lah /opt/saas/runtime/qa/out
find /opt/saas/runtime/qa/out -maxdepth 2 -type f | tail -50
```

打包某次证据：

```bash
cd /opt/saas/runtime/qa/out
tar -czf real-pre-evidence-$(date +%Y%m%d-%H%M%S).tar.gz deploy-real-pre-目录名
```

查看关键证据：

```bash
cat /opt/saas/runtime/qa/out/deploy-real-pre-目录名/commit-before.txt
cat /opt/saas/runtime/qa/out/deploy-real-pre-目录名/commit-after.txt
cat /opt/saas/runtime/qa/out/deploy-real-pre-目录名/backend-health.json
cat /opt/saas/runtime/qa/out/deploy-real-pre-目录名/frontend-health.txt
```

## 验收标准

- 本地静态检查通过。
- 服务器目录、Git、Docker、Compose 准备完成。
- `.env.real-pre` 已创建且未提交 Git。
- `docker compose config --quiet` 通过。
- `scripts/deploy-real-pre.sh` 执行完成。
- `docker compose ps` 中 PostgreSQL、Redis、后端、前端运行正常。
- `http://127.0.0.1:8081/api/system/health` 返回 `UP`。
- `http://127.0.0.1:3001/healthz` 返回 `ok`。
- 如果有域名，`https://real-pre.xxx.com/api/system/health` 返回 `UP`。
- 百应 OAuth 能回调到后端，并最终跳回 success 或失败页。
- E2E `preflight / roles / p0` 已执行并按 `PASS / FAIL / PENDING` 分类。
- 真实订单、`pick_source` 归因、寄样自动完成、业绩双轨金额只能在真实样本出现后升级结论。

## 常见问题

### 1. 端口占用

```bash
ss -lntp | grep -E '3001|8081'
```

判断：已占用时停止冲突进程，或修改 `/opt/saas/env/.env.real-pre` 中的 `BACKEND_HOST_PORT` / `FRONTEND_HOST_PORT`。

### 2. Compose 渲染失败

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml config
```

判断：多数是 env 缺值、占位符未替换或 YAML 渲染失败。

### 3. 后端不健康

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml logs --tail=300 backend-real-pre
```

判断：重点查 DB、Redis、JWT、抖音配置、profile。

### 4. 前端白屏

```bash
curl -fsS http://127.0.0.1:3001/healthz
```

判断：前端容器健康但页面失败时，再查浏览器 Console、Network 和 `/api` 请求。

### 5. Nginx 502

```bash
curl -v https://real-pre.xxx.com/api/system/health
```

判断：多数是反代端口、路径或后端健康状态错误。

### 6. OAuth 失败

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml logs -f --tail=300 backend-real-pre | grep -Ei "oauth|token"
```

判断：查 callback 地址、code 是否过期、`client_key` / `client_secret`、权限包、店铺授权和 Nginx `/api/` 转发。

### 7. 没有真实订单

```bash
docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre psql -U saas -d saas_real_pre -c "select count(*) from colonelsettlement_order;"
```

判断：没有真实订单样本时只能标记 `PENDING`，不能写成 `P0 PASS`。

### 8. 回滚失败

```bash
git status --short
```

判断：`scripts/rollback-real-pre.sh` 要求 tracked worktree 干净；先处理未提交变更，再回滚。
