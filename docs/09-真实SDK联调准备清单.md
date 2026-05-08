# 09-真实 SDK 联调准备清单

更新时间：2026-05-08

## 一、文档目标

当前项目已经完成本地 Mock 核心闭环。

下一步不是直接全量切真实接口，而是进入：

> 真实 SDK 联调准备阶段

本清单用于明确：

- 需要准备什么
- 应该先联调哪些 Gateway
- 哪些东西不能被真实联调反向破坏

## 当前准备进度（2026-05-08）

- [x] real-pre 后端 `8081`、PostgreSQL `5433`、Redis `6380` 已稳定可用
- [x] `GET /api/douyin/tokens`、Webhook 验签与日志脱敏、商品素材状态检查已拿到验证结果；2026-05-08 real-pre `real` profile 复验确认 `buyin.materialsProductStatus` 使用商品详情 URL 入参返回上游 `10000 / success`
- [x] `backend mvn test` 当前基线已更新为 `597 tests, 0 failures, 0 errors`（2026-05-06 全量回归）
- [x] real-pre 浏览器全路径回归 `45/45` 通过
- [x] `real-pre` 已切换为 `SPRING_PROFILES_ACTIVE=real`，并补齐 `application-real.yml`；当前真实联调开关为 `DOUYIN_TEST_ENABLED=false`、`APP_TEST_ENABLED=false`、`ORDER_SYNC_ENABLED=true`
- [x] 当前 real-pre Redis DB 0 已存在真实 `access_token / refresh_token / expire_at` 缓存
- [x] `POST /api/douyin/token-refreshes` 已完成真实刷新链路验证：HTTP 200、统一响应 `code=200`、未触发重新授权
- [x] `GET /api/douyin/institution-info` 已完成授权主体确认：HTTP 200、上游 `code=10000 / msg=success`，返回机构 / 团长身份信息
- [x] `GET /api/douyin/activities` 已完成活动列表联调接口采样：上游 `code=10000 / msg=success`，首批 `activity_list` 20 条，已拿到可用于后续接口的真实 `activity_id`
- [x] `GET /api/colonel/activities` 已完成活动列表业务接口对照：`data.total=21`、`data.activityList[0].activityId=3916506`，并已补齐 `activityStatus / startTime / endTime` 兼容字段避免当前页面列渲染错位
- [x] `GET /api/douyin/activities/3916506` 已完成活动详情采样：上游 `code=10000 / msg=success`，已确认详情字段口径与列表字段存在差异
- [x] `GET /api/douyin/activity-product-list?activityId=3916506&count=20` 已完成活动商品联调接口采样：上游 `code=10000 / msg=success`，真实数组位于 `data.data`，首批 20 条并返回 `next_cursor`
- [x] `GET /api/colonel/activities/3916506/products?count=20&refresh=true` 已完成业务接口刷新闭环：旧快照 10 条，强制刷新后回写为 20 条，随后默认查询也返回 20 条；证据目录 `runtime/qa/out/activity-product-refresh-real-20260507-210819`
- [x] `POST /api/douyin/product-material-status-checks` 已在 real-pre `real` profile 复验：纯数字商品 ID 返回 `40004 / isv.parameter-invalid:257`，抖店商品详情 URL 入参返回 `code=10000 / msg=success`，数据字段包含 `status / join_alliance / promotion_status / can_share / product_url`
- [~] `GET /api/douyin/order-settlements` 已完成首轮多结算订单查询取证：上游成功结构键为 `data.cursor / data.orders`，当前 7/30 天窗口样本均为空；已确认超出 `t-90d` 会返回 `40004 / isv.parameter-invalid:1036`
- [~] `POST /api/orders/phone-decryptions` 已完成首轮负向取证：真实链路可达；当前已补核官方口径为 `order.batchDecrypt`，并确认入参必须是 `cipher_infos=[{auth_id,cipher_text}]`；现阶段仍缺当前授权主体名下的真实订单号与对应密文字段样本
- [~] 官方文档 `https://op.jinritemai.com/docs/api-docs/15/982` 已补核到明确契约：接口为 `/order/batchDecrypt`（`method=order.batchDecrypt`），需具备店铺授权、物流商授权与敏感数据解密权限包；文档说明了解密入参结构，但不提供“如何获取真实订单号 / cipher_infos 样本”的取数路径说明
- [x] `RealDouyinOrderGateway` 已补齐 `buyin.instituteOrderColonel` 真实返回映射；上游订单主接口入参时间必须使用 `yyyy-MM-dd HH:mm:ss` 字符串，秒级 / 毫秒级时间戳会返回 `40004 / isv.parameter-invalid:1034`
- [x] `POST /api/orders/sync` 已完成 real-pre 30 分钟窗口真实同步：拉取 10 单、落库 10 单、失败 0、归因 0；证据目录 `runtime/qa/out/orders-sync-real-20260507-203422`
- [~] 真实订单展示 smoke 已完成：`/api/orders` 可读到同步后订单，`/api/dashboard/metrics?timeField=createTime` 可统计到今日 10 单；订单重复同步更新已修复 jsonb `extra_data` 写入问题，真实订单达人字段 `author_buyin_id / author_account` 可保留并展示为 `talentName`；但数据平台订单页与默认 `settleTime` 指标暂不覆盖这批未结算订单，后续 M1.6 需明确 create_time / settle_time 展示口径
- [~] 真实团长订单主接口当前未返回收件人姓名 / 手机 / 地址密文字段；`order.orderDetail`、`order.searchList` raw probe 均返回 `30001 / isv.app-permissions-insufficient`，需要补订单管理接口权限包及对应店铺 / 物流商 / 敏感数据授权后再验证解密成功样本
- [x] 三方联调 SOP 防护补查已完成：本机到 `openapi-fxg.jinritemai.com:443` 可达；抖店 `RestTemplate` 默认超时已收紧为 `connectTimeout=3s / readTimeout=5s`，可通过 `DOUYIN_CONNECT_TIMEOUT / DOUYIN_READ_TIMEOUT` 覆盖；网络异常不再把含 `access_token / sign` 的原始 URL 作为日志 throwable 或上抛 cause；real-pre 后端已重启并确认实际环境为真实上游模式，最近日志未命中 `access_token=`、`refresh_token=`、`sign=` 等敏感字段
- [x] Webhook 日志防护已补齐：验签失败、正常接收、JSON 解析失败均只记录事件名、bodyLength、签名是否存在 / 长度与异常类型，不记录原始 body、手机号、token 或签名值；real-pre 重启后异常 JSON smoke 返回 `success`，容器日志敏感字段命中数为 0
- [x] Token 初始化接口已使用新 OAuth 授权码完成重放：`POST /api/douyin/tokens` HTTP 200、`code=200`，`hasAccessToken=true`、`hasRefreshToken=true`、`reauthorizeRequired=false`；详见 `docs/archive/records/20-2026-05-08-新授权码三方全流程联调报告.md`
- [~] 商品真实样本采集已完成业务快照详情 smoke，但上游 `product.detail` 原始探针当前返回 `30001 / isv.app-permissions-insufficient`，SKU 样本需补商品详情接口权限包后继续；活动商品快照刷新策略已落地为显式 `refresh=true` 强制拉上游并回写，本地默认查询继续保持快照优先

