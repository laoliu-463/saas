# 18-Real-Pre 真实 SDK 联调记录

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

### 接口 02: Token 刷新 (阻塞)

- 需先完成接口 01 获取 refresh_token

### 接口 03 后续依赖链路现状 ⚠️

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
| 1 | Auth Token | Token 初始化/刷新/查询 | 需授权码 |
| 2 | Activities | 创建/查询活动 | 待测（需 token） |
| 3 | Products | 商品查询/报名 | 待测（需 token） |
| 4 | Promotion | 推广链接生成 | 待测（需 token） |
| 5 | Orders | 订单查询/同步 | 待测（需 token） |
| 6 | Webhook | 订单回调 | ✅ 已联通 |

---

## 当前状态总结

| 管道 | 状态 |
| :--- | :--- |
| JWT 登录 (admin/admin123) | ✅ 正常 |
| Token 状态查询 (Redis 读写) | ✅ 正常 |
| Webhook 签名验证 + 事件接收 | ✅ 正常 |
| Redis 探针 | ✅ 已通，容器 Redis(6380) 可用 |
| Token 初始化 (真实抖店 API / SDK 裸调) | ✅ 已联通，但 `authorization_self` 稳定返回 `50002` |
| 所有需要 token 的业务接口 | ⏳ 阻塞，等待有效 token / refresh_token |
| 商品素材状态原始接口 | ✅ 已触达真实上游，但当前测试参数非法 |

## 下一步行动

**当前主阻塞项：平台确认 `token.create` 对自用型应用的真实校验口径**

1. 用 `POST /api/douyin/token-create-probes` 保留提单证据
2. 向平台提供：
   - 应用 `星链达客`
   - `app_key=7623665273727387199`
   - 关联机构 `7351155267604218149`
   - 后台显示为自用型
   - `authorization_self` + `authId` 裸调仍返回 `50002`
3. 平台若确认仍需 `code`，则走 OAuth 授权流程获取 code
4. 用授权码调用：
```bash
curl -X POST http://localhost:8081/api/douyin/tokens \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"app_id":"7623665273727387199","grant_type":"authorization_code","code":"<授权码>"}'
```
5. Token 获取成功后，Phase 2-5 全部解锁

## Token 依赖阻塞清单（2026-05-02）

当前凡是走 `DouyinApiClient.post(...)` 的真实抖店接口，都会先进入：

- `DouyinApiClient.post(...)`
- `DouyinTokenService.getValidToken(appId)`
- 若 Redis 中不存在可用 token，则进入 refresh 分支
- 当前统一卡在：`missing refresh_token, cannot refresh token`

### A. 已实测确认被 token 阻塞的接口

| 序号 | 本地入口 | 上游能力 | 当前现象 | 当前阻塞结论 |
| :--- | :--- | :--- | :--- | :--- |
| 04 | `GET /api/douyin/activities` | `alliance.instituteColonelActivityList` | 当前未单独重测，但代码路径明确依赖 token | 等 token 后执行 |
| 05 | `GET /api/colonel/activities` | `alliance.instituteColonelActivityList` | 业务接口底层同样依赖 `activityApi.listActivities(...)` | 等 token 后执行 |
| 06 | `GET /api/douyin/activities/{activityId}` | `buyin.colonelActivityDetail` | 当前未单独重测，但代码路径明确依赖 token | 需先从活动列表拿到真实 `activityId` |
| 07 | `GET /api/douyin/activities/{activityId}/products` / `GET /api/douyin/activity-product-list` | `alliance.colonelActivityProduct` | 当前未单独重测，但代码路径明确依赖 token | 需先有 token + 真实 `activityId` |
| 08 | `GET /api/colonel/activities/{activityId}/products` | `alliance.colonelActivityProduct` | 若本地无快照则会回退真实上游 | 需先有 token + 真实 `activityId` |
| 10 | `POST /api/colonel/activities/{activityId}/products/{productId}/promotion-links` | `buyin.promotion.link.generate` | 当前未实测，代码路径明确依赖 token | 需先有 token + 真实商品 |
| 11 | 同 10 | `buyin.kolProductShare` | 仅在主方法 API 下线时 fallback | 前置仍是 token |
| 12 | 同 10 | `buyin.getProductShareMaterial` | 仅在 fallback-1 继续下线时 fallback | 前置仍是 token |
| 13 | `GET /api/douyin/order-settlements` | `buyin.colonelMultiSettlementOrders` | 已实测返回 `missing refresh_token, cannot refresh token` | 等 token 后执行 |
| 14 | `OrderDecryptService#decryptPhones` | `order.batchSensitiveDataRequest` / `order.batchSensitive` | 当前未实测，代码路径明确依赖 token | 需先有真实订单号 |

### B. 不仅缺 token，还存在代码待补全的接口

| 序号 | 本地入口 | 上游能力 | token 放开后是否能直接闭环 | 额外阻塞 |
| :--- | :--- | :--- | :--- | :--- |
| 15 | `POST /api/orders/sync` | `buyin.instituteOrderColonel` | **不能** | `RealDouyinOrderGateway.listSettlement(...)` 目前返回 `OrderListResult(List.of(), ...)`，未把真实上游订单映射进 `orders`，因此同步入库仍无法闭环 |

### D. 订单同步代码断点定位（2026-05-02）

当前订单主链路的真实阻塞，不在 `OrderSyncService` 的循环本身，而在它的上游输入为空：

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

### E. 订单映射最小补全范围

要让真实订单同步可闭环，至少需要在 `RealDouyinOrderGateway.listSettlement(...)` 中补齐以下几件事：

1. 从 `orderApi.listSettlement(...)` 的原始响应里提取订单数组
   - 重点候选字段：
     - `data.order_list`
     - 或其他真实上游数组字段（待 token 放开后抓样本确认）

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

- `buyin.instituteOrderColonel` 的真实调用入口已具备
- 当前**主缺口是“原始响应 -> DouyinOrderItem”映射层为空**
- 因此订单同步现在是“双重阻塞”：
  1. 先被 token 挡住
  2. token 放开后，还会被订单映射缺口挡住

### C. 当前可直接进入的下一个动作

1. 平台一旦放开 token，优先顺序仍按文档执行：
   - 01 Token 初始化
   - 02 Token 刷新
   - 03 Token 状态查询
   - 04 活动列表联调接口
   - 05 活动列表业务接口
   - 06 活动详情
   - 07/08 活动商品接口
   - 10/11/12 转链
   - 13 多结算订单
   - 14 订单解密
2. `POST /api/orders/sync` 不建议在 token 放开后立刻测试，需先补 `RealDouyinOrderGateway` 的真实订单映射
3. 当前可继续保留的独立验证项：
   - `POST /api/douyin/product-material-status-checks`
   - `POST /api/douyin/webhooks/colonel-open-events`

## 遗留问题

1. **RealDouyinAuthGateway.ensureToken()** 返回 null — 可优化为启动时从 Redis 检查已有缓存 token，非阻塞
2. **OrderController** 已确认当前代码无编译问题（历史遗留已修复）
