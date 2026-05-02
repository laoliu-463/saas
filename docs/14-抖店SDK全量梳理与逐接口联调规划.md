# 14-抖店 SDK 接口逐项联调执行文档

更新时间：2026-04-28

## 1. 文档定位

本文是抖店第三方 SDK 联调的专属执行文档，执行口径统一为：

> 严格按照单个接口为单位，逐个依次开展联调；每完成一个接口，立即同步该接口完整联调结果，再进入下一个接口。

本文不再按大模块泛泛描述，而是直接面向“逐接口执行、逐接口回写、逐接口验收”。

与其他文档的关系：

- `docs/09-真实SDK联调准备清单.md`：联调前准备口径
- `docs/14-抖店SDK接口逐项联调执行文档.md`：逐接口执行主文档
- `docs/04-开发进度.md`：阶段状态更新

## 2. 执行总规则

### 2.0 文档优先规则

接口测试必须严格依据现有接口文档、网关契约文档和联调准备文档执行，不允许脱离文档约束自行假设。

本次接口测试的文档依据固定为：

1. `docs/05-接口与数据模型.md`
2. `docs/03-Test与Real网关契约.md`
3. `docs/09-真实SDK联调准备清单.md`
4. 本文档 `docs/14-抖店SDK全量梳理与逐接口联调规划.md`
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
2. 活动列表、活动商品列表、转链、订单解密都已有真实调用入口
3. `RealDouyinOrderGateway` 订单映射未完成，真实订单同步当前不能闭环
4. `RealDouyinProductGateway.queryProductDetail` 未实现
5. `RealDouyinProductGateway.queryProductSkus` 未实现
6. `RealDouyinAuthGateway.ensureToken` 当前返回 `null`
7. Webhook 当前只接收、验签、记日志，未接业务消费

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
5. 已确认活动、商品、推广、订单、解密权限
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
| 01 | Token 初始化 | `token.create` | `POST /api/douyin/tokens` | P0 | 未开始 | 已适配 | 是 |
| 02 | Token 刷新 | `token.refresh` | `POST /api/douyin/token-refreshes` | P0 | 未开始 | 已适配 | 是 |
| 03 | Token 状态查询 | 项目能力 | `GET /api/douyin/tokens` | P0 | 未开始 | 已适配 | 是 |
| 04 | 活动列表联调接口 | `alliance.instituteColonelActivityList` | `GET /api/douyin/activities` | P0 | 未开始 | 已适配 | 是 |
| 05 | 活动列表业务接口 | `alliance.instituteColonelActivityList` | `GET /api/colonel/activities` | P0 | 未开始 | 已适配 | 是 |
| 06 | 活动详情 | `buyin.colonelActivityDetail` | `GET /api/douyin/activities/{activityId}` | P1 | 未开始 | 已通未入链 | 是 |
| 07 | 活动商品联调接口 | `alliance.colonelActivityProduct` | `GET /api/douyin/activities/{activityId}/products` | P0 | 未开始 | 已适配 | 是 |
| 08 | 活动商品业务接口 | `alliance.colonelActivityProduct` | `GET /api/colonel/activities/{activityId}/products` | P0 | 未开始 | 已适配 | 是 |
| 09 | 商品素材状态 | `buyin.materialsProductStatus` | `POST /api/douyin/product-material-status-checks` | P1 | 未开始 | 已通未入链 | 是 |
| 10 | 转链主接口 | `buyin.promotion.link.generate` | `POST /api/colonel/activities/{activityId}/products/{productId}/promotion-links` | P0 | 未开始 | 已适配 | 是 |
| 11 | 转链 fallback-1 | `buyin.kolProductShare` | 同上 | P1 | 未开始 | 已适配 | 是 |
| 12 | 转链 fallback-2 | `buyin.getProductShareMaterial` | 同上 | P1 | 未开始 | 已适配 | 是 |
| 13 | 多结算订单查询 | `buyin.colonelMultiSettlementOrders` | `GET /api/douyin/order-settlements` | P0 | 未开始 | 已通未入链 | 是 |
| 14 | 订单解密 | `order.batchSensitiveDataRequest` / `order.batchSensitive` | `OrderDecryptService` 链路 | P0 | 未开始 | 已适配 | 是 |
| 15 | 订单同步主接口 | `buyin.instituteOrderColonel` | `POST /api/orders/sync` | P0 | 未开始 | 代码待补全 | 否 |
| 16 | 活动创建 | `alliance.colonelActivityCreateOrUpdate` | `POST /api/douyin/activities` | P2 | 未开始 | 已通未入链 | 后置 |
| 17 | 活动更新 | `alliance.colonelActivityCreateOrUpdate` | `PUT /api/douyin/activities/{activityId}` | P2 | 未开始 | 已通未入链 | 后置 |
| 18 | 活动商品取消 | `alliance.colonelActivityProductCancel` | `POST /api/douyin/activity-product-cancellations` | P2 | 未开始 | 已通未入链 | 后置 |
| 19 | 活动商品取消-原始报文 | `alliance.colonelActivityProductCancel` | `POST /api/douyin/activity-product-cancellations/raw` | P2 | 未开始 | 已通未入链 | 后置 |
| 20 | Webhook 接收 | 回调接收 | `POST /api/douyin/webhooks/colonel-open-events` | P1 | 未开始 | 已通未入链 | 是 |
| 21 | `instPickSourceConvert` | `buyin.instPickSourceConvert` | 当前无外部入口 | P2 | 未开始 | 已通未入链 | 否 |
| 22 | 商品详情 Real | 待确认 | 当前无接口 | P1 | 未开始 | 代码待补全 | 否 |
| 23 | 商品 SKU Real | 待确认 | 当前无接口 | P1 | 未开始 | 代码待补全 | 否 |

