# 15-Real/Pre 最小联调落地方案

更新时间：2026-05-03

## 1. 文档目标

本文用于把当前项目的真实抖店接口联调，从“概念讨论”落到“可执行动作”。

但需要先明确一条当前事实：

> 截至 2026-05-03，仓库中的 `real-pre` 已经是独立端口 / 容器的浏览器回归环境，**还不是**真实 SDK 已接通的最终联调环境。

本方案坚持两个原则：

1. 不破坏现有 `test` 基线
2. 以最小成本拉起一套仅用于真实第三方接口联调的 `real/pre` 后端环境

## 2. 当前环境事实

截至当前排查结果：

| 项目 | 当前事实 |
| :--- | :--- |
| 稳定运行的联调基线 | `saas-test-backend-1` / `saas-test-postgres-1` / `saas-test-redis-1` |
| `test` 后端 profile | `test` |
| `test` 数据库 | `colonel_saas_test` |
| `test` Redis DB | `1` |
| 当前 real-pre 后端状态 | `saas-backend-real-pre-1` healthy，对外 `8081->8080` |
| 当前 real-pre PostgreSQL / Redis | `saas-postgres-real-pre-1`、`saas-redis-real-pre-1` healthy |
| 当前前端口径 | `3001` 可由 `start-real-pre.ps1` 或本机 `npm run dev -- --port 3001` 提供 |
| 当前 real-pre profile | `.env.real-pre` 实际为 `SPRING_PROFILES_ACTIVE=local-mock` |
| 当前 real-pre 三方开关 | `APP_TEST_ENABLED=true`、`DOUYIN_TEST_ENABLED=true` |
| 当前真实联调阻塞点 | Token 建立、真实订单映射、部署链路继续收口 |

结论：

- 当前 `real-pre` 独立后端已经落地
- `test` 与 `real-pre` 已形成双轨口径，但当前 `real-pre` 仍是“回归环境”而非“真实 SDK 环境”
- 下一步重点不再是“先拉起实例”，而是“决定是否把当前 real-pre 继续演进成真实 SDK pre，还是另起一套更纯粹的 real-gateway 环境”

## 3. 最小成本方案

### 3.1 方案边界

本轮只新增：

1. 一套 `real/pre` 后端实例
2. 一个独立数据库名
3. 一个独立 Redis DB 编号

本轮不强制新增：

1. 第二套前端容器
2. 第二个 PostgreSQL 容器
3. 第二个 Redis 容器

### 3.2 目标口径（未来真实 SDK pre）

| 配置项 | `test` | 未来真实 SDK pre 建议值 |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `test` | `prod` |
| `DOUYIN_TEST_ENABLED` | `true` | `false` |
| `DB_NAME` | `colonel_saas_test` | `colonel_saas_real` |
| `REDIS_DATABASE` | `1` | `2` |
| 后端端口 | `8080` | `8081` |
| `DOUYIN_TOKEN_AUTO_REFRESH_ENABLED` | `true` | `false` |
| `DOUYIN_WEBHOOK_VERIFY_SIGN` | `false` | `true` |

### 3.3 当前 real-pre 与未来 real SDK pre 的区别

| 维度 | 当前 real-pre | 未来真实 SDK pre |
| :--- | :--- | :--- |
| 主要用途 | 浏览器回归 / 权限验收 / 部署形态验证 | 真实 Token / 活动 / 商品 / 订单接口联调 |
| profile | `local-mock` | 建议 `prod` |
| `DOUYIN_TEST_ENABLED` | `true` | `false` |
| `/dev/test` 与 `/api/test/*` | 允许 | 原则上关闭 |
| 是否已落地 | 是 | 否 |

## 4. 环境变量模板

以下模板用于“未来真实 SDK pre”，不是当前仓库内 `.env.real-pre` 的实际值。

关键变量如下：

```text
SPRING_PROFILES_ACTIVE=prod
DOUYIN_TEST_ENABLED=false

DB_HOST=localhost
DB_PORT=5432
DB_NAME=colonel_saas_real
DB_USER=saas
DB_PASSWORD=...

REDIS_HOST=127.0.0.1
REDIS_PORT=6380
REDIS_PASSWORD=
REDIS_DATABASE=2

DOUYIN_BASE_URL=https://openapi-fxg.jinritemai.com
DOUYIN_APP_ID=...
DOUYIN_CLIENT_KEY=...
DOUYIN_CLIENT_SECRET=...

DOUYIN_TOKEN_AUTO_REFRESH_ENABLED=false
DOUYIN_WEBHOOK_VERIFY_SIGN=true
JWT_SECRET=...
```

## 5. 启动方式

### 5.1 数据库准备

先创建真实联调专用数据库：

```sql
CREATE DATABASE colonel_saas_real;
```

然后把当前项目所需的初始化 SQL 与增量 SQL 全部执行到该库。

最低要求：

1. `init-db.sql`
2. 当前 `backend/src/main/resources/db/` 目录下所有已启用增量脚本