## 当前阻塞事实（2026-05-08）

1. `refresh_token` 缺失阻塞已解除，新的 OAuth 授权码初始化、授权主体确认、活动列表联调接口、活动列表业务接口、活动详情、活动商品联调接口、活动商品业务刷新、转链 raw probe、多结算订单查询首轮取证与订单主同步入库已通过；下一步阻塞点转为真实商品详情样本、订单归因口径与订单解密权限包
2. 官方解密文档已确认正式方法名、授权要求与 `cipher_infos={auth_id,cipher_text}` 结构，但当前团长订单主接口没有返回收件人密文字段；`order.orderDetail` / `order.searchList` 又被 `30001 / isv.app-permissions-insufficient` 权限包挡住，因此若要完成 `14 订单解密` 成功样本验证，需先补订单管理接口权限包、店铺 / 物流商授权与敏感数据解密权限
3. 当前 `3000/8080` 仍是 test / 本地基线；`3001/8081` 已切换到 `real` profile，用于真实上游模式接口联调，但仍是 `real-pre` 容器环境，不得等同生产环境
4. 真实 Gateway 工作必须遵守“只替换 Gateway，不反向破坏现有 Controller / Service / 前端 / 浏览器回归”的约束
5. 当前首轮执行顺序已经明确为：
   - `AuthGateway`
   - 授权主体确认 `GET /api/douyin/institution-info`
   - `ActivityGateway / ProductGateway`
   - `PromotionGateway`
   - `OrderGateway`
