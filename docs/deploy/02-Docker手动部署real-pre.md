# Docker 手动部署 real-pre

> **⚠️ BREAK-GLASS ONLY — 2026-07-21**
>
> 本文用于**首次初始化一台新的 real-pre 服务器**，或 Jenkins 不可用时的人工恢复。
> **常规发布已统一由 Jenkins real-pre CD 完成**：`release/real-pre` 提升 PR → Jenkins 校验 SHA + CI Attestation + 全局锁 + 数据库备份 + 部署 + 验证。
> 请勿再按本文档做常规部署。
> 真实服务器 IP、SSH 用户名、私钥路径、`.env.real-pre` 实际内容均不得在公开仓库保留。
> 详见 [docs/deploy/README.md](./README.md) 中"⚠️ BREAK-GLASS 紧急恢复"一节。

## 适用场景

本文用于第一次在服务器上手动启动 real-pre，验证 PostgreSQL、Redis、后端、前端容器和端口连通。当前目标是受控部署验证，不是正式生产全量上线。

## 当前仓库实际情况

以当前仓库为准：

| 项目 | 值 |
| --- | --- |
| 仓库 | `https://github.com/laoliu-463/saas.git` |
| 当前部署分支 | `release/real-pre`（BREAK-GLASS 时手工 `git checkout`；常规发布由 Jenkins 自动） |
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
git checkout release/real-pre
git pull --ff-only
git rev-parse --short HEAD
```

如果目录已存在：

```bash
cd /opt/saas/app
git fetch origin
git checkout release/real-pre
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
| `DOUYIN_REAL_PROMOTION_WRITE_ENABLED` | 真实转链写操作主开关，real-pre 默认 `true` |
| `ALLOW_REAL_PROMOTION_WRITE` | 真实转链写操作二次确认开关，real-pre 默认 `true`，必须与 `DOUYIN_REAL_PROMOTION_WRITE_ENABLED` 同时为 `true` 才允许真实 `instPickSourceConvert` |
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
TALENT_PUBLIC_PAGE_CRAWL_ENABLED=false
TALENT_PROFILE_PUBLIC_WEB_ENABLED=false
TALENT_PROFILE_HTTP_ENABLED=false
EXCLUSIVE_ENABLED=false
```

这些不是“功能没上线”，而是避免 real-pre 切回测试 / Mock、公开爬虫或未决归因规则。当前代码的 `RealProdEnvironmentGuard` 会阻断其中部分危险组合。

real-pre 默认开启真实推广写入，`docker-compose.real-pre.yml` 会将 `DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true` 与 `ALLOW_REAL_PROMOTION_WRITE=true` 显式传入 backend；后端 `douyin.real.promotion-write-enabled` 与 `douyin.real.allow-promotion-write` 必须同时为 `true`，才会调用真实 `buyin.instPickSourceConvert` 并写入 `pick_source_mapping`。

商品库“复制简介”与真实转链写操作已解耦：

- 双开关开启时，“复制简介”应真实生成推广链接，接口返回 `promotionLinkGenerated=true`，并写入 `pick_source_mapping`。
- 双开关被临时关闭时，“复制基础简介”应返回 PASS，接口返回 `promotionLinkGenerated=false`、`fallbackReason=REAL_PROMOTION_WRITE_DISABLED`，复制文案不包含真实推广链接。
- 双开关被临时关闭时，真实推广链接、`pick_source` 归因和真实成交回流应标记为 `BLOCKED_BY_PROMOTION_WRITE_DISABLED`，不得记为代码失败。

IP 端口测试示例：

```dotenv
COMPOSE_PROJECT_NAME=saas-active
SPRING_PROFILES_ACTIVE=real-pre
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

## 前端改动后重建镜像并重启容器

本节是开发期高频动作：本地改完 `frontend/` 任何文件后，要把新代码推进运行中的容器里验证。**不适用于后端改动**（后端改 Java 后端需要走 `backend/target/*.jar` 重新构建，且目前 real-pre 用的是预先构建好的 jar 镜像）。

### 为什么不能只 `restart`

`real-pre` 的前端服务配置如下（`docker-compose.real-pre.yml` line 167-198）：

