# 14-抖店 SDK 接口逐项联调执行文档

更新时间：2026-05-09

> 历史说明（2026-05-15 补记）：
> 本文保留 2026-05 的逐接口联调现场记录。当前项目口径已移除“订单手机号解密”功能，因此文中 `14 订单解密` 及相关“下一步/阻塞/样本”表述仅作历史取证，不再属于当前联调执行范围。

## 1. 文档定位

本文是抖店第三方 SDK 联调的专属执行文档，执行口径统一为：

> 严格按照单个接口为单位，逐个依次开展联调；每完成一个接口，立即同步该接口完整联调结果，再进入下一个接口。

本文不再按大模块泛泛描述，而是直接面向“逐接口执行、逐接口回写、逐接口验收”。

与其他文档的关系：

- `docs/09-真实SDK联调准备清单.md`：联调前准备口径
- `docs/archive/records/14-抖店SDK全量梳理与逐接口联调规划.md`：逐接口执行主文档
- `docs/04-开发进度.md`：阶段状态更新

## 1.1 当前执行进度（2026-05-08）

- `02 Token 刷新`：已在 real-pre 真实上游模式验证通过；HTTP 200，统一响应 `code=200`，Redis 中仍有 `access_token / refresh_token / expire_at`，未触发重新授权
- `03 Token 状态查询`：已验证通过
- `03A 授权身份确认`：已验证通过；`GET /api/douyin/institution-info` 返回上游 `code=10000 / msg=success`，确认当前 token 可读取机构身份
- `04 活动列表联调接口`：已验证通过；`GET /api/douyin/activities` 返回上游 `code=10000 / msg=success`，首批 `activity_list` 20 条，可用活动样本 `activity_id=3916506`
- `05 活动列表业务接口`：已验证通过；`GET /api/colonel/activities` 返回统一业务结构 `data.total=21 / data.activityList[0].activityId=3916506`，并已补齐 `activityStatus / startTime / endTime` 兼容字段避免现有页面列渲染错位
- `06 活动详情`：已验证通过；`GET /api/douyin/activities/3916506` 返回上游 `code=10000 / msg=success`，已确认详情字段含 `activity_desc / apply_start_time / apply_end_time / commission_rate / service_rate / institution_id / colonel_buyin_id`
- `07 活动商品联调接口`：已验证通过；`GET /api/douyin/activity-product-list?activityId=3916506&count=20` 返回上游 `code=10000 / msg=success`，真实数组位于 `data.data`，首批 20 条
- `08 活动商品业务接口`：部分成功；`GET /api/colonel/activities/3916506/products?count=20` 当前优先返回本地快照 10 条，未与同批上游 20 条实时样本对齐
- `08 活动商品业务接口`：2026-05-08 21:19 二次复验继续可用；本轮选定真实商品样本 `product_id=3816304798194598152`
- `09 商品素材状态`：已在 real-pre `real` profile 复验通过；商品详情 URL 入参返回上游 `10000 / success`，纯数字商品 ID 入参返回 `40004 / isv.parameter-invalid:257`
- `10 转链主接口`：主链路已由前序真实样本与订单归因结果间接验通；本轮 raw probe 因活动商品样本 `detailUrl` 为空触发 `40004 / isv.parameter-invalid:1056 / 无效商品URL`，不视为接口回退
- `11 转链 fallback-1`：已完成 raw probe 负向取证；`buyin.kolProductShare` 可达真实上游，但当前授权主体返回 `40003 / isv.authorization-type-invalid`
- `12 转链 fallback-2`：已完成 raw probe 负向取证；`buyin.getProductShareMaterial` 可达真实上游，但返回 `90000 / isp.unknown-error`，提示接口已不再提供服务
- `15 订单同步主接口`：2026-05-08 21:19 二次复验完成；7 天窗口 `totalFetched=10 / created=10 / updated=0 / attributed=10 / unattributed=0 / failed=0`，全库订单统计 `totalOrders=326 / attributedOrders=326 / unattributedOrders=0`
- `前端联调页`：`runtime/qa/out/real-pre-douyin-frontend-20260508211901/report.md` 二次复验 `1/1 PASS`，Token、授权主体、活动商品、订单同步、Dashboard 与店铺侧权限阻塞均可见
- `20 Webhook 接收`：接收、验签、快速返回与日志脱敏单测已验证通过；仍未接业务消费与幂等落库
- `01 Token 初始化`：2026-05-08 已使用新 OAuth 授权码重放 `POST /api/douyin/tokens`，HTTP 200、`code=200`，`hasAccessToken=true`、`hasRefreshToken=true`、`reauthorizeRequired=false`
- 后续接口仍需继续按顺序逐项联调和回写；`09/10/11/12/15` 的最新结论是：团长侧订单归因链路已跑通，剩余真实阻塞集中在商品详情 / SKU 权限、店铺侧订单管理权限与 Webhook 业务消费

## 2. 执行总规则

### 2.0 文档优先规则

接口测试必须严格依据现有接口文档、网关契约文档和联调准备文档执行，不允许脱离文档约束自行假设。

本次接口测试的文档依据固定为：

1. `docs/05-接口与数据模型.md`
2. `docs/03-Test与Real网关契约.md`
3. `docs/09-真实SDK联调准备清单.md`
4. 本文档 `docs/archive/records/14-抖店SDK全量梳理与逐接口联调规划.md`
5. 当前代码实现中可验证的接口事实

每个接口测试前，必须先核对以下项目是否与文档一致：

- 鉴权方式
- 请求协议与请求方法
- 请求路径
- 请求头要求
- 入参名称
- 入参格式
- 字段取值规则
- 权限限制
- 业务校验逻辑
- 返回结构
- 状态码与错误处理口径

如代码实现与文档描述不一致，测试记录中必须明确标注：

- 以文档为准还是以代码为准
- 差异点是什么
- 是否需要同步修正文档或代码

### 2.1 单接口推进规则

每次联调只允许处理一个接口，顺序必须按本文执行清单推进。

单接口标准动作固定为：

1. 确认前置条件
2. 发起接口调用
3. 记录调用参数
4. 记录返回数据
5. 记录异常问题
6. 判断适配状态
7. 回写联调结果
8. 明确是否进入下一个接口

未完成第 7 步，不允许直接跳到下一个接口。

### 2.2 单接口结果即时回写规则

每完成一个接口，必须即时补齐以下内容：

- 当前进度
- 调用时间
- 调用环境
- 调用入口
- 鉴权方式
- 请求方法
- 请求协议
- 请求参数
- 请求头
- 返回结果摘要
- HTTP 状态码
- 原始返回样本位置
- 数据校验结果
- 异常问题
- 适配状态
- 是否阻塞后续接口
- 下一步动作

其中“数据校验结果”至少要覆盖：

- 文档字段是否齐全
- 字段类型是否符合约定
- 字段值是否符合规则
- 权限与业务限制是否符合文档说明
- 返回结构是否符合统一响应格式

### 2.3 联调状态定义

| 状态 | 含义 |
| --- | --- |
| `未开始` | 尚未进入该接口联调 |
| `进行中` | 正在联调，但结果尚未确认 |
| `成功` | 调用成功，字段满足当前项目接入要求 |
| `部分成功` | 接口已通，但字段、权限、映射或稳定性仍有缺口 |
| `失败` | 接口当前不可用或不满足验收条件 |
| `阻塞` | 由于前置接口、权限、配置或代码未完成，暂时无法继续 |
| `跳过` | 当前阶段明确不做 |

### 2.4 适配状态定义

| 适配状态 | 含义 |
| --- | --- |
| `已适配` | 当前代码实现已可直接支撑业务链路 |
| `已通未入链` | 接口可调用，但尚未接入业务主链路 |
| `返回已确认待映射` | 上游返回已拿到，但本地 DTO / 持久化映射未完成 |
| `代码待补全` | 当前仓库存在明确实现缺口 |
| `权限待补齐` | 主要阻塞来自店铺授权或开放能力开通 |

## 3. 当前代码事实总览

### 3.1 SDK 与调用层

- SDK 依赖：`com.doudian:open-sdk:1.1.0`
- 官方 SDK 直调范围：
  - `token.create`
  - `token.refresh`
- 其余业务接口统一走：
  - `backend/src/main/java/com/colonel/saas/douyin/DouyinApiClient.java`

### 3.2 当前 Real 联调关键事实

1. Token 创建、刷新链路已具备真实调用能力
2. 活动列表、活动商品列表、转链都已有真实调用入口
3. `RealDouyinOrderGateway` 已完成当前真实样本最小映射，团长侧订单主同步可落库；店铺侧订单详情仍受权限包阻塞
4. `RealDouyinProductGateway.queryProductDetail` 未实现
5. `RealDouyinProductGateway.queryProductSkus` 未实现
6. `RealDouyinAuthGateway.ensureToken` 当前返回 `null`
7. Webhook 当前只接收、验签、脱敏记日志，未接业务消费、幂等落库与重放补偿

因此，本轮联调的执行策略必须分为两段：

- 第一段：先做“已具备调用能力”的单接口联调
- 第二段：补实现后再做“主链路闭环接口”联调

## 4. 联调环境固定口径

### 4.1 环境划分

| 环境 | 用途 | 要求 |
| --- | --- | --- |
| `test` | 演示、回归、基线保护 | `douyin.test.enabled=true` |
| `real/pre` | 真实联调 | `douyin.test.enabled=false`，数据库/Redis/Webhook 独立 |

### 4.2 核心配置项

| 配置项 | 用途 |
| --- | --- |
| `DOUYIN_BASE_URL` | 抖店开放平台地址 |
| `DOUYIN_CLIENT_KEY` | 默认 appId、签名主键 |
| `DOUYIN_CLIENT_SECRET` | 签名密钥、Webhook 验签密钥 |
| `DOUYIN_APP_ID` | 兜底 appId |
| `DOUYIN_TEST_ENABLED` | Test/Real 开关 |
| `DOUYIN_TOKEN_AUTO_REFRESH_ENABLED` | Token 自动刷新开关 |
| `DOUYIN_TOKEN_AUTO_REFRESH_CRON` | Token 自动刷新计划 |
| `DOUYIN_WEBHOOK_VERIFY_SIGN` | Webhook 验签开关 |