### 5.2 推荐启动方式

推荐直接使用脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-real-pre.ps1
```

脚本默认行为：

1. 读取根目录 `.env.real`
2. 校验 `DOUYIN_TEST_ENABLED=false`
3. 校验 `DB_NAME != colonel_saas_test`
4. 校验 `REDIS_DATABASE != 1`
5. 启动 `8081` 后端
6. 启动 `3001` 前端
7. 将日志写入 `runtime/real-pre/logs/`

首次使用前请执行：

```powershell
Copy-Item .\.env.real.example .\.env.real
```

然后按真实联调参数填写 `.env.real`。

### 5.3 手动启动方式

首轮联调推荐直接本机起后端，不额外新建前端：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DOUYIN_TEST_ENABLED="false"
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="colonel_saas_real"
$env:DB_USER="saas"
$env:DB_PASSWORD="请替换"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6380"
$env:REDIS_DATABASE="2"
$env:DOUYIN_BASE_URL="https://openapi-fxg.jinritemai.com"
$env:DOUYIN_APP_ID="请替换"
$env:DOUYIN_CLIENT_KEY="请替换"
$env:DOUYIN_CLIENT_SECRET="请替换"
$env:DOUYIN_WEBHOOK_VERIFY_SIGN="true"
$env:DOUYIN_TOKEN_AUTO_REFRESH_ENABLED="false"
$env:JWT_SECRET="请替换"
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8081"
```

启动成功后，真实联调后端地址为：

```text
http://localhost:8081/api
```

## 6. 启动后必须先做的环境验收

在开始任何真实接口联调前，先确认以下 4 项：

1. 运行时变量包含：

```text
SPRING_PROFILES_ACTIVE=prod
DOUYIN_TEST_ENABLED=false
DB_NAME=colonel_saas_real
REDIS_DATABASE=2
```

2. 后端不是指向 `colonel_saas_test`
3. Redis 不是使用 `1` 号 DB
4. 当前地址是 `http://localhost:8081/api`，不是 `8080`

推荐核验命令：

```powershell
docker inspect <container> --format "{{range .Config.Env}}{{println .}}{{end}}"
```

或直接查看本机启动终端中的环境变量输出与启动日志。

## 7. 联调执行顺序

`real/pre` 拉起后，严格按以下顺序推进：

### 第一组：认证与权限

1. `POST /api/douyin/tokens`
2. `GET /api/douyin/tokens`
3. `POST /api/douyin/token-refreshes`
4. `buyin.institutionInfo`

目的：

- 确认当前 `app_key / app_secret / authorization_code` 是同一套
- 确认当前 token 对应的主体、角色、百应 ID

### 第二组：活动与商品

1. `GET /api/douyin/activities`
2. `GET /api/douyin/activities/{activityId}`
3. `GET /api/douyin/activities/{activityId}/products`
4. 商品详情接口文档核验

目的：

- 确认真实活动可查
- 确认活动商品字段足以进入商品库

### 第三组：转链与归因

1. `buyin.instPickSourceConvert`
2. `pick_source` 提取规则
3. `pick_source_mapping` 落库规则

目的：

- 确认渠道一键转链真实可用
- 确认 `pick_source` 可回流到订单链路

### 第四组：订单与 Webhook

1. `GET /api/douyin/order-settlements`
2. `buyin.instituteOrderColonel` 对比
3. `order.batchSensitiveDataRequest` / `order.batchSensitive`
4. `POST /api/douyin/webhooks/colonel-open-events`

目的：

- 确认真实订单回流字段
- 确认解密链路
- 确认回调链路与验签

## 8. 当前代码缺口与影响

### 8.1 不影响当前第一轮联调的项

以下问题不阻塞认证、身份、活动、商品、转链接口联调：

1. `RealDouyinProductGateway.queryProductDetail` 未实现
2. `RealDouyinProductGateway.queryProductSkus` 未实现
3. Webhook 未接业务消费

### 8.2 会阻塞后续闭环验收的项

以下问题会阻塞订单主链路闭环：

1. `RealDouyinOrderGateway` 尚未把上游订单映射到 `OrderListResult.orders`
2. `RealDouyinAuthGateway.ensureToken` 当前返回 `null`

处理策略：

- 先联调前置接口
- 拿到真实回包样本
- 再补映射与闭环逻辑

## 9. 文档回写规则

从现在开始，所有真实联调记录统一回写到：

- `docs/archive/records/14-抖店SDK全量梳理与逐接口联调规划.md`

回写格式必须包含：

1. 当前接口
2. 当前环境
3. 请求命令
4. 请求头与鉴权
5. 原始响应
6. HTTP 状态码
7. 业务判定
8. 异常问题
9. 下一步动作

## 10. 当前执行结论

本轮正式开始动作定义为：

1. `real/pre` 独立环境已经具备启动与健康检查能力
2. 当前进入 token 建立、真实接口样本采集与订单映射补全阶段
3. 后续继续收口 `3001` 前端承载方式、部署验证与全链路真实联调