6. 在商品详情 / SKU、店铺侧订单详情、订单解密成功样本与 Webhook 真实业务消费未完成前，不应把数据看板真实化或部署验证误判为“真实环境已经就绪”
7. `TalentGateway` 目前没有真实达人资料接口实现；当前 `TalentApi` 主要包装 `buyin.instPickSourceConvert`，不能当作达人信息刷新链路已联通
8. `LogisticsGateway` 当前只有测试实现，真实物流状态查询还缺上游接口选型、真实 Gateway 与样本单号
9. 真实限流 / 429 分支尚未触发；由于抖店 POST 方法里包含活动创建、更新、取消等有副作用能力，后续退避重试必须按接口白名单设计，不能在通用 `DouyinApiClient` 上无差别自动重试

开始本清单前，建议先阅读：

- [archive/records/16-local-mock业务联调记录](./archive/records/16-local-mock业务联调记录.md)
- [archive/records/17-项目剩余事项看板](./archive/records/17-项目剩余事项看板.md)

## 二、联调总原则

### 1. 只替换 Gateway

真实联调时允许替换：

- Gateway 实现类
- SDK 配置
- 认证与 token 管理

真实联调时不允许顺手改坏：

- Controller
- 前端页面
- 主业务 Service
- 现有 Test 闭环

### 2. Test 仍然保留

真实联调不是用 Real 覆盖 Test，而是：

- `test` 环境继续作为演示与回归基线
- `real` 环境用于逐项联调

对于当前项目，更准确的执行口径是：

- 本地 Mock / `test` 基线继续作为演示与回归基线
- `real` 环境用于逐项联调

### 3. 先契约，后实现

每个 Gateway 在切真实前都要先对齐：

- 入参
- 出参
- 错误码
- 空数据分支
- 限流分支
- 状态枚举映射

## 三、联调前置准备

### 1. 环境准备

- [ ] 明确真实联调环境地址
- [ ] 明确回调地址 / Webhook 地址
- [ ] 明确网络访问策略与白名单
- [ ] 明确真实环境是否需要固定出口 IP

### 2. 应用与权限准备

- [ ] 申请 `AppKey / AppSecret`
- [ ] 获取真实测试店铺或测试主体
- [ ] 获取真实授权账号
- [ ] 明确活动、商品、订单、推广、达人、物流相关权限范围

### 3. 数据与配置准备

- [ ] 确认数据库结构已完成当前 SQL 升级
- [ ] 确认 `application-test.yml` 与真实联调配置隔离
- [ ] 确认 token、secret、回调配置不写死在代码中
- [ ] 确认抖店 SDK 依赖通过项目内本地 Maven 仓库加载，而不是回退到 `systemPath`

当前构建口径说明：

- 抖店 SDK Jar 保存在 `backend/lib/`
- Maven 通过 `backend/lib/maven-repo/` 作为项目内本地仓库解析 `com.doudian:open-sdk:1.1.0`
- 不允许再把 SDK 依赖改回 `scope=system + systemPath`

## 四、按 Gateway 拆分的联调清单

### 1. `DouyinAuthGateway`

目标：

- 获取 token
- 刷新 token
- 验证授权信息

检查项：

- [ ] token 获取成功
- [x] token 刷新成功（2026-05-07 real-pre，HTTP 200 / `code=200`）
- [ ] 过期后重试机制明确
- [ ] 无权限错误码有清楚处理

开始条件：

- [ ] 真实 `AppKey / AppSecret`
- [ ] 可用回调地址
- [ ] 真实授权主体
- [ ] 独立环境变量已配置完成

完成条件：

- [ ] 成功建立真实 token
- [x] 刷新 token 成功
- [ ] 失败 / 过期 / 无权限分支有记录
- [ ] 未破坏当前 mock / test 浏览器回归基线