### 4.3 联调前必须满足

1. `mvn test` 全绿
2. Redis 可用
3. PostgreSQL 已完成当前 SQL 升级
4. 已确认真实店铺授权
5. 已确认活动、商品、推广、订单相关联调前提
6. 已准备真实回调地址
7. 已明确本次联调使用的 app 与授权码归属一致

补充执行口径：

8. 真实联调前必须先完成 `real/pre` 环境检查，至少确认：
   - `SPRING_PROFILES_ACTIVE=prod`
   - `DOUYIN_TEST_ENABLED=false`
   - `DB_NAME != colonel_saas_test`
   - `REDIS_DATABASE != 1`
9. 建议使用独立端口（当前推荐 `8081`）拉起 `real/pre` 后端
10. 首轮真实联调建议关闭 `DOUYIN_TOKEN_AUTO_REFRESH_ENABLED`

### 4.4 测试限制

当前阶段执行接口测试时，必须遵守以下限制：

1. 不允许为了测通接口临时改前端字段
2. 不允许为了测通接口修改主业务 Service 逻辑
3. 不允许绕过文档中既定鉴权方式直接拼非标准请求
4. 不允许把 Test 返回结果当成 Real 联调结果
5. 不允许跳过权限校验、参数校验、业务校验直接判定“通过”
6. 对于当前明确“代码待补全”的接口，只能做阻塞说明，不能强行记为成功

## 5. 接口执行顺序总表

以下顺序为强制顺序，不允许跳项。

| 序号 | 接口名称 | 上游能力 | 项目入口 | 优先级 | 当前状态 | 适配状态 | 是否允许现在执行 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 01 | Token 初始化 | `token.create` | `POST /api/douyin/tokens` | P0 | 部分成功 | 已适配 | 需新授权码时执行 |
| 02 | Token 刷新 | `token.refresh` | `POST /api/douyin/token-refreshes` | P0 | 成功 | 已适配 | 已执行 |
| 03 | Token 状态查询 | 项目能力 | `GET /api/douyin/tokens` | P0 | 成功 | 已适配 | 已执行 |
| 03A | 授权身份确认 | `buyin.institutionInfo` | `GET /api/douyin/institution-info` | P0 | 成功 | 已适配 | 已执行 |
| 04 | 活动列表联调接口 | `alliance.instituteColonelActivityList` | `GET /api/douyin/activities` | P0 | 成功 | 已适配 | 已执行 |
| 05 | 活动列表业务接口 | `alliance.instituteColonelActivityList` | `GET /api/colonel/activities` | P0 | 成功 | 已适配 | 已执行 |
| 06 | 活动详情 | `buyin.colonelActivityDetail` | `GET /api/douyin/activities/{activityId}` | P1 | 成功 | 已通未入链 | 已执行 |
| 07 | 活动商品联调接口 | `alliance.colonelActivityProduct` | `GET /api/douyin/activity-product-list` | P0 | 成功 | 已适配 | 已执行 |
| 08 | 活动商品业务接口 | `alliance.colonelActivityProduct` | `GET /api/colonel/activities/{activityId}/products` | P0 | 部分成功 | 已适配 | 已执行 |
| 09 | 商品素材状态 | `buyin.materialsProductStatus` | `POST /api/douyin/product-material-status-checks` | P1 | 成功 | 已通未入链 | 已执行 |
| 10 | 转链主接口 | `buyin.promotion.link.generate` | `POST /api/colonel/activities/{activityId}/products/{productId}/promotion-links` | P0 | 部分成功 | 已适配 | 是 |
| 11 | 转链 fallback-1 | `buyin.kolProductShare` | 同上 | P1 | 部分成功 | 权限待补齐 | 已执行 |
| 12 | 转链 fallback-2 | `buyin.getProductShareMaterial` | 同上 | P1 | 失败 | 上游已下线 | 已执行 |
| 13 | 多结算订单查询 | `buyin.colonelMultiSettlementOrders` | `GET /api/douyin/order-settlements` | P0 | 部分成功 | 已通未入链 | 是 |
| 14 | 订单解密（历史项） | `order.batchDecrypt`（历史探针曾覆盖 `order.batchSensitiveDataRequest` / `order.batchSensitive`） | `OrderDecryptService` 链路 | 历史记录 | 部分成功 | 历史记录 | 否 |
| 15 | 订单同步主接口 | `buyin.instituteOrderColonel` | `POST /api/orders/sync` | P0 | 成功 | 已适配 | 已执行 |
| 16 | 活动创建 | `alliance.colonelActivityCreateOrUpdate` | `POST /api/douyin/activities` | P2 | 未开始 | 已通未入链 | 后置 |
| 17 | 活动更新 | `alliance.colonelActivityCreateOrUpdate` | `PUT /api/douyin/activities/{activityId}` | P2 | 未开始 | 已通未入链 | 后置 |
| 18 | 活动商品取消 | `alliance.colonelActivityProductCancel` | `POST /api/douyin/activity-product-cancellations` | P2 | 未开始 | 已通未入链 | 后置 |
| 19 | 活动商品取消-原始报文 | `alliance.colonelActivityProductCancel` | `POST /api/douyin/activity-product-cancellations/raw` | P2 | 未开始 | 已通未入链 | 后置 |
| 20 | Webhook 接收 | 回调接收 | `POST /api/douyin/webhooks/colonel-open-events` | P1 | 部分成功 | 已通未入链 | 是 |
| 21 | `instPickSourceConvert` | `buyin.instPickSourceConvert` | 当前无外部入口 | P2 | 未开始 | 已通未入链 | 否 |
| 22 | 商品详情 Real | 待确认 | 当前无接口 | P1 | 未开始 | 代码待补全 | 否 |
| 23 | 商品 SKU Real | 待确认 | 当前无接口 | P1 | 未开始 | 代码待补全 | 否 |

> 2026-04-28 补充：Token 初始化、Token 状态查询、Token 刷新通过后，活动接口前必须先执行授权身份确认。
> 当前项目入口为 `GET /api/douyin/institution-info`，上游能力为 `buyin.institutionInfo`。
> 当前 real-pre 容器实际使用 Redis DB 0；若当前 DB 中缺少 `douyin:refresh:<appId>`，该入口会返回 `missing refresh_token, cannot refresh token`，不得继续测活动、商品、转链或订单。

## 6. 单接口联调记录模板

以下模板是每个接口都必须复制并填写的固定格式。

```md
### 接口名称
- 执行序号：
- 当前进度：
- 联调状态：
- 适配状态：
- 执行人：
- 执行时间：
- 联调环境：
- 项目入口：
- 上游能力：
- 文档依据：

#### 1. 前置条件
- 

#### 2. 文档约束核对
- 鉴权方式：
- 请求方法：
- 请求协议：
- 权限限制：
- 入参格式：
- 字段规则：
- 业务规则：

#### 3. 调用参数
- 

#### 4. 请求头
- 

#### 5. 调用情况
- 

#### 6. 返回数据摘要
- 

#### 7. HTTP 状态码
- 

#### 8. 报错信息
- 

#### 9. 原始返回样本
- 文件位置 / 日志位置：

#### 10. 数据校验结果
- 统一响应结构校验：
- 字段完整性校验：
- 字段类型校验：
- 字段值规则校验：
- 权限校验：
- 业务规则校验：

#### 11. 字段校验结论
- 

#### 12. 异常问题
- 

#### 13. 对项目适配影响
- 

#### 14. 是否阻塞后续
- 是 / 否
- 阻塞原因：

#### 15. 下一步动作
- 
```

## 7. 单接口执行卡片

以下卡片按执行顺序排列。执行时只能从上往下推进。

### 01. Token 初始化

- 执行序号：01
- 联调状态：未开始
- 适配状态：已适配
- 项目入口：`POST /api/douyin/tokens`
- 上游能力：`token.create`
- 代码路径：
  - `DoudianTokenGateway#createToken`
  - `RealDouyinAuthGateway#createToken`
  - `DouyinTokenService#exchangeCodeAndBootstrap`

#### 执行重点

1. 验证授权码是否能换出 token
2. 验证 Redis 是否成功写入 token / refresh token / expire_at
3. 验证 `appId` 与配置 `clientKey` 是否一致

#### 验收标准

- `hasAccessToken=true`
- `hasRefreshToken=true`
- `tokenExpireAtEpochSeconds` 大于当前时间
- 未出现 `reauthorizeRequired=true`

#### 联调结果记录区

- 当前进度：2026-05-08 已在 real-pre `real` profile 下完成 raw probe 负向取证；接口可达真实上游，但当前授权主体不可调用
- 文档依据校验：按 `docs/archive/runbooks/15-real-pre后续联调执行清单.md` 使用 `POST /api/douyin/promotion-link-probes/raw` 透传 `method=buyin.kolProductShare`；本轮只验证 fallback 返回结构，不改变 `10` 主转链已通过结论
- 调用情况：登录 real-pre 获取管理员 JWT 后，调用 raw probe，透传 `product_id` 与 `pick_extra`
- 请求参数：`{"method":"buyin.kolProductShare","product_id":"3811489772686409810","pick_extra":"fallback_11_probe"}`
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入文档与样本
- 返回数据：项目接口 HTTP 200、统一响应 `code=200`，业务层 `status=failed`；上游错误 `40003 / isv.authorization-type-invalid`，`logId=20260508172433617E6C942B129503F322`
- HTTP 状态码：200
- 报错信息：`auth2协议验签失败, 当前用户无法访问该接口, 授权主体不匹配 请仔细检查`
- 数据校验结果：raw probe 已证明该方法可被项目转发到真实上游；当前没有返回链接字段、`pick_source` 或等价归因字段
- 异常问题：当前机构 / 团长授权主体与 `buyin.kolProductShare` 所需主体不匹配，fallback-1 无法作为当前 real-pre 主体下的可用转链兜底
- 适配状态结论：权限 / 授权主体待确认；不阻塞主转链链路，因为 `buyin.instPickSourceConvert` 已在 `10` 通过
- 是否阻塞下一个接口：否
- 下一步：继续执行 `12 转链 fallback-2`，核对 `buyin.getProductShareMaterial` 在当前授权主体下的真实返回

