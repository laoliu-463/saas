# 03-Test 与 Real 网关契约

更新时间：2026-05-09（与 `docs/README.md`、`docs/04-开发进度.md` 事实口径对齐）

## 一、目的

为了实现环境的无缝切换，本项目所有与第三方（抖音、物流等）交互的逻辑均通过 Gateway 接口定义。
- `Test` 实现：用于 `test` 环境，返回本地构造的拟真数据。
- `Real` 实现：用于后期真实第三方 SDK 联调。

核心目标只有一个：
> 切换 Test / Real 时，只替换 Gateway 实现，不改 Controller、不改前端、不改主业务 Service。

## 二、统一约束

### 1. 数据模型转换
- Service 只依赖 Gateway 接口。
- Gateway 负责吸收第三方接口差异。
- Repository / Entity 不直接感知第三方 SDK 结构。

### 2. 状态码与异常处理
- 统一使用 `ApiResult` 或业务异常抛出。
- 不允许在 Service 中写 `if test` / `if real` 业务分支。
- 不允许为了适配 Real 接口去污染前端字段结构。

### 3. 数据透传规则
- 必须通过 DTO 进行转换。
- 不直接把第三方 SDK 原始对象向上透传。

## 三、当前覆盖的网关 (Gateways)

| 接口名称 | 描述 | Test 状态 | Real 状态 |
| :--- | :--- | :--- | :--- |
| `DouyinAuthGateway` | 授权与 Token 管理 | [x] 已完成 | [x] 已完成 |
| `DouyinProductGateway` | 商品同步与详情查询 | [x] 已完成 | [x] 已完成 |
| `DouyinActivityGateway` | 团长活动列表同步 | [x] 已完成 | [x] 已完成 |
| `DouyinOrderGateway` | 订单回流 (增量/Webhook) | [x] 已完成 | [x] 已完成 |
| `DouyinPromotionGateway` | 自动化转链 (Pick Source) | [x] 已完成 | [x] 已完成 |
| `LogisticsGateway` | 物流轨迹查询与发货 | [x] 已完成，已补无轨迹 / 在途 / 签收 / 问题件 / 失败模拟底座 | [~] 快递鸟即时查询 API 网关底座已接入本地代码，Sandbox / real-pre 尚未验证 |

## 四、Test 拟真化要求

Test 实现不应只是简单的静态返回，应支持：
- **随机性**：模拟不同商品的佣金率、库存状态。
- **关联性**：模拟订单中的商品 ID 必须能在商品池中找到。
- **时效性**：模拟物流单号的状态应随时间推进而变化。

## 五、当前 real-pre 与真实 Gateway 口径

为避免把真实抖店联调和现有 `test` 基线混在一起，当前项目统一执行口径如下：

- `test` 是当前系统功能、权限测试、自动化测试和 Mock 闭环的默认环境
- `real-pre` 仅用于真实 SDK / 真实上游联调
- `local-mock` 仅保留为历史口径和回滚参考，不再作为独立容器环境扩展
- 本机同一时间只允许启动一套 SAAS 环境，test 用 `docker-compose.test.yml`，real-pre 用 `docker-compose.real-pre.yml`

当前代码合并口径是：同一套 Controller / Service / 前端调用同时支持 Test 与 Real，运行时只通过 Gateway Bean 和环境配置切换，不把 Test 与 Real 的数据环境合并。当前事实如下：

| 项目 | `test` | 当前 `real-pre` |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `test` | `real-pre` |
| `APP_TEST_ENABLED` | `true` | `false` |
| `DOUYIN_TEST_ENABLED` | `true` | `false` |
| `DB_NAME` | `saas_test` | `saas_real_pre` |
| `REDIS_DATABASE` | `1` | `0` |
| 前端端口 | `3000` | `3000`（单活环境，不与 test 同时运行） |
| 后端端口 | `8080` | `8080`（单活环境，不与 test 同时运行） |
| `/api/test/**` | 可用 | 不作为 real-pre 联调入口 |
| 当前职责 | 系统功能 / 权限测试 / 自动化验证 / Mock 基线 | 真实 SDK 联调 |

固定容器命名如下，环境切换时复用同一组名字，不再同时保留 test / real-pre 双套 backend/frontend：

- `saas-frontend`
- `saas-backend`
- `saas-postgres`
- `saas-redis`

执行约束：

1. 不允许把真实 `access_token`、真实订单、真实回调写进 `test` 或 `local-mock` 基线
2. 不允许为了真实联调临时修改 `test`、`local-mock` 的 DTO、页面字段和调试台口径
3. 当前 `real-pre` 已可命中真实上游，但仍是预备联调环境，不等同生产环境
4. 所有真实联调记录统一回写到 `docs/archive/records/14-抖店SDK全量梳理与逐接口联调规划.md`
5. 后续真实 Gateway 联调环境必须关闭 Spring Boot DEBUG、`RestTemplate` DEBUG 与抖店 SDK INFO 原始报文日志，避免 `access_token`、`refresh_token`、签名或 JWT 出现在日志中
6. 若 Token 失效或重建缓存，应先暂停订单自动同步或确认 `ORDER_SYNC_ENABLED` 不会反复触发无效 Real Gateway 调用

当前已知代码缺口：

1. `RealDouyinAuthGateway.ensureToken` 已补为“从 Redis 读取已有 token / refresh_token / expire_at 的非阻塞兜底”，不再固定返回 `null`
2. `RealDouyinOrderGateway` 已完成订单主同步映射，并已将常规时间范围同步切到 `buyin.colonelMultiSettlementOrders`；旧 `buyin.instituteOrderColonel` 仅保留为 RAW 探针和历史口径对照。多结算接口时间参数必须传 `yyyy-MM-dd HH:mm:ss` 字符串，`POST /api/orders/sync` 继续通过统一 Gateway 契约落库、归因和触发寄样副作用
3. 订单归因已补齐抖店原生 `colonel_order_info` 口径：Real 网关会把 `colonel_buyin_id / activity_id` 扁平化到订单 raw payload，主业务归因优先用 `colonel_buyin_id + activity_id + product_id` 做唯一匹配，不再把 19 位 `colonel_buyin_id` 塞进 8-10 位 `short_id`
4. 当前 real-pre 历史订单仍缺“通过系统推广链接下单后产生的精确活动+商品映射样本”，因此不能把现有未归因真实订单误判为代码归因失败
5. 商品详情 / SKU 真实样本当前受 `product.detail` 权限包阻塞，不能用活动商品快照替代

补充说明（与 `docs/04` 对齐）：Webhook 本地收件箱、幂等落库与重放框架已具备；具体业务事件副作用仍依赖上游真实样本确认。

上述编号缺口（1–5）不阻塞认证、身份、活动、商品、转链等前置接口联调，但会阻塞订单主链路闭环验收。