2026-05-07 复核补充：

- 当前 real-pre Redis DB 0 已有 `douyin:token:<appId>`、`douyin:refresh:<appId>`、`douyin:token:expire_at:<appId>`
- `POST /api/douyin/token-refreshes?appId=<masked>` 返回 HTTP 200、统一响应 `code=200`
- `GET /api/douyin/institution-info?appId=<masked>` 返回 HTTP 200、统一响应 `code=200`，上游 `code=10000 / msg=success`
- 后端日志出现 `Douyin token refreshed successfully`
- 后端日志出现 `Douyin API call success, method=buyin.institutionInfo`
- 证据文件：`runtime/qa/out/douyin-token-refresh-20260507-150839/token-refresh-summary.json`
- 授权主体证据文件：`runtime/qa/out/douyin-institution-info-20260507-151422/institution-info-summary.json`
- 哈希复核显示刷新前后 access token 摘要未变化、过期时间未前移；因此本轮只确认刷新链路与 refresh token 可用，不额外宣称上游已轮换新 token

### 2. `DouyinColonelActivityGateway`

目标：

- 获取活动列表
- 获取活动详情
- 获取活动商品

检查项：

- [ ] 分页参数与当前 DTO 对齐
- [ ] 活动状态能映射到系统口径
- [ ] 空活动、过期活动场景能处理
- [ ] 限流时不会把页面直接打挂

完成条件：

- [x] 至少拿到一组真实活动列表样本
- [ ] 活动详情与活动商品字段能回写到当前 DTO 口径
- [ ] 浏览器端活动页未出现回归

2026-05-07 复核补充：

- `GET /api/douyin/activities?appId=<masked>` 返回 HTTP 200、统一响应 `code=200`
- 上游 `alliance.instituteColonelActivityList` 返回 `code=10000 / msg=success`
- `remoteResponse.data.total=21`，首批 `activity_list` 为 20 条
- 首条样本必核字段齐全：`activity_id / activity_name / status / application_start_time / application_end_time`
- 当前后续样本活动 ID：`3916506`
- 证据文件：`runtime/qa/out/douyin-activities-20260507-151728/activities-summary.json`
- `GET /api/colonel/activities?status=0&searchType=0&sortType=1&page=1&pageSize=20&appId=<masked>` 返回 HTTP 200、统一响应 `code=200`
- 业务字段样本为 `activityId / activityName / activityStartTime / activityEndTime / status / statusText / applicationStartTime / applicationEndTime`
- 为兼容当前活动页旧字段读取，业务返回层已补齐 `activityStatus / startTime / endTime`
- 证据文件：`runtime/qa/out/colonel-activities-20260507-164832/colonel-activities-summary.json`
- `GET /api/douyin/activities/3916506` 返回 HTTP 200、统一响应 `code=200`
- 上游 `buyin.colonelActivityDetail` 返回 `code=10000 / msg=success`
- 详情样本字段包含 `activity_desc / apply_start_time / apply_end_time / commission_rate / service_rate / institution_id / colonel_buyin_id / categories / min_promotion_days`
- 详情样本中的加密态 `wechat_id / phone_num` 已脱敏存档
- 证据文件：`runtime/qa/out/douyin-activity-detail-20260507-1657/activity-detail-summary.json`
- `GET /api/douyin/activity-product-list?activityId=3916506&count=20` 返回 HTTP 200、统一响应 `code=200`
- 上游 `alliance.colonelActivityProduct` 返回 `code=10000 / msg=success`
- 真实商品数组位于 `remoteResponse.data.data`，首批 20 条，并返回 `next_cursor`
- 首条样本字段覆盖 `product_id / title / price / cos_ratio / activity_cos_ratio / cos_type / detail_url / status / promotion_start_time / promotion_end_time`
- 证据文件：`runtime/qa/out/douyin-activity-products-20260507-1700/activity-products-summary.json`
- `GET /api/colonel/activities/3916506/products?count=20` 返回 HTTP 200、统一响应 `code=200`
- 业务视图返回 `items` 10 条并带 `bizStatus / bizStatusLabel / promotion / systemTags / alertTags`
- 当前与上游实时 20 条样本未对齐，说明快照优先策略仍需单独评估
- 证据文件：`runtime/qa/out/colonel-activity-products-20260507-1700/activity-products-summary.json`
- `GET /api/colonel/activities/3916506/products?count=20&refresh=true` 已完成 real-pre 刷新 smoke：刷新前默认业务视图 10 条，显式刷新后返回 20 条，刷新后默认业务视图也返回 20 条
- 刷新策略固定为：不传 `refresh` 时继续快照优先，传 `refresh=true` 时绕过已有快照、调用上游 `alliance.colonelActivityProduct`、回写快照后再返回业务视图
- 证据文件：`runtime/qa/out/activity-product-refresh-real-20260507-210819/summary.json`