### 02. Token 刷新

- 执行序号：02
- 联调状态：成功
- 适配状态：已适配
- 项目入口：`POST /api/douyin/token-refreshes`
- 上游能力：`token.refresh`
- 代码路径：
  - `DoudianTokenGateway#refreshToken`
  - `RealDouyinAuthGateway#refreshToken`
  - `DouyinTokenService#refreshToken`

#### 执行重点

1. 验证 refresh token 是否可用
2. 验证刷新后 access token 缓存是否仍有效，并记录是否发生轮换
3. 验证过期时间是否保持有效或后移

#### 验收标准

- 刷新成功
- Redis 中 token 缓存保持有效，并记录本次是否发生轮换
- 未触发重新授权标记

#### 联调结果记录区

- 当前进度：2026-05-07 已完成 real-pre 首轮真实刷新验证
- 文档依据校验：符合 `docs/05` 中 `/api/douyin/token-refreshes` 联调接口、`docs/03` Test/Real Gateway 隔离契约、`docs/09` AuthGateway 优先联调顺序；本轮未修改 Controller / Service / 前端字段
- 调用情况：先登录 real-pre `POST /api/auth/login` 获取 JWT，再以管理员 JWT 调用 `POST /api/douyin/token-refreshes?appId=<masked>`；调用后补查 `GET /api/douyin/tokens`
- 请求参数：`appId=<masked>`；refresh token 来自 real-pre Redis DB 0 中的 `douyin:refresh:<appId>` 缓存
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入文档与样本
- 返回数据：统一响应 `code=200`、`msg=操作成功`；返回状态显示 `hasAccessToken=true`、`hasRefreshToken=true`、`tokenExpiringSoon=false`、`reauthorizeRequired=false`
- HTTP 状态码：200
- 报错信息：无；后端日志出现 `Douyin token refreshed successfully`
- 原始返回样本：`runtime/qa/out/douyin-token-refresh-20260507-150839/token-refresh-summary.json`、`runtime/qa/out/douyin-token-refresh-20260507-150839/token-refresh-hash-check.json`（仅含脱敏与哈希摘要）
- 数据校验结果：统一响应结构符合项目约定；Token 明文未返回；Redis `douyin:token / douyin:refresh / douyin:token:expire_at` 均存在；`refresh_token` 缺失阻塞已解除
- 异常问题：二次哈希核对显示刷新前后 access token 哈希未变化、过期时间未前移；当前只能判定 `token.refresh` 链路可调用且 refresh token 可用，不能额外宣称上游已轮换出新 access token
- 适配状态结论：已适配；刷新接口可继续作为真实联调 AuthGateway 的基础能力
- 是否阻塞下一个接口：否
- 下一步：执行授权主体确认 `GET /api/douyin/institution-info`，确认当前 token 对应机构/角色后再进入 `04 活动列表联调接口`

### 03. Token 状态查询

- 执行序号：03
- 联调状态：成功
- 适配状态：已适配
- 项目入口：`GET /api/douyin/tokens`
- 上游能力：项目内部能力

#### 执行重点

1. 校验 token 脱敏展示
2. 校验过期时间和即将过期判断
3. 校验是否出现重新授权标记

#### 验收标准

- 字段完整
- 脱敏格式正确
- 状态与 Redis 实际一致

#### 联调结果记录区

- 当前进度：2026-05-07 作为 `02 Token 刷新` 前后置核对再次通过
- 文档依据校验：符合 `docs/05` 中 `/api/douyin/tokens` 联调接口与 `docs/09` AuthGateway 检查项
- 调用情况：调用 `GET /api/douyin/tokens?appId=<masked>`；刷新前后各查一次
- 请求参数：`appId=<masked>`
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入文档与样本
- 返回数据：`hasAccessToken=true`、`hasRefreshToken=true`、`tokenExpiringSoon=false`、`reauthorizeRequired=false`
- HTTP 状态码：200
- 报错信息：无
- 数据校验结果：字段完整；只返回脱敏 token 状态；Redis DB 0 中 `douyin:token / douyin:refresh / douyin:token:expire_at` 与接口状态一致
- 异常问题：无
- 适配状态结论：已适配
- 是否阻塞下一个接口：否
- 下一步：随 `02 Token 刷新` 结论，进入授权主体确认

### 03A. 授权身份确认

- 执行序号：03A
- 联调状态：成功
- 适配状态：已适配
- 项目入口：`GET /api/douyin/institution-info`
- 上游能力：`buyin.institutionInfo`

#### 执行重点

1. 验证当前 token 能否读取授权主体信息
2. 确认返回中包含机构、团长或百应身份字段
3. 为活动、商品、转链与订单接口确认权限主体

#### 验收标准

- 项目接口 HTTP 200
- 统一响应 `code=200`
- 上游响应 `code=10000 / msg=success`
- `remoteResponse.data` 存在并包含机构 / 团长身份信息

#### 联调结果记录区

- 当前进度：2026-05-07 已完成 real-pre 授权身份确认
- 文档依据校验：符合 2026-04-28 补充口径“活动接口前必须先执行授权身份确认”；本轮未修改 Controller / Service / 前端字段
- 调用情况：先登录 real-pre `POST /api/auth/login` 获取 JWT，再调用 `GET /api/douyin/institution-info?appId=<masked>`；调用前用 `GET /api/douyin/tokens` 确认 token 缓存有效
- 请求参数：`appId=<masked>`
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入文档与样本
- 返回数据：项目接口 `status=success`；上游 `code=10000`、`msg=success`、`remoteResponse.data` 存在；返回字段包含 `institution_id`、`colonel.buyin_id/name`、`mcn.buyin_id/name`
- HTTP 状态码：200
- 报错信息：无；后端日志出现 `Douyin API call success, method=buyin.institutionInfo`
- 原始返回样本：`runtime/qa/out/douyin-institution-info-20260507-151422/institution-info-summary.json`、`runtime/qa/out/douyin-institution-info-20260507-151422/institution-info-sanitized-response.json`
- 数据校验结果：统一响应结构符合项目约定；上游成功结构已确认；样本中 appId 已脱敏，未包含 access_token / refresh_token / JWT
- 异常问题：无
- 适配状态结论：已适配；可进入 `04 活动列表联调接口`
- 是否阻塞下一个接口：否
- 下一步：执行 `GET /api/douyin/activities` 采集真实活动列表样本

### 04. 活动列表联调接口

- 执行序号：04
- 联调状态：成功
- 适配状态：已适配
- 项目入口：`GET /api/douyin/activities`
- 上游能力：`alliance.instituteColonelActivityList`

#### 执行重点

1. 拿到真实活动列表原始返回
2. 确认 `status/searchType/sortType/page/pageSize` 参数口径
3. 为后续活动详情与商品列表准备真实 `activityId`

#### 必核字段

- `activity_id`
- `activity_name`
- `status`
- `application_start_time`
- `application_end_time`

#### 联调结果记录区

- 当前进度：2026-05-07 已完成 real-pre 真实活动列表联调接口采样
- 文档依据校验：符合 `docs/05` 中 `/api/douyin/activities` 联调接口、`docs/03` Gateway 契约隔离要求、`docs/09` ActivityGateway 在 AuthGateway 后推进的顺序；本轮未修改 Controller / Service / 前端字段
- 调用情况：先登录 real-pre `POST /api/auth/login` 获取 JWT，再调用 `GET /api/douyin/activities?appId=<masked>`；调用前用 `GET /api/douyin/tokens` 和 `GET /api/douyin/institution-info` 确认 token 与授权主体有效
- 请求参数：`appId=<masked>`；未额外传 `status/searchType/sortType/page/pageSize`
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入文档与样本
- 返回数据：项目接口 `status=success`；上游 `code=10000`、`msg=success`；`remoteResponse.data.total=21`；首批 `activity_list` 20 条；首条样本包含 `activity_id=3916506`、`status=3`、`activity_name`、`activity_start_time`、`activity_end_time`、`application_start_time`、`application_end_time`
- HTTP 状态码：200
- 报错信息：无；后端日志出现 `Douyin API call success, method=alliance.instituteColonelActivityList`
- 原始返回样本：`runtime/qa/out/douyin-activities-20260507-151728/activities-summary.json`、`runtime/qa/out/douyin-activities-20260507-151728/activities-sanitized-response.json`
- 数据校验结果：统一响应结构符合项目约定；上游成功结构已确认；必核字段 `activity_id / activity_name / status / application_start_time / application_end_time` 在首条样本中齐全；样本中 appId 已脱敏，未包含 access_token / refresh_token / JWT
- 异常问题：无；但本接口仅确认联调入口和原始上游返回，不代表 `/api/colonel/activities` 业务标准化字段已验证
- 适配状态结论：已适配；可进入 `05 活动列表业务接口`
- 是否阻塞下一个接口：否
- 下一步：执行 `GET /api/colonel/activities`，对比业务接口与联调接口返回是否一致，并校验页面所需标准化字段

### 05. 活动列表业务接口

- 执行序号：05
- 联调状态：成功
- 适配状态：已适配
- 项目入口：`GET /api/colonel/activities`
- 上游能力：`alliance.instituteColonelActivityList`

#### 执行重点

1. 对比业务接口与联调接口返回是否一致
2. 校验业务标准化字段是否满足页面使用
3. 校验错误映射是否清楚

#### 联调结果记录区