> 2026-04-28 补充：Token 初始化、Token 状态查询、Token 刷新通过后，活动接口前必须先执行授权身份确认。
> 当前项目入口为 `GET /api/douyin/institution-info`，上游能力为 `buyin.institutionInfo`。
> 若 Redis DB 2 中缺少 `douyin:refresh:<appId>`，该入口会返回 `missing refresh_token, cannot refresh token`，不得继续测活动、商品、转链或订单。

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

### 02. Token 刷新

- 执行序号：02
- 联调状态：未开始
- 适配状态：已适配
- 项目入口：`POST /api/douyin/token-refreshes`
- 上游能力：`token.refresh`
- 代码路径：
  - `DoudianTokenGateway#refreshToken`
  - `RealDouyinAuthGateway#refreshToken`
  - `DouyinTokenService#refreshToken`

#### 执行重点

1. 验证 refresh token 是否可用
2. 验证刷新后 access token 是否更新
3. 验证过期时间是否后移

#### 验收标准

- 刷新成功
- Redis 中 token 被更新
- 未触发重新授权标记

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

### 03. Token 状态查询

- 执行序号：03
- 联调状态：未开始
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

### 04. 活动列表联调接口

- 执行序号：04
- 联调状态：未开始
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

### 05. 活动列表业务接口

- 执行序号：05
- 联调状态：未开始
- 适配状态：已适配
- 项目入口：`GET /api/colonel/activities`
- 上游能力：`alliance.instituteColonelActivityList`

#### 执行重点

1. 对比业务接口与联调接口返回是否一致
2. 校验业务标准化字段是否满足页面使用
3. 校验错误映射是否清楚

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

### 06. 活动详情

- 执行序号：06
- 联调状态：未开始
- 适配状态：已通未入链
- 项目入口：`GET /api/douyin/activities/{activityId}`
- 上游能力：`buyin.colonelActivityDetail`

#### 执行重点

1. 用真实 `activityId` 拉详情
2. 摸清详情返回字段
3. 判断是否需要补业务 DTO

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

### 07. 活动商品联调接口

- 执行序号：07
- 联调状态：未开始
- 适配状态：已适配
- 项目入口：`GET /api/douyin/activities/{activityId}/products`
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

### 08. 活动商品业务接口

- 执行序号：08
- 联调状态：未开始
- 适配状态：已适配
- 项目入口：`GET /api/colonel/activities/{activityId}/products`
- 上游能力：`alliance.colonelActivityProduct`

#### 执行重点