```yaml
frontend-real-pre:
  image: colonel-saas/frontend:${IMAGE_TAG:-real-pre}   # 镜像，不是 dev server
  build:
    context: ./frontend
    dockerfile: Dockerfile                                # 生产构建（multi-stage + nginx）
  # 没有 bind mount、没有 volumes
```

要点：

- 容器**不是 dev server**，是 nginx 跑生产构建的静态资源
- **没有** `./frontend:/app` 挂载，所以本地改文件**容器看不见**
- nginx 容器启动时只会从 `/usr/share/nginx/html` 读取构建好的产物

因此 `docker compose restart frontend-real-pre` 只能重启旧镜像，新代码**不会生效**。必须先 `docker build` 重新生成镜像，再 `up -d --force-recreate` 用新镜像重建容器。

### 适用改动

| 改动类型 | 是否走本流程 |
| --- | --- |
| `frontend/src/**/*.vue` / `*.ts` / `*.css` | ✅ 是 |
| `frontend/package.json` / `pnpm-lock.yaml` / `Dockerfile` | ✅ 是 |
| `frontend/index.html` / `vite.config.ts` | ✅ 是 |
| `docs/**/*.md` | ❌ 否（前端镜像构建不依赖 docs） |
| `backend/**` | ❌ 否（走后端 jar 重建流程，不在本节范围） |
| `docker-compose.real-pre.yml` 本身 | ⚠️ 改完只重启服务即可，无需重建前端镜像 |

### 标准步骤

#### 1. 本地构建验证（前置）

在宿主机先跑 `npm run build` 确保 TypeScript 0 错误。镜像构建会执行相同的 `pnpm build`，本地先过一遍能省 5-10 分钟。

```powershell
cd D:\Projects\SAAS\frontend
npm run build
```

输出末尾必须是 `built in X.XXs`，且 `Select-String "error TS"` 返回空。

#### 2. 重建前端镜像

```powershell
docker build -f D:\Projects\SAAS\frontend\Dockerfile -t colonel-saas/frontend:real-pre D:\Projects\SAAS\frontend
```

- `-f` 显式指定 `Dockerfile`（不是 `Dockerfile.dev`）
- `-t` 镜像名必须与 compose `image:` 字段一致（`colonel-saas/frontend:real-pre`）
- 上下文路径用绝对路径，避免 PowerShell `cd` 编码坑

构建时间大约 1-3 分钟（取决于缓存命中），末尾应看到 `naming to docker.io/colonel-saas/frontend:real-pre done`。

#### 3. 强制重建容器并启动

```powershell
docker compose -f D:\Projects\SAAS\docker-compose.real-pre.yml up -d --no-deps --force-recreate frontend-real-pre
```

关键参数：

- `--no-deps`：不重启 backend / postgres / redis，避免无谓中断
- `--force-recreate`：即使 image tag 没变也强制用新镜像重建容器
- 只指定 `frontend-real-pre` 一个服务，不动其他三个

#### 4. 健康检查

```powershell
# 等 5-8 秒让 nginx 启动
sleep 6
docker compose -f D:\Projects\SAAS\docker-compose.real-pre.yml ps
```

预期输出 `STATUS` 列显示 `Up X seconds (healthy)`，且端口 `0.0.0.0:3001->80/tcp` 已暴露。

#### 5. 验证新代码已进入容器

```powershell
# 看 nginx 服务的 dist 产物里有没有新文件
docker exec saas-active-frontend-real-pre-1 ls /usr/share/nginx/html/assets/
```

产物文件名带 hash（例如 `index-Bq6id53v.js`），新构建的 hash 必须与本地 `frontend/dist/assets/` 下的 hash 一致。`Select-String` 找新组件关键词：

```powershell
docker exec saas-active-frontend-real-pre-1 ls /usr/share/nginx/html/assets/ | Select-String "ProductActionColumn|index-"
```

#### 6. 路由可访问性

```powershell
docker exec saas-active-frontend-real-pre-1 wget -q -O /dev/null -S "http://127.0.0.1:80/product/manage"
```

预期 `HTTP/1.1 200 OK`。前端是 SPA，nginx 会 fallback 到 `index.html`。

### 常见踩坑