- 当前进度：2026-05-07 已完成 real-pre 活动列表业务接口对照，并补齐业务字段兼容收口
- 文档依据校验：符合 `docs/05` 中 `/api/colonel/activities` 业务入口、`docs/03` Test/Real Gateway 契约与 `docs/09` “真实联调不得反向改坏前端 / Controller / Service”约束；本轮只在业务网关标准化返回层补充兼容字段，不改变上游联调接口结构
- 调用情况：先登录 real-pre `POST /api/auth/login` 获取 JWT，再调用 `GET /api/colonel/activities?status=0&searchType=0&sortType=1&page=1&pageSize=20&appId=<masked>`；调用前已用 `GET /api/douyin/tokens`、`GET /api/douyin/institution-info`、`GET /api/douyin/activities` 确认 token、授权主体和原始活动样本有效
- 请求参数：`status=0`、`searchType=0`、`sortType=1`、`page=1`、`pageSize=20`、`appId=<masked>`
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入文档与样本
- 返回数据：项目接口 HTTP 200、统一响应 `code=200 / msg=操作成功`；`data.total=21`、`data.activityList` 首批 20 条；首条样本 `activityId=3916506`，标准化字段包含 `activityName / activityStartTime / activityEndTime / status / statusText / applicationStartTime / applicationEndTime`
- HTTP 状态码：200
- 报错信息：无；后端日志沿用上游成功日志 `Douyin API call success, method=alliance.instituteColonelActivityList`
- 原始返回样本：`runtime/qa/out/colonel-activities-20260507-164832/colonel-activities-summary.json`、`runtime/qa/out/colonel-activities-20260507-164832/colonel-activities-sanitized-response.json`
- 数据校验结果：统一响应结构符合项目约定；业务接口与 `04 活动列表联调接口` 的 `total=21` 保持一致；标准化字段类型和取值符合页面使用；补充兼容字段 `activityStatus / startTime / endTime` 后，现有 `frontend/src/views/product/ActivityList.vue` 不再因旧字段名导致状态列和起止时间列空白
- 异常问题：首次证据摘要脚本按旧 `records` 结构读取，已修正为 `data.activityList` 口径；同时发现活动页仍在读取旧字段名，本轮已在业务返回层补兼容键，避免为联调临时改前端展示字段
- 适配状态结论：已适配；业务接口已可直接支撑当前活动列表页
- 是否阻塞下一个接口：否
- 下一步：进入 `06 活动详情`，继续使用真实活动 ID `3916506` 采样详情字段

### 06. 活动详情

- 执行序号：06
- 联调状态：成功
- 适配状态：已通未入链
- 项目入口：`GET /api/douyin/activities/{activityId}`
- 上游能力：`buyin.colonelActivityDetail`

#### 执行重点

1. 用真实 `activityId` 拉详情
2. 摸清详情返回字段
3. 判断是否需要补业务 DTO

#### 联调结果记录区

- 当前进度：2026-05-07 已完成 real-pre 活动详情首轮采样
- 文档依据校验：符合 `docs/05` 中 `/api/douyin/activities/{activityId}` 联调入口、`docs/03` Test/Real Gateway 契约与 `docs/09` “活动详情与活动商品字段能回写到当前 DTO 口径”的准备项；本轮只采集真实上游字段样本，未改业务 DTO 与页面
- 调用情况：先登录 real-pre `POST /api/auth/login` 获取 JWT，再调用 `GET /api/douyin/activities/3916506`；调用前已用 `GET /api/douyin/tokens`、`GET /api/douyin/institution-info` 和 `GET /api/douyin/activities` 确认 token、授权主体与活动样本有效
- 请求参数：路径参数 `activityId=3916506`；本轮未额外传 `appId`
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入文档与样本
- 返回数据：项目接口 HTTP 200、统一响应 `code=200 / status=success`；上游 `code=10000 / msg=success`；详情样本字段包含 `activity_id / activity_name / activity_desc / activity_type / apply_start_time / apply_end_time / commission_rate / service_rate / institution_id / colonel_buyin_id / colonel_name / categories / min_promotion_days`
- HTTP 状态码：200
- 报错信息：无；后端日志出现 `Douyin API call success, method=buyin.colonelActivityDetail`
- 原始返回样本：`runtime/qa/out/douyin-activity-detail-20260507-1657/activity-detail-summary.json`、`runtime/qa/out/douyin-activity-detail-20260507-1657/activity-detail-sanitized-response.json`
- 数据校验结果：统一响应结构符合项目约定；上游详情成功结构已确认；加密态 `wechat_id / phone_num` 已在证据文件中脱敏保留占位；当前详情样本未直接给出列表接口中的 `status / activity_start_time / activity_end_time`，说明活动详情 DTO 不能简单复用活动列表字段假设
- 异常问题：无阻塞性报错；但详情字段口径与活动列表存在差异，若后续进入本地业务 DTO 映射，需要单独定义详情视图字段而不是强套列表结构
- 适配状态结论：已通未入链；真实详情接口可调用，但当前仓库尚未形成统一的业务详情 DTO/页面主链
- 是否阻塞下一个接口：否
- 下一步：进入 `07 活动商品联调接口`，使用同一真实活动 ID `3916506` 采样商品列表与分页字段

### 07. 活动商品联调接口

- 执行序号：07
- 联调状态：成功
- 适配状态：已适配
- 项目入口：`GET /api/douyin/activity-product-list`
- 上游能力：`alliance.colonelActivityProduct`

#### 执行重点

1. 获取真实商品返回样本
2. 验证 cursor / page 模式
3. 确认价格、佣金、类目、库存等字段口径

#### 必核字段

- `product_id`
- `title`
- `price`
- `cos_ratio`
- `activity_cos_ratio`
- `cos_type`
- `detail_url`

#### 联调结果记录区

- 当前进度：2026-05-07 已完成 real-pre 活动商品联调接口首轮采样
- 文档依据校验：当前代码真实联调入口实际为 `GET /api/douyin/activity-product-list`，由 `DouyinController#shangpinLiebiao` 调 `ProductApi.listProductsByActivity(...)`；与执行顺序中的“活动商品联调接口”目标一致，但需按代码事实记录实际入口
- 调用情况：先登录 real-pre `POST /api/auth/login` 获取 JWT，再调用 `GET /api/douyin/activity-product-list?activityId=3916506&count=20`；调用前已确认 token、授权主体和活动 ID 有效
- 请求参数：`activityId=3916506`、`count=20`
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入文档与样本
- 返回数据：项目接口 HTTP 200、统一响应 `code=200 / status=success`；上游 `code=10000 / msg=success`；真实商品数组位于 `remoteResponse.data.data`，首批 20 条，`next_cursor` 已返回；首条样本包含 `product_id / title / price / cos_ratio / activity_cos_ratio / cos_type / detail_url / status / promotion_start_time / promotion_end_time`
- HTTP 状态码：200
- 报错信息：无
- 原始返回样本：`runtime/qa/out/douyin-activity-products-20260507-1700/activity-products-summary.json`、`runtime/qa/out/douyin-activity-products-20260507-1700/activity-products-sanitized-response.json`
- 数据校验结果：统一响应结构符合项目约定；真实数组字段不是预期中的 `product_list`，而是 `data.data`；`remoteResponse.data.total=0` 但首批真实列表已返回 20 条并带 `next_cursor`，说明 `total` 字段当前不能直接当成分页总数使用
- 异常问题：样本中存在加密态 `shop_contact`，证据文件已脱敏；后续若写更强的 DTO 校验或联调脚本，应以 `data.data + next_cursor` 作为真实分页口径
- 适配状态结论：已适配；真实上游活动商品能力已确认可用
- 是否阻塞下一个接口：否
- 下一步：执行 `08 活动商品业务接口`，对照本地快照视图与真实上游样本是否一致

### 08. 活动商品业务接口

- 执行序号：08
- 联调状态：成功
- 适配状态：已适配
- 项目入口：`GET /api/colonel/activities/{activityId}/products`
- 上游能力：`alliance.colonelActivityProduct`

#### 执行重点

1. 验证首次调用是否落 `product_snapshot`
2. 验证再次调用是否优先读本地快照
3. 校验业务字段与页面字段是否匹配

#### 联调结果记录区

- 当前进度：2026-05-07 已完成 real-pre 首轮对照，确认业务接口优先返回本地快照视图
- 文档依据校验：符合 `ColonelActivityController#listProducts` 当前实现“本地快照优先、未命中才回退真实上游再落库”的设计；本轮未修改该业务策略
- 调用情况：登录 real-pre 后调用 `GET /api/colonel/activities/3916506/products?count=20`，并与同时间窗口内的 `07 活动商品联调接口` 结果对照
- 请求参数：`activityId=3916506`、`count=20`
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入文档与样本
- 返回数据：项目接口 HTTP 200、统一响应 `code=200`；业务视图返回 `total=10`、`items` 10 条、`nextCursor=""`；首条样本 `productId=3810562766247428542`，已带 `bizStatus / bizStatusLabel / promotion / systemTags / alertTags` 等业务扩展字段
- HTTP 状态码：200
- 报错信息：无
- 原始返回样本：`runtime/qa/out/colonel-activity-products-20260507-1700/activity-products-summary.json`、`runtime/qa/out/colonel-activity-products-20260507-1700/activity-products-sanitized-response.json`
- 数据校验结果：业务字段结构完整，说明活动商品业务视图可用；但与 `07` 的实时上游样本相比，本轮业务接口只返回已有本地快照 10 条，未覆盖上游实时返回的 20 条商品样本
- 异常问题：`hasActivitySnapshots(activityId)` 命中后直接走本地快照视图，导致本轮真实上游新样本未进入业务响应；这不是接口报错，但属于“实时联调样本与业务快照口径未对齐”的缺口
- 适配状态结论：已适配但当前仅“部分成功”；业务主链路可用，真实联调场景下仍需补一轮“快照刷新策略”评估，明确是否增加主动刷新入口或首屏刷新策略
- 是否阻塞下一个接口：否
- 下一步：进入 `09 商品素材状态` 之外，优先评估是否直接推进 `10 转链主接口`，或先补一条活动商品快照刷新策略记录

### 09. 商品素材状态

- 执行序号：09
- 联调状态：部分成功
- 适配状态：已通未入链
- 项目入口：`POST /api/douyin/product-material-status-checks`
- 上游能力：`buyin.materialsProductStatus`

#### 执行重点

1. 验证该接口是否确实不需要 access token
2. 验证 `products <= 50`
3. 确认返回状态字段含义

#### 联调结果记录区