### 3. `DouyinProductGateway`

目标：

- 拉取商品基础信息
- 拉取商品详情与 SKU 明细

2026-05-07 复核补充：

- `GET /api/colonel/activities/3916506/products/3780271777075298337` 与 `3810562766247428542` 业务详情均返回 HTTP 200 / `code=200`，字段来自活动商品快照与本地业务状态，覆盖 `title / price / shopName / bizStatus / promotionLinkStatus` 等主链路字段
- 业务详情当前不返回 SKU 明细，不能作为上游商品详情 / SKU 的完整样本来源
- 通过 raw 探针调用 `product.detail` 验证两个真实商品 ID，当前均返回 `30001 / isv.app-permissions-insufficient`，说明应用缺商品详情接口权限包
- 证据文件：`runtime/qa/out/product-detail-real-20260507-210924/summary.json`、`runtime/qa/out/product-detail-raw-probes-20260507-210941/summary.json`
- 拉取商品详情
- 支撑商品主链路

检查项：

- [ ] 商品 ID、标题、店铺、价格字段对齐
- [ ] 图片缺失时前端可降级
- [ ] 类目、佣金、素材字段映射明确
- [ ] 与当前商品详情结构兼容

完成条件：

- [ ] 至少拿到一组真实商品详情样本
- [ ] 当前 `/product` 与 `/product/activity/:id` 不回退

### 4. `DouyinPromotionGateway`

目标：

- 生成真实推广链接
- 获取真实归因标识

检查项：

- [x] 转链成功能返回可用链接
- [x] `pick_source` 或等价归因参数口径明确
- [x] 失败原因能回传
- [x] `promotion_link` 与 `pick_source_mapping` 仍按当前系统逻辑落库

完成条件：

- [x] 至少打通一次真实转链
- [x] 成功写入本地映射关系
- [x] 失败原因可见且不破坏当前页面流程

2026-05-07 联调补记：

- 已在 `real-pre` 完成两轮真实转链：
  1. 首轮样本 `productId=3809019427689726334` 用于确认前置条件与失败分支
  2. 修复后样本 `productId=3810562766247428542` 用于确认主链路成功
- 当前真实前置条件已确认：
  1. 商品需先 `selectedToLibrary=true`
  2. `channel_staff` 不能在 `APPROVED` 状态直接转链，需先由 `biz_leader` 分配给本组 `biz_staff`，把状态推进到 `ASSIGNED`
- 通过 `POST /api/douyin/promotion-link-probes/raw` 已确认：`buyin.instPickSourceConvert` 真实成功响应主字段为 `data.product_url`
- 项目已补齐 `product_url` 兼容后，主转链接口可直接返回 `promoteLink`，详情页回读为 `promotionLinkStatus=READY / 已生成`、`promotion.copyEnabled=true`
- 当前真实上游仍未回 `shortLink`，但不影响复制主推广链接，也不阻塞继续推进订单链路
- 2026-05-08 已补跑 fallback raw probe：`buyin.kolProductShare` 当前返回 `40003 / isv.authorization-type-invalid`（授权主体不匹配）；`buyin.getProductShareMaterial` 当前返回 `90000 / isp.unknown-error`（接口已不再提供服务）。后续真实转链继续以 `buyin.instPickSourceConvert` 为主。

### 5. `DouyinOrderGateway`

目标：

- 拉取真实订单
- 支撑订单归因、看板与详情排查

检查项：

