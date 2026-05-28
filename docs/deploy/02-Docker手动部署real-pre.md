# Docker 手动部署 real-pre

## 适用场景

本文用于第一次在服务器上手动启动 real-pre，验证 PostgreSQL、Redis、后端、前端容器和端口连通。当前目标是受控部署验证，不是正式生产全量上线。

## 当前仓库实际情况

以当前仓库为准：

| 项目 | 值 |
| --- | --- |
| 仓库 | `https://github.com/laoliu-463/saas.git` |
| 当前部署分支 | `feature/auth-system` |
| Compose 文件 | `docker-compose.real-pre.yml` |
| Compose project | `saas-active` |
| env 示例 | `.env.real-pre.example` |
| 服务器 env | `/opt/saas/env/.env.real-pre` |
| 后端健康检查 | `http://127.0.0.1:8081/api/system/health` |
| 前端健康检查 | `http://127.0.0.1:3001/healthz` |

## 前置条件

- 已完成 [01-服务器初始化与宝塔配置.md](01-服务器初始化与宝塔配置.md)。
- 服务器能拉取 GitHub 仓库，或已经上传代码包。
- 已准备真实抖音 / 抖店配置，但不要把真实密钥写入仓库。
- 当前部署用户可以执行 `docker compose`。

## 执行步骤

### 1. 拉取代码

```bash
cd /opt/saas
git clone https://github.com/laoliu-463/saas.git app
cd /opt/saas/app
git checkout feature/auth-system
git pull --ff-only
git rev-parse --short HEAD
```

如果目录已存在：

```bash
cd /opt/saas/app
git fetch origin
git checkout feature/auth-system
git pull --ff-only
```

### 2. 创建 real-pre 环境文件

```bash
mkdir -p /opt/saas/env
cp /opt/saas/app/.env.real-pre.example /opt/saas/env/.env.real-pre
chmod 600 /opt/saas/env/.env.real-pre
vi /opt/saas/env/.env.real-pre
```

### 3. 必须人工替换的配置项

不要在文档、终端截图或 Git 中暴露真实值。

| 配置项 | 要求 |
| --- | --- |
| `COMPOSE_PROJECT_NAME` | 固定 `saas-active` |
| `DB_PASSWORD` | 替换为真实强密码 |
| `ADMIN_PASSWORD` | 替换为初始管理员强密码，仅首次初始化 volume 生效 |
| `REDIS_PASSWORD` | 替换为真实强密码 |
| `JWT_SECRET` | 至少 32 位随机字符串 |
| `DOUYIN_APP_ID` | 真实抖音应用值 |
| `DOUYIN_CLIENT_KEY` | 真实抖音应用值 |
| `DOUYIN_CLIENT_SECRET` | 真实抖音应用值 |
| `DOUYIN_BASE_URL` | 当前示例为 `https://openapi-fxg.jinritemai.com` |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | IP 模式填 `http://服务器IP:3001`；域名模式填 `https://real-pre.xxx.com` |
| `DOUYIN_OAUTH_REDIRECT_URI` | IP 模式填 `http://服务器IP:8081/api/douyin/oauth/callback`；域名模式填 HTTPS |
| `DOUYIN_OAUTH_FRONTEND_SUCCESS_URL` | IP 或 HTTPS 前端 success 地址 |
| `DOUYIN_OAUTH_FRONTEND_FAILURE_URL` | IP 或 HTTPS 前端 failed 地址 |
| `APP_TEST_ENABLED` | 必须 `false`，表示关闭应用侧 mock/test 模式 |
| `DOUYIN_TEST_ENABLED` | 必须 `false`，表示关闭抖音 / 抖店 mock gateway |
| `DOUYIN_REAL_UPSTREAM_MODE` | 必须 `live`，real-pre 真实联调不可使用 contract/mock upstream |
| `ORDER_SYNC_ENABLED` | 必须 `true`，否则真实订单回流与后续归因无输入 |
| `DOUYIN_REAL_PROMOTION_WRITE_ENABLED` | 真实转链写操作主开关，受控部署默认 `false` |
| `ALLOW_REAL_PROMOTION_WRITE` | 真实转链写操作二次确认开关，必须与 `DOUYIN_REAL_PROMOTION_WRITE_ENABLED` 同时为 `true` 才允许真实 `instPickSourceConvert` |
| `TALENT_COLLECT_MODE` | real-pre 守卫要求 `api`，不能用 `crawler` 或 `api_then_crawler` |
| `TALENT_COLLECT_API_ENABLED` | 上线部署为 `true` |
| `TALENT_PUBLIC_PAGE_CRAWL_ENABLED` | 必须 `false`，real-pre 守卫禁止公开页爬虫 |
| `LOGISTICS_PROVIDER` | 上线部署使用 `kuaidi100` |
| `LOGISTICS_KD100_ENABLED` | 上线部署为 `true`，并必须填写真实 customer/key |
| `LOGISTICS_KD100_SUBSCRIBE_ENABLED` | 上线部署为 `true`，并必须填写 callback URL/salt |
| `LOGISTICS_SYNC_ENABLED` | 上线部署为 `true` |
| `EXCLUSIVE_ENABLED` | 当前 real-pre 守卫要求 `false`；改为 `true` 会改变归因优先级并导致启动失败 |

受控部署必须保持关闭的开关：

```dotenv
APP_TEST_ENABLED=false
APP_TEST_SEED_ON_STARTUP=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_PROMOTION_WRITE_ENABLED=false
ALLOW_REAL_PROMOTION_WRITE=false
TALENT_PUBLIC_PAGE_CRAWL_ENABLED=false
TALENT_PROFILE_PUBLIC_WEB_ENABLED=false
TALENT_PROFILE_HTTP_ENABLED=false
EXCLUSIVE_ENABLED=false
```