- 当前进度：2026-05-08 已在 real-pre `real` profile 重启后复验通过；接口可命中真实上游成功分支，仍属于“已通未入链”
- 文档依据校验：当前代码通过 `ProductApi.materialsProductStatus()` 走 `postWithoutAuth("buyin.materialsProductStatus", params)`，与本轮“验证该接口是否确实不需要 access token”的目标一致；本轮未修改 Controller / Service / 前端字段
- 调用情况：登录 real-pre `POST /api/auth/login` 获取管理员 JWT 后调用 `POST /api/douyin/product-material-status-checks`；先用纯数字商品 ID 复核失败分支，再用抖店商品详情 URL 复核成功分支
- 请求参数：失败样例 `{"products":["3811489772686409810","3793873371549270033"]}`；成功样例 `{"products":["https://haohuo.jinritemai.com/views/product/detail?id=3811489772686409810","https://haohuo.jinritemai.com/views/product/detail?id=3793873371549270033"]}`
- 请求头：`Authorization: Bearer <JWT>`（项目接口层需要 JWT；上游接口本身未使用 access_token）；JWT 未入文档与样本
- 返回数据：数字 ID 样例返回业务层 `status=failed`、上游 `40004 / isv.parameter-invalid:257 / 参数校验失败`、`logId=20260508172331FF08AFF8E27C9DAA7760`；商品 URL 样例返回业务层 `status=success`、上游 `code=10000 / msg=success`、`logId=202605081723489AE12772C61172A0EE72`、`data` 2 条
- HTTP 状态码：项目接口统一为 200；成功与失败差异体现在 `data.status` 与上游错误码
- 报错信息：纯数字商品 ID 不符合上游 `products` 参数口径，返回 `40004 / isv.parameter-invalid:257`
- 数据校验结果：统一响应结构符合项目约定；成功分支每条数据包含 `status / join_alliance / promotion_status / can_share / product_url`，两条样本 `status=0`；说明该接口有效入参为抖店商品详情 URL，不是纯商品数字 ID
- 异常问题：当前接口只验证素材状态查询能力，尚未接入商品主链路；字段含义仍需结合后续真实素材场景继续标注
- 适配状态结论：已通未入链；不依赖商品详情权限包，不阻塞继续执行转链 fallback 探针
- 是否阻塞下一个接口：否
- 下一步：继续执行 `11 转链 fallback-1` 与 `12 转链 fallback-2`，仅验证真实返回结构，不改变主转链已通过的结论

### 10. 转链主接口

- 执行序号：10
- 联调状态：成功
- 适配状态：已适配
- 项目入口：`POST /api/colonel/activities/{activityId}/products/{productId}/promotion-links`
- 上游能力：`buyin.promotion.link.generate`

#### 执行重点

1. 验证主转链方法是否可用
2. 校验 `promoteLink`、`shortLink`、`pickSource`
3. 校验 `promotion_link` 与 `pick_source_mapping` 是否落库

#### 联调结果记录区

- 当前进度：2026-05-07 已完成两轮 real-pre 取证与一次兼容修复验证；当前主转链接口已可稳定返回可用推广链接
- 文档依据校验：符合当前商品主链路状态机 `PENDING_AUDIT -> APPROVED -> ASSIGNED -> LINKED -> FOLLOWING`；`ProductBizStatusService.ensureAllowed(...)` 明确要求 `PROMOTION_LINK` 前置状态为 `ASSIGNED`
- 调用情况：
  1. 首轮使用 `channel_staff` 直接对 `productId=3809019427689726334` 转链，商品处于 `APPROVED + selectedToLibrary=true`，返回 `code=460`：`当前状态不允许执行PROMOTION_LINK，当前状态：APPROVED`
  2. 首轮由 `biz_leader` 误分配给 `channel_staff`，返回 `code=460`：`只能分配给本组招商下属`
  3. 首轮改为先分配给 `biz_staff` 后再转链，主接口返回 `code=200`，但项目解析层未识别真实上游 `data.product_url`，因此详情页误判为“生成失败”
  4. 增加 `POST /api/douyin/promotion-link-probes/raw` 探针后确认：`buyin.instPickSourceConvert` 真实成功响应只有 `data.product_url`
  5. 补齐 `PromotionApi.PromotionLinkResult.from(...)` 对 `product_url` 的兼容后，使用新样本 `productId=3810562766247428542` 重新执行“审核 -> 分配 -> 转链”，接口与详情回读均恢复正常
- 请求参数：
  - 主链路成功样本：`activityId=3916506`、`productId=3810562766247428542`
  - 转链请求体：`{"externalUniqueId":"real-pre-fix-20260507-193023","promotionScene":4,"needShortLink":true,"scene":"PRODUCT_LIBRARY"}`
- 请求头：`Authorization: Bearer <JWT>`；本轮分别使用 `biz_staff`（审核）、`biz_leader`（分配）、`channel_staff`（转链）、`admin`（raw probe）登录态，JWT 未入文档与样本
- 返回数据：
  - 审核后 `bizStatus=APPROVED`
  - 分配后 `bizStatus=ASSIGNED`、`assigneeName=招商专员测试 (biz_staff)`
  - 转链成功调用返回 HTTP 200、统一响应 `code=200`，本轮 `data` 包含 `pickSource / pickExtra / shortId / promoteLink / uuidSeed`
  - 转链后详情回读：`bizStatus=LINKED`、`promotionLinkStatus=READY`、`promotionLinkStatusLabel=已生成`、`promotionLinkCount=1`、`promotion.copyEnabled=true`
  - raw probe 结果：`buyin.instPickSourceConvert` 上游返回 `code=10000 / msg=success`，`dataKeys=["product_url"]`
- HTTP 状态码：200
- 报错信息：
  - 首轮直接转链失败：`当前状态不允许执行PROMOTION_LINK，当前状态：APPROVED`
  - 首轮错误分配失败：`只能分配给本组招商下属`
  - 修复后同类错误未再复现
- 原始返回样本：
  - 首轮失败取证：`runtime/qa/out/colonel-promotion-link-20260507-171507/promotion-link-summary.json`
  - 字段差异取证：`runtime/qa/out/promotion-raw-probe-20260507-193058/promotion-raw-probe-summary.json`
  - 修复后成功样本：`runtime/qa/out/colonel-promotion-link-fixed-20260507-193023/promotion-link-summary.json`
  - 修复后详情回读：`runtime/qa/out/colonel-promotion-link-fixed-20260507-193023/detail-after-promotion-response.json`
- 数据校验结果：
  - `pick_source` 已生成并可回读
  - `promoteLink` 已可在主接口返回体与详情页中拿到
  - `promotion_link` 记录已落库（详情 `promotionLinkCount=1`）
  - `shortLink` 在真实上游当前仍为空，但不影响详情页进入 `READY / 已生成`、也不影响页面复制主推广链接
- 异常问题：当前真实上游 `buyin.instPickSourceConvert` 仅回 `product_url`，不回 `short_link / converted_link`；项目需要对该字段做兼容，不能只依赖旧字段名
- 适配状态结论：成功；主接口已可命中真实上游、生成可用 `promoteLink`、推进状态并落库
- 是否阻塞下一个接口：否
- 下一步：11/12 fallback 仍可保留为补充验证项，但不再阻塞继续推进订单与解密接口

### 11. 转链 fallback-1

- 执行序号：11
- 联调状态：部分成功
- 适配状态：权限待补齐
- 项目入口：同 10
- 上游能力：`buyin.kolProductShare`

#### 执行重点

1. 验证主方法失效时能否自动切到 fallback-1
2. 验证 fallback-1 返回字段是否可兼容当前项目

#### 联调结果记录区

- 当前进度：2026-05-08 已在 real-pre `real` profile 下完成 raw probe 负向取证；接口可达真实上游，但上游明确返回接口不再提供服务
- 文档依据校验：按 `docs/archive/runbooks/15-real-pre后续联调执行清单.md` 使用 `POST /api/douyin/promotion-link-probes/raw` 透传 `method=buyin.getProductShareMaterial`；本轮只验证 fallback 返回结构，不改变 `10` 主转链已通过结论
- 调用情况：登录 real-pre 获取管理员 JWT 后，调用 raw probe，透传 `product_id` 与 `pick_extra`
- 请求参数：`{"method":"buyin.getProductShareMaterial","product_id":"3811489772686409810","pick_extra":"fallback_12_probe"}`
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入文档与样本
- 返回数据：项目接口 HTTP 200、统一响应 `code=200`，业务层 `status=failed`；上游错误 `90000 / isp.unknown-error`，`logId=20260508172458973387994C2D72029248`
- HTTP 状态码：200
- 报错信息：`8194:接口已不再提供服务`
- 数据校验结果：raw probe 已证明该方法可被项目转发到真实上游；当前没有返回链接字段、`pick_source` 或等价归因字段
- 异常问题：`buyin.getProductShareMaterial` 当前在真实上游返回“接口已不再提供服务”，不适合作为当前 real-pre 主体下的可用转链兜底
- 适配状态结论：上游能力不可用；不阻塞主转链链路，因为 `buyin.instPickSourceConvert` 已在 `10` 通过
- 是否阻塞下一个接口：否
- 下一步：后续真实转链继续以 `buyin.instPickSourceConvert` 为主；若要保留 fallback 逻辑，需要将 `buyin.kolProductShare` 的主体要求与 `buyin.getProductShareMaterial` 的下线状态同步到后续治理项

### 12. 转链 fallback-2

- 执行序号：12
- 联调状态：失败
- 适配状态：上游已下线
- 项目入口：同 10
- 上游能力：`buyin.getProductShareMaterial`

#### 执行重点

1. 验证 fallback-2 是否可命中
2. 验证返回的链接字段是否能被 `PromotionApi.PromotionLinkResult.from(...)` 正确吸收

#### 联调结果记录区

- 当前进度：
- 文档依据校验：
- 调用情况：
- 请求参数：
- 请求头：
- 返回数据：
- HTTP 状态码：
- 报错信息：
- 数据校验结果：
- 异常问题：
- 适配状态结论：
- 是否阻塞下一个接口：
- 下一步：

### 13. 多结算订单查询