- [~] 多结算订单查询成功结构已确认：真实字段为 `data.cursor / data.orders`，并已确认 `t-90d` 时间窗边界校验
- [x] 订单同步主接口成功结构已确认：`buyin.instituteOrderColonel` 返回 `data.cursor / data.orders`
- [x] 订单同步主接口时间参数口径已确认：`start_time / end_time` 必须传 `yyyy-MM-dd HH:mm:ss` 字符串
- [x] 订单主接口真实样本已完成入库：30 分钟窗口拉取 10 单、落库 10 单、失败 0
- [~] 订单解密入口已命中真实上游错误分支，当前进一步阻塞为 `order.orderDetail / order.searchList` 应用权限包不足
- [~] 订单状态、金额、创建时间与达人账号字段已完成最小映射并通过真实同步验证，仍需补更多状态枚举样本
- [~] 无渠道归因参数时能走未归因分支：本轮真实 10 单均无 `pick_source / pick_extra`，全部为 `UNATTRIBUTED`
- [~] 已拿到真实达人字段 `author_buyin_id / author_account`，可支撑达人维度展示与后续独家达人归因；尚未拿到渠道 `pick_source / pick_extra`

完成条件：

- [~] 订单状态、金额、时间字段映射固定（已覆盖当前真实样本，仍待更多状态枚举）
- [x] 至少一组真实订单样本完成入库
- [~] `/orders`、`/dashboard`、`/data` 未出现回归（`/orders` 与 Dashboard `createTime` 口径已读到真实同步订单；`/data` 当前默认按 `settle_time`，未覆盖未结算样本）
- [x] 订单重复同步更新路径已验证：同一窗口重放 `POST /api/orders/sync` 返回 `updated=10 / failed=0`，证据目录 `runtime/qa/out/orders-sync-author-alias-20260507-211944`

### 6. `TalentGateway`

目标：

- 获取达人信息
- 刷新达人数据

检查项：

- [ ] 抖音号 / UID / sec_uid 对齐
- [ ] 粉丝数、获赞数、作品数字段对齐
- [ ] 空达人资料可降级
- [ ] 刷新失败不影响现有达人 CRM 展示
- [ ] 真实达人资料 API 与 Gateway 实现确认；当前 `TalentApi` 不等于达人资料刷新链路

### 7. `LogisticsGateway`

目标：

- 获取物流状态
- 推进寄样物流节点

检查项：

- [ ] 物流单号来源明确
- [ ] 签收状态同步口径明确
- [ ] 延迟、失败、查无结果分支明确
- [ ] 真实物流查询 Gateway 实现确认；当前只有测试实现，不能直接联调真实物流

## 五、联调验证顺序建议

建议按这个顺序推进：

1. `AuthGateway`
2. `ActivityGateway`
3. `ProductGateway`
4. `PromotionGateway`
5. `OrderGateway`
6. `TalentGateway`
7. `LogisticsGateway`

当前执行口径补充：

- 第一批只要求打开真实认证、活动、商品、转链、订单主链路
- `TalentGateway` 与 `LogisticsGateway` 可以在上述链路稳定后再推进
- 每完成一个 Gateway，都要先守住当前浏览器回归绿灯，再进入下一项

原因：

- 认证最先打通
- 商品与活动先能拉下来
- 再打通转链
- 最后打通订单、达人和物流

## 六、联调期间必须保留的回归项

每切通一个 Gateway，都要回归：

- [ ] 商品库
- [ ] 订单工作台
- [ ] Dashboard
- [ ] 寄样台
- [ ] 达人 CRM
- [ ] `/dev/test` 调试链路

重点要求：

- Real 联调失败时，Test 基线不能被破坏
- 不允许为了联调临时改前端展示字段
- 不允许把真实联调逻辑直接硬编码到页面中

## 七、当前结论

真实 SDK 联调的正确姿势不是“把现有 Test 替换掉”，而是：

1. 固化本地 Mock 基线
2. 逐个 Gateway 对照契约联调
3. Token 刷新与授权主体确认通过后，进入活动、商品、转链和订单样本采集
4. 每完成一个 Gateway 就做全链路回归

这样才能保证项目既能继续演示，也能稳步迈向真实环境。
