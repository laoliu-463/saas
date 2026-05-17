# 18-Real-Pre 真实 SDK 联调记录

> 历史说明（2026-05-15 补记）
>
> 本文为 real-pre API 联调归档。当前项目口径已移除“订单手机号解密”功能，因此文中关于解密入口、权限包、密文字段来源与成功样本的描述，仅保留为历史取证，不再构成当前待办。

> 2026-05-06 补充：联盟自研应用获取 AccessToken 的执行口径已确认应使用 `grant_type=authorization_code`，并携带达人或机构授权返回的 `code`。本文较早阶段围绕 `authorization_self` 的试探性记录仅保留为排查历史，不再作为当前联调指引。

> 2026-05-07 复核：real-pre 容器实际已关闭 `DOUYIN_TEST_ENABLED` / `APP_TEST_ENABLED`，Redis DB 0 中已有真实 `access_token / refresh_token / expire_at` 缓存；`POST /api/douyin/token-refreshes` 已返回 HTTP 200 / `code=200`。本文中 2026-05-02 关于 `refresh_token` 缺失的阻塞结论仅作为历史状态保留。

> 2026-05-07 15:14 复核：`GET /api/douyin/institution-info` 已返回 HTTP 200 / 上游 `code=10000`，当前 token 对应机构 / 团长身份可读取，活动列表接口前置门槛已通过。

> 2026-05-07 15:17 复核：`GET /api/douyin/activities` 已返回 HTTP 200 / 上游 `code=10000`，首批 `activity_list` 20 条，已拿到可用于后续活动详情和活动商品接口的真实 `activity_id=3916506`。

> 2026-05-07 16:48 复核：`GET /api/colonel/activities` 已返回 HTTP 200 / `code=200`，业务结构 `data.total=21 / data.activityList[0].activityId=3916506` 与原始活动接口总数一致；同时发现活动页仍依赖旧字段名，已在业务返回层补齐 `activityStatus / startTime / endTime` 兼容字段。

> 2026-05-07 16:56 复核：`GET /api/douyin/activities/3916506` 已返回 HTTP 200 / 上游 `code=10000`，详情样本已确认包含 `activity_desc / apply_start_time / apply_end_time / commission_rate / service_rate / institution_id / colonel_buyin_id` 等字段；证据文件已对加密态 `wechat_id / phone_num` 做脱敏。

> 2026-05-07 17:00 复核：`GET /api/douyin/activity-product-list?activityId=3916506&count=20` 已返回 HTTP 200 / 上游 `code=10000`，真实商品数组位于 `remoteResponse.data.data`，首批 20 条并带 `next_cursor`；同批对照 `GET /api/colonel/activities/3916506/products?count=20` 仅返回本地快照 10 条，说明业务接口当前优先快照视图，尚未与实时上游样本对齐。

> 2026-05-07 19:36 复核：`GET /api/douyin/order-settlements` 已返回 HTTP 200 / 统一响应 `code=200`，上游 `buyin.colonelMultiSettlementOrders` 成功结构的真实数据键为 `data.cursor`、`data.orders`；当前 7 天与 30 天窗口样本均为空数组。同时已确认越过 `t-90d` 的时间窗会返回 `40004 / isv.parameter-invalid:1036`。

> 2026-05-07 19:44 复核：`POST /api/orders/phone-decryptions` 已命中真实上游错误分支；使用本地 Mock 种子订单 `MOCK_SEED_TALENT_D_ORDER` 返回 `40003`，错误信息为“授权主体不匹配”。说明解密入口与真实网关链路可达，但继续验证需要当前真实授权主体名下的订单号或可接受的 `cipher_infos` 样本。

> 2026-05-07 19:49 复核：通过 raw probe 进一步确认，旧接口 `order.batchSensitiveDataRequest` 在真实上游已返回 `70000 / isp.api-service-off / API不存在或API已下线`；新接口 `order.batchSensitive` 可达，但当前以 Mock 订单号充当 `cipher_infos` 仍返回 `40003` 授权主体不匹配。

> 2026-05-07 补核：官方文档页 `https://op.jinritemai.com/docs/api-docs/15/982` 已由外部资料补全明确契约：正式方法名为 `/order/batchDecrypt`（`method=order.batchDecrypt`），需店铺授权、物流商授权与敏感数据解密权限包，`cipher_infos` 必须为 `[{auth_id,cipher_text}]`。因此 `14 订单解密` 的剩余阻塞仍是“缺少当前授权主体名下真实订单及其密文字段样本”，不是“缺文档”。

> 2026-05-07 20:31 复核：`POST /api/douyin/order-sync-probes/raw` 已确认 `buyin.instituteOrderColonel` 真实入参时间口径：`start_time / end_time` 必须传 `yyyy-MM-dd HH:mm:ss` 字符串；秒级、毫秒级时间戳均返回 `40004 / isv.parameter-invalid:1034 / 无效开始时间`。成功响应结构为 `data.cursor / data.orders`。

> 2026-05-07 20:34 复核：`POST /api/orders/sync` 已完成 real-pre 30 分钟窗口真实同步，结果为 `totalFetched=10 / created=10 / updated=0 / attributed=0 / unattributed=10 / failed=0`；证据目录为 `runtime/qa/out/orders-sync-real-20260507-203422`。本轮真实订单未回 `pick_source`，因此全部进入未归因分支。

> 2026-05-07 20:34 复核：围绕订单详情与店铺订单列表的 raw probe 已确认当前应用缺少订单管理接口权限包：`order.orderDetail` 与 `order.searchList` 均返回 `30001 / isv.app-permissions-insufficient / 应用无权限调用该接口，请先申请接口权限包`。团长订单主接口可同步订单，但当前返回中没有收件人姓名 / 手机 / 地址密文字段；这部分解密样本要求现仅保留为历史背景。