| 现象 | 原因 | 修法 |
| --- | --- | --- |
| `lstat /target: no such file or directory` | `docker compose up --build` 同时构建 backend，但 backend Dockerfile 找不到 `target/*.jar` | 单独 `docker build` 前端镜像，再用 `up -d --no-deps` 启动 |
| 容器 healthy 但页面是旧版本 | 只 `restart` 没 `build` | 重新走步骤 2-3 |
| `wget: bad address` 或 `Connection refused` | nginx 还没起完 | 加 `sleep 8` 再测 |
| 新组件存在但页面 404 | SPA 路由没 fallback | 确认 `nginx/default.conf.template` 含 `try_files $uri /index.html`（real-pre 镜像已含） |
| PowerShell 把 `curl`/`wget` 解析错 | PS 别名冲突 | 用 `docker exec` 内部跑 `wget`，或用 `irm http://...`（PS 7） |

### 一行命令（最常用）

如果你只改了一两个 `.vue` 文件且本地 build 已经通过，复制这一行就能完成重建 + 重启 + 健康检查：

```powershell
docker build -f D:\Projects\SAAS\frontend\Dockerfile -t colonel-saas/frontend:real-pre D:\Projects\SAAS\frontend 2>&1 | Select-Object -Last 3; docker compose -f D:\Projects\SAAS\docker-compose.real-pre.yml up -d --no-deps --force-recreate frontend-real-pre 2>&1 | Select-Object -Last 3; sleep 6; docker compose -f D:\Projects\SAAS\docker-compose.real-pre.yml ps | Select-String "frontend-real-pre"
```

期望最后一行包含 `Up X seconds (healthy)`。

### 不适用本流程的情况

- 改 `docker-compose.real-pre.yml`：只 `docker compose -f ... up -d frontend-real-pre` 即可（compose 会识别配置变更）
- 改 `frontend/nginx/*.conf`：镜像层已 COPY 进去，要重新 build
- 改 `frontend/Dockerfile`：必然重新 build
- 同时改前后端：先单独 build 后端 jar（`cd backend && mvn package`），再走本流程

## 后端改动后重新打包 jar 并重建镜像

与前端镜像不同，后端 real-pre 镜像是**纯运行时**（不构建），必须在宿主机先把 Java 代码编译成 fat jar，再让镜像 `COPY` 进去。**后端没有 hot reload**，任何 Java 改动都必须重新打包。

### 为什么不能只 `restart`

`real-pre` 后端服务配置（`docker-compose.real-pre.yml` line 70-165）：

```yaml
backend-real-pre:
  image: colonel-saas/backend:${IMAGE_TAG:-real-pre}
  build:
    context: ./backend
    dockerfile: Dockerfile     # 4 行：JDK 17 + COPY target/*.jar + ENTRYPOINT java -jar
  # 没有 bind mount、没有 volumes
```

`backend/Dockerfile` 全文：

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

要点：

- 镜像**只复制** `target/*.jar`，**不编译**
- 没有 `./backend:/app` 挂载，本地改 Java 容器看不见
- 启动时把 jar 加载进 JVM，JVM 启动慢（30-60 秒）

因此 `docker compose restart backend-real-pre` 只重启旧 jar，新代码**不会生效**。必须先 `mvn package` 出新 jar，再 `docker build` 重新打镜像，最后 `up -d --force-recreate` 重建容器。

### 适用改动

| 改动类型 | 是否走本流程 |
| --- | --- |
| `backend/src/main/java/**/*.java` | ✅ 是 |
| `backend/src/main/resources/**` | ✅ 是（application.yml、SQL 脚本、static 资源都会进 jar） |
| `backend/pom.xml`（依赖变更） | ✅ 是 |
| `backend/Dockerfile` | ✅ 是 |
| `backend/src/test/**` | ❌ 否（不走 jar 也不走镜像） |
| `docs/**/*.md` | ❌ 否（后端 jar 不打包 docs） |
| `docker-compose.real-pre.yml` 本身 | ⚠️ 改完只重启服务即可，无需重新打 jar |

### 标准步骤

#### 1. 本地编译验证（前置）

在宿主机先跑 `mvn package -DskipTests`，让本地立刻看到编译错误。镜像构建会执行相同的 `mvn package`，但镜像里没 mount host 工作区，定位错误更费劲。

```powershell
cd D:\Projects\SAAS\backend
mvn package -DskipTests
```

输出末尾必须是 `BUILD SUCCESS`，且 `target/colonel-saas.jar`（或 `target/*.jar`）已生成。如果只想编译不打包：

```powershell
mvn -DskipTests compile
```