这些不是“功能没上线”，而是避免 real-pre 切回测试 / Mock、公开爬虫或未决归因规则。当前代码的 `RealProdEnvironmentGuard` 会阻断其中部分危险组合。

真实推广写窗口开启后，`docker-compose.real-pre.yml` 会将 `DOUYIN_REAL_PROMOTION_WRITE_ENABLED` 与 `ALLOW_REAL_PROMOTION_WRITE` 显式传入 backend；后端 `douyin.real.promotion-write-enabled` 与 `douyin.real.allow-promotion-write` 必须同时为 `true`，否则不会调用真实 `buyin.instPickSourceConvert`，也不会写入 `pick_source_mapping`。

商品库“复制简介”与真实转链写操作已解耦：

- 双开关关闭时，“复制基础简介”应返回 PASS，接口返回 `promotionLinkGenerated=false`、`fallbackReason=REAL_PROMOTION_WRITE_DISABLED`，复制文案不包含真实推广链接。
- 双开关关闭时，真实推广链接、`pick_source` 归因和真实成交回流应标记为 `BLOCKED_BY_PROMOTION_WRITE_DISABLED`，不得记为代码失败。
- 当验收目标包含真实推广链接、`pick_source` 归因或真实成交回流时，必须进入人工批准写窗口，并同时设置 `DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true` 与 `ALLOW_REAL_PROMOTION_WRITE=true`。

IP 端口测试示例：

```dotenv
COMPOSE_PROJECT_NAME=saas-active
SPRING_PROFILES_ACTIVE=real-pre
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_UPSTREAM_MODE=live
DOUYIN_REAL_PROMOTION_WRITE_ENABLED=false
ALLOW_REAL_PROMOTION_WRITE=false
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
DB_NAME=saas_real_pre
BACKEND_HOST_PORT=8081
FRONTEND_HOST_PORT=3001
CORS_ALLOWED_ORIGIN_PATTERNS=http://服务器IP:3001
DOUYIN_OAUTH_REDIRECT_URI=http://服务器IP:8081/api/douyin/oauth/callback
DOUYIN_OAUTH_FRONTEND_SUCCESS_URL=http://服务器IP:3001/system/douyin?oauth=success
DOUYIN_OAUTH_FRONTEND_FAILURE_URL=http://服务器IP:3001/system/douyin?oauth=failed
```

### 4. Compose 配置检查

```bash
cd /opt/saas/app
export REAL_PRE_ENV_FILE=/opt/saas/env/.env.real-pre
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  --project-name saas-active \
  -f docker-compose.real-pre.yml \
  config >/tmp/saas-real-pre-compose.yml
```

### 5. 手动启动

优先使用仓库脚本，它会留证据：

```bash
cd /opt/saas/app
chmod +x scripts/deploy-real-pre.sh scripts/backup-db.sh scripts/health-check.sh scripts/run-real-pre-db-migrations.sh scripts/real-pre-startup-check.sh

# 部署前自检：验证 .env.real-pre 开关基线
ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/real-pre-startup-check.sh

# 基线通过后执行部署
ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/deploy-real-pre.sh
```

如果需要直接执行 Compose：

```bash
cd /opt/saas/app
export REAL_PRE_ENV_FILE=/opt/saas/env/.env.real-pre
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  --project-name saas-active \
  -f docker-compose.real-pre.yml \
  up -d --build
```

### 6. 查看状态和日志

```bash
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  --project-name saas-active \
  -f docker-compose.real-pre.yml \
  ps

docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  --project-name saas-active \
  -f docker-compose.real-pre.yml \
  logs --tail=300 backend-real-pre
```

### 7. 健康检查

```bash
curl -fsS http://127.0.0.1:8081/api/system/health
curl -fsS http://127.0.0.1:3001/healthz
```

期望：

```json
{"status":"UP"}
```

前端期望返回：

```text
ok
```

### 8. IP + 端口测试访问

```text
前端：http://服务器IP:3001
后端：http://服务器IP:8081/api/system/health
```

如果已经配置域名和 Nginx，外部只访问：

```text
https://real-pre.xxx.com
https://real-pre.xxx.com/api/system/health
```

## 验收标准

- `docker compose ps` 中四个服务均为 running 或 healthy。
- 后端 `/api/system/health` 返回 `UP`。
- 前端 `/healthz` 返回 `ok`。
- 后端日志能看到 real-pre profile 和真实 upstream 配置。
- `.env.real-pre` 未进入 Git：`git check-ignore -q .env.real-pre` 返回成功。

## 常见问题

| 问题 | 排查命令 | 判断方法 |
| --- | --- | --- |
| 端口占用 | `ss -lntp | grep -E '3001|8081'` | 已有进程占用时先停止或改 env 端口 |
| Docker 未启动 | `systemctl status docker` | Docker inactive 时启动服务 |
| env 缺变量 | `docker compose ... config` | config 阶段会暴露缺失变量或占位符 |
| 数据库密码不一致 | `docker compose logs postgres-real-pre` | 已有 volume 不会因 env 改密码自动更新 |
| Redis 密码不一致 | `docker compose logs redis-real-pre` 和后端日志 | 后端 Redis AUTH 失败 |
| 后端连不上 PostgreSQL | `docker compose logs backend-real-pre` | 检查 `DB_HOST=postgres-real-pre`、数据库健康状态 |
| 后端连不上 Redis | `docker compose logs backend-real-pre` | 检查 `REDIS_HOST=redis-real-pre`、密码是否一致 |
| 前端 API 地址错误 | 浏览器 Network / Nginx 日志 | 当前前端生产容器走相对路径 `/api` |
| CORS 错误 | 浏览器 Console | `CORS_ALLOWED_ORIGIN_PATTERNS` 未包含当前访问源 |