> 2026-05-07 20:56 复核：真实订单展示 smoke 显示 `/api/orders` 已可读到同步后的订单总量 `11`、首条真实订单 `6952647330859784065`；`/api/dashboard/metrics?timeField=createTime` 可统计今日 `10` 单；`timeField=settleTime` 与 `/api/data/orders` 默认口径暂未覆盖这批未结算真实单，说明 M1.6 数据看板真实化需要明确 create_time / settle_time 的页面默认口径。

> 更新时间：2026-05-03 10:00
> 环境：real-pre (port 8081)
> 分支：feature/auth-system
> Context Path: `/api`（所有接口需加 `/api` 前缀）

> 2026-05-03 事实复核补充：`backend-real-pre`、`postgres-real-pre`、`redis-real-pre` 当前仍 healthy；`3001` 前端可由本机 Node 进程承载页面回归，不再默认要求 `saas-frontend-real-pre-1` 常驻。

## 联调原则

- 按 superpowers 规范，模块一小步一小步测试对接
- 每成功一部分记录到本文档
- 不破坏现有 test 基线

---

## Phase 1: Auth Token 联调

### 环境准备 ✅

| 项目 | 状态 |
| :--- | :--- |
| `.env.real` DOUYIN_REAL_UPSTREAM_MODE | `real` (已从 contract 切换) |
| postgres-real-pre (5433) | healthy |
| redis-real-pre (6380) | healthy |
| backend-real-pre (8081) | healthy |
| JWT 登录 | 正常，admin/admin123 |

### 2026-05-02 环境修正记录 ✅

- 本地 `real-pre` 后端最初误连本机 `6379`，触发 `NOAUTH Authentication required`
- 已确认容器版 `redis-real-pre` 映射端口为 `6380`
- `.env.real` 已切换为：
  - `REDIS_HOST=127.0.0.1`
  - `REDIS_PORT=6380`
  - `REDIS_DATABASE=2`
- 新增运行时探针 `GET /api/ops/redis-probe`
- 探针结果：`host=127.0.0.1, port=6380, database=2, ping=PONG`
- 结论：Redis 环境阻塞已解除，后续接口失败原因重新收敛到 Token 未建立

### 接口 03: Token 状态查询 ✅

- **Endpoint**: `GET /api/douyin/tokens`
- **请求**:
```bash
curl -X GET http://localhost:8081/api/douyin/tokens \
  -H "Authorization: Bearer <JWT>"
```
- **响应 (Redis 空)**: 200, `hasAccessToken=false, hasRefreshToken=false`
- **响应 (Redis 有缓存)**: 200, `hasAccessToken=true, maskedAccessToken="test...edis", tokenExpiringSoon=false`
- **结论**: Token 缓存读写管道完整，Redis 序列化/反序列化正常

### 接口 01: Token 初始化 (接口联通，鉴权口径待平台确认) ⚠️

- **Endpoint**: `POST /api/douyin/tokens`
- **请求**:
```bash
curl -X POST http://localhost:8081/api/douyin/tokens \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"app_id":"7623665273727387199","grant_type":"authorization_self"}'
```
- **响应**: `460 - 抖店接口错误[50002]: 非自用型应用code不能为空`
- **结论（旧）**: 文档初始判断为工具型应用，需要 `authorization_code` + OAuth code

### 接口 01A: TokenCreate SDK 裸调探针 ✅

- **Endpoint**: `POST /api/douyin/token-create-probes`
- **请求**:
```bash
curl -X POST http://localhost:8081/api/douyin/token-create-probes \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"appId":"7623665273727387199","grantType":"authorization_self","authId":"7351155267604218149","authSubjectType":"Colonel"}'
```
- **响应摘要**:
```json
{
  "endpoint": "token.create",
  "requestSnapshot": {
    "grantType": "authorization_self",
    "codeState": "absent",
    "authIdPresent": true,
    "authSubjectType": "Colonel"
  },
  "response": {
    "code": "50002",
    "subMsg": "非自用型应用code不能为空 ..."
  }
}
```
- **结论（新）**:
  - 后台已核实应用 `星链达客` 为**自用型**
  - 但业务接口与 SDK 裸调都稳定返回 `50002`
  - 当前更像是平台侧 `token.create` 对该应用的实际校验口径仍要求 `code`
  - 在平台确认前，仍无法建立 `access_token / refresh_token`

### 接口 01A-补充: authorization_self 两种主体口径复测 ✅

- **shopId 口径请求**:
```bash
curl -X POST http://localhost:8081/api/douyin/token-create-probes \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"appId":"7623665273727387199","grantType":"authorization_self","shopId":"56591058"}'
```
- **shopId 口径响应摘要**:
  - `code=50002`
  - `subCode=isv.business-failed:4`
  - `subMsg=非自用型应用code不能为空`

- **authId 口径请求**:
```bash
curl -X POST http://localhost:8081/api/douyin/token-create-probes \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"appId":"7623665273727387199","grantType":"authorization_self","authId":"7351155267604218149","authSubjectType":"Colonel"}'
```
- **authId 口径响应摘要**:
  - `code=50002`
  - `subCode=isv.business-failed:4`
  - `subMsg=非自用型应用code不能为空`

- **补充结论**:
  - `authorization_self` 在当前应用下，`shopId` 与 `authId` 两种主体传法都未改变结果
  - 因此当前阻塞点已基本可排除“单一字段名填错/漏填”的方向
  - 平台侧对该应用类型的实际校验口径异常判断，仍是更高概率原因

### 接口 02: Token 刷新 ✅