- 执行序号：13
- 联调状态：部分成功
- 适配状态：已通未入链
- 项目入口：`GET /api/douyin/order-settlements`
- 上游能力：`buyin.colonelMultiSettlementOrders`
- 授权前提：当前 `access_token` 对应主体需完成团长授权
- 接口属性：免费 API；用于查询团长分次结算订单

#### 执行重点

1. 采样真实订单返回结构
2. 明确金额、状态、时间字段口径
3. 为订单同步映射做准备

#### 联调结果记录区

- 当前进度：2026-05-07 已完成 real-pre 首轮多结算订单查询取证；已确认成功结构、空数据分支与 90 天时间窗边界校验
- 文档依据校验：本地入口符合 `docs/05` 中 `GET /api/douyin/order-settlements`，上游能力符合 `buyin.colonelMultiSettlementOrders`；本轮只补调试回显与文档状态，未把该接口与 `15 订单同步主接口` 的 `buyin.instituteOrderColonel` 混为同一链路
- 调用情况：
  1. `timeType=update`、近 7 天窗口：HTTP 200，统一响应 `code=200`，上游 `code=10000 / msg=success`
  2. `timeType=update`、近 30 天窗口：HTTP 200，统一响应 `code=200`，上游 `code=10000 / msg=success`
  3. 超出 `t-90d` 的时间窗：HTTP 200，统一响应 `code=200`，业务层 `status=failed`，上游 `40004 / isv.parameter-invalid:1036`
- 请求参数：
  - 成功样本：`size=20&cursor=0&timeType=update&startTime=2026-04-30 19:33:41&endTime=2026-05-07 19:33:41`
  - 边界样本：`size=20&cursor=0&timeType=update&startTime=2026-02-06 00:00:00&endTime=2026-05-07 23:59:59`
  - 补充说明：项目联调入口已兼容 `orderIds` / `order_ids` 订单号查询，最多 100 个，和时间范围二选一
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入样本
- 返回数据：
  - 成功样本 `remoteResponse.data` 当前键为 `cursor`、`orders`
  - 当前 7/30 天窗口样本 `orders=[]`、`cursor=""`
  - 边界失败样本 message=`参数校验失败：无效时间区间（开始时间不能大于结束时间，并且不能小于t-90d）`
- HTTP 状态码：项目接口统一为 200；成功与失败差异体现在 `data.status` 与上游错误码
- 原始返回样本：
  - 成功空样本：`runtime/qa/out/douyin-order-settlements-20260507-193340/order-settlements-summary.json`
  - 成功原始结构：`runtime/qa/out/douyin-order-settlements-20260507-193340/order-settlements-response.json`
  - 边界失败样本：`runtime/qa/out/douyin-order-settlements-boundary-20260507-193636/order-settlements-boundary-summary.json`
- 数据校验结果：
  - 已确认真实上游数组字段不是历史猜测的 `data.order_list`，而是 `data.orders`
  - 已确认分页字段当前为 `data.cursor`
  - 已确认上游对查询时间窗有 `t-90d` 边界校验
  - 由于当前窗口内无真实订单，金额、状态、结算时间、归因字段仍未拿到非空样本，暂不能据此定义订单 DTO
- 异常问题：当前仅拿到空订单样本，说明接口可用但缺少“非空真实订单”来完成字段口径固化；同时该接口是多结算查询，不可直接替代 `15` 所依赖的 `buyin.instituteOrderColonel`
- 业务边界补记：该接口虽为免费 API，但依然受“团长授权主体”约束；后续若命中无权限或空样本，需优先核对授权主体，而不是把问题误判为订单同步主链路异常
- 适配状态结论：部分成功；查询链路已通，返回结构已确认，但仍处于“已通未入链”，后续需继续补非空样本与订单同步所需的另一条上游映射
- 是否阻塞下一个接口：否
- 下一步：当前保持 `13` 的“空样本成功 + 边界失败已取证”阶段结论，并继续等待真实多结算样本

### 14. 订单解密（历史项）

- 执行序号：14
- 联调状态：部分成功
- 适配状态：已适配
- 项目入口：`OrderDecryptService#decryptPhones`
- 上游能力：
  - `order.batchDecrypt`
  - 历史探针：`order.batchSensitiveDataRequest`
  - 历史探针：`order.batchSensitive`

#### 执行重点

1. 以官方正式口径核对 `order.batchDecrypt`
2. 记录历史老接口 / 猜测接口是否仍可用
3. 验证普通号与虚拟号解析逻辑

#### 联调结果记录区

- 当前进度：2026-05-07 已完成 real-pre 首轮负向取证；已确认本地解密入口可达，并能命中真实上游错误分支
- 文档依据校验：当前对外入口实际为 `POST /api/orders/phone-decryptions`，由 `DataController#decryptOrderPhones -> OrderDecryptService#decryptPhones -> DouyinOrderGateway.decryptSensitiveData(...)` 触发；与 `docs/05` 的数据平台解密接口一致
- 调用情况：登录 real-pre 后，以管理员 JWT 调用 `POST /api/orders/phone-decryptions`，请求体 `{"orderIds":["MOCK_SEED_TALENT_D_ORDER"]}`；该订单号来自 real-pre 本地库中的 Mock 种子单
- 请求参数：`orderIds=["MOCK_SEED_TALENT_D_ORDER"]`
- 请求头：`Authorization: Bearer <JWT>`；JWT 未入样本
- 返回数据：项目接口 HTTP 200，统一响应 `code=460`，错误信息为 `抖店接口错误[40003]: auth2协议验签失败, 当前用户无法访问该接口, 授权主体不匹配 请仔细检查`
- HTTP 状态码：200（项目统一响应）；业务失败码：460；上游错误码：40003
- 原始返回样本：`runtime/qa/out/order-phone-decrypt-20260507-194415/order-phone-decrypt-summary.json`
- 数据校验结果：
  - 已确认本地解密入口、真实网关和上游调用链路可达
  - 当前失败原因不是接口下线，也不是本地参数格式校验，而是“订单号所属授权主体与当前 token 不匹配”
-  - 通过 raw probe 已进一步确认：旧接口 `order.batchSensitiveDataRequest` 返回 `70000 / isp.api-service-off / API不存在或API已下线`，当前项目里的 fallback 方向是正确的
-  - 历史 raw probe 调用 `order.batchSensitive` 且传 `cipher_infos=["MOCK_SEED_TALENT_D_ORDER"]` 时，返回 `40003` 授权主体不匹配；但现已确认这组参数结构本身不符合官方 `cipher_infos=[{auth_id,cipher_text}]` 契约，因此该结果只能作为“接口可达 / 主体不匹配”的辅助证据，不能当作正式成功口径
-  - 已补核官方文档 `https://op.jinritemai.com/docs/api-docs/15/982`：正式方法名为 `/order/batchDecrypt`（`method=order.batchDecrypt`），需店铺授权、物流商授权，并具备“消费者敏感数据解密”相关权限包；最大单次 50 条
-  - 官方文档已明确 `cipher_infos` 结构为 `[{auth_id: 订单号或售后单号, cipher_text: 待解密密文}]`；说明当前主要缺口不是“没有文档”，而是“没有当前授权主体名下真实订单及其密文字段样本”
- 异常问题：real-pre 本地库当前只有 Mock 种子订单 `MOCK_SEED_TALENT_D_ORDER`，不属于当前真实授权主体，无法用于完成成功样本验证
- 适配状态结论：部分成功；解密链路已命中真实上游且失败原因可见，但仍缺真实授权主体名下订单号，尚未拿到成功解密样本
- 业务口径补记：当前项目已明确“订单展示、归因与看板以上游已返回字段为准”，且当前产品范围已移除订单手机号解密功能，因此本节仅保留历史联调取证价值
- 是否阻塞下一个接口：否
- 阻塞原因：历史记录；当前范围已移除该功能
- 下一步：无；保留历史证据，不再继续推进
- raw probe 证据：`runtime/qa/out/order-decrypt-raw-probes-20260507-194927/order-decrypt-raw-probes-summary.json`

### 15. 订单同步主接口

- 执行序号：15
- 联调状态：未开始
- 适配状态：代码待补全
- 项目入口：`POST /api/orders/sync`
- 上游能力：`buyin.instituteOrderColonel`

#### 当前阻塞事实

`RealDouyinOrderGateway.listSettlement(...)` 当前没有把上游订单映射到 `OrderListResult.orders`，因此 `OrderSyncService` 无法用真实数据落库。

#### 执行前必须先补

1. 订单返回字段映射
2. `hasMore/nextCursor` 处理
3. `pick_source`、金额、时间、状态字段校准

#### 联调结果记录区

- 当前进度：
- 文档依据校验：
- 调用情况：
- 请求参数：
- 请求头：
- 返回数据：
- HTTP 状态码：
- 报错信息：
- 数据校验结果：
- 异常问题：
- 适配状态结论：
- 是否阻塞后续接口：
- 下一步：

### 16-23 后置接口

以下接口保留独立执行原则，但建议在 P0 主链路稳定后再做：

- 16 活动创建
- 17 活动更新
- 18 活动商品取消
- 19 活动商品取消-原始报文
- 20 Webhook 接收
- 21 `buyin.instPickSourceConvert`
- 22 商品详情 Real
- 23 商品 SKU Real

这些接口执行时，仍然必须按“单接口模板”逐项回写，不得合并成一条结果。

## 8. 每次联调后的对外反馈格式

每完成一个接口，对内文档回写后，对外同步也必须使用固定格式：

```md
【接口联调进度】
- 当前接口：
- 执行序号：
- 当前状态：

【文档校验】
- 依据文档：
- 鉴权方式核对：
- 请求方法/协议核对：
- 参数与字段规则核对：
- 权限与业务规则核对：

【调用情况】
- 环境：
- 入口：
- 请求参数：
- 请求头：
- 调用结果：

【返回数据】
- HTTP 状态码：
- 关键字段：
- 原始返回样本位置：
- 数据校验结果：

【异常问题】
- 

【适配结论】
- 

【下一步】
- 
```

禁止只反馈“调通了”或“失败了”，必须带字段和结论。