1. 验证首次调用是否落 `product_snapshot`
2. 验证再次调用是否优先读本地快照
3. 校验业务字段与页面字段是否匹配

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

- 当前进度：2026-05-02 已完成首轮 real-pre 验证
- 文档依据校验：当前代码通过 `ProductApi.materialsProductStatus()` 走 `postWithoutAuth("buyin.materialsProductStatus", params)`，与本轮“验证该接口是否确实不需要 access token”的目标一致
- 调用情况：同一接口分别用数字商品 ID 与商品详情 URL 两种口径调用；数字 ID 返回参数校验失败，商品 URL 返回上游成功
- 请求参数：失败样例 `{"appId":"7623665273727387199","products":["101"]}`；成功样例 `{"appId":"7623665273727387199","products":["https://haohuo.jinritemai.com/views/product/detail?id=1"]}`
- 请求头：`Authorization: Bearer <JWT>`（项目接口层需要 JWT；上游接口本身未使用 access_token）
- 返回数据：失败样例 `40004 / isv.parameter-invalid:257 / 参数校验失败`；成功样例 `code=10000, msg=success, data=[]`
- HTTP 状态码：
- 报错信息：
- 数据校验结果：
- 异常问题：
- 适配状态结论：
- 是否阻塞下一个接口：
- 下一步：

### 10. 转链主接口

- 执行序号：10
- 联调状态：未开始
- 适配状态：已适配
- 项目入口：`POST /api/colonel/activities/{activityId}/products/{productId}/promotion-links`
- 上游能力：`buyin.promotion.link.generate`

#### 执行重点

1. 验证主转链方法是否可用
2. 校验 `promoteLink`、`shortLink`、`pickSource`
3. 校验 `promotion_link` 与 `pick_source_mapping` 是否落库

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

### 11. 转链 fallback-1

- 执行序号：11
- 联调状态：未开始
- 适配状态：已适配
- 项目入口：同 10
- 上游能力：`buyin.kolProductShare`

#### 执行重点

1. 验证主方法失效时能否自动切到 fallback-1
2. 验证 fallback-1 返回字段是否可兼容当前项目

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

### 12. 转链 fallback-2

- 执行序号：12
- 联调状态：未开始
- 适配状态：已适配
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
- 联调状态：未开始
- 适配状态：已通未入链
- 项目入口：`GET /api/douyin/order-settlements`
- 上游能力：`buyin.colonelMultiSettlementOrders`

#### 执行重点

1. 采样真实订单返回结构
2. 明确金额、状态、时间字段口径
3. 为订单同步映射做准备

#### 联调结果记录区

- 当前进度：
- 调用情况：
- 返回数据：
- 异常问题：
- 适配状态结论：
- 是否阻塞下一个接口：
- 下一步：

### 14. 订单解密

- 执行序号：14
- 联调状态：未开始
- 适配状态：已适配
- 项目入口：`OrderDecryptService#decryptPhones`
- 上游能力：
  - `order.batchSensitiveDataRequest`
  - `order.batchSensitive`

#### 执行重点

1. 验证老接口是否还能用
2. 验证是否会 fallback 到新接口
3. 验证普通号与虚拟号解析逻辑

#### 联调结果记录区

- 当前进度：
- 调用情况：
- 返回数据：
- 异常问题：
- 适配状态结论：
- 是否阻塞下一个接口：
- 下一步：

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
2. 02 Token 刷新
3. 03 Token 状态查询
4. 04 活动列表联调接口
5. 05 活动列表业务接口
6. 06 活动详情
7. 07 活动商品联调接口
8. 08 活动商品业务接口
9. 09 商品素材状态
10. 10 转链主接口
11. 11 转链 fallback-1
12. 12 转链 fallback-2
13. 13 多结算订单查询
14. 14 订单解密
15. 补代码后再进 15 订单同步主接口

## 10. 本文档使用说明

后续真实联调执行时，所有操作统一按本文推进：

- 只做一个接口
- 只反馈一个接口
- 只回写一个接口
- 未形成接口结论前，不进入下一个接口

这样做的目的，是把“联调准备文档”转成“可落地执行文档”，确保整个过程有顺序、有结果、有记录、有闭环。