- **Endpoint**: `POST /api/douyin/token-refreshes?appId=<masked>`
- **执行时间**: 2026-05-07 15:08:41 +08:00
- **环境**: real-pre 后端 `8081`，Redis `6380 / DB 0`，`DOUYIN_TEST_ENABLED=false`，`APP_TEST_ENABLED=false`，`ORDER_SYNC_ENABLED=false`
- **前置状态**:
  - `GET /api/douyin/tokens` 返回 HTTP 200 / `code=200`
  - `hasAccessToken=true`
  - `hasRefreshToken=true`
  - `tokenExpiringSoon=false`
  - `reauthorizeRequired=false`
- **刷新响应**:
  - HTTP 200
  - 统一响应 `code=200`
  - `msg=操作成功`
  - 刷新后仍为 `hasAccessToken=true / hasRefreshToken=true`
- **Redis 复核**:
  - 存在 `douyin:token:<appId>`
  - 存在 `douyin:refresh:<appId>`
  - 存在 `douyin:token:expire_at:<appId>`
- **证据文件**:
  - `runtime/qa/out/douyin-token-refresh-20260507-150839/token-refresh-summary.json`
  - `runtime/qa/out/douyin-token-refresh-20260507-150839/token-refresh-hash-check.json`
- **边界说明**:
  - 后端日志出现 `Douyin token refreshed successfully`
  - 二次哈希核对显示刷新前后 access token 摘要未变化、过期时间未前移；当前结论只确认刷新链路和 refresh token 可用，不额外宣称上游已轮换新 token

### 接口 03 后续依赖链路现状（2026-05-02 历史）⚠️

- `GET /api/douyin/tokens`
  - 当前响应：200，`hasAccessToken=false, hasRefreshToken=false`
- `GET /api/douyin/institution-info`
  - 当前响应：200，业务失败信息 `missing refresh_token, cannot refresh token`
- `GET /api/douyin/activities`
  - 当前响应：200，业务失败信息 `missing refresh_token, cannot refresh token`
- `GET /api/douyin/order-settlements`
  - 当前响应：200，业务失败信息 `missing refresh_token, cannot refresh token`
- **结论**:
  - Redis 阻塞已解除
  - 需要 token 的真实业务接口当前统一阻塞在 `refresh_token` 缺失

### 接口 03 后续依赖链路现状（2026-05-07 复核）⚠️

- `GET /api/douyin/tokens`
  - 当前响应：200，`hasAccessToken=true, hasRefreshToken=true, tokenExpiringSoon=false, reauthorizeRequired=false`
- `POST /api/douyin/token-refreshes`
  - 当前响应：200，`code=200`
- **结论**:
  - `refresh_token` 缺失阻塞已经解除
  - 下一步不再继续停在 Token 状态查询，应先执行 `GET /api/douyin/institution-info` 确认授权主体，再进入活动列表真实样本采集

### 接口 03A: 授权主体确认 ✅

- **Endpoint**: `GET /api/douyin/institution-info?appId=<masked>`
- **执行时间**: 2026-05-07 15:14:23 +08:00
- **环境**: real-pre 后端 `8081`，Redis `6380 / DB 0`，`DOUYIN_TEST_ENABLED=false`，`APP_TEST_ENABLED=false`，`ORDER_SYNC_ENABLED=false`
- **前置状态**:
  - `GET /api/douyin/tokens` 返回 HTTP 200 / `code=200`
  - `hasAccessToken=true`
  - `hasRefreshToken=true`
  - `tokenExpiringSoon=false`
  - `reauthorizeRequired=false`
- **响应摘要**:
  - 项目接口 HTTP 200
  - 统一响应 `code=200`
  - 业务状态 `status=success`
  - 上游 `code=10000`
  - 上游 `msg=success`
  - `remoteResponse.data` 包含 `institution_id`、`colonel.buyin_id/name`、`mcn.buyin_id/name`
- **证据文件**:
  - `runtime/qa/out/douyin-institution-info-20260507-151422/institution-info-summary.json`
  - `runtime/qa/out/douyin-institution-info-20260507-151422/institution-info-sanitized-response.json`
- **结论**:
  - 当前 token 对应的授权主体可以读取机构 / 团长身份信息
  - 活动列表真实联调的前置门槛已通过

### 接口 04: 活动列表联调接口 ✅

- **Endpoint**: `GET /api/douyin/activities?appId=<masked>`
- **执行时间**: 2026-05-07 15:17:29 +08:00
- **环境**: real-pre 后端 `8081`，Redis `6380 / DB 0`，`DOUYIN_TEST_ENABLED=false`，`APP_TEST_ENABLED=false`，`ORDER_SYNC_ENABLED=false`
- **前置状态**:
  - `GET /api/douyin/tokens` 返回 HTTP 200 / `code=200`
  - `hasAccessToken=true`
  - `hasRefreshToken=true`
  - `reauthorizeRequired=false`
  - `GET /api/douyin/institution-info` 返回上游 `code=10000`
- **响应摘要**:
  - 项目接口 HTTP 200
  - 统一响应 `code=200`
  - 业务状态 `status=success`
  - 上游 `code=10000`
  - 上游 `msg=success`
  - `remoteResponse.data.total=21`
  - 首批 `activity_list` 为 20 条
  - 首条样本字段包含 `activity_id / status / activity_name / activity_start_time / activity_end_time / application_start_time / application_end_time`
  - 后续样本活动 ID：`3916506`
- **证据文件**:
  - `runtime/qa/out/douyin-activities-20260507-151728/activities-summary.json`
  - `runtime/qa/out/douyin-activities-20260507-151728/activities-sanitized-response.json`
- **结论**:
  - 活动列表联调入口已拿到真实上游成功样本
  - 下一步应执行 `GET /api/colonel/activities`，校验业务接口标准化字段和页面可用性

### 接口 05: 活动列表业务接口 ✅