## 9. 当前执行建议

从现在开始，联调顺序建议固定为：

1. 01 Token 初始化
2. 02 Token 刷新（2026-05-07 已通过，后续仅在 token 即将过期或重建缓存时复测）
3. 03 Token 状态查询（已通过，作为后续接口前置核对）
4. 授权身份确认 `GET /api/douyin/institution-info`
5. 04 活动列表联调接口（2026-05-07 已通过，后续仅在 token / 权限 / 参数口径变化时复测）
6. 05 活动列表业务接口
7. 06 活动详情
8. 07 活动商品联调接口
9. 08 活动商品业务接口
10. 09 商品素材状态
11. 10 转链主接口
12. 11 转链 fallback-1
13. 12 转链 fallback-2
14. 13 多结算订单查询
16. 补代码后再进 15 订单同步主接口

## 10. 本文档使用说明

后续真实联调执行时，所有操作统一按本文推进：

- 只做一个接口
- 只反馈一个接口
- 只回写一个接口
- 未形成接口结论前，不进入下一个接口

这样做的目的，是把“联调准备文档”转成“可落地执行文档”，确保整个过程有顺序、有结果、有记录、有闭环。

## 11. 2026-05-08 店铺侧订单接口补充联调

本轮补充联调对象：

- `order.searchList`
- `order.orderDetail`

执行环境：

- `real-pre`
- 后端入口：`http://localhost:8081/api`
- 当前应用：`7623...7199`

前置核对：

- `GET /api/douyin/tokens` 返回 HTTP 200，当前缓存存在 `access_token / refresh_token`
- `GET /api/douyin/institution-info` 返回 HTTP 200，上游 `code=10000 / msg=success`

调用结论：

- 通过现有 raw 入口 `POST /api/douyin/promotion-link-probes/raw` 透传 `method=order.searchList`
- 最近 6 小时、`page=0`、`size=10` 查询已到达上游，但返回 `30001 / isv.app-permissions-insufficient`
- `size=100`、`page=1`、90 天边界均被权限校验提前拦截，暂不能验证分页和时间边界分支
- 通过同一 raw 入口透传 `method=order.orderDetail`，随机 `shop_order_id` 与空 `shop_order_id` 均返回 `30001 / isv.app-permissions-insufficient`

同步复核：

- 团长侧 `POST /api/orders/sync` 仍返回 HTTP 200，`failed=0`
- `/api/orders` 与 `/api/dashboard/metrics?timeField=createTime` 可读取当前真实同步订单统计

阻塞结论：

> 店铺侧订单接口不是本地链路或签名问题，当前明确阻塞于订单管理接口权限包 / 店铺授权未对当前应用生效。下一轮必须等权限生效后，从 `docs/archive/records/18-抖店店铺侧订单接口联调备忘录.md` 第 11 节 checklist 继续。

## 12. 2026-05-08 三方联调 SOP 防护补查

按三方接口联调 SOP，本轮在继续调用业务接口前先补查了工程底座：

- 网络层：本机到 `openapi-fxg.jinritemai.com:443` TCP 可达
- 超时层：抖店 `douyinRestTemplate` 默认从 `15s / 30s` 收紧为 `connectTimeout=3s`、`readTimeout=5s`
- 配置层：新增 `DOUYIN_CONNECT_TIMEOUT`、`DOUYIN_READ_TIMEOUT` 覆盖入口，默认值写入 `application.yml`
- 日志层：网络异常不再带原始 `ResourceAccessException` cause 上抛或打印，避免完整请求 URL 中的 `access_token / sign` 出现在 Controller 日志、全局异常日志或容器日志中
- Webhook 层：回调验签失败、JSON 解析失败、正常接收均只记录签名是否存在、签名长度、事件名、bodyLength、异常类型，不记录原始请求体、手机号、token 或签名值
- 业务错误层：上游业务错误仍保留 `code / subCode / logId / endpoint / msg`，便于向抖店平台提单

验证命令：

- `mvn -Dtest=DouyinConfigTest test`
- `mvn -Dtest=DouyinApiClientTest#post_shouldNotLogOrThrowSensitiveRequestUrlWhenTransportFails test`
- `mvn -Dtest=DouyinWebhookControllerTest test`

验证结论：

> 当前真实联调底座已补齐“短超时 + 敏感 URL 不落日志”的防护。权限包未生效前，店铺侧订单补全仍不进入业务实现；后续继续以 raw probe 与 Gateway 契约验证为先。

### 12.1 real-pre 应用复核

已执行：

- 重建并重启 `backend-real-pre`
- 确认容器实际环境变量：`DOUYIN_TEST_ENABLED=false`、`APP_TEST_ENABLED=false`、`ORDER_SYNC_ENABLED=false`、`DOUYIN_CONNECT_TIMEOUT=3s`、`DOUYIN_READ_TIMEOUT=5s`
- 重放 Token 状态、授权主体、店铺侧 `order.searchList`、团长侧 `POST /api/orders/sync`、`/api/orders`、Dashboard `createTime` 指标

复核结果：

- Token 状态：HTTP 200，`hasAccessToken=true`、`hasRefreshToken=true`
- 授权主体：HTTP 200，上游 `code=10000 / msg=success`
- 店铺侧 `order.searchList`：仍为 `30001 / isv.app-permissions-insufficient`
- 团长侧订单同步：HTTP 200，`failed=0`
- 订单列表：HTTP 200，当前总数 `24`
- Dashboard：HTTP 200，今日订单 `13`、今日 GMV `231.47`
- 容器日志敏感字段扫描：最近 8 分钟未命中 `access_token=`、`refresh_token=`、`sign=`、`app_secret`
- Webhook 日志脱敏 smoke：向 `POST /api/douyin/webhooks/colonel-open-events` 投递包含手机号、token、签名字段的异常 JSON；接口 HTTP 200 返回 `success`，容器日志敏感字段命中数为 0

### 12.2 当前仍差的三方联调项

截至 2026-05-08，本地网络、Token 刷新、授权主体、活动、活动商品、转链、团长侧订单同步、订单列表与 Dashboard smoke 均已有真实上游或 real-pre 证据。剩余缺口集中在以下几类：

1. `POST /api/douyin/tokens`：仍需新的 OAuth 授权码重放一次完整 Token 初始化，补齐“新码换 token”证据。
2. 店铺侧订单接口：`order.searchList`、`order.orderDetail` 已到达上游，但固定返回 `30001 / isv.app-permissions-insufficient`；需订单管理接口权限包、店铺授权与敏感数据授权生效后继续。
3. 订单解密能力：`order.batchDecrypt` 入参契约已在当时联调中确认；现已退出当前产品范围，仅保留为历史取证，不再列为后续增强项。
4. 商品详情 / SKU：业务快照详情可 smoke，但上游 `product.detail` raw probe 仍被 `30001` 权限包阻塞，SKU 字段不能用快照替代。
5. Webhook：接收、验签与日志脱敏已单测通过，尚未完成抖店控制台真实回调投递、业务消费、幂等表或重放补偿。
6. Talent / Logistics：当前仓库没有可直接联调的真实达人资料网关和真实物流网关；`TalentApi` 主要包装 `buyin.instPickSourceConvert`，`LogisticsGateway` 当前只有测试实现，需先补真实接口选型和 Gateway 实现再联调。
7. 限流 / 退避：尚未对真实接口触发 429 或官方限流错误；考虑到当前 POST 能力里包含创建、更新、取消等有副作用方法，不能在 `DouyinApiClient` 上做无差别自动重试，后续应按方法白名单设计查询类退避策略。

2026-05-08 21:19 复核更新：

- 第 1 项已完成，新 OAuth 授权码重放证据见 `docs/archive/records/20-2026-05-08-新授权码三方全流程联调报告.md`
- 当前不再把“真实订单归因未成立”或“订单解密成功样本未补齐”视为阻塞项；团长原生订单 `colonel_native` 归因已跑通，剩余阻塞集中在商品详情 / SKU 权限、店铺侧订单管理权限与 Webhook 业务消费。订单解密相关样本是否齐备，仅保留为历史联调说明。

## 13. 2026-05-08 新授权码三方全流程联调

已按 real-pre 真实上游模式完成一次全流程重放，专用报告见：

- `docs/archive/records/20-2026-05-08-新授权码三方全流程联调报告.md`

本轮关键结论：

- 新 OAuth 授权码换 token 成功，`hasAccessToken=true`、`hasRefreshToken=true`、`reauthorizeRequired=false`
- 授权主体、活动列表、活动详情、活动商品 raw、活动商品业务刷新均返回成功
- 转链 raw probe 使用 `buyin.instPickSourceConvert` 成功，上游 `10000 / success`，返回 `data.product_url`
- 团长侧订单同步近 30 分钟窗口成功：拉取 10 单，新增 7，更新 3，失败 0
- 订单列表总数更新为 31，Dashboard createTime 口径今日订单 20、今日 GMV 370.88
- 店铺侧 `order.searchList / order.orderDetail` 仍为 `30001 / isv.app-permissions-insufficient`
- real-pre 后端最近 500 行日志未命中新授权码、`access_token=`、`refresh_token=`、`sign=`、`DOUYIN_CLIENT_SECRET`、`app_secret`

补充说明：

- 本轮业务转链接口没有选到一个“已入库且状态为 ASSIGNED”的新商品；已转链样本与未入库样本分别被本地状态机和入库前置条件拦截。该结果不代表三方转链失败，已用 raw probe 隔离验证上游转链能力。

## 14. 2026-05-08 接口 10 业务转链复验补记

本轮按真实业务前置流程重新选取活动商品执行接口 10：

- 活动 ID：`3223881`
- 商品 ID：`3686785923229548825`
- 流程：`biz_staff` 审核入库 -> `biz_leader` 分配招商 -> `channel_staff` 调用业务转链接口
- 业务入口：`POST /api/colonel/activities/3223881/products/3686785923229548825/promotion-links`
- 上游方法：`buyin.instPickSourceConvert`
- 结果：HTTP 200 / `code=200`，返回 `pickSource=v.MxZLIw`、`pickExtra=channel_channelstaff`、`promoteLink` 非空
- 详情回读：`bizStatus=LINKED`、`promotionLinkStatus=READY`、`promotion.copyEnabled=true`
- 库内复核：`promotion_link` 新增 `8700cde6-946b-43bd-a778-9a327a1b1ad2`，`pick_source_mapping` 命中并回写 `pick_source=v.MxZLIw` 对应映射