#### 2. 确认 jar 已生成

```powershell
Get-ChildItem D:\Projects\SAAS\backend\target -Filter "*.jar" | Select-Object Name, Length
```

预期看到一个或多个 jar（看 pom 配置，可能是 `colonel-saas.jar` 或 `colonel-saas-1.0.0.jar`），size 一般在 60-150 MB。**如果为空，停下来检查 pom**：

- `maven-jar-plugin` 是否配置了 `<finalName>colonel-saas</finalName>` 或类似
- 可能是 `mvn package` 没真正跑（maven 缓存了"已编译"状态），加 `-U` 强制更新
- 可能是 `<skip>true</skip>` 被设了

#### 3. 重建后端镜像

```powershell
docker build -f D:\Projects\SAAS\backend\Dockerfile -t colonel-saas/backend:real-pre D:\Projects\SAAS\backend
```

- `-f` 显式指定 `Dockerfile`（**不是** `Dockerfile.test`，那个是 test 模式用的，target 目录结构不同）
- `-t` 镜像名必须与 compose `image:` 字段一致（`colonel-saas/backend:real-pre`）
- 上下文路径用绝对路径

构建时间 30 秒-1 分钟（jar COPY 进去 + JRE 拉取层可能命中缓存）。末尾应看到 `naming to docker.io/colonel-saas/backend:real-pre done`。

#### 4. 强制重建容器并启动

```powershell
docker compose -f D:\Projects\SAAS\docker-compose.real-pre.yml up -d --no-deps --force-recreate backend-real-pre
```

关键参数：

- `--no-deps`：不重启 postgres / redis（数据库有数据，**不能轻易重启**）
- `--force-recreate`：用新镜像强制重建容器
- 只指定 `backend-real-pre` 一个服务

启动会比前端慢，**JVM 启动 + Spring 上下文初始化约 30-60 秒**，加上 healthcheck 的 120s `start_period`，**总等待约 2-3 分钟**才能 healthy。

#### 5. 健康检查

```powershell
# 等 JVM 启动 + Spring 上下文初始化
sleep 90
docker compose -f D:\Projects\SAAS\docker-compose.real-pre.yml ps | Select-String "backend-real-pre"
```

预期 `STATUS` 列显示 `Up X minutes (healthy)`，且端口 `0.0.0.0:8081->8080/tcp` 暴露。

#### 6. 验证新 jar 已进入容器

```powershell
# 容器内 jar 路径
docker exec saas-active-backend-real-pre-1 ls -la /app/app.jar
# 容器内 jar 的 mtime 应与本地 jar 的 mtime 接近（几小时内）
docker exec saas-active-backend-real-pre-1 stat -c '%y' /app/app.jar
```

也可以直接看容器内 jar 的 SHA-256 是否和本地一致：

```powershell
# 本地
Get-FileHash D:\Projects\SAAS\backend\target\*.jar -Algorithm SHA256
# 容器内（无 Get-FileHash 替代，用 sha256sum）
docker exec saas-active-backend-real-pre-1 sha256sum /app/app.jar
```

两者 hash 一致才能确认新 jar 已生效。

#### 7. 接口可访问性

```powershell
docker exec saas-active-backend-real-pre-1 wget -qO- "http://127.0.0.1:8080/api/system/health"
```

预期看到 `{"status":"UP",...}`。这是容器内直连，不用走宿主机端口。

如果要在宿主机本地测：

```powershell
# PowerShell 7+
irm http://localhost:8081/api/system/health
# 或 docker exec 出容器
docker exec saas-active-backend-real-pre-1 wget -qO- "http://127.0.0.1:8080/api/system/health" 2>&1
```

### 数据库迁移注意

如果改动涉及 Flyway 迁移脚本（`backend/src/main/resources/db/migration/V*.sql` 或 `db/create-*.sql`），**仅重新打 jar 不够**：

1. 镜像构建 COPY resources 时新 SQL 已经进 jar，OK
2. 但**已存在的 real-pre 数据库**不会因为新 jar 自动跑迁移
3. Spring Boot 启动时会根据 `spring.flyway.enabled` 决定：
   - `enabled=true`（默认）：启动时自动比对 `flyway_schema_history` 与 jar 里的迁移版本号，按需执行
   - `enabled=false`：完全不跑，需要手动 SQL