- **Endpoint**: `GET /api/colonel/activities?status=0&searchType=0&sortType=1&page=1&pageSize=20&appId=<masked>`
- **执行时间**: 2026-05-07 16:48:32 +08:00
- **环境**: real-pre 后端 `8081`，Redis `6380 / DB 0`，`DOUYIN_TEST_ENABLED=false`，`APP_TEST_ENABLED=false`，`ORDER_SYNC_ENABLED=false`
- **前置状态**:
  - `GET /api/douyin/tokens` 返回 HTTP 200 / `code=200`
  - `GET /api/douyin/institution-info` 返回上游 `code=10000`
  - `GET /api/douyin/activities` 返回 `remoteResponse.data.total=21`
- **响应摘要**:
  - 项目接口 HTTP 200
  - 统一响应 `code=200`
  - `msg=操作成功`
  - `data.total=21`
  - `data.activityList` 首批 20 条
  - 首条样本字段包含 `activityId / activityName / activityStartTime / activityEndTime / status / statusText / applicationStartTime / applicationEndTime`
  - 首条样本活动 ID：`3916506`
- **证据文件**:
  - `runtime/qa/out/colonel-activities-20260507-164832/colonel-activities-summary.json`
  - `runtime/qa/out/colonel-activities-20260507-164832/colonel-activities-sanitized-response.json`
- **兼容收口**:
  - 联调中发现 `frontend/src/views/product/ActivityList.vue` 仍读取旧字段 `activityStatus / startTime / endTime`
  - 当前已在 `DouyinColonelActivityGateway.ActivityItem#toMap()` 补齐兼容字段，不改变现有页面调用方式
- **结论**:
  - 活动列表业务接口已拿到真实业务样本，且与原始接口总数对齐
  - 当前活动页所需状态列与起止时间列不再因字段名错位出现空白
  - 下一步进入 `GET /api/douyin/activities/{activityId}` 采样活动详情

### 接口 06: 活动详情 ✅

- **Endpoint**: `GET /api/douyin/activities/3916506`
- **执行时间**: 2026-05-07 16:56:49 +08:00
- **环境**: real-pre 后端 `8081`，Redis `6380 / DB 0`，`DOUYIN_TEST_ENABLED=false`，`APP_TEST_ENABLED=false`，`ORDER_SYNC_ENABLED=false`
- **前置状态**:
  - `GET /api/douyin/tokens` 返回 HTTP 200 / `code=200`
  - `GET /api/douyin/institution-info` 返回上游 `code=10000`
  - `GET /api/douyin/activities` 已拿到真实活动 ID `3916506`
- **响应摘要**:
  - 项目接口 HTTP 200
  - 统一响应 `code=200`
  - 业务状态 `status=success`
  - 上游 `code=10000`
  - 上游 `msg=success`
  - 详情样本字段包含 `activity_id / activity_name / activity_desc / activity_type / apply_start_time / apply_end_time / commission_rate / service_rate / institution_id / colonel_buyin_id / colonel_name / categories / min_promotion_days`
- **证据文件**:
  - `runtime/qa/out/douyin-activity-detail-20260507-1657/activity-detail-summary.json`
  - `runtime/qa/out/douyin-activity-detail-20260507-1657/activity-detail-sanitized-response.json`
- **字段观察**:
  - 详情样本存在加密态 `wechat_id / phone_num`，已在证据文件中脱敏为占位值
  - 详情样本未直接提供活动列表里的 `status / activity_start_time / activity_end_time`，后续若进入业务 DTO 需按详情字段单独设计映射
- **结论**:
  - 活动详情上游接口已拿到真实成功样本
  - 当前能力可用于活动信息补充与后续 DTO 设计，但尚未进入业务主链页面
  - 下一步进入活动商品联调接口

### 接口 07: 活动商品联调接口 ✅

- **Endpoint**: `GET /api/douyin/activity-product-list?activityId=3916506&count=20`
- **执行时间**: 2026-05-07 17:00:34 +08:00
- **环境**: real-pre 后端 `8081`，Redis `6380 / DB 0`，`DOUYIN_TEST_ENABLED=false`，`APP_TEST_ENABLED=false`，`ORDER_SYNC_ENABLED=false`
- **前置状态**:
  - `GET /api/douyin/tokens` 返回 HTTP 200 / `code=200`
  - `GET /api/douyin/institution-info` 返回上游 `code=10000`
  - 活动 ID `3916506` 已由活动列表与活动详情接口确认可用
- **响应摘要**:
  - 项目接口 HTTP 200
  - 统一响应 `code=200`
  - 业务状态 `status=success`
  - 上游 `code=10000`
  - 上游 `msg=success`
  - 真实商品数组位于 `remoteResponse.data.data`
  - 首批返回 20 条，并给出 `next_cursor`
  - 首条样本 `product_id=3633173211082040315`
  - 首条样本字段包含 `title / price / cos_ratio / activity_cos_ratio / cos_type / detail_url / status / promotion_start_time / promotion_end_time`
- **证据文件**:
  - `runtime/qa/out/douyin-activity-products-20260507-1700/activity-products-summary.json`
  - `runtime/qa/out/douyin-activity-products-20260507-1700/activity-products-sanitized-response.json`
- **字段观察**:
  - `remoteResponse.data.total=0`，但实际已返回 20 条并带 `next_cursor`，说明 `total` 当前不能直接当成总数口径
  - 样本中的加密态 `shop_contact` 已脱敏存档
- **结论**:
  - 活动商品上游接口已拿到真实成功样本
  - 当前分页结构与最初预期不完全一致，后续若写更强的 DTO 校验或联调脚本，应以 `data.data + next_cursor` 为准

### 接口 08: 活动商品业务接口 ⚠️