结论：

- 接口 10 当前已完成业务入口复验，不能再按“未开始执行”处理。
- 当前真实订单归因已经成立，但主路径是团长原生订单 `colonel_native -> colonel_buyin_id + activity_id + product_id` 归因，不再依赖渠道侧 `pick_source / pick_extra` 才能形成有效归因。
- 需继续关注 `pickSource=v.MxZLIw` 被多商品复用导致 `pick_source_mapping` 唯一键覆盖的问题；若订单侧只回传 `pick_source`，归因查询需要进一步补商品 / 活动 / `pick_extra` 维度防误配。

## 15. 2026-05-08 订单归因专项修复补记

问题定位：

- 真实订单 raw payload 已能拿到 `colonel_order_info.colonel_buyin_id` 与 `activity_id`
- 旧归因逻辑把 19 位 `colonel_buyin_id` 当作 `pick_source_mapping.short_id` 查询，而 `short_id` 字段长度只有 8-10 位口径，导致真实原生团长订单稳定落入 `NO_PICK_SOURCE`
- 当前 real-pre 历史订单与已有推广映射没有形成精确 `activity_id + product_id` 交集，不能靠补历史种子映射强行归因

修复口径：

- `pick_source_mapping` 新增 `colonel_buyin_id VARCHAR(32)` 与索引
- `RealDouyinOrderGateway` 扁平化 `colonel_order_info` 到订单 raw payload 顶层
- `AttributionService` 原生归因优先按 `colonel_buyin_id + activity_id + product_id` 找唯一映射；历史映射没有 `colonel_buyin_id` 时，可在 `activity_id + product_id` 唯一的前提下兜底；候选多条时保持未归因
- `PickSourceMappingService` 保留 `colonel_buyin_id` 写入入口，为后续真实转链或迁移补齐原生归因字段

验证结果：

- 已执行 real-pre 数据库升级脚本 `alter-pick-source-mapping-colonel-buyin-id.sql`，字段与索引存在
- 已重建并重启 `backend-real-pre`，健康检查 `UP`
- 宿主机定向回归：`AttributionServiceTest`、`PickSourceMappingServiceTest`、`RealDouyinOrderGatewayTest`、`OrderSyncServiceTest`、`OrderSyncPersistenceServiceTest` 共 `36 tests, 0 failures, 0 errors`
- 容器内同一组定向测试退出码 `0`
- 库内复核：2026-05-08 21:19 二次复验后，全库订单统计为 `totalOrders=326 / attributedOrders=326 / unattributedOrders=0 / partialOrders=0 / syncFailedOrders=0`；当前真实原生团长订单归因已不再是 `0`

## 16. 2026-05-09 二级团长归因排查补记

问题定位：

- real-pre 当前未归因订单再次复核后，不再是文档旧快照中的 `18` 单，而是 `21` 单，且全部为 `COLONEL_MAPPING_NOT_FOUND`
- 抽样订单显示：`extra_data.colonel_order_info.colonel_buyin_id` 存在，但一级 `activity_id` 为空；同时 `extra_data.colonel_order_info_second` 全部携带二级 `colonel_buyin_id` 与 `activity_id`
- 旧代码仅把一级 `colonel_order_info` 扁平化到 raw payload 顶层，`AttributionService` 也只消费一级 `colonel_buyin_id`
- 若直接放开按 `colonel_buyin_id` 泛匹配，当前 real-pre 仅有的 `7351155267604218149 -> admin` seed 映射会把订单误归到系统管理员，属于假归因

代码收口口径：

- `RealDouyinOrderGateway` 新增二级团长字段扁平化：`second_colonel_buyin_id / second_colonel_activity_id`
- `AttributionService` 新增二级团长归因候选：
  1. 先尝试一级团长精确匹配
  2. 若存在二级活动信息，则继续尝试二级团长精确匹配
  3. 当订单携带二级活动信息时，禁止退回“仅按 colonel_buyin_id 命中 seed 泛映射”的 admin 误归因路径
- 该改动当前只完成代码与单测，尚未宣称 real-pre 数据已修复

验证结果：

- `backend mvn "-Dtest=RealDouyinOrderGatewayTest,AttributionServiceTest" test`：`21 tests, 0 failures, 0 errors`
- 数据排查结论：这批未归因订单对应的 6 个二级活动、13 组 `activity_id + product_id` 组合当前在本地 `colonel_activity / product_snapshot / product_operation_state` 均无落库记录，因此后续仍需结合活动商品同步链路补业务映射，而不能只靠归因代码兜底
- 2026-05-09 16:31 real-pre 继续复跑后，未归因样本已扩大为 `46` 单；`POST /api/orders/sync` 正确 JSON 调用成功，说明同步接口本身无阻塞
- 已新增管理员收口入口 `POST /api/orders/replay-attribution`，real-pre `dryRun(limit=50)` 返回 `scanned=46 / attributed=0 / unattributed=46 / updated=0`
- 当前本地缺口同步更新为 `10` 个缺失活动、`27` 个缺失 `activity_id + product_id` 组合；后续收口重点已切到补业务映射，而非继续修改二级团长归因代码

## 17. 2026-05-10 P1-2 real-pre 真实链路联调补记

执行环境：

- 后端：`http://localhost:8081/api`
- 前端：`http://localhost:3001`
- profile：`SPRING_PROFILES_ACTIVE=real`
- 数据库：`colonel_saas_real`
- Redis：DB `0`
- 抖音开关：`DOUYIN_TEST_ENABLED=false`、`APP_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`
- 证据目录：
  - `runtime/qa/out/real-pre-p1-2-20260510-020545`
  - `runtime/qa/out/real-pre-p1-2-product-chain-20260510-020840`

本轮环境约束：

- `test` 继续保持 `SPRING_PROFILES_ACTIVE=test`、`colonel_saas_test`、Redis DB `1`、`DOUYIN_TEST_ENABLED=true`
- 本轮未删除 `test` 容器或 volume
- 本轮未把 `test` 改成真实接口环境

逐项结果：

| 项 | 入口 / 操作 | 结果 | 问题与下一步 |
| --- | --- | --- | --- |
| Token 状态 / 刷新 | `GET /api/douyin/tokens`、`POST /api/douyin/token-refreshes`、`GET /api/douyin/institution-info` | Token 缓存可用，刷新返回 `code=200`，授权主体返回上游 `10000 / success` | 暂无；后续 token 失效再执行授权码初始化 |
| 活动列表 | `GET /api/douyin/activities`、`GET /api/colonel/activities` | 上游与业务接口均返回首批 `20` 条，真实总数 `21`，活动样本 `3916506` | 暂无 |
| 活动商品 | `GET /api/douyin/activity-product-list?activityId=3916506&count=20`、`GET /api/colonel/activities/3916506/products?count=20&refresh=true` | 上游返回 `20` 条，业务刷新返回 `20` 条，业务总数 `56` | 暂无 |
| 商品审核 / 入库 | `biz_staff` 审核活动 `3916506` 商品 `3810699728333702016`，随后幂等调用 `library-entry` | 审核后 `bizStatus=APPROVED`、`selectedToLibrary=true`；入库确认仍为 `selectedToLibrary=true` | 发现 real-pre `biz_staff` 测试账号密码漂移，已用管理员接口恢复为文档约定 `admin123`；后续角色流失败先核账号状态 |
| 分配招商 | `biz_leader` 查询可分配负责人后调用 `assignee` | 商品进入 `ASSIGNED`，负责人为招商专员 | 首轮失败原因是前序未审核入库；账号与状态修复后通过 |
| 真实转链 | `channel_staff` 调用 `POST /api/colonel/activities/3916506/products/3810699728333702016/promotion-links` | 上游 `buyin.instPickSourceConvert` 成功；返回 `pickSource=v.MxZLIw`、`pickExtra=channel_channelstaff`、`shortId` 与 `promoteLink`；详情回读 `LINKED / READY / copyEnabled=true` | 上游未返回短链，不阻塞主推广链接 |
| `pick_source_mapping` | real-pre DB 复核 `pick_source_mapping` 与 `promotion_link` | `3916506 / 3810699728333702016` 均存在记录，证据见 `db-mapping-corrected.json` | 首版证据脚本 SQL 引号误报 `0`，已修正复核 |
| 订单同步 | `POST /api/orders/sync` 最近 `7` 天 | `totalFetched=100 / created=5 / updated=95 / attributed=95 / unattributed=5 / failed=0` | 真实接口非空，且无同步失败 |
| 订单归因 | `GET /api/orders/stats?timeField=createTime`、`POST /api/orders/replay-attribution` dry-run | 订单同步后全库 `totalOrders=1802 / attributedOrders=1665 / unattributedOrders=137 / syncFailedOrders=0`；dry-run `scanned=50 / attributed=0 / unattributed=50`；最终收口复核更新为 `totalOrders=1821 / attributedOrders=1683 / unattributedOrders=138` | 未归因仍是本地活动/商品/mapping 缺口，不能用 Mock 数据覆盖成通过 |
| Dashboard | `GET /api/dashboard/metrics?timeField=createTime`、`GET /api/dashboard/summary` | 同步后 `todayOrderCount=617 / todayGmv=12081.57 / serviceFee=181.55 / grossProfit=127.07`；最终收口复核为 `todayOrderCount=636 / todayGmv=12448.27`，Summary 可读 | 后续 M1.6 继续细化真实看板字段口径 |

结论：

P1-2 real-pre 真实链路本轮已完成 Token、活动、活动商品、商品审核入库、分配、真实转链、`pick_source_mapping`、订单同步、订单归因统计与 Dashboard 展示验证。剩余风险继续集中在真实订单未归因样本的本地业务映射缺口，而不是同步入口或 Real Gateway 主链路不可用。