4. 迁移报错会让容器 healthcheck 失败，需要看 `docker logs saas-active-backend-real-pre-1 | grep -i flyway`

如果改了 **destructive migration**（删列、删表），建议先备份：

```powershell
docker exec saas-active-postgres-real-pre-1 pg_dump -U <DB_USER> <DB_NAME> > backup-before-migration.sql
```

### 常见踩坑

| 现象 | 原因 | 修法 |
| --- | --- | --- |
| `lstat /target: no such file or directory` | `target/*.jar` 不存在 | 先跑 `mvn package -DskipTests` |
| `target/colonel-saas.jar (No such file or directory)` | pom 改了 finalName 但没重新打包 | `mvn clean package -DskipTests` |
| 容器一直 `starting` 不 healthy | Spring 启动慢 + Flyway 迁移阻塞 | 查 `docker logs saas-active-backend-real-pre-1 --tail 200` |
| 启动报 `FlywayValidateException: Migration checksum mismatch` | 数据库迁移记录与 jar 内 SQL hash 不一致 | 查文档 `docs/deploy/06-回滚与故障排查.md`（如存在） |
| 接口返回 500 `BeanCreationException` | 配置缺失 / 循环依赖 | 看后端日志 stack trace，不要只看 health check |
| `connection refused` 访问宿主机端口 | 后端还在启动 | `sleep 90` 再试，**JVM 启动比前端慢一个数量级** |
| 数据库连不上 | env 变量被覆盖 | 看 compose 注释 line 88-90：JWT_SECRET / CORS 必须放 env_file，不能放 `environment` 用 `${}` 解析 |
| `OutOfMemoryError` | 容器内存限制 2G 不足 | 检查 `docker stats saas-active-backend-real-pre-1`，或临时调 `JAVA_OPTS` 的 `-XX:MaxRAMPercentage` |

### 一行命令（最常用）

如果只改了一两个 Java 文件且本地 `mvn package` 已经通过：

```powershell
# 1) 重新打包 jar
Set-Location D:\Projects\SAAS\backend; mvn package -DskipTests 2>&1 | Select-String "BUILD SUCCESS|BUILD FAILURE"; Set-Location D:\Projects\SAAS; `
# 2) 重建镜像 + recreate 容器
docker build -f D:\Projects\SAAS\backend\Dockerfile -t colonel-saas/backend:real-pre D:\Projects\SAAS\backend 2>&1 | Select-Object -Last 3; docker compose -f D:\Projects\SAAS\docker-compose.real-pre.yml up -d --no-deps --force-recreate backend-real-pre 2>&1 | Select-Object -Last 3; `
# 3) 等 JVM 起来 + 健康检查
sleep 90; docker compose -f D:\Projects\SAAS\docker-compose.real-pre.yml ps | Select-String "backend-real-pre"
```

期望最后一行包含 `Up X minutes (healthy)`。

### 不适用本流程的情况

- 改 `docker-compose.real-pre.yml`：只 `docker compose up -d backend-real-pre` 即可（compose 识别配置变更，**不会**重新打 jar）
- 改 env 文件 `/opt/saas/env/.env.real-pre`：只 `docker compose up -d backend-real-pre` 即可（env 重新注入）
- 改 `backend/Dockerfile.test`：test 模式用的，不影响 real-pre
- 同时改前后端：**先**走本流程把后端 jar + 镜像搞定，**再**走前端章节。顺序不能反，因为前端构建是独立的，前端没有依赖后端 jar

### 与前端的对比

| 步骤 | 前端 | 后端 |
| --- | --- | --- |
| 本地构建 | `npm run build` | `mvn package -DskipTests` |
| 构建产物 | `dist/` 静态资源 | `target/*.jar` |
| Dockerfile 角色 | multi-stage 构建（npm install + vite build + nginx 拷产物） | 4 行只 COPY jar |
| 镜像 tag | `colonel-saas/frontend:real-pre` | `colonel-saas/backend:real-pre` |
| 容器启动延迟 | 1-3 秒（nginx 启动） | 30-60 秒（JVM 启动 + Spring 上下文） |
| 健康检查延迟 | 30s interval | 30s interval + 120s start_period |
| 数据库依赖 | 无 | postgres + redis（不能轻易 restart） |
| 是否需要等迁移 | 否 | 是（如果改了 SQL） |

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