- **Endpoint**: `GET /api/colonel/activities/3916506/products?count=20`
- **执行时间**: 2026-05-07 17:00:34 +08:00
- **环境**: real-pre 后端 `8081`，Redis `6380 / DB 0`，`DOUYIN_TEST_ENABLED=false`，`APP_TEST_ENABLED=false`，`ORDER_SYNC_ENABLED=false`
- **响应摘要**:
  - 项目接口 HTTP 200
  - 统一响应 `code=200`
  - 业务视图返回 `total=10`、`items` 10 条、`nextCursor=""`
  - 首条样本 `productId=3810562766247428542`
  - 已带 `bizStatus / bizStatusLabel / promotion / systemTags / alertTags` 等业务扩展字段
- **证据文件**:
  - `runtime/qa/out/colonel-activity-products-20260507-1700/activity-products-summary.json`
  - `runtime/qa/out/colonel-activity-products-20260507-1700/activity-products-sanitized-response.json`
- **差异结论**:
  - 与同批 `07` 的上游 20 条实时样本相比，业务接口当前只返回已有本地快照 10 条
  - 这符合当前“命中快照即优先读库”的实现，但对真实联调观察来说属于“部分成功”：业务口可用，实时样本未同步进入结果
  - 若要继续推进真实商品、转链与订单联调，建议先明确是否需要活动商品快照刷新入口或首屏刷新策略

### 接口 09: 商品素材状态 (真实上游已确认) ✅

- **Endpoint**: `POST /api/douyin/product-material-status-checks`
- **请求**:
```bash
curl -X POST http://localhost:8081/api/douyin/product-material-status-checks \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"appId":"7623665273727387199","products":["101","102"]}'
```
- **响应**: `40004 - 参数校验失败`
- **二次请求（按商品 URL 口径）**:
```bash
curl -X POST http://localhost:8081/api/douyin/product-material-status-checks \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"appId":"7623665273727387199","products":["https://haohuo.jinritemai.com/views/product/detail?id=1"]}'
```
- **二次响应**:
```json
{
  "code": 10000,
  "msg": "success",
  "data": [],
  "sub_code": "",
  "sub_msg": ""
}
```
- **结论**:
  - `buyin.materialsProductStatus` 已经能直接打到真实抖店上游
  - 当前接口**不依赖 access_token** 也可联通
  - `products` 字段当前验证通过的口径是**商品详情 URL**，不是纯商品数字 ID
  - 数字 ID 示例 `["101","102"]` 触发 `40004`，说明此前失败点在测试值口径，不在网络或鉴权
  - 当前样本 URL 返回 `data=[]`，说明结构已通，但还需后续补真实有效商品样本验证状态字段含义

### 接口 09-补充: 商品素材状态边界分支 ✅

- **本地参数上限校验**:
  - 请求：`products` 传 51 条合法 `haohuo` 商品 URL
  - 响应：`status=failed`, `errorType=BusinessException`, `message=products size cannot exceed 50`
  - 结论：`products <= 50` 已在本地接口层先行拦截

- **本地空白值校验**:
  - 请求：`products=["   "]`
  - 响应：`status=failed`, `errorType=BusinessException`, `message=products contains blank url`
  - 结论：空白 URL 已在本地接口层先行拦截

- **上游非法域名校验**:
  - 请求：`products=["https://example.com/not-douyin"]`
  - 响应：`40004 / isv.parameter-invalid:257 / 参数校验失败`
  - 结论：非抖店商品详情 URL 会真实打到上游，再由上游判定参数非法

- **多条合法 URL 复测**:
  - 请求：2 条 `https://haohuo.jinritemai.com/views/product/detail?id=...`
  - 响应：`code=10000, msg=success, data=[]`
  - 结论：多条合法 URL 仍可稳定命中真实上游成功分支

---

## 下一步: 平台确认 + Token 建立

当前更合理的执行口径：

- 平台后台显示应用为**自用型**
- 但 `token.create` 的实际返回仍表现为“必须带 code”
- 下一步需要平台确认该应用在 `token.create` 上的真实校验口径

在平台未给出口径前，可先保留 OAuth 方案作为兜底验证：

### 步骤 1: 构造授权 URL

```
https://op.jinritemai.com/oauth2/authorize?app_key=7623665273727387199&response_type=code&redirect_uri=<回调地址>&state=<随机值>
```

- `redirect_uri` 需在抖店开放平台后台配置
- `state` 用于防 CSRF，随机字符串即可

### 步骤 2: 用户授权

商家登录抖店开放平台，访问上述 URL 完成授权，回调 URL 会带上 `code` 参数

### 步骤 3: 用 code 换 token

```bash
curl -X POST http://localhost:8081/api/douyin/tokens \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"app_id":"7623665273727387199","grant_type":"authorization_code","code":"<授权码>"}'
```

### 替代方案: 抖店后台生成测试授权码

1. 登录 [抖店开放平台](https://op.jinritemai.com)
2. 进入应用 → 授权管理
3. 生成测试授权码（部分应用支持）
4. 用生成的 code 调用上述接口

## Phase 6: Webhook 回调 ✅

### 接口 06: Webhook 事件接收

- **Endpoint**: `POST /api/douyin/webhooks/colonel-open-events`
- **请求**:
```bash
BODY='{"event":"doudian_alliance_colonelOpenEvent","data":{"test":true}}'
SIGN=$(echo -n "$BODY" | openssl dgst -sha256 -hmac "28973aaf-8c3c-4e39-908f-adb4ddd9b70c" | awk '{print $2}')
curl -X POST http://localhost:8081/api/douyin/webhooks/colonel-open-events \
  -H "Content-Type: application/json" \
  -H "x-doudian-sign: $SIGN" \
  -d "$BODY"
```
- **响应**: 200, `"success"`
- **结论**: Webhook 签名验证（HmacSHA256）正常，事件接收管道完整

> 注意：Webhook 端点无需 JWT 认证（已在 `WebConfig` 中排除），仅依赖 `x-doudian-sign` 签名验证

### Webhook 验签负分支复测 ✅

- 当前 `.env.real` 已确认：`DOUYIN_WEBHOOK_VERIFY_SIGN=true`
- **无签名请求**:
  - 响应：`401 invalid sign`
- **错误签名请求（x-doudian-sign=deadbeef）**:
  - 响应：`401 invalid sign`
- **补充结论**:
  - `real-pre` 当前为严格验签模式
  - Webhook 的正向与负向验签分支都已验证通过

---

## 待联调模块

| Phase | 模块 | 接口 | 状态 |
| :--- | :--- | :--- | :--- |
| 1 | Auth Token | Token 初始化/刷新/查询/授权主体 | 刷新与授权主体确认已通过；新授权码初始化待补 |
| 2 | Activities | 创建/查询活动 | 活动列表联调接口、业务接口、活动详情与活动商品联调接口已通过；活动商品业务快照仍待对齐 |
| 3 | Products | 商品查询/报名 | 待测（已有 token，需活动样本） |
| 4 | Promotion | 推广链接生成 | 已通过（主转链已完成真实取证并修正 `product_url` 兼容，`promoteLink` 可回读） |
| 5 | Orders | 订单查询/同步 | 多结算查询已完成首轮取证；`buyin.instituteOrderColonel` 映射已补，`POST /api/orders/sync` 已真实入库 10 单；订单详情 / 店铺订单接口当前缺权限包 |
| 6 | Webhook | 订单回调 | ✅ 已联通 |

---

## 当前状态总结

| 管道 | 状态 |
| :--- | :--- |
| JWT 登录 (admin/admin123) | ✅ 正常 |
| Token 状态查询 (Redis 读写) | ✅ 正常，2026-05-07 复核为 `hasAccessToken=true / hasRefreshToken=true` |
| Token 刷新 | ✅ 2026-05-07 真实刷新链路返回 HTTP 200 / `code=200` |
| 授权主体确认 | ✅ 2026-05-07 `buyin.institutionInfo` 返回上游 `code=10000 / msg=success` |
| 活动列表联调接口 | ✅ 2026-05-07 `alliance.instituteColonelActivityList` 返回上游 `code=10000 / msg=success`，首批 20 条 |
| 活动商品联调接口 | ✅ 2026-05-07 `alliance.colonelActivityProduct` 返回上游 `code=10000 / msg=success`，真实数组位于 `data.data` |
| 转链主接口 | ✅ 2026-05-07 已命中真实上游并生成 `pickSource`、`promoteLink`；真实上游主字段为 `data.product_url` |
| 订单主同步 raw 探针 | ✅ 2026-05-07 `buyin.instituteOrderColonel` 成功结构为 `data.cursor / data.orders`，时间参数必须为 `yyyy-MM-dd HH:mm:ss` 字符串 |
| 订单主同步业务接口 | ✅ 2026-05-07 `POST /api/orders/sync` 30 分钟窗口拉取 10 单、落库 10 单、失败 0；2026-05-08 `pick_source` 重复映射修复后复验：重放同步 `totalFetched=10 / created=4 / updated=6 / attributed=10 / unattributed=0`，全库 `colonel_native` 场景 316 单全部 `ATTRIBUTED`（归因率 100%） |
| 订单展示与统计 smoke | ⚠️ `/api/orders` 与 Dashboard `createTime` 口径能看到真实同步订单；数据平台订单页与 Dashboard 默认 `settleTime` 口径暂不覆盖未结算订单 |
| 订单详情 / 店铺订单接口 | ⚠️ 2026-05-07 `order.orderDetail`、`order.searchList` 均返回 `30001 / isv.app-permissions-insufficient`，需补订单管理接口权限包 |
| Webhook 签名验证 + 事件接收 | ✅ 正常 |
| Redis 探针 | ✅ 已通，容器 Redis(6380 / DB 0) 可用 |
| Token 初始化 (真实抖店 API / SDK 裸调) | ✅ 已联通；`authorization_self` 旧路稳定返回 `50002`，当前若重建 token 应走 `authorization_code` |
| 所有需要 token 的业务接口 | ⏭️ `refresh_token` 缺失阻塞已解除，仍需按单接口继续采样 |
| 商品素材状态原始接口 | ✅ 已触达真实上游，但当前测试参数非法 |

## 下一步行动

**当前主阻塞项：活动商品业务快照对齐、订单归因字段来源，以及订单详情 / 敏感数据权限包**

1. 评估 `GET /api/colonel/activities/3916506/products` 是否需要主动刷新快照入口或首屏刷新策略
2. 继续验证 `11/12` fallback 是否还有实际命中场景，但它们已不再阻塞 Promotion 主链路
3. 补订单归因口径：当前 `buyin.instituteOrderColonel` 真实样本未返回 `pick_source`，需确认是否存在其他归因字段、推广链接回传参数或订单详情扩展字段
4. 补订单详情 / 店铺订单接口权限包，再复测 `order.orderDetail`、`order.searchList`；`order.batchDecrypt` 相关项已转为历史记录，不再作为当前执行目标
5. 若后续需要重建 token，再用新 OAuth code 调用 `POST /api/douyin/tokens`
6. `POST /api/douyin/token-create-probes` 仍保留为平台提单取证工具；如需继续向平台确认旧问题，提供：
   - 应用 `星链达客`
   - `app_key=7623665273727387199`
   - 关联机构 `7351155267604218149`
   - 后台显示为自用型
   - `authorization_self` + `authId` 裸调仍返回 `50002`
7. 平台若确认仍需 `code`，则走 OAuth 授权流程获取 code
8. 用授权码调用：
```bash
curl -X POST http://localhost:8081/api/douyin/tokens \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"app_id":"7623665273727387199","grant_type":"authorization_code","code":"<授权码>"}'
```
9. Token 获取成功后，Phase 2-5 继续按 `docs/archive/records/14` 单接口推进

## Token 依赖阻塞清单（2026-05-02 历史）

当前凡是走 `DouyinApiClient.post(...)` 的真实抖店接口，都会先进入：

- `DouyinApiClient.post(...)`
- `DouyinTokenService.getValidToken(appId)`
- 若 Redis 中不存在可用 token，则进入 refresh 分支
- 当前统一卡在：`missing refresh_token, cannot refresh token`

> 2026-05-07 复核：上述阻塞已经被 Redis DB 0 中的真实 token 缓存和 `POST /api/douyin/token-refreshes` 成功调用解除。下面清单仍保留，用于说明哪些接口仍需要按顺序重测，而不是说明当前仍缺 refresh token。

### A. 已实测确认被 token 阻塞的接口

| 序号 | 本地入口 | 上游能力 | 当前现象 | 当前阻塞结论 |
| :--- | :--- | :--- | :--- | :--- |
| 04 | `GET /api/douyin/activities` | `alliance.instituteColonelActivityList` | 当前未单独重测，但代码路径明确依赖 token | 等 token 后执行 |
| 05 | `GET /api/colonel/activities` | `alliance.instituteColonelActivityList` | 业务接口底层同样依赖 `activityApi.listActivities(...)` | 等 token 后执行 |
| 06 | `GET /api/douyin/activities/{activityId}` | `buyin.colonelActivityDetail` | 当前未单独重测，但代码路径明确依赖 token | 需先从活动列表拿到真实 `activityId` |
| 07 | `GET /api/douyin/activities/{activityId}/products` / `GET /api/douyin/activity-product-list` | `alliance.colonelActivityProduct` | 当前未单独重测，但代码路径明确依赖 token | 需先有 token + 真实 `activityId` |
| 08 | `GET /api/colonel/activities/{activityId}/products` | `alliance.colonelActivityProduct` | 若本地无快照则会回退真实上游 | 需先有 token + 真实 `activityId` |
| 10 | `POST /api/colonel/activities/{activityId}/products/{productId}/promotion-links` | `buyin.promotion.link.generate` | 2026-05-07 已实测通过；前置为“审核通过 -> 分配招商”，真实上游主字段为 `data.product_url` | token 依赖已解除，主链路已打通 |
| 11 | 同 10 | `buyin.kolProductShare` | 仅在主方法 API 下线时 fallback | 前置仍是 token |
| 12 | 同 10 | `buyin.getProductShareMaterial` | 仅在 fallback-1 继续下线时 fallback | 前置仍是 token |
| 13 | `GET /api/douyin/order-settlements` | `buyin.colonelMultiSettlementOrders` | 2026-05-07 已实测成功命中真实上游；当前拿到 `data.orders=[]` 空样本，并确认 `t-90d` 边界错误码 | 继续作为结算订单补充样本来源 |
| 14 | `POST /api/orders/phone-decryptions` | `order.batchDecrypt`（历史探针曾验证 `order.batchSensitiveDataRequest` / `order.batchSensitive`） | 已确认旧接口下线；同时已确认官方正式契约要求 `cipher_infos=[{auth_id,cipher_text}]`。当前团长订单主接口未返回收件人密文字段，订单详情 / 店铺订单接口又缺权限包，旧探针结果仅保留为负向取证 | 历史记录，不再作为当前执行项 |

### B. 已解除的订单同步代码缺口

| 序号 | 本地入口 | 上游能力 | 当前状态 | 剩余阻塞 |
| :--- | :--- | :--- | :--- | :--- |
| 15 | `POST /api/orders/sync` | `buyin.instituteOrderColonel` | **已闭环入库**：30 分钟窗口 `totalFetched=10 / created=10 / failed=0` | 2026-05-08 `pick_source` 重复映射修复后：`colonel_native` 场景通过 `colonel_buyin_id + activity_id + product_id` 三层级联匹配，全库 316 单 100% `ATTRIBUTED`；种子 SQL 扩展为 8 个 `colonel_buyin_id` |

### C. 活动商品业务刷新闭环

2026-05-07 已补齐 `/api/colonel/activities/{activityId}/products` 的显式刷新口径：

- 默认不传 `refresh` 时，继续保持本地快照优先，避免页面频繁触发真实上游调用。
- 传 `refresh=true` 时，绕过已有快照，调用上游 `alliance.colonelActivityProduct`，执行 `upsertSnapshots(activityId, result.items())` 后再返回本地业务视图。
- real-pre smoke：`GET /api/colonel/activities/3916506/products?count=20&refresh=true` 返回 HTTP 200 / `code=200`；刷新前默认业务视图 10 条，刷新后 20 条，随后默认业务视图也返回 20 条。
- 证据目录：`runtime/qa/out/activity-product-refresh-real-20260507-210819`。

### C1. 商品详情 / SKU 权限取证

2026-05-07 已区分“活动商品业务详情”和“上游商品详情 / SKU”两类来源：

- `/api/colonel/activities/3916506/products/3780271777075298337` 与 `3810562766247428542` 均返回 HTTP 200 / `code=200`，可用于页面业务详情、状态、转链与本地运营字段展示。
- 上述业务详情来自活动商品快照与本地业务状态，不包含 SKU 明细。
- 通过 raw 探针调用 `product.detail`，两个真实商品 ID 均返回 `30001 / isv.app-permissions-insufficient`。
- 因此 SKU 样本当前不是字段映射问题，而是商品详情接口权限包缺口；需补商品详情权限后再验证 `RealDouyinProductGateway.queryProductDetail/queryProductSkus`。
- 证据目录：`runtime/qa/out/product-detail-real-20260507-210924`、`runtime/qa/out/product-detail-raw-probes-20260507-210941`。

### D. 订单同步代码断点定位（2026-05-02 历史）

当时订单主链路的真实阻塞，不在 `OrderSyncService` 的循环本身，而在它的上游输入为空。该缺口已于 2026-05-07 通过 `RealDouyinOrderGateway` 映射补齐并完成 real-pre 同步验证，以下保留为历史定位记录：

1. `OrderController.syncOrders(...)`
   - 入口：`POST /api/orders/sync`
   - 调用：`OrderSyncService.syncByTimeRange(...)`

2. `OrderSyncService.syncRange(...)`
   - 会循环调用 `douyinOrderGateway.listSettlement(...)`
   - **只有当 `OrderListResult.orders` 非空时**，才会继续：
     - `mapOrder(item)`
     - `attributionService.resolveAttribution(...)`
     - `persistenceService.persistOrder(order)`

3. `RealDouyinOrderGateway.listSettlement(...)`
   - 当前真实实现是：
     - 调 `orderApi.listSettlement(...)` 拿到上游原始 `Map<String,Object> response`
     - 但直接返回 `new OrderListResult(List.of(), false, "0", response)`
   - 也就是说：**原始响应拿到了，但没有解析成 `DouyinOrderItem` 列表**

4. 直接后果
   - `OrderSyncService` 收到的 `items` 恒为空
   - 同步循环会直接 `break`
   - 所以当前 `/api/orders/sync` 即使 token 打通，也只会表现为：
     - 请求执行完成
     - 但 `created/updated/attributed` 仍可能为 0

### E. 订单映射最小补全范围（已完成首轮）

要让真实订单同步可闭环，至少需要在 `RealDouyinOrderGateway.listSettlement(...)` 中补齐以下几件事；当前首轮均已落地，但仍需更多状态样本扩充枚举：

1. 从 `orderApi.listSettlement(...)` 的原始响应里提取订单数组
   - 重点候选字段：
     - `data.orders`
     - 兼容 `data.order_list / orderList / list / data`

2. 为每条订单映射 `DouyinOrderGateway.DouyinOrderItem`
   - 关键字段至少包括：
     - `externalOrderId`
     - `productId`
     - `merchantId / merchantName`
     - `pickSource`
     - `orderAmount`
     - `serviceFee`
     - `orderStatus`
     - `createTime`
     - `settleTime`
     - `rawPayload`

3. 补齐分页字段
   - `hasMore`
   - `nextCursor`

4. 然后再验证下游是否能正常工作
   - `OrderSyncService.mapOrder(...)`
   - `AttributionService.resolveAttribution(...)`
   - `OrderSyncPersistenceService.persistOrder(...)`

### F. 当前对订单链路的结论

- `buyin.instituteOrderColonel` 的真实调用入口与映射层已具备
- 当前真实入参时间口径固定为 `yyyy-MM-dd HH:mm:ss` 字符串，不能传秒级 / 毫秒级时间戳
- `/api/orders/sync` 已完成真实订单入库闭环；2026-05-08 `pick_source` 重复映射修复后复验：重放同步 `totalFetched=10 / created=4 / updated=6 / attributed=10 / unattributed=0`
- `colonel_native` 场景归因已打通：通过 `colonel_buyin_id + activity_id + product_id` 三层级联匹配，全库 316 单 100% `ATTRIBUTED`；修复前 `pick_source` 被视为全局唯一导致归因失配，修复后改为复合键匹配（`pick_source + activity_id + product_id + user_id`）
- 种子 SQL 扩展为 8 个 `colonel_buyin_id`：`7351155267604218149`、`7345890512227811619`、`7622387250219827506`、`7349597984361611561`、`7341320980353073418`、`7108286947231105312`、`7109679864001364265`、`7350227679947440424`
- 当前真实订单样本稳定返回达人字段 `author_buyin_id / author_account / author_short_id`；系统已将 `author_buyin_id` 纳入达人 UID 识别，将 `author_account` 写入 `talent_name`，可支撑达人展示与后续独家达人归因
- 订单重复同步更新路径已修复：`extra_data` 在专用 update SQL 中显式 `CAST(... AS JSONB)`，同一窗口重放结果为 `updated=6 / failed=0`
- 证据目录：`runtime/qa/out/orders-attribution-evidence-20260507-211000`、`runtime/qa/out/orders-sync-author-alias-20260507-211944`、`runtime/qa/out/orders-sync-real-20260507-203422`
- 订单解密相关问题仅保留为当时联调背景：当时应用缺少订单详情 / 店铺订单接口权限包，且团长订单主接口不返回收件人密文字段

### C. 当前可直接进入的下一个动作

1. 真实 token 已可用，优先顺序更新为：
   - 01 Token 初始化
   - 02 Token 刷新
   - 03 Token 状态查询
   - 04 活动列表联调接口
   - 05 活动列表业务接口
   - 06 活动详情
   - 07/08 活动商品接口
   - 10/11/12 转链
- 13 多结算订单：已完成首轮 real-pre 取证；成功结构为 `data.cursor + data.orders`，当前窗口内订单为空，另已确认时间窗越界报错 `40004 / isv.parameter-invalid:1036`
- 14 订单解密：已完成首轮负向取证；相关契约与阻塞保留为历史记录，不再作为当前范围
2. `POST /api/orders/sync` 已完成首轮真实同步；`/orders` 与 Dashboard `createTime` 口径已能看到真实订单，下一步应在 M1.6 明确数据平台和 Dashboard 默认使用 `create_time` 还是 `settle_time`
3. 当前可继续保留的独立验证项：
   - `POST /api/douyin/product-material-status-checks`
   - `POST /api/douyin/webhooks/colonel-open-events`

## 遗留问题

1. **RealDouyinAuthGateway.ensureToken()** 返回 null — 可优化为启动时从 Redis 检查已有缓存 token，非阻塞
2. **OrderController** 已确认当前代码无编译问题（历史遗留已修复）
