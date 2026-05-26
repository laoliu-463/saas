# 09-真实 SDK 联调准备清单

更新时间：2026-05-21

## 一、文档目标

当前项目已经完成本地 Mock 核心闭环。

下一步不是直接全量切真实接口，而是进入：

> 真实 SDK 联调准备阶段

本清单用于明确：

- 需要准备什么
- 应该先联调哪些 Gateway
- 哪些东西不能被真实联调反向破坏

## 当前准备进度（2026-05-08）

> 2026-05-21 起，匿名运行态探针统一使用 `GET /api/system/health`（仅返回 `{"status":"UP"}`）。`/api/actuator/**` 已收紧为需要 JWT 的内部诊断端点；下文历史记录中出现的 `/api/actuator/health` 保留为当时取证口径。

- [x] real-pre 后端 `8081`、PostgreSQL `5433`、Redis `6380` 已稳定可用
- [x] real-pre 后端 compose 已移除公开 JDWP debug 端口，不再映射 `5006:5005`；JVM 启动参数保留 `spring.devtools.restart.enabled=false`，不启用远程 debug
- [x] `GET /api/douyin/tokens`、Webhook 验签与日志脱敏、商品素材状态检查已拿到验证结果；2026-05-08 real-pre `real` profile 复验确认 `buyin.materialsProductStatus` 使用商品详情 URL 入参返回上游 `10000 / success`
- [x] `backend mvn clean test` 当前基线已更新为 `652 tests, 0 failures, 0 errors`（2026-05-09 全量回归）
- [x] real-pre 浏览器全路径回归 `45/45` 通过
- [x] 2026-05-24 real-pre 测试脚本口径收口：本地 test/mock 继续固定 `3000/8080`，real-pre 脚本固定 `3001/8081`；新增 `npm run e2e:real-pre:preflight` 与 `npm run e2e:real-pre:all`，预检覆盖运行态、Token、关键迁移表/字段、可复用推广映射与 PlanOnly 清理计划。缺真实 Token、上游样本或 pick_source 时输出 `BLOCKED/PENDING`，不计为业务流 PASS。
- [x] `real-pre` 已切换为 `SPRING_PROFILES_ACTIVE=real`，并补齐 `application-real.yml`；当前真实联调开关为 `DOUYIN_TEST_ENABLED=false`、`APP_TEST_ENABLED=false`、`ORDER_SYNC_ENABLED=true`
- [x] 当前 real-pre Redis DB 0 已存在真实 `access_token / refresh_token / expire_at` 缓存
- [x] `POST /api/douyin/token-refreshes` 已完成真实刷新链路验证：HTTP 200、统一响应 `code=200`、未触发重新授权
- [x] `GET /api/douyin/institution-info` 已完成授权主体确认：HTTP 200、上游 `code=10000 / msg=success`，返回机构 / 团长身份信息
- [x] 管理后台已补一键 OAuth 授权入口：`/system/douyin` 点击“去抖店授权”会请求 `GET /api/douyin/oauth/authorize-url`，抖店回调 `GET /api/douyin/oauth/callback` 校验 Redis `state` 后复用 `DouyinTokenService.exchangeCodeAndBootstrap(...)` 写入 Token，再跳回前端。官方后台本地授权回调地址填写 `http://localhost:8081/api/douyin/oauth/callback`；如平台不接受 `localhost`，使用公网 HTTPS 测试域名并保持 `/api/douyin/oauth/callback` 路径。
- [x] `GET /api/douyin/activities` 已完成活动列表联调接口采样：上游 `code=10000 / msg=success`，首批 `activity_list` 20 条，已拿到可用于后续接口的真实 `activity_id`
- [x] `GET /api/colonel/activities` 已完成活动列表业务接口对照：`data.total=21`、`data.activityList[0].activityId=3916506`，并已补齐 `activityStatus / startTime / endTime` 兼容字段避免当前页面列渲染错位
- [x] `GET /api/douyin/activities/3916506` 已完成活动详情采样：上游 `code=10000 / msg=success`，已确认详情字段口径与列表字段存在差异
- [x] `GET /api/douyin/activity-product-list?activityId=3916506&count=20` 已完成活动商品联调接口采样：上游 `code=10000 / msg=success`，真实数组位于 `data.data`，首批 20 条并返回 `next_cursor`
- [x] `GET /api/colonel/activities/3916506/products?count=20&refresh=true` 已完成业务接口刷新闭环：旧快照 10 条，强制刷新后回写为 20 条，随后默认查询也返回 20 条；证据目录 `runtime/qa/out/activity-product-refresh-real-20260507-210819`
- [x] `POST /api/douyin/product-material-status-checks` 已在 real-pre `real` profile 复验：纯数字商品 ID 返回 `40004 / isv.parameter-invalid:257`，抖店商品详情 URL 入参返回 `code=10000 / msg=success`，数据字段包含 `status / join_alliance / promotion_status / can_share / product_url`
- [x] `GET /api/douyin/order-settlements` 已完成首轮多结算订单查询取证：上游成功结构键为 `data.cursor / data.orders`，当前 7/30 天窗口样本均为空；已确认超出 `t-90d` 会返回 `40004 / isv.parameter-invalid:1036`
- [x] `RealDouyinOrderGateway` 已补齐真实返回映射，并已将常规时间范围主同步切到 `buyin.colonelMultiSettlementOrders`；旧 `buyin.instituteOrderColonel` 仅保留为 RAW 探针和历史口径对照。多结算接口入参时间必须使用 `yyyy-MM-dd HH:mm:ss` 字符串，非空结算样本仍待上游返回后继续核验字段
- [x] `POST /api/orders/sync` 已完成 real-pre 30 分钟窗口真实同步：拉取 10 单、落库 10 单、失败 0、归因 0；证据目录 `runtime/qa/out/orders-sync-real-20260507-203422`；2026-05-08 pick_source 重复映射修复后复验：重放同步 `totalFetched=10 / created=4 / updated=6 / attributed=10 / unattributed=0`，全库 `colonel_native` 场景 316 单全部 `ATTRIBUTED`（归因率 100%）
- [x] 2026-05-08 21:19 real-pre 三方联调二次复验：证据目录 `runtime/qa/out/real-pre-sdk-retest-20260508-211901`；本轮 `POST /api/orders/sync` 7 天窗口返回 `totalFetched=10 / created=10 / updated=0 / attributed=10 / unattributed=0 / failed=0`，全库订单统计更新为 `totalOrders=326 / attributedOrders=326 / unattributedOrders=0 / partialOrders=0 / syncFailedOrders=0`
- [~] 2026-05-09 15:29 按联盟测范围重新确认：real-pre 后端 `UP`，Token 可用，`buyin.institutionInfo / alliance.instituteColonelActivityList / alliance.colonelActivityProduct / buyin.productSkus.v2` 均返回 `10000`；活动商品样本 `20` 条，SKU 样本 `1` 条；30 分钟订单同步 `totalFetched=10 / created=0 / updated=10 / attributed=10 / unattributed=0 / failed=0`；该条为 15:29 文档快照，后续统计已继续变化
- [x] 订单归因逻辑已完成 `colonel_order_info` 专项修复：`colonel_buyin_id` 使用独立 `pick_source_mapping.colonel_buyin_id` 字段和唯一候选匹配，不再复用 `short_id`；已补迁移 SQL、real-pre 手工升级、容器重启和容器内定向测试
- [x] real-pre `pick_source` 重复映射问题已修复：同 `pick_source` 可复用于不同 `activity_id + product_id` 组合，原逻辑将 `pick_source` 视为全局唯一导致归因失配；修复后改为复合键匹配（`pick_source + activity_id + product_id + user_id`），seed SQL 扩展为 8 个 `colonel_buyin_id`，全库 316 单 `colonel_native` 场景归因率 100%（316/316 `ATTRIBUTED`）
- [~] 2026-05-09 已定位 `COLONEL_MAPPING_NOT_FOUND` 新一轮根因：未归因订单 raw payload 存在 `colonel_order_info_second` 二级团长信息，但本地旧逻辑只消费一级 `colonel_order_info`；代码侧已补二级团长字段扁平化、二级活动优先归因以及“存在二级活动时禁止泛 seed 映射误归到 admin”保护
- [~] 2026-05-09 16:31 real-pre 新代码复验：`POST /api/orders/sync` 使用正确 `application/json` 请求体可稳定成功，但会继续拉入同类未归因订单；当前全库更新为 `916` 单中 `870` 已归因、`46` 未归因，且全部为 `COLONEL_MAPPING_NOT_FOUND`
- [x] 已新增管理员收口入口 `POST /api/orders/replay-attribution`：支持对历史落库订单做归因重算，默认仅扫描未归因订单；real-pre `dryRun(limit=50)` 结果为 `scanned=46 / attributed=0 / unattributed=46 / updated=0`
- [~] 2026-05-09 16:31 根因进一步钉实：当前未归因订单对应本地缺口扩大为 `10` 个缺失活动、`27` 个缺失 `activity_id + product_id` 组合；当前不是“订单没拉到”或“重算逻辑没生效”，而是本地业务映射链路未建立
- [x] 2026-05-10 P1-2 real-pre 真实链路联调：确认 `test` 与 `real-pre` 在 profile、数据库、Redis、抖音开关上隔离；real-pre `/api/actuator/health` 返回 `UP`；Token 状态 / 刷新、授权主体、活动列表、活动商品、商品审核入库、分配、真实转链、`pick_source_mapping`、订单同步、订单归因统计与 Dashboard 展示均完成验证。订单同步最近 7 天返回 `totalFetched=100 / created=5 / updated=95 / attributed=95 / unattributed=5 / failed=0`；同步后全库统计为 `totalOrders=1802 / attributedOrders=1665 / unattributedOrders=137 / syncFailedOrders=0`，最终收口复核更新为 `totalOrders=1821 / attributedOrders=1683 / unattributedOrders=138`；未归因 dry-run 仍为映射缺口，不用 Mock 数据覆盖真实未归因结果。证据目录：`runtime/qa/out/real-pre-p1-2-20260510-020545`、`runtime/qa/out/real-pre-p1-2-product-chain-20260510-020840`
- [x] 2026-05-10 real-pre 数据看板真实口径纯化：已软删除 real-pre 历史遗留的演示 / 测试 active 数据（订单 1、商品快照 23、推广映射 2、商品状态 6、寄样单 5、达人认领 3、商品 3、达人 7），未删除容器或 volume；复核脚本 `scripts/qa/check-real-pre-real-data.ps1` 返回 `status=OK`，环境为 `real / APP_TEST_ENABLED=false / DOUYIN_TEST_ENABLED=false / ORDER_SYNC_ENABLED=true / Redis DB 0 / colonel_saas_real`，8 个核心表 active mock/test/演示命中均为 `0`；当前订单汇总为 `activeOrders=2187 / attributedOrders=2025 / unattributedOrders=162 / totalGmv=42668.11`，Dashboard `createTime` 返回 `todayOrderCount=1003 / todayGmv=19539.13`，近 7 日趋势中 `2026-05-04` 已回到 `0 / 0.00`
- [~] 2026-05-10 13:51 real-pre 数据看板复核：当前前端 `/dashboard` 与 `/data` 均直接调用真实后端 API，未发现 active mock/demo/test 业务数据；`/dashboard/summary` 与 `/dashboard/metrics?timeField=createTime` 已和 `colonelsettlement_order` 聚合结果对齐。最新复核值：Summary `orderCount=2307 / orderAmount=4512678 / serviceFee=70682 / attributedOrderCount=2135 / unattributedOrderCount=172`；Metrics(createTime) `todayOrderCount=1123 / todayGmv=21997.80 / pendingShipCount=2146`。但当前 Dashboard / stats / summary 核心指标仍主要直接读取 `colonelsettlement_order`，未直接聚合 `product / colonel_activity / pick_source_mapping`，因此本轮结论定为“半真实化”，不是“所有业务表全部打通”。证据包括：`http://localhost:8081/api/dashboard/summary`、`http://localhost:8081/api/dashboard/metrics?timeField=createTime`、`scripts/qa/check-real-pre-real-data.ps1`、`docker exec saas-postgres-real-pre-1 psql ...`
- [x] 真实订单展示 smoke 已完成：`/api/orders` 可读到同步后订单，`/api/dashboard/metrics?timeField=createTime` 可统计到今日订单；2026-05-08 21:19 二次复验更新为 `todayOrderCount=315`、`todayGmv=6035.07`、`serviceFee=94.73`、`grossProfit=66.31`；订单重复同步更新已修复 jsonb `extra_data` 写入问题，真实订单达人字段 `author_buyin_id / author_account` 可保留并展示为 `talentName`；数据平台订单页与默认 `settleTime` 指标暂不覆盖未结算订单，后续 M1.6 需明确 create_time / settle_time 展示口径
- [x] 当前订单展示口径已明确：仅使用上游主订单接口已返回字段，不再提供订单手机号解密能力
- [x] 三方联调 SOP 防护补查已完成：本机到 `openapi-fxg.jinritemai.com:443` 可达；抖店 `RestTemplate` 默认超时已收紧为 `connectTimeout=3s / readTimeout=5s`，可通过 `DOUYIN_CONNECT_TIMEOUT / DOUYIN_READ_TIMEOUT` 覆盖；网络异常不再把含 `access_token / sign` 的原始 URL 作为日志 throwable 或上抛 cause；real-pre 后端已重启并确认实际环境为真实上游模式，最近日志未命中 `access_token=`、`refresh_token=`、`sign=` 等敏感字段
- [x] Webhook 日志防护与本地消费框架已补齐：验签失败、正常接收、JSON 解析失败均只记录事件名、bodyLength、签名是否存在 / 长度与异常类型，不记录原始 body、手机号、token 或签名值；事件会按 `event_key` 幂等写入 `douyin_webhook_event`，并支持 `POST /api/douyin/webhook-events/replay` 重放 `RECEIVED / FAILED` 状态事件；real-pre 重启后异常 JSON smoke 返回 `success`，容器日志敏感字段命中数为 0
- [x] Token 初始化接口已使用新 OAuth 授权码完成重放：`POST /api/douyin/tokens` HTTP 200、`code=200`，`hasAccessToken=true`、`hasRefreshToken=true`、`reauthorizeRequired=false`；详见 `docs/archive/records/20-2026-05-08-新授权码三方全流程联调报告.md`
- [~] 商品真实样本采集已完成业务快照详情 smoke；SKU 已按精选联盟文档改为 `/buyin/productSkus/v2`，代码侧方法名使用 `buyin.productSkus.v2`；raw probe 用真实商品 `3810562766247428542` 返回 `10000 / success`，字段包含 `specs / pictures / skus`，后续需在 real-pre 重启后复验业务 Gateway 解析
- [x] real-pre 前端抖店联调页二次复验通过：`runtime/qa/out/real-pre-douyin-frontend-20260508211901/report.md`，`DY-FE-01 PASS`；2026-05-09 页面与 E2E 口径已改为联盟测范围，不再展示店铺侧订单权限阻塞
- [~] 2026-05-08 21:19 本轮 promotion raw probe 命中样本质量问题：`buyin.instPickSourceConvert` 返回 `40004 / isv.parameter-invalid:1056 / 无效商品URL`，原因是活动商品业务样本里的 `detailUrl` 为空；该结果不视为主转链回归，真实转链主链路仍以前序成功样本和当前订单归因结果为准

## 当前阻塞事实（2026-05-09）

1. `refresh_token` 缺失阻塞已解除，新的 OAuth 授权码初始化、授权主体确认、活动列表联调接口、活动列表业务接口、活动详情、活动商品联调接口、活动商品业务刷新、多结算订单查询首轮取证、订单主同步入库已通过；2026-05-09 代码侧已把 SKU 查询改走精选联盟 `/buyin/productSkus/v2`，raw probe 已成功返回 `specs / pictures / skus`；下一步需 real-pre 重启后复验业务 Gateway 解析。店铺商品与店铺订单接口不再纳入联盟测进度
2. 订单主同步当前仍可成功回流并归因本轮窗口订单，但全库归因仍需继续收口：2026-05-09 16:31 real-pre 最新统计为 `916` 单中 `870` 已归因、`46` 未归因，未归因样本全部为 `COLONEL_MAPPING_NOT_FOUND`。当前代码已补二级团长信息归因、防 admin seed 误归因保护，并新增 `POST /api/orders/replay-attribution` 历史重算入口；real-pre `dryRun(limit=50)` 结果仍为 `46` 单全部未归因，因此下一步焦点已收敛为补齐本地 `activity/product/mapping` 缺失，而不是继续怀疑同步或重算代码
3. 当前订单展示、归因与看板以上游主订单接口已返回字段为准，不再继续推进订单手机号解密能力
4. 当前 `3000/8080` 仍是 test / 本地基线；`3001/8081` 已切换到 `real` profile，用于真实上游模式接口联调，但仍是 `real-pre` 容器环境，不得等同生产环境
5. 真实 Gateway 工作必须遵守“只替换 Gateway，不反向破坏现有 Controller / Service / 前端 / 浏览器回归”的约束
6. 当前首轮执行顺序已经明确为：
   - `AuthGateway`
   - 授权主体确认 `GET /api/douyin/institution-info`
   - `ActivityGateway / ProductGateway`
   - `PromotionGateway`
   - `OrderGateway`
7. 在联盟 SKU 业务 Gateway 与 Webhook 真实业务事件副作用样本未完成前，不应把数据看板真实化或部署验证误判为“真实环境已经就绪”
8. `TalentGateway` 目前没有真实达人资料接口实现；当前 `TalentApi` 主要包装 `buyin.instPickSourceConvert`，不能当作达人信息刷新链路已联通
9. `LogisticsGateway` 已有快递鸟即时查询、快递100实时查询与快递100企业版订阅推送本地实现；真实物流仍缺 Sandbox / real-pre 样本单号、企业版回调地址和凭证验证
10. 真实限流 / 429 分支尚未触发；由于抖店 POST 方法里包含活动创建、更新、取消等有副作用能力，后续退避重试必须按接口白名单设计，不能在通用 `DouyinApiClient` 上无差别自动重试

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

### 4. real-pre 写入与清理边界（2026-05-21 补充）

- real-pre 完整业务流程、RBAC 与浏览器可视化旅程不得自动新建不可回滚的真实抖店上游转链；转链步骤只允许复用已有 real-pre `pick_source_mapping` / `promotion_link`，缺少可复用映射时按前置条件阻塞。
- 运行 real-pre 业务脚本前先执行 `npm run e2e:real-pre:preflight`；一键验收使用 `npm run e2e:real-pre:all`，统一报告位于 `runtime/qa/out/real-pre-all-*`。
- 本地 QA 写入必须携带 `runId=QA...` 或写入 `journey-state.json`，并在清理前导出 `cleanup-plan.json` / `cleanup-plan.sql` / `cleanup-verify.sql`。
- 清理默认只 PlanOnly；人工审核确认只包含本次 runId 数据后，才允许使用 `scripts/qa/cleanup-real-pre-journey.ps1 -Execute -RunId <runId>` 执行。
- 清理器必须同时守住 `/api/system/env=REAL-PRE`、数据库 `saas_real_pre`、非 prod/production 容器或连接；清理后 SQL 复核本次 runId 残留为 `0` 前，不得宣称本次 real-pre run 完成。
- 清理范围仅限本系统 real-pre 本次 QA 数据和 `product_operation_state` 快照恢复；不清理真实订单、真实商品、真实活动、真实 Token 或真实上游不可回滚记录。

## 三、联调前置准备

### 1. 环境准备

- [ ] 明确真实联调环境地址
- [x] 明确 OAuth 授权回调地址：本地 real-pre 为 `http://localhost:8081/api/douyin/oauth/callback`；公网调试域名使用 `https://<域名>/api/douyin/oauth/callback`
- [ ] 明确 Webhook 地址 / 网络策略：Webhook 与 OAuth 不共用地址，路径为 `/api/douyin/webhooks/colonel-open-events`
- [ ] 明确网络访问策略与白名单
- [ ] 明确真实环境是否需要固定出口 IP

### 2. 应用与权限准备

- [ ] 申请 `AppKey / AppSecret`
- [ ] 获取真实测试授权主体
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
- 拉取精选联盟 SKU 明细

2026-05-07 复核补充：

- `GET /api/colonel/activities/3916506/products/3780271777075298337` 与 `3810562766247428542` 业务详情均返回 HTTP 200 / `code=200`，字段来自活动商品快照与本地业务状态，覆盖 `title / price / shopName / bizStatus / promotionLinkStatus` 等主链路字段
- 业务详情当前字段来自活动商品快照与本地状态；联盟测不再把店铺商品 `product.detail / sku.detail` 作为进度项
- 2026-05-09 复核精选联盟文档后，SKU 查询已从店铺商品 `sku.detail` 调整为精选联盟 `/buyin/productSkus/v2`，请求参数为 `product_id`；raw probe 以 `buyin.productSkus.v2` 方法名成功返回 `10000 / success`，字段包含 `specs / pictures / skus`
- 拉取精选联盟 SKU
- 支撑商品主链路

## 六点十九、2026-05-10 P1-7.2 dashboardDiagnosis 穿透口径对齐

- 目标：不改已通过的 Dashboard summary 核心聚合口径、不改已通过的 `activity-products` 对表逻辑，只修 `dashboardDiagnosis -> /orders/stats -> /orders` 的查询口径一致性。
- 根因钉实：
  - `DashboardService` 的诊断分类使用互斥 `CASE` 链路，`UPSTREAM_PRODUCT_UNCOVERED / CANNOT_AUTO_ATTRIBUTION / AMBIGUOUS_MAPPING / MECHANISM_HIT_HISTORY_UNSAFE / NATIVE_KEY_MISMATCH` 是单一归类，不是可重叠布尔条件。
  - `OrderController` 之前是单独拼 diagnosis 条件，导致 `/orders/stats` 与 summary 分类边界漂移。
  - 进一步确认 controller 相关子查询里使用了外层裸列名，像 `ps.product_id = product_id` 会在 PostgreSQL 中优先绑定到子查询内列，导致 `UPSTREAM_PRODUCT_UNCOVERED` 被错分到 `CANNOT_AUTO_ATTRIBUTION`。
  - `/orders` 与 `/orders/stats` 底层查询还缺少显式 `deleted = 0`，会把软删订单混入 drilldown 结果。
- 本轮代码收口：
  - `DashboardService` 抽出共享诊断分类 SQL：`diagnosisCategoryCaseSql(...)`，并增加 diagnosis 名称归一化，兼容 `UNSAFE_BECAUSE_CREATED_AFTER_ORDER -> MECHANISM_HIT_HISTORY_UNSAFE`。
  - `OrderController` 改为直接复用共享分类 SQL，`/orders/stats` 与 `/orders` 走同一套 diagnosis 条件构造逻辑。
  - `OrderController` 的 diagnosis 相关子查询统一改为显式外层限定列名：`colonelsettlement_order.*`，避免相关子查询绑定错误。
  - `/orders` 与 `/orders/stats` 基础查询补齐 `deleted = 0`。
  - `DashboardService` 的 diagnosis drillDownQuery 移除多余 `attributionStatus=UNATTRIBUTED`，避免前端带着隐形二次过滤。
- real-pre 运行态复核（`http://localhost:8081/api/actuator/health` 返回 `UP`）：
  - `/api/dashboard/summary` 当前核心值：`orderCount=3262 / orderAmount=6510728 / serviceFee=102750 / attributedOrderCount=2376 / unattributedOrderCount=886`
  - PostgreSQL 同步对表：`colonelsettlement_order where deleted=0` 聚合结果完全一致。
  - diagnosis 三端一致：
    - `UPSTREAM_PRODUCT_UNCOVERED`：summary `507`，`/api/orders/stats?dashboardDiagnosis=UPSTREAM_PRODUCT_UNCOVERED`=`507`，`/api/orders?dashboardDiagnosis=UPSTREAM_PRODUCT_UNCOVERED`=`507`
    - `CANNOT_AUTO_ATTRIBUTION`：summary `244`，stats=`244`，list=`244`
    - `AMBIGUOUS_MAPPING`：summary `115`，stats=`115`，list=`115`
    - `MECHANISM_HIT_HISTORY_UNSAFE`：summary `0`，stats=`0`，list=`0`
    - `NATIVE_KEY_MISMATCH`：summary `0`，stats=`0`，list=`0`
    - alias 兼容：`dashboardDiagnosis=UNSAFE_BECAUSE_CREATED_AFTER_ORDER` 也返回 `0`，与 unsafe 类目一致。
  - 重点活动 `UPSTREAM_PRODUCT_UNCOVERED` drilldown 复核：
    - `3859423`：stats=`91`，list=`91`，PostgreSQL=`91`
    - `3864871`：stats=`38`，list=`38`，PostgreSQL=`38`
    - `3601935`：stats=`0`，list=`0`，PostgreSQL=`0`
    - `3223881`：stats=`23`，list=`23`，PostgreSQL=`23`
    - `3559407`：stats=`11`，list=`11`，PostgreSQL=`11`
  - `activity-products` 本轮未改聚合口径，复核仍对表：
    - `3223881`：`53` 个商品分组 / `600` 单 / `170` 未归因
    - `3559407`：`2` 个商品分组 / `11` 单 / `11` 未归因
    - `3601935`：`5` 个商品分组 / `38` 单 / `23` 未归因
    - `3859423`：`49` 个商品分组 / `619` 单 / `192` 未归因
    - `3864871`：`19` 个商品分组 / `70` 单 / `57` 未归因
    - PostgreSQL 聚合结果与 `/api/dashboard/activity-products?page=1&size=2000` 一致。
- 测试与构建：
  - `backend mvn -q -DskipTests compile` 通过
  - `backend mvn -q "-Dtest=DashboardServiceTest,OrderControllerTest,OrderQueryServiceTest" test` 通过
  - 本轮未改前端参数透传实现，未触发新的前端 build 必要性；沿用 P1-7.1 已通过的 `frontend npm run build` 结论
- 结论：
  - P1-7.2 已完成，`dashboardDiagnosis -> /orders/stats -> /orders` 口径已对齐。
  - 当前可以进入 `P1-8 Dashboard 全链路真实化验收`。
  - 仍需保持既有约束不变：不真实回填历史订单、不修改 replay 安全判断、不把 `unsafeBecauseCreatedAfterOrder` 算成已归因、不把 `nativeKeyMatched` 直接算渠道/招商业绩。

## 六点二十、2026-05-12 P1-8 Dashboard 全链路真实化验收

- 验收目标：
  - 在 `real-pre` 环境下，同时确认 `Dashboard API 口径真实对表`、`dashboardDiagnosis drilldown 一致`、`前台页面可见链路可走通`。
- 环境状态：
  - 日期：`2026-05-12`
  - real-pre 容器：`frontend/backend/postgres/redis` 全部 `healthy`
  - 健康检查：`GET http://localhost:8081/api/actuator/health` 返回 `{"status":"UP"}`
  - 纯数据检查：`powershell -ExecutionPolicy Bypass -File scripts/qa/check-real-pre-real-data.ps1` 返回 `status=OK`
  - real-pre 环境变量确认：
    - `SPRING_PROFILES_ACTIVE=real`
    - `APP_TEST_ENABLED=false`
    - `DOUYIN_TEST_ENABLED=false`
    - `ORDER_SYNC_ENABLED=true`
    - `REDIS_DATABASE=0`
    - `DB_NAME=colonel_saas_real`
  - 8 个核心表 active mock/test/demo 命中仍为 `0`
- 2026-05-12 当前真实数据基线：
  - `activeOrders=7398`
  - `attributedOrders=2483`
  - `unattributedOrders=4915`
  - `totalGmv=151055.14`
- Dashboard summary / PostgreSQL 对表：
  - API：`orderCount=7398 / orderAmount=15105514 / serviceFee=231334 / attributedOrderCount=2483 / unattributedOrderCount=4915`
  - PostgreSQL：`colonelsettlement_order where deleted=0` 聚合结果完全一致
- Dashboard diagnosis drilldown 复核：
  - `UPSTREAM_PRODUCT_UNCOVERED`：summary=`3249`，`/orders/stats`=`3249`，`/orders`=`3249`
  - `CANNOT_AUTO_ATTRIBUTION`：summary=`1531`，`/orders/stats`=`1531`，`/orders`=`1531`
  - `AMBIGUOUS_MAPPING`：summary=`115`，`/orders/stats`=`115`，`/orders`=`115`
  - `MECHANISM_HIT_HISTORY_UNSAFE`：summary=`0`，`/orders/stats`=`0`，`/orders`=`0`
  - `NATIVE_KEY_MISMATCH`：summary=`0`，`/orders/stats`=`0`，`/orders`=`0`
  - alias `UNSAFE_BECAUSE_CREATED_AFTER_ORDER`：`/orders/stats`=`0`，`/orders`=`0`
  - PostgreSQL 复核结果与 API 一致：当前非零类目仅 `UPSTREAM_PRODUCT_UNCOVERED / CANNOT_AUTO_ATTRIBUTION / AMBIGUOUS_MAPPING`
- Dashboard activity-products 复核：
  - `GET /api/dashboard/activity-products?page=1&size=2000` 当前 `total=366`
  - PostgreSQL `group by colonel_activity_id, product_id` 结果同为 `366`
- 前台验收：
  - `frontend npm run build`：通过
  - `npx playwright test tests/e2e/02-dashboard.spec.ts --project=chromium`（`E2E_BASE_URL=http://localhost:3001`）：通过
  - `npx playwright test tests/e2e/09-full-user-journey.spec.ts --project=full-journey`（`E2E_BASE_URL=http://localhost:3001`）：通过
  - 本轮对旅程 spec 的必要校准：
    - 数据看板进入订单页改为使用当前真实入口 `查看完整明细`
    - 系统管理页不再依赖侧边栏是否默认展开，而是直接校验 `/system/users` 页面主体
    - 抖店联调页作为独立系统子路由 `/system/douyin` 校验
  - 额外发现但未阻断：
    - Header 中 `寄样审核` 与 `寄样发货台` 目前共用 `data-testid="nav-sample"`，对真实用户无影响，但会增加自动化定位歧义；可在后续测试治理阶段单独收口
- 结论：
  - `P1-8 Dashboard 全链路真实化验收` 通过。
  - 当前结论不是“未归因已清零”，而是“在真实数据持续增长的前提下，Dashboard summary、activity-products、diagnosis drilldown 与前台展示链路保持一致”。

## 六点二十一、2026-05-12 real-pre Webhook 收件箱补建与接收链路复核

- 背景：
  - 原计划中的“Webhook 真实业务事件副作用确认”在 real-pre 首次实投时暴露出一个更基础的问题：`POST /api/douyin/webhooks/colonel-open-events` 返回 `500 receive failed`
  - 追日志后确认根因不是验签或 controller，而是 real-pre 现有 PostgreSQL volume 中缺少 `douyin_webhook_event` 表；fresh DB 会吃到 `init-db.sql / 17-create-douyin-webhook-event.sql`，但历史 volume 不会自动补建
- 本轮修复：
  - 新增应用启动幂等补建：`backend/src/main/java/com/colonel/saas/service/DouyinWebhookSchemaBootstrap.java`
  - 启动时执行 `CREATE TABLE/INDEX IF NOT EXISTS douyin_webhook_event ...`，让老 real-pre 库无需清空 volume 也能补齐 Webhook 收件箱
  - 新增定向测试：`DouyinWebhookSchemaBootstrapTest`
- 本地验证：
  - `backend mvn "-Dtest=DouyinWebhookSchemaBootstrapTest,DouyinWebhookEventServiceTest,DouyinWebhookControllerTest" test` 通过
  - 重建 real-pre 后端后，`GET /api/actuator/health` 返回 `UP`
- real-pre 实投复核：
  - 首次修复前事实：
    - `POST /api/douyin/webhooks/colonel-open-events` 返回 `500`
    - 后端日志：`ERROR: relation "douyin_webhook_event" does not exist`
  - 修复后用样本事件 `event_id=evt-realpre-fb32377e6ad8` 实投：
    - `POST /api/douyin/webhooks/colonel-open-events` 返回 `200 success`
    - PostgreSQL 行结果：
      - `event_key=doudian_alliance_colonelOpenEvent:evt-realpre-fb32377e6ad8`
      - `status=CONSUMED`
      - `consume_result=COLONEL_OPEN_EVENT_CAPTURED`
      - `retry_count=1`
    - 重复投递同一 `event_id` 后，表内仍仅 `1` 行，确认 `event_key` 幂等生效
    - `POST /api/douyin/webhook-events/replay?limit=5` 当前返回 `scanned=0 / consumed=0 / failed=0`，符合该事件已在首次接收时直接消费为 `CONSUMED`
- 当前结论：
  - real-pre 现已确认“回调接收 -> 幂等落库 -> 标记消费 -> 重放接口可读”链路可用
  - 当前 `consume_result=COLONEL_OPEN_EVENT_CAPTURED` 仍说明业务副作用尚未接入订单 / 商品 / 达人等下游处理；“真实业务事件副作用确认”仍未完成，但阻塞点已从“基础收件箱失效”收敛为“缺真实事件消费实现 / 样本”

## 六点二十二、2026-05-13 Webhook 订单定向同步消费实现

- 目标：
  - 不再让 `doudian_alliance_colonelOpenEvent` 只停留在 `CAPTURED`
  - 当回调 payload 明确带出 `order_id / order_ids` 时，直接触发现有订单同步链，对这些订单做定向拉取和落库
- 本轮实现：
  - `DouyinOrderGateway` 新增 `listSettlementByOrderIds(List<String>)`
  - `RealDouyinOrderGateway` 改为调用上游 `buyin.colonelMultiSettlementOrders` 的 `order_ids` 查询分支，按订单号定向拉取
  - `TestDouyinOrderGateway` 同步补齐定向查询返回，保持 test/mock 行为一致
  - `OrderSyncService` 新增 `syncByOrderIds(List<String>)`，复用现有 attribution、持久化、`pick_source_mapping`、merchant 和 sample lifecycle 副作用
  - `DouyinWebhookEventService` 现会从 payload 顶层或 `data` 中提取 `order_id / order_ids / orderId / orderIds`
    - 若未提取到订单号，仍记为 `COLONEL_OPEN_EVENT_CAPTURED`
    - 若提取到订单号，则执行定向同步，并把收件箱 `consume_result` 标记为 `COLONEL_OPEN_EVENT_SYNCED:fetched=...,created=...,updated=...,failed=...`
- 测试：
  - `RealDouyinOrderGatewayTest` 已覆盖按 `orderIds` 调上游查询
  - `OrderSyncServiceTest` 已覆盖定向同步只走 `listSettlementByOrderIds`
  - `DouyinWebhookEventServiceTest` 已覆盖回调携带 `order_ids` 时触发定向同步并写入新的 `consume_result`
  - 2026-05-13 本地执行：
    - `mvn "-Dtest=RealDouyinOrderGatewayTest,OrderSyncServiceTest,DouyinWebhookEventServiceTest,DouyinWebhookControllerTest,DouyinWebhookSchemaBootstrapTest" test`
    - 结果：通过
- 当前边界：
  - 这次实现的是“订单类 Webhook 的保守副作用”能力，不会对未带订单号的泛事件做猜测性全窗同步
  - real-pre 现场复核本轮暂未完成，不是代码失败，而是执行时本机 Docker Desktop Linux engine 不可用，待环境恢复后再补“带真实 order_id 的 Webhook -> 定向同步 -> 收件箱结果”证据

## 六点二十三、2026-05-14 real-pre Webhook 定向同步现场复核

- 环境时间：
  - 复核时间为 `2026-05-14`
  - real-pre 后端健康检查：`GET /api/actuator/health` 返回 `{"status":"UP"}`
- 现场样本：
  - 从 real-pre 库 `colonelsettlement_order` 中读取最近真实订单号样本，包含：
    - `6926253382114246100`
    - `6926262735237971212`
    - `6926266566906051746`
    - `6952764148781422124`
    - `6952768332160177667`
- Webhook 实投：
  - 向 `POST /api/douyin/webhooks/colonel-open-events` 投递：
    - `event=doudian_alliance_colonelOpenEvent`
    - `event_id=evt-realpre-sync-77604b22a9`
    - `data.order_id=6926253382114246100`
  - HTTP 返回：`200 success`
- 收件箱结果：
  - `douyin_webhook_event.event_key=doudian_alliance_colonelOpenEvent:evt-realpre-sync-77604b22a9`
  - `status=CONSUMED`
  - `consume_result=COLONEL_OPEN_EVENT_SYNCED:fetched=0,created=0,updated=0,failed=0`
  - `retry_count=1`
- 后端日志事实：
  - 同一事件请求日志明确出现：
    - `RealDouyinOrderGateway`
    - `Douyin API call success, method=buyin.colonelMultiSettlementOrders`
    - `Order sync completed, range=[1778733570, 1778733570], pages=0, fetched=0, created=0, updated=0, attributed=0`
  - 这说明：
    - Webhook 消费代码已真实命中“按 orderIds 定向同步”分支
    - 真实上游调用成功发出，并非停留在本地 mock 或仅落库不消费
- 上游空样本复核：
  - 用 `admin / admin123` 登录 real-pre 后，调用：
    - `GET /api/douyin/order-settlements?orderIds=6926253382114246100,6926262735237971212,6926266566906051746,6952764148781422124,6952768332160177667`
  - 上游返回：
    - `code=10000`
    - `data.orders=[]`
  - 再以时间范围查询：
    - `startTime=2026-05-01 00:00:00`
    - `endTime=2026-05-14 23:59:59`
  - 上游同样返回：
    - `code=10000`
    - `data.orders=[]`
- 主订单事实对照：
  - 调用 `POST /api/douyin/order-sync-probes/raw`
    - `start_time=2026-05-12 00:00:00`
    - `end_time=2026-05-14 23:59:59`
    - `count=5`
  - `buyin.instituteOrderColonel` 返回了最新真实订单样本，例如：
    - `6952836057142924613`
    - `6952836049016198890`
    - `6952836025413080498`
    - `6952836015887620033`
    - `6952836008364807907`
  - 这些真实订单样本共同特征：
    - `flow_point` 为 `PAY_SUCC` 或 `REFUND`
    - `settled_goods_amount=0`
    - `colonel_order_info.real_commission=0`
    - `colonel_order_info.settled_tech_service_fee=0`
  - 同时 real-pre 本地库 `colonelsettlement_order` 当前 `settle_time IS NOT NULL` 的记录数为 `0`
- 推断口径：
  - 以上事实说明当前授权主体下，主订单链路拿到的是“已成交但未进入结算口径”的真实订单
  - 因此 `buyin.colonelMultiSettlementOrders` 持续返回 `orders=[]` 与主订单事实并不矛盾，更像是**当前主体暂时没有可供多结算接口返回的真实结算样本**
- 2026-05-14 13:06 自动探针补证：
  - 新增脚本：`powershell -ExecutionPolicy Bypass -File scripts/qa/watch-real-pre-order-settlements.ps1`
  - 证据目录：`runtime/qa/out/real-pre-order-settlements-watch-20260514-130606`
  - 本轮脚本结果：
    - `institution-info` 仍为 `星链达客 / colonelBuyinId=7351155267604218149`
    - `buyin.colonelMultiSettlementOrders` 30 天窗口仍返回 `orderCount=0`
    - `buyin.instituteOrderColonel` 3 天窗口抓到 `10` 条真实订单样本，且这 `10` 条样本均满足 `settled_goods_amount=0 / real_commission=0`
    - Docker 侧库证据显示当前 `colonelsettlement_order` 中 `settle_time IS NOT NULL` 的历史记录数为 `48`
  - 说明：
    - 本地库里并非绝对没有“历史已结算”订单
    - 但当前上游多结算接口在最近窗口内仍未返回可复用样本，因此自动脚本按预期跳过了 Webhook 回投
- 当前结论：
  - “Webhook 带订单号 -> 触发 real-pre 定向同步 -> 调用真实上游 `buyin.colonelMultiSettlementOrders` -> 收件箱记录同步结果”链路已确认真实可执行
  - 当前没有拿到 `fetched>0` 的真实结算订单样本，因此还不能把这条链路升级为“已确认产生订单更新副作用”
  - 现阶段剩余阻塞已进一步收敛为：**当前授权主体下缺少“已进入多结算口径”的真实订单样本，导致 `buyin.colonelMultiSettlementOrders` 持续返回空数组**

## 六点二十一、2026-05-14 real-pre 收口复核

- 本轮目标：
  - 继续观察 `buyin.colonelMultiSettlementOrders` 是否出现非空样本
  - 对 real-pre 主链路做一轮最终回归，并把结果明确分类为代码问题 / 配置问题 / 平台权限问题 / 上游样本问题
- 证据目录：
  - watcher：`runtime/qa/out/real-pre-order-settlements-watch-20260514-142923`
  - final regression：`runtime/qa/out/real-pre-final-regression-20260514-143347`
- watcher 复跑结果：
  - `GET /api/douyin/institution-info` 仍返回 `星链达客 / colonelBuyinId=7351155267604218149`
  - `GET /api/douyin/order-settlements` 本轮返回 `status=success / remoteCode=10000 / orderCount=0`
  - `POST /api/douyin/order-sync-probes/raw` 3 天窗口返回 `orderCount=10`
  - 这 10 条样本仍全部满足未结算特征：`settled_goods_amount=0`，且 `colonel_order_info.real_commission=0` 或 `colonel_order_info_second.real_commission=0`
  - Docker / PostgreSQL 复核：`colonelsettlement_order` 中 `settle_time IS NOT NULL` 的历史记录数仍为 `48`
- 结论更新：
  - 当前不是“本地没有任何历史结算单”，而是“当前授权主体在最近观察窗口内没有可供 `buyin.colonelMultiSettlementOrders` 返回的非空样本”
  - 因此该问题应固化为：**上游样本等待项，不是本地代码阻塞项**

- final regression 覆盖范围与结果：
  - 健康检查：
    - `GET /api/actuator/health` 返回 `status=UP`
    - compose 运行态正常：`backend-real-pre / postgres-real-pre / redis-real-pre` 均为 `healthy`
  - 环境 / 配置基线：
    - `scripts/qa/check-real-pre-real-data.ps1` 本轮继续通过，说明 `real-pre` 仍保持真实数据隔离，不是 profile / DB / Redis 混用问题
  - 授权主体：
    - `GET /api/douyin/institution-info` 返回上游 `code=10000`
  - 活动 / 商品：
    - `GET /api/douyin/activities`
    - `GET /api/douyin/activities/3223881`
    - `GET /api/douyin/activity-product-list?activityId=3223881&count=20`
    - `GET /api/colonel/activities/3223881/products?count=20&productInfo=3814081914181124118`
    - `GET /api/colonel/activities/3223881/products?count=20&refresh=true&productInfo=3814081914181124118`
    - 上述接口均返回 `code=200`；业务视图样本商品 `3814081914181124118` 仍为 `bizStatus=LINKED / promotionLinkStatus=READY / promotionLinkCount=1`
  - 转链 / `pick_source_mapping`：
    - DB 证据文件：`product-state-db.json`
    - 样本活动 / 商品：`3223881 / 3814081914181124118`
    - `pick_source_mapping` 命中：
      - `pickSource=v.MxZLIw`
      - `colonel_buyin_id=7109679864001364265`
      - `source_type=NATIVE`
      - `promotion_link_id=b96e7c89-2475-4b7d-a271-5c84df058113`
    - `promotion_link` 命中：
      - `link_status=ACTIVE`
      - `promotion_url` 与业务视图中的 `promoteLink` 一致
  - 主订单同步：
    - `POST /api/orders/sync` 最近 30 分钟返回：
      - `totalFetched=97 / created=3 / updated=94 / attributed=1 / unattributed=96 / failed=0`
    - 说明主订单同步链路正常可执行；本轮未归因增加来自真实新单，不是同步失败
  - 订单归因：
    - `POST /api/orders/replay-attribution` 以 `reason=COLONEL_MAPPING_NOT_FOUND / limit=20 / dryRun=true` 复核：
      - `scanned=20 / attributed=0 / unattributed=20 / updated=0`
    - 说明 replay 机制本身可执行；当前这批样本仍未满足可安全命中条件，不构成代码故障
  - Dashboard：
    - `GET /api/dashboard/summary`（`channel_leader` 口径）返回：
      - `orderCount=425 / attributedOrderCount=425 / unattributedOrderCount=0 / attributionRate=1.0`
    - `GET /api/dashboard/metrics?timeField=createTime` 返回：
      - `todayOrderCount=1 / todayGmv=18.80 / pendingShipCount=395`
    - `GET /api/dashboard/activity-products?page=1&size=5` 正常返回活动商品聚合
  - Webhook 接收：
    - 本轮投递无 `order_id` 的 `doudian_alliance_colonelOpenEvent`
    - 收件箱事件可写入并消费成功
  - Webhook 重放：
    - 人工将同一条 capture 事件重置为 `RECEIVED` 后调用 `POST /api/douyin/webhook-events/replay?limit=5`
    - 返回 `scanned=1 / consumed=1 / failed=0`
    - 说明 replay 补偿链路正常
  - Webhook 定向同步：
    - 以真实订单号回投 `doudian_alliance_colonelOpenEvent`
    - 收件箱记录：
      - `status=CONSUMED`
      - `consume_result=COLONEL_OPEN_EVENT_SYNCED:fetched=0,created=0,updated=0,failed=0`
    - 说明“Webhook -> 定向同步 -> 上游多结算查询 -> 收件箱记账”链路本身正常；当前 `fetched=0` 仍是上游样本问题

- 本轮问题分类：
  - 代码问题：
    - 未发现新的 real-pre 主链路代码阻塞项
  - 配置问题：
    - 未发现新的 profile / compose / Redis / PostgreSQL 配置阻塞项
  - 平台权限问题：
    - 当前授权主体可以读取 `institution-info / activities / activity-product-list / order-sync` 主链路，也可以消费 Webhook / replay
    - 本轮未出现新的“账号无权限调用主链路接口”问题
  - 上游样本问题：
    - `buyin.colonelMultiSettlementOrders` 在最近观察窗口内仍无非空真实结算样本
    - 因此 Webhook 定向同步虽可执行，但暂时只能得到 `fetched=0`

检查项：

- [ ] 商品 ID、标题、店铺、价格字段对齐
- [ ] 图片缺失时前端可降级
- [ ] 类目、佣金、素材字段映射明确
- [ ] 与当前商品详情结构兼容

完成条件：

- [~] 至少拿到一组真实 SKU 样本（raw probe 已通过，业务 Gateway 待 real-pre 重启后复验）
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
- [~] 订单状态、金额、创建时间与达人账号字段已完成最小映射并通过真实同步验证，仍需补更多状态枚举样本
- [x] `colonel_native` 场景归因已打通：通过 `colonel_buyin_id + activity_id + product_id` 三层级联匹配，全库 316 单 100% `ATTRIBUTED`；修复前为 `pick_source` 重复映射导致归因失配，修复后改为复合键匹配
- [x] 已拿到真实达人字段 `author_buyin_id / author_account`，已支撑达人维度展示与 `colonel_native` 归因；`colonel_native` 场景不再依赖渠道侧 `pick_source / pick_extra`，改走 `colonel_buyin_id` 独立匹配路径

完成条件：

- [x] 订单状态、金额、时间字段映射固定（已覆盖当前真实样本，仍待更多状态枚举）
- [x] 至少一组真实订单样本完成入库
- [x] `/orders`、`/dashboard` 未出现回归（`/orders` 可读到真实同步订单；Dashboard `createTime` 口径 `todayOrderCount=305`、`todayGmv=5860.27`）；`/data` 默认按 `settle_time` 口径，当前真实样本含未结算订单，后续 M1.6 需明确 create_time / settle_time 展示切换
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
- [ ] 快递100企业版订阅凭证、公网回调 URL、`callbackSalt` 与真实样本单号明确
- [ ] 回调验签、公司编码 / 单号一致性校验、轨迹 `node_hash` 幂等与 `state=3` 签收推进在 real-pre 取证
- [ ] 实时查询仅作为手动刷新和超过 6 小时无回调后的兜底，不替代订阅推送主路径

2026-05-25 补充：real-pre 已接入快递100 `customer/key` 到本机 `.env.real-pre`，`docker-compose.real-pre.yml` 已透传 `LOGISTICS_KD100_*`，后端诊断接口返回 `provider=kuaidi100 / enabled=true / configured=true / status=READY`；已通过临时公网隧道验证 `/api/public/logistics/kuaidi100/callback` 可访问，正确签名 / 错误签名 / 缺 `param` 均返回快递100格式 JSON。当前仍缺真实运单号 + 快递公司编码 + 必要手机号，未发起真实实时查询或订阅提交；另快递100文档注明 `callbackurl` 默认仅支持 HTTP，当前 HTTPS 临时隧道需确认快递100侧是否已放通。

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

## 六点五、2026-05-10 Dashboard 真实化复核记录

| 模块 | 检查项 | 证据 | 结论 | 问题 | 下一步 |
| --- | --- | --- | --- | --- | --- |
| Dashboard 前端 | `/dashboard` 是否存在 mock / demo / hardcode / sample 静态业务数据 | `frontend/src/views/dashboard/index.vue` 当前仅保留 `-` 占位和空数组默认值，真实数据通过 `getSummary()` 载入；`frontend/src/api/dashboard.ts` 直连 `GET /dashboard/summary`；代码搜索 `rg -n "mock\\|demo\\|sample\\|hardcode" frontend/src/views/dashboard/index.vue frontend/src/api/dashboard.ts` 未发现业务演示数据回填 | 未发现静态业务数据，首页概览依赖真实接口返回 | 仍保留默认空态兜底；接口失败时页面会停留在空态，不能从前端单独判断“是假数据还是后端失败” | 后续若做验收截图，需同时附接口响应或数据库聚合，避免只看页面文案 |
| Data 前端 | `/data` 是否存在 mock / demo / hardcode / sample 静态业务数据 | `frontend/src/views/data/index.vue` 通过 `getMetrics()` 请求 `GET /dashboard/metrics`；`trend7d`、`todayOrderCount`、`todayGmv`、`serviceFee`、`grossProfit` 全部来自接口响应；`rg -n "mock\\|demo\\|sample\\|hardcode" frontend/src/views/data/index.vue frontend/src/api/data.ts` 未发现业务演示数据 | 未发现静态业务数据，数据页指标和趋势图依赖真实接口 | 页面文案里仍有“默认按订单创建时间统计”等说明性文字，但不是业务演示数据 | 继续沿用 `createTime / settleTime` 双口径复核，不要只看单一页面 |
| Dashboard API | 前端是否调用真实后端 API | real-pre 登录后调用 `GET http://localhost:8081/api/dashboard/summary`、`GET http://localhost:8081/api/dashboard/metrics?timeField=createTime` 均返回 200；当前 real-pre 容器 `saas-backend-real-pre-1` healthy，前端容器 `saas-frontend-real-pre-1` healthy | 已调用真实后端 API | 无 | 后续浏览器验收继续固定走 `3001/8081` 这组 real-pre 拓扑 |
| 运行环境 | 当前 profile 是否为 real-pre，且 Mock 开关关闭 | `.env.real-pre` 为 `SPRING_PROFILES_ACTIVE=real`、`DOUYIN_TEST_ENABLED=false`、`APP_TEST_ENABLED=false`、`ORDER_SYNC_ENABLED=true`；容器内 `printenv` 复核结果一致；`/api/actuator/health` 返回 `UP`；`scripts/qa/check-real-pre-real-data.ps1` 返回 `status=OK` | real-pre 环境成立，Mock 开关已关闭 | 无 | 保持 `test` 与 `real-pre` 隔离，不要把真实回流写回 `test` |
| Dashboard / Summary / Stats 后端 | 核心指标接口的数据来源 | `backend/src/main/java/com/colonel/saas/service/DashboardService.java` 直接对 `colonelsettlement_order` 做 `count/sum/group by`；`backend/src/main/java/com/colonel/saas/controller/DataController.java` 的 `/dashboard/metrics` 和 `backend/src/main/java/com/colonel/saas/controller/OrderController.java` 的 `/orders/stats` 也都直接读 `colonelsettlement_order`；`backend/src/main/resources/mapper/ColonelsettlementOrderMapper.xml` 的 `findPageWithScope` 同样从 `colonelsettlement_order co` 查询 | 核心指标、首页概览、订单统计已接真实订单表 | 当前核心指标不是从 `product / colonel_activity / pick_source_mapping` 直接聚合，而是以订单事实表为主；因此“业务指标真实化”成立，但“所有业务表全链路打通”不成立 | 后续若要宣称“全业务表打通”，需补充活动 / 商品 / 映射维度指标直接取数证明 |
| 业务表读取范围 | 后端是否读取真实 `orders / products / activities / pick_source_mapping` 业务表 | 实体映射：`ColonelsettlementOrder -> colonelsettlement_order`、`PickSourceMapping -> pick_source_mapping`、`ColonelsettlementActivity -> colonel_activity`；`OrderQueryService` 订单详情 SQL 会 `LEFT JOIN pick_source_mapping`、`LEFT JOIN product_operation_state`，寄样关联会 `JOIN product`；但 Dashboard 指标接口本身未直接查询 `product / colonel_activity / pick_source_mapping` | 后端确实会读取真实业务表，但 Dashboard 核心指标主数据源仍是 `colonelsettlement_order` | 当前 real-pre 库内 `product` active=0、`colonel_activity` active=0、`pick_source_mapping` active=15、`product_operation_state` active=256；说明真实订单已在跑，但商品 / 活动 active 主表并未成为当前 Dashboard 指标依赖 | 后续需单独复核 real-pre 商品 / 活动软删除策略是否符合“真实化”预期，避免把“订单真实”误写成“商品活动全量真实” |
| 数据库订单样本 | 是否存在真实三方订单数据 | `colonelsettlement_order` 最新 active 订单 `2307`；最近 5 条订单号样本为 `6952700454567023722`、`6952706867136632240`、`6952702629464708392` 等，带真实商品 ID、店铺名、`colonel_activity_id` 和归因状态；`scripts/qa/check-real-pre-real-data.ps1` 对 active mock/test/demo 行返回 0 | 存在真实三方订单数据，且 active mock/test/demo 行已清零 | 仍有 `172` 条未归因，原因集中为 `COLONEL_MAPPING_NOT_FOUND` | 继续按活动 / 商品 / 映射缺口补齐归因，不要用假数据掩盖未归因真实结果 |
| 指标一致性 | Dashboard 指标是否与 orders 表聚合结果一致 | API 复核：Summary `orderCount=2307 / orderAmount=4512678 / serviceFee=70682 / attributedOrderCount=2135 / unattributedOrderCount=172`；DB 聚合与之完全一致。Metrics(createTime) `todayOrderCount=1123 / todayGmv=21997.80 / pendingShipCount=2146`；DB 以 `create_time` 聚合结果完全一致。Metrics(settleTime) 当前返回 `0 / 0.00`，与 DB 当前“今日无已结算单”一致 | 指标与 orders 表聚合一致 | `/dashboard/summary` 默认按累计/结算口径，`/dashboard/metrics` 默认可切 `createTime / settleTime`；若验收时未带口径，容易误判数值“不一致” | 后续所有看板验收都必须显式写清楚使用 `summary`、`createTime` 还是 `settleTime` 口径 |
| 本轮总评 | 是否已与后端真实业务数据打通 | 前端无静态业务数据；real-pre 环境为 `real` 且 Mock 关闭；API 返回与真实订单聚合一致；active mock/test/demo 行为 0；但 Dashboard 核心指标仍主要建立在 `colonelsettlement_order`，未直接拉通 `product / colonel_activity / pick_source_mapping` 做指标聚合 | **半真实化** | 当前可以确认“订单事实层真实化”和“页面/API 不靠假数据”；不能确认“所有业务维度表均已被 Dashboard 真实消费” | 下一步单独拆 `活动 / 商品 / 推广映射` 维度复核，并把是否需要这些维度进入 Dashboard 指标定义写清楚 |

## 六点六、2026-05-10 P1-3 Dashboard 全链路真实化前置排查

| 模块 | 检查项 | 证据 | 结论 | 问题 | 下一步 |
| --- | --- | --- | --- | --- | --- |
| real-pre 商品 / 活动主表 | `product active=0`、`colonel_activity active=0` 原因 | real-pre 库复核：`product` 仅剩 3 条历史演示商品且全部 `deleted=1`，`colonel_activity` 当前 `0` 条 active；同时 `product_snapshot` active `239`、`product_operation_state` active `256`、`pick_source_mapping` active `15`。代码侧 `ColonelsettlementActivityService` 只在 `app.activities.seed-demo-on-empty=true` 时补本地演示活动，real-pre 未开启该兜底；`/api/colonel/activities` 走 `DouyinColonelActivityGateway.listActivities(...)`，不是先落 `colonel_activity` 再读 | 半完成 | real-pre 已有真实活动 / 商品快照链路，但真实活动没有沉淀进 `colonel_activity`，真实商品也没有沉淀进 `product` 主表；当前主表 `active=0` 属于链路落点不一致，不是“前端假数据”问题 | 下一步明确 real-pre 下活动 / 商品的真实落库口径：是继续以 `product_snapshot + product_operation_state` 为准，还是补齐 `colonel_activity / product` 主表同步 |
| 真实活动同步 / 活动商品同步 | 是否执行、是否成功入库 | 代码检索：`backend/src/main/java/com/colonel/saas/controller/ColonelActivityController.java` 中 `/colonel/activities` 直接调用真实上游 `listActivities(...)`；`/colonel/activities/{activityId}/products?refresh=true` 会调用 `queryActivityProducts(...)` 后执行 `productService.upsertSnapshots(activityId, result.items())`。当前库内 `product_snapshot` 已覆盖 9 个活动、238 个商品，最近同步时间分别到 `2026-05-10 02:22:13`（活动 `3223881`）和 `2026-05-10 02:05:48`（活动 `3916506`）；`product_operation_state` 对应 10 个活动、255 个商品 | 半完成 | 已确认“真实活动可读、真实活动商品可落快照 / 状态表”，但未发现定时活动主表同步任务；当前更像“按活动商品刷新时落快照”，不是“活动主表已全量入库” | 下一步补做活动主表同步策略复核，并确认 Dashboard 若要消费活动维度，应该依赖 `colonel_activity` 还是 `product_snapshot` |
| 真实订单商品匹配 | 真实订单 `product_id` 是否可匹配 `product / product_operation_state / product_snapshot` | real-pre 库 active 订单 `2330`；其中 `product` 主表可匹配 `0` 单，`product_operation_state` 可匹配 `203` 单，`product_snapshot` 可匹配 `203` 单；订单一共覆盖 `18` 个活动、`247` 个商品、`241` 个活动-商品对，但快照只命中其中 `8` 个活动-商品对 | 半完成 | 订单事实层已经真实回流，但商品链路覆盖面明显不足；大量真实订单的 `activity_id + product_id` 还没有进入当前本地商品快照 / 状态表 | 下一步按缺口活动优先补查 `3859423 / 3272470 / 3558291 / 3592624 / 3676949 / 3176208 / 3864871` 等高频活动的活动商品同步是否被执行 |
| pick_source_mapping 覆盖 | 15 条 active 映射是否能覆盖真实订单 `pick_source` | active 订单 `2330` 中，`pick_source` 非空订单 `0`，因此“按 `pick_source` 直接命中映射”的订单数为 `0`；但 `extra_data->>'colonel_buyin_id'` 非空订单 `2128`，其中可命中 active `pick_source_mapping` 的订单 `1998`，当前已归因订单 `2150` 的归因原因全部为 `COLONEL_ORDER_INFO` | 半完成 | 当前真实订单并不携带 `pick_source`，所以 15 条 active 映射不能证明“pick_source 链路已覆盖真实订单”；它们主要承担的是 native 团长订单映射，不是前端 Dashboard 直接可见的 `pick_source` 覆盖 | 下一步将“pick_source 直连覆盖”和“native 团长映射覆盖”分开统计，避免把 native 映射误写成 `pick_source` 完整覆盖 |
| 订单归因覆盖率 | 总订单数 / 有 `pick_source` 订单数 / `pick_source` 可匹配映射数 / 可匹配商品数 / 可匹配活动数 / 可归属招商数 / 可归属渠道数 / 未归因数 | real-pre 库当前结果：总订单数 `2330`；有 `pick_source` 订单数 `0`；`pick_source` 可匹配映射数 `0`；可匹配商品数 `203`（`product=0`，`product_operation_state/product_snapshot=203`）；可匹配活动数 `203`；可归属招商数 `175`（订单已有 `colonel_user_id` 或状态表已有 `assignee_id`）；可归属渠道数 `2200`（订单已有 `channel_user_id` 或 native 映射可落渠道）；未归因数 `180`。未归因原因全部为 `COLONEL_MAPPING_NOT_FOUND` | 半完成 | 当前订单归因覆盖更依赖订单事实层和 native 映射，商品 / 活动 / 招商责任人覆盖仍偏低；尤其招商覆盖只有 `175 / 2330`，不能写成“全链路已打通” | 下一步把 `COLONEL_MAPPING_NOT_FOUND` 按 `colonel_buyin_id + activity_id` 缺口清单继续拆解，优先处理 `3859423 / 3864871 / 3223881 / 3559407 / 3601935` 等未覆盖活动 |
| DashboardService 聚合层级 | 是否需要从单表订单聚合升级为“订单 + 商品 + 活动 + 映射”的分层聚合 | 代码复核：`backend/src/main/java/com/colonel/saas/service/DashboardService.java` 仅对 `colonelsettlement_order` 做 `count/sum/group by`；`/orders/stats` 与 `/dashboard/metrics` 也都以订单事实表为主。当前 Dashboard 数字可以和订单表完全对齐，但不能反映商品快照覆盖率、活动入库覆盖率、招商归属覆盖率、映射缺口覆盖率 | 下一步 | 继续维持单表聚合，只能证明“订单事实层真实化”；无法证明“活动 / 商品 / 映射全链路真实化” | 下一步若 P1-3 目标是“Dashboard 全链路真实化”，需要先补齐分层指标定义，再决定是否升级为 `订单 + 商品快照/状态 + 活动 + 映射` 的分层聚合 |

## 六点七、2026-05-10 P1-4 COLONEL_MAPPING_NOT_FOUND 缺口拆解

| 模块 | 检查项 | 证据 | 结论 | 问题 | 下一步 |
| --- | --- | --- | --- | --- | --- |
| 目标活动 3223881 | active 订单、商品覆盖、快照 / 状态命中、真实活动商品同步、active 过滤可见性 | 本次排查时点 active 订单 `419`、覆盖 `50` 个 `product_id`；`activity_id + product_id` 在 `product_snapshot` 命中 `3` 对，在 `product_operation_state` 命中 `3` 对；`product_snapshot` / `product_operation_state` 均存在该活动 active 行 `42` 条，最近更新时间 `2026-05-10 02:22:13`，说明真实活动商品同步已执行且成功落库；deleted-only 对数 `0 / 0`，未发现“同步成功但被 active 过滤隐藏” | 半完成 | 该活动已同步，但订单覆盖商品远大于当前快照覆盖商品；`COLONEL_MAPPING_NOT_FOUND` 当前 `28` 单，其中 `21` 单缺 `product_snapshot`，另有 `7` 单虽然已命中快照和状态表，但仍无可用映射，且订单 `pick_source` 仍为空 | 下一步优先补查该活动剩余高频商品对为什么未进入快照，以及已同步商品为何仍未形成 native 映射 |
| 目标活动 3859423 | active 订单、商品覆盖、快照 / 状态命中、真实活动商品同步、active 过滤可见性 | 本次排查时点 active 订单 `407`、覆盖 `42` 个 `product_id`；`product_snapshot` 命中 `0` 对，`product_operation_state` 命中 `0` 对；该活动在 `product_snapshot` / `product_operation_state` 中均无任何行，未发现真实活动商品同步成功落库痕迹；deleted-only 对数 `0 / 0` | 阻塞 | 当前订单量很大，但活动商品事实层完全空白；不是 active 过滤问题，而是没有同步成功落库证据 | 下一步把 `3859423` 列为最高优先级，先人工触发一次真实活动商品刷新并核对是否能落 `product_snapshot + product_operation_state` |
| 目标活动 3864871 | active 订单、商品覆盖、快照 / 状态命中、真实活动商品同步、active 过滤可见性 | 本次排查时点 active 订单 `42`、覆盖 `17` 个 `product_id`；`product_snapshot` 命中 `0` 对，`product_operation_state` 命中 `0` 对；该活动在 `product_snapshot` / `product_operation_state` 中均无任何行，未发现真实活动商品同步成功落库痕迹；deleted-only 对数 `0 / 0` | 阻塞 | 该活动未归因单量与活动商品未同步完全重合，属于“订单先到了，商品事实层没到” | 下一步优先触发一次 `3864871` 的真实活动商品刷新，并核对 refresh 请求是否成功写入快照和状态表 |
| 目标活动 3559407 | active 订单、商品覆盖、快照 / 状态命中、真实活动商品同步、active 过滤可见性 | 本次排查时点 active 订单 `10`、覆盖 `2` 个 `product_id`；`product_snapshot` 命中 `0` 对，`product_operation_state` 命中 `0` 对；该活动在 `product_snapshot` / `product_operation_state` 中均无任何行；deleted-only 对数 `0 / 0` | 阻塞 | 当前这 10 单都落在“活动商品未同步”，不是 `pick_source` 缺失可以单独解释的问题 | 下一步按小流量活动处理，先用真实活动商品刷新验证能否补齐这 2 个商品对 |
| 目标活动 3601935 | active 订单、商品覆盖、快照 / 状态命中、真实活动商品同步、active 过滤可见性 | 本次排查时点 active 订单 `16`、覆盖 `5` 个 `product_id`；`product_snapshot` 命中 `0` 对，`product_operation_state` 命中 `0` 对；该活动在 `product_snapshot` / `product_operation_state` 中均无任何行；deleted-only 对数 `0 / 0` | 阻塞 | 活动商品未同步直接造成 `13` 单 `COLONEL_MAPPING_NOT_FOUND`，剩余订单虽已归因但仍不能说明活动商品事实层完整 | 下一步与 `3859423 / 3864871 / 3559407` 一起走最小验证：只补活动商品刷新取证，不改看板逻辑 |
| 缺口分类 | `COLONEL_MAPPING_NOT_FOUND` 分类拆解 | 上一轮复核值为 `180` 单；本次排查时点由于 real-pre 同步继续运行，当前 `COLONEL_MAPPING_NOT_FOUND` 已增至 `185` 单。按互斥规则分类：`缺 activity_id = 20`、`缺 product_id = 0`、`缺 product_snapshot = 33`、`缺 product_operation_state = 0`、`字段格式不匹配 = 0`、`活动商品未同步 = 125`、`非系统转链 / 无 pick_source 链路 = 7`。其中 `125` 单的活动在 `product_snapshot` 与 `product_operation_state` 都没有 active 落库；`33` 单集中在已做过同步但商品对未覆盖的活动（以 `3223881` 为主）；`7` 单为 `3223881` 下已命中快照 / 状态表但仍无可用映射，且 `pick_source` 为空 | 半完成 | 当前主矛盾不是字段格式，也不是 `product_operation_state` 单表缺失，而是活动商品根本没同步、或者同步只覆盖了少量商品对 | 下一步把排查顺序固定为：先补活动商品同步，再看快照覆盖，再看映射沉淀；不要把 15 条 `pick_source_mapping` 写成真实订单 `pick_source` 覆盖证据 |
| 活动与映射关系 | 5 个目标活动在 `pick_source_mapping` 的沉淀情况 | 当前 active `pick_source_mapping` 里，5 个目标活动中只有 `3223881` 有 `2` 条映射，且都属于渠道侧 `pick_source=v.MxZLIw` 手工链路，没有 `colonel_buyin_id` native 映射；`3859423 / 3864871 / 3559407 / 3601935` 均为 `0`。订单商品覆盖与映射覆盖对比：`3223881` 有 `50` 个订单商品，仅 `1` 个商品命中过活动商品映射；其余 4 个活动都是 `0` | 半完成 | 当前 15 条 active 映射不能证明真实订单 `pick_source` 覆盖；目标活动的 native 映射沉淀几乎为空，是 `COLONEL_MAPPING_NOT_FOUND` 的直接证据之一 | 下一步先补最小 native 映射沉淀验证，优先围绕 `extra_data->>'colonel_buyin_id' + activity_id + product_id` 做取证，不改 DashboardService |
| 真实商品事实层口径 | real-pre 商品事实层应以哪张表为准 | 代码与数据同时指向 `product_snapshot + product_operation_state`：`/colonel/activities/{activityId}/products` 的真实刷新会直接 `upsertSnapshots(...)`；`ProductController` 旧版 `product` 入口已标注多处 `[已废弃]`，商品主链路说明已迁移到 `/colonel/activities/{activityId}/products/{productId}`；real-pre 当前 `product active=0`，但 `product_snapshot` active `239`、`product_operation_state` active `256`，且订单详情 SQL 也会关联这两张表 | 已完成 | 在 real-pre 当前阶段，把 `product` 主表当作“真实商品事实层”会得到错误结论；它更像共享商品库 / 旧入口残留，不是活动商品实时事实层 | 下一步统一验收口径：P1-4 / P1-5 排查都以 `product_snapshot + product_operation_state` 作为真实商品事实层，不再用 `product active=0` 推导“没有真实商品” |
| 最小修复建议 | 不做大重构前，下一步最小修复动作是什么 | 结合上面证据，当前最小动作不需要改 DashboardService，也不需要页面开发：1）对 `3859423 / 3864871 / 3559407 / 3601935` 手工触发真实活动商品刷新，验证是否能落 `product_snapshot + product_operation_state`；2）对 `3223881` 继续补齐未覆盖的高频商品对；3）对已命中快照 / 状态表但仍未归因的 `7` 单，只补 native 映射取证，不引入 mock `pick_source`；4）每次补齐后只重放对应缺口订单归因，不做全量大回放 | 下一步 | 当前缺口已经缩到“活动商品未同步 / 快照未覆盖 / native 映射未沉淀”三类，小步修复即可继续推进 | 下一步进入 P1-5 时，按活动逐个做“刷新活动商品 -> 复核快照 / 状态 -> 复核 native 映射 -> 定向 replay 订单”的闭环验证 |

## 六点八、2026-05-10 P1-5 活动商品真实同步补齐 + 缺口订单定向 replay（首轮）

固定 `as_of_time`：`2026-05-10 14:16:43`（Asia/Shanghai）。本节所有订单统计均按 `deleted=0 AND create_time <= as_of_time` 口径复核；活动商品刷新和 replay 操作发生在该时点之后，但前后对比始终锚定同一批 active 订单。

| 模块 | 检查项 | 证据 | 结论 | 问题 | 下一步 |
| --- | --- | --- | --- | --- | --- |
| 活动 3859423 | 修复前 | 固定 `as_of_time` 下，`3859423` active 订单 `407`、覆盖 `42` 个 `product_id`；`activity_id + product_id` 在 `product_snapshot` 命中 `0` 对，在 `product_operation_state` 命中 `0` 对；`product_snapshot` active 行 `0`、`product_operation_state` active 行 `0`；native 映射 `0`；该活动 `COLONEL_MAPPING_NOT_FOUND = 19`；全局 `COLONEL_MAPPING_NOT_FOUND = 185` | 半完成 | 订单事实层已存在，但活动商品事实层和 native 映射都为空，replay 前没有可用修复基础 | 下一步先执行真实活动商品刷新，再复核快照 / 状态落库是否建立最小事实层 |
| 活动 3859423 | 操作 | 使用 `admin / admin123` 登录 real-pre，执行 `GET /api/colonel/activities/3859423/products?count=20&refresh=true`；接口返回 `200`，业务视图 `activityId=3859423`、`total=20`、`hasMore=false`，说明真实活动商品刷新链路可调用；随后按 `as_of_time` 下的 `19` 条缺口订单执行 `POST /api/orders/replay-attribution`，仅传 `orderIds`，不做全表 replay | 已完成 | 这一步证明“活动商品刷新”和“定向 replay”能力本身都可执行，不是接口权限或调用链断裂 | 下一步必须看刷新后是否真的补到了订单解释层，而不是只补了展示快照 |
| 活动 3859423 | 修复后 | 刷新后 `product_snapshot` 新增该活动 active 行 `20` 条，`product_operation_state` 新增 active 行 `20` 条；固定 `as_of_time` 订单集上的 `activity_id + product_id` 命中数从 `0 -> 4`；最近落库时间为 `2026-05-10 06:17:26`。但 native 映射仍为 `0`；定向 replay 结果 `scanned=19 / attributed=0 / unattributed=19 / updated=19`；该活动 `COLONEL_MAPPING_NOT_FOUND` 仍为 `19`，全局 `COLONEL_MAPPING_NOT_FOUND` 仍为 `185` | 半完成 | 本轮只补到了活动商品事实层，没补到 native 映射层；所以 replay 虽然执行成功，但归因结果完全不变 | 下一步不要直接推进后续活动全量刷新；先围绕 `3859423` 补 native 映射沉淀验证，再决定是否复制到 `3864871 / 3559407 / 3601935` |
| 活动 3859423 | 结论 | `3859423` 路径验证结果很清楚：真实活动商品刷新有效，能把“未同步活动”推进到“已有快照 / 状态但仍缺映射”；但单靠刷新不能消除 `COLONEL_MAPPING_NOT_FOUND`。当前 19 条缺口订单仍全部缺 `channel_user_id / colonel_user_id`，典型 `colonel_buyin_id` 为 `7509487299174170880`、`7293293346398011698`、`7584756746875601206`、`7236516099667656963`，而 `pick_source_mapping` 中对应 native 映射仍为空 | 半完成 | 如果把“刷新成功落快照”误写成“缺口订单已修复”，会高估 P1-5 完成度 | 下一步把 `3859423` 作为 P1-5 第二步样板：优先验证 `extra_data->>'colonel_buyin_id' + activity_id + product_id` 的 native 映射沉淀链路 |
| 后续活动节奏 | 是否继续处理 `3864871 / 3559407 / 3601935 / 3223881` | 根据本轮门槛，“如果 `3859423` 路径有效，再按顺序处理后续活动”。当前只验证到“刷新有效、replay 无效”，尚未形成闭环修复；因此本轮未继续推进 `3864871 / 3559407 / 3601935`，`3223881` 继续保持最后处理且归类为“同步覆盖不足”，不与“未同步活动”混淆 | 下一步 | 继续向后推进会放大同类快照噪声，但还不能证明 replay 可修复缺口订单 | 下一步进入下一轮时，先补 `3859423` native 映射取证；若这一步打通，再按顺序复制到 `3864871 / 3559407 / 3601935`，最后单独处理 `3223881` 的覆盖不足问题 |

## 六点九、2026-05-10 P1-5.1 3859423 native 映射沉淀取证

固定 `as_of_time`：`2026-05-10 14:16:43`（Asia/Shanghai）。本节只分析 `3859423` 在该口径下的 `COLONEL_MAPPING_NOT_FOUND` 缺口订单与 native 映射代码路径，不继续处理其他活动。

| 模块 | 检查项 | 证据 | 结论 | 问题 | 下一步 |
| --- | --- | --- | --- | --- | --- |
| 缺口订单样本 | 3859423 的缺口订单明细 | 按固定 `as_of_time` 复核，当前 active 且 `create_time <= as_of_time` 的 `3859423` 缺口订单为 `20` 单（较上一轮的 `19` 单多 1 单，说明在同一订单创建时间口径下又补同步进来一条历史订单）。20 单明细均为：`order_id / activity_id=3859423 / product_id / attribution_status=UNATTRIBUTED / unattributed_reason=COLONEL_MAPPING_NOT_FOUND`。其中订单表列 `colonel_buyin_id` 全空，但 `extra_data->>'colonel_buyin_id'` 全都有值，主要集中为 `7509487299174170880`，另有 `7293293346398011698`、`7584756746875601206`、`7236516099667656963` | 半完成 | “订单缺 colonel_buyin_id”不是直接原因；真正情况是订单主列没落值，但 `extra_data` 已带 19 位 buyin id | 下一步后续排查都以 `extra_data->>'colonel_buyin_id'` 为准，不再用空的订单主列误判“源数据缺失” |
| 归因代码读参 | native 映射实际使用哪些 key | `AttributionSourceNormalizer.normalize(...)` 会把 `extra_data.colonel_order_info.colonel_buyin_id` / `colonelOrderInfo.colonelBuyinId` 扁平化到 `source['colonel_buyin_id']`；`AttributionService.resolveAttribution(...)` 对 native 归因实际读的是 `colonel_buyin_id + activity_id + product_id`，并通过 `resolveNativeColonelAttribution(...)` / `resolveNativeColonelOrderMapping(...)` 去 `pick_source_mapping` 里查 `colonel_buyin_id + activity_id + product_id` 精确匹配，找不到再降级到 `activity_id + product_id`，最后才尝试只按 `colonel_buyin_id` | 已完成 | 代码已经会读 `extra_data`，问题不在“代码没读到 extra_data”，而在“本地没有对应 native 映射行可查” | 下一步把修复焦点放在 native 映射沉淀，不再纠结 replay 读参逻辑 |
| native 映射结构 | native 映射需要哪些本地字段 | `PickSourceMapping` 实体里 native 归因关键字段是 `colonel_buyin_id`、`activity_id`、`product_id`、`user_id`、`dept_id`、`channel_user_name`；归因真正用于落订单归属的是 `mapping.getUserId()`，没有单独的 `owner_id` / `account_id` 字段。也就是说，仅有 `colonel_buyin_id` 不够，若映射行没有 `user_id`，订单依然不能归到渠道负责人 | 已完成 | native 映射本质上不仅要“识别是哪条团长链路”，还要回答“归给哪个渠道用户” | 下一步所有补映射方案都要明确 `user_id` 从哪里来，否则就算补了 buyin id 也无法落归属 |
| 映射沉淀入口 | native 映射应该在哪个流程生成 | 代码检索结果：1）`活动商品刷新` 只会 `upsertSnapshots(activityId, result.items())`，不会写 `pick_source_mapping`；2）`订单 replay` 只读映射，不建映射；3）`PickSourceMappingService.ensureFromOrder(...)` 只处理“有 `pick_source` 且已归因”的订单，native 空 `pick_source` 订单不会进这里；4）当前真正会写 `pick_source_mapping` 的业务主入口是 `ProductService.generatePromotionLink(...)` / `PromotionApi.saveMappingIfNecessary(...)`，即转链流程 | 已完成 | 现有系统没有“活动刷新自动沉淀 native 映射”或“replay 自动补 native 映射”的逻辑；native 映射主要依赖转链时写入 | 下一步如果不改代码，就只能先补业务动作（例如先形成有效转链）；如果要最小代码修复，就要在“能拿到 buyin + user_id 的链路”上补沉淀 |
| 3859423 当前业务状态 | 为什么 3859423 的 native 映射仍为 0 | 数据复核：`pick_source_mapping` 中 `activity_id=3859423` 且 `status=1` 的行数为 `0`；`promotion_link` 中 `activity_id=3859423` 的行数为 `0`；`product_operation_state` 中该活动 20 条 active 行全部 `assignee_id=NULL`、`promote_link/short_link` 为空、`selected_to_library=false`、`biz_status=PENDING_AUDIT`。这说明 3859423 当前既没有转链记录，也没有渠道负责人、没有入库推进痕迹 | 阻塞 | 对这 20 单来说，系统没有任何现成业务事实可以生成 native 映射中的 `user_id`；单纯刷新活动商品不会补这层 | 下一步先确认业务上是否应由某个渠道用户承接 3859423 下相关商品；若没有，就算技术上补 buyin id 也无法自动归因 |
| 直接原因判定 | native 映射为 0 的直接原因属于哪一类 | 对照本轮要求逐项判定：`订单缺 colonel_buyin_id` 否；`字段存在但代码未读取 extra_data` 否；`key 不一致` 否（代码查的是 `colonel_buyin_id + activity_id + product_id`，与订单和映射设计一致）；`沉淀流程未执行` 是；`需要业务人工绑定招商/团长关系` 是。更具体地说：native 映射沉淀流程当前只挂在转链，而 3859423 既没有转链，也没有本地活动主表 `colonel_activity` 行；`ProductService.generatePromotionLink(...)` 还依赖 `resolveColonelBuyinIdFromActivity(activityId)` 从 `colonel_activity` 取 `colonel_buyin_id`，但 real-pre 当前 `colonel_activity` 对 `3859423` 是空的，所以即使后续有人在这条活动上转链，当前代码也拿不到 `colonel_buyin_id` 去写 native 映射 | 阻塞 | 直接阻塞并不是 replay，而是“映射生成流程没跑”叠加“生成流程当前还拿不到活动 buyin 主数据” | 下一步优先把修复点锁在映射生成路径，而不是继续重放同一批订单 |
| 是否具备 native 归因条件 | 3859423 当前是否已经具备 native 映射归因条件 | 从“订单原始字段”看，具备：`activity_id / product_id / extra_data.colonel_buyin_id` 都存在；从“本地映射归因”看，不具备：没有 `pick_source_mapping` native 行，没有 `promotion_link`，没有 `user_id` 归属来源，没有 `colonel_activity.colonel_buyin_id` 本地主数据 | 半完成 | 上游订单事实已经具备归因原料，但本地映射事实层还不具备归因条件 | 下一步先补本地 native 映射事实层，再谈 replay 是否能见效 |
| 是否需要最小代码修复 | 当前是否需要最小代码修复，修复点在哪 | 需要。最小代码修复点不在 `DashboardService`，而在 native 映射沉淀路径：第一修复点是让本地能拿到活动级 `colonel_buyin_id`，至少补齐 `colonel_activity` 对真实活动 `3859423` 的沉淀，或让 `generatePromotionLink(...)` 在 `colonel_activity` 为空时从别的可靠事实源取到 `colonel_buyin_id`；第二修复点是为 native 订单补一条“有 `user_id` 的映射沉淀路径”，否则 replay 永远只能查空。仅靠订单 replay 本身不能修复这个问题 | 下一步 | 当前 replay 无变化不是 replay 逻辑错，而是前置映射事实不存在 | 下一步进入修复轮时，优先做最小修复：补活动主表 buyin 沉淀 / 补 native 映射生成条件；修复后再回放 `3859423` 的 20 单验证效果 |

## 六点十、2026-05-10 P1-5.2 3859423 native 映射最小代码修复

固定 `as_of_time`：`2026-05-10 14:16:43`（Asia/Shanghai）。本节只围绕 `3859423` 执行最小代码修复、真实转链验证和 dry-run replay 取证，不处理 `3864871 / 3559407 / 3601935 / 3223881`，也不修改 `DashboardService`。

| 模块 | 检查项 | 证据 | 结论 | 问题 | 下一步 |
| --- | --- | --- | --- | --- | --- |
| 活动 3859423 | 修复前 | 修复前 real-pre 库中 `pick_source_mapping` 对 `activity_id=3859423 AND source_type='NATIVE'` 的 active 行数为 `0`，`promotion_link` 对 `activity_id=3859423` 的 active 行数为 `0`，`colonel_activity` 对 `activity_id=3859423` 的 active 行数也为 `0`；固定 `as_of_time` 下该活动 `COLONEL_MAPPING_NOT_FOUND=20`，其中 `product_id=3816127512791089531` 的缺口订单有 `2` 单，订单 `extra_data->>'colonel_buyin_id' = 7293293346398011698` | 半完成 | 修复前能证明“订单侧有 buyin id”，但映射事实层完全空白 | 下一步只对 `3816127512791089531` 走一次最小真实业务链路，验证 native 映射能否开始沉淀 |
| 活动 3859423 | 操作 | 代码侧最小修复：`ProductService.generatePromotionLink(...)` 的 native 映射 `colonel_buyin_id` 改为分层解析，优先读本地 `colonel_activity.colonel_buyin_id`，为空时尝试补水真实 `ActivityApi.detail(...)` 到本地 `colonel_activity`，再回退 `product_snapshot.raw_payload`、`product_operation_state.audit_payload`；`PickSourceMapping` 新增 `source_type`，native 映射按 `source_type='NATIVE'` 落库，并新增 real-pre SQL 升级 `alter-pick-source-mapping-native-source-type.sql` 与 native 组合唯一索引 `uk_psm_native_activity_product_user`。真实验证动作：1）`biz_staff` 审核 `3859423 / 3816127512791089531`，商品进入 `APPROVED + selectedToLibrary=true`；2）`biz_leader` 分配给 `biz_staff`；3）`channel_staff` 调用 `POST /api/colonel/activities/3859423/products/3816127512791089531/promotion-links`，真实上游 `buyin.instPickSourceConvert` 返回 `pickSource=v.MxZLIw / pickExtra=channel_channelstaff / shortId=MAUOKJYP / promoteLink`；4）仅对 20 条缺口订单执行 `POST /api/orders/replay-attribution` 且 `dryRun=true` | 已完成 | 这一步证明“最小代码修复 + 真实转链 + dry-run replay”链路都能执行，不是接口权限或写库中断 | 下一步看 native 映射沉淀后的字段是否真能被 3859423 缺口订单消费 |
| 活动 3859423 | 修复后 | 修复后 real-pre 库新增 `promotion_link=1`、`pick_source_mapping native=1`：`activity_id=3859423`、`product_id=3816127512791089531`、`user_id=fd701815-f7ed-4046-9db6-0ba2fee42eab`、`pick_source=v.MxZLIw`、`source_type=NATIVE`、`promotion_link_id=26057cbd-1543-4c18-9525-3bfc977c7f6b`；本地 `colonel_activity` 也已补水出 active 行，`colonel_buyin_id=7351155267604218149`。对固定 `as_of_time` 下的 20 条缺口订单做 dry-run replay，结果为 `scanned=20 / attributed=2 / unattributed=18 / updated=0 / dryRun=true`；dry-run 后 DB 中 `COLONEL_MAPPING_NOT_FOUND` 仍为 `20`，没有真实落库变化 | 半完成 | native 映射事实层已开始沉淀，且 20 单里已有 `2` 单在 dry-run 中可命中；但本轮只验证了 dry-run，未真实更新历史订单 | 下一步必须先判断可命中的 2 单是否满足 `mapping.created_at <= order.create_time`，不满足则只能作为业务授权回填候选，不能直接真回放 |
| 活动 3859423 | 关键差异取证 | 新沉淀的 native 映射 `colonel_buyin_id=7351155267604218149` 来自本地补水后的 `colonel_activity.extra_data.colonel_buyin_id`；而 `3816127512791089531` 这 2 条缺口订单的 `extra_data->>'colonel_buyin_id'` 都是 `7293293346398011698`，两者不一致。同时，这 2 条订单的 `create_time` 分别为 `2026-05-09 22:08:13`、`2026-05-10 01:04:11`，而新 native 映射 `create_time=2026-05-10 06:41:19.55264`，明显晚于订单创建时间。dry-run 能命中 `2` 单，不是因为 `colonel_buyin_id` 精确对齐，而是因为当前 `AttributionService.resolveNativeColonelOrderMapping(...)` 在 `activity_id + product_id` 唯一时会降级命中该唯一映射 | 半完成 | 当前 native 映射已经能落库，但 3859423 订单解释层仍存在两个残余问题：1）沉淀出的活动级 `colonel_buyin_id` 与订单 `extra_data` buyin id 不一致；2）本轮映射生成时间晚于历史订单时间，不能直接把 dry-run 命中写成“历史单已可安全回填” | 下一步继续限定在 3859423：先复核 `ActivityApi.detail(...)` 的 `colonel_buyin_id` 是否就是业务上应使用的 native key；若不是，最小修复点应继续下沉到 `product_snapshot / product_operation_state` 的更细粒度字段，而不是直接放开历史真回放 |
| 活动 3859423 | 结论 | 本轮最小代码修复已经达成两个目标：1）真实转链时可在 `3859423` 下新增 `promotion_link`；2）可新增 `source_type=NATIVE` 的正式映射行，说明 native 映射不再完全依赖空的 `colonel_activity` 旧状态。与此同时，本轮结论不能扩大：`3859423` 的 native 映射只是“开始沉淀”，还不能写成“历史缺口订单已真实修复”；当前更准确的状态是 `promotion_link 0 -> 1`、native 映射 `0 -> 1`、dry-run 命中 `0 -> 2`、`COLONEL_MAPPING_NOT_FOUND` 实际数量不变 | 半完成 | “映射能沉淀”与“历史单可安全真归因”不是一回事；本轮只证明第一步，不证明第二步 | 下一步若要继续推进，只能做 3859423 的第二轮精确取证：核 buyin id 来源一致性、核 `mapping.created_at <= order.create_time`，满足条件后再决定是否申请业务授权真回放 |

## 六点十一、2026-05-10 P1-5.3 3859423 native 映射机制一次性修复

固定 `as_of_time`：`2026-05-10 14:16:43`（Asia/Shanghai）。本节只围绕 `3859423` 修 native 映射机制，不修改 `DashboardService`，也不真实更新历史订单归因。

| 模块 | 检查项 | 证据 | 结论 | 问题 | 下一步 |
| --- | --- | --- | --- | --- | --- |
| 活动 3859423 | 修复前 | 代码修复前已有历史真实转链沉淀：`pick_source_mapping` 中 `activity_id=3859423 AND source_type='NATIVE'` 为 `1` 条，`promotion_link` 为 `1` 条；对应 native 映射 `colonel_buyin_id=7351155267604218149`、`user_id=fd701815-f7ed-4046-9db6-0ba2fee42eab`、`create_time=2026-05-10 06:41:19.55264`。固定 `as_of_time` 下重新复核 `3859423` 的 `COLONEL_MAPPING_NOT_FOUND` 订单，不再是上一轮记录的 `19/20`，而是 **`21` 单**（本次查询时间 `2026-05-10`，筛选条件仍为 `create_time <= 2026-05-10 14:16:43`） | 半完成 | 真实库在同一固定时点内又补同步进了历史订单，所以本轮缺口单数应以 `21` 为准，不能继续沿用旧的 `19/20` | 后续所有 3859423 统计都以这次固定条件查询结果为准，避免口径漂移 |
| 活动 3859423 | 代码修复 | `ProductService.generatePromotionLink(...)` 已改为分层解析 native `colonel_buyin_id`：先查 `product_snapshot`，再查 `product_operation_state`，再查 `colonel_activity`，最后才补水 `ActivityApi.detail(...)`；`PickSourceMappingService.saveOrUpdate(...)` 已支持 `source_type=NATIVE`、native 组合键去重和冲突告警；`OrderAttributionReplayService` dry-run 已输出 `nativeKeyMatched / safeToUpdate / unsafeBecauseCreatedAfterOrder / colonelBuyinIdMismatch / ambiguousMapping / stillUnattributed`；新增单测覆盖商品事实层优先、活动表为空时仍可生成 native 映射、冲突映射标记 ambiguous、`mapping.created_at > order.create_time` 时 dry-run 不允许真更新。验证结果：`mvn -q -DskipTests compile` 通过；`mvn -q "-Dtest=ProductServiceTest,PickSourceMappingServiceTest,AttributionServiceTest" test` 通过；补充回归 `OrderAttributionReplayServiceTest` 通过 | 机制已修复 | 机制修复的是“生成和判断逻辑”，不是“历史订单已经自动变为可安全回填” | 下一步只在真实业务再发生一次可转链商品动作时继续观察是否能优先命中商品事实层 |
| 活动 3859423 | 当前实际使用的 `colonel_buyin_id` 来源 | real-pre 重启后容器日志取证：`2026-05-10T07:30:55.895Z ProductService: Native mapping resolved for activityId=3859423, productId=3816127512791089531, colonelBuyinId=7351155267604218149, source=COLONEL_ACTIVITY`。同时数据库复核：`product_snapshot.raw_payload` 与 `product_operation_state.audit_payload` 当前都**未包含** `3816127512791089531` 对应的 `colonel_buyin_id=7293293346398011698`，因此该商品本次运行时虽然已经具备“商品事实层优先”的代码能力，但实际命中的来源仍是活动层 `colonel_activity` | 半完成 | 代码优先级已调正，但当前 real-pre 的这个商品事实样本还没有落出历史订单里的 product-level `colonel_buyin_id`，所以运行态无法证明“已改用商品事实层” | 下一步继续观察真实活动商品刷新或商品状态补水后，`product_snapshot / product_operation_state` 是否能沉淀 product-level buyin id |
| 活动 3859423 | 真实转链与沉淀结果 | 用 `channel_staff/admin123` 调用 `POST /api/colonel/activities/3859423/products/3816127512791089531/promotion-links`，当前返回 `code=460 / 当前状态不允许执行PROMOTION_LINK，当前状态：LINKED`；说明该商品在本轮验证前已是 `LINKED`，因此没有新增真实 `promotion_link` 或 native 映射。DB 复核仍为：`native 映射数量=1`、`promotion_link 数量=1`，最近一条 native 映射仍是 `d03cd0ea-0d2a-45cc-96c9-1c286d7bdd5e / colonel_buyin_id=7351155267604218149 / source_type=NATIVE` | 半完成 | 当前样本商品已进入 `LINKED` 状态，不能再用它重复制造“修复后新增一条映射”的运行态证据 | 下一步若要补充运行态新增证据，只能在 `3859423` 下找另一条真实可转链且未 `LINKED` 的商品，不扩到其他活动 |
| 活动 3859423 | dry-run replay 诊断 | 对固定 `as_of_time` 下 `3859423` 的 `21` 条缺口订单执行 `POST /api/orders/replay-attribution` 且仅传 `orderIds + dryRun=true`，结果为：`scanned=21 / attributed=2 / unattributed=19 / updated=0 / nativeKeyMatched=2 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=2 / colonelBuyinIdMismatch=2 / ambiguousMapping=0 / stillUnattributed=19` | 半完成 | 当前只有 `2` 单能通过 `activity_id + product_id` fallback 命中现有 native 映射，而且这 `2` 单全部同时满足“buyin id 不一致”与“映射生成时间晚于订单时间”，因此 dry-run 只能诊断命中，不能真实更新 | 下一步除非未来形成 `mapping.created_at <= order.create_time` 且 `colonel_buyin_id` 一致的真实映射，否则历史订单仍不能安全回填 |
| 活动 3859423 | 历史订单是否可安全回填 | 这 `2` 条可命中的历史订单分别为 `6952715652988737383 (create_time=2026-05-09 22:08:13)`、`6952704630788003151 (create_time=2026-05-10 01:04:11)`；订单 `extra_data->>'colonel_buyin_id' = 7293293346398011698`，但当前唯一 native 映射 `create_time=2026-05-10 06:41:19.55264` 且 `colonel_buyin_id=7351155267604218149`。因此在保留 `mapping.created_at <= order.create_time` 安全判断前提下，**历史订单不可安全回填** | 阻塞 | 仍不能回填的直接原因有两个：1）映射创建时间晚于订单创建时间；2）订单侧 buyin id 与当前 native 映射 buyin id 不一致 | 下一步若业务要真回填，只能先补出“早于订单时间、且 buyin id 一致”的真实 native 映射，或单独走业务授权回填流程 |
| 活动 3859423 | 本轮结论 | 本轮能明确分成两层：第一层，**机制已修复**，代码已经具备“商品事实层优先、活动表缺失可兜底、native 映射冲突可识别、dry-run 可给出安全诊断”的能力；第二层，**历史订单暂不能安全回填**，因为 real-pre 当前 3859423 的真实商品事实还没有提供与历史订单一致的 product-level `colonel_buyin_id`，现有 native 映射仍来自 `COLONEL_ACTIVITY` 且创建时间晚于历史订单。基于这个状态，当前**不建议立即扩展到 `3864871 / 3559407 / 3601935`**，应先在 `3859423` 把“商品事实层是否能沉淀一致 buyin id”钉实 | 下一步 | 若现在直接平移到其他活动，极可能复制出“机制对了，但运行态 buyin 来源仍不对、历史单依然不可回填”的同类问题 | 下一步先限定在 `3859423` 继续观察真实活动商品同步 / 商品状态补水是否能沉淀 product-level buyin id；一旦这一点拿到正证据，再扩到 `3864871 / 3559407 / 3601935` |

## 六点十二、2026-05-10 P1-5.4 3859423 / 3816127512791089531 商品事实层 `colonel_buyin_id` 取证

固定 `as_of_time`：`2026-05-10 14:16:43`（Asia/Shanghai）。本节只围绕 `activity_id=3859423 / product_id=3816127512791089531`，不处理其他活动，不真实更新历史订单，也不删除现有 native 映射。

| 模块 | 检查项 | 证据 | 结论 | 问题 | 下一步 |
| --- | --- | --- | --- | --- | --- |
| 订单事实层对照 | 历史订单侧的目标 `colonel_buyin_id` 是什么 | 固定 `as_of_time` 下，该商品在 `3859423` 的未归因历史订单仍是 `2` 单：`6952715652988737383 (2026-05-09 22:08:13)`、`6952704630788003151 (2026-05-10 01:04:11)`；两单 `extra_data->>'colonel_buyin_id'` 均为 **`7293293346398011698`** | 已完成 | 后续所有“商品事实层是否正确”的判断都以这两个历史订单的 buyin id 为对照，不再用活动层 buyin id 代替 | 下一步继续比对真实活动商品接口与本地商品事实层是否出现同一值 |
| 真实活动商品接口 | 上游真实活动商品原始响应中是否存在 `7293293346398011698` 或等价字段 | `GET /api/douyin/activity-product-list?activityId=3859423&count=20` 的真实原始响应里，`product_id=3816127512791089531` 这条商品明确带有 **`origin_colonel_buyin_id=7293293346398011698`**，同时还有 `origin_activity_id=3906136 / origin_institution_id=3555765809504767219 / origin_institution_name=星芒联创`。说明上游活动商品接口**存在**与历史订单一致的 product-level buyin 事实，只是字段名不是当前解析器优先识别的 `colonel_buyin_id` | 已完成 | 这一步排除了“上游活动商品接口没有正确 buyin”的可能 | 下一步继续看本地商品事实层有没有把 `origin_colonel_buyin_id` 保存下来 |
| `product_snapshot` 事实层 | `product_snapshot.extra_data` 或等价字段中是否存在 `7293293346398011698` | real-pre `product_snapshot` 表**没有 `extra_data` 列**，当前等价事实列只有 `raw_payload`。查询 `activity_id=3859423 AND product_id=3816127512791089531` 的快照行，`raw_payload` 内容仅为 `item.toMap()` 生成的精简字段：`productId/title/cover/price/.../detailUrl`，其中 **既没有 `7293293346398011698`，也没有 `origin_colonel_buyin_id`**；`position('7293293346398011698' in raw_payload)=0`，`position('origin_colonel_buyin_id' in raw_payload)=0` | 半完成 | 商品快照表当前没有保存这个上游字段，因此运行态无法从 `product_snapshot` 命中历史订单 buyin | 下一步若要让商品事实层可用，最小修复应落在活动商品同步入库链路，而不是继续指望现有快照解析 |
| `product_operation_state` 事实层 | `product_operation_state.extra_data` 或等价字段中是否存在 `7293293346398011698` | real-pre `product_operation_state` 表同样**没有 `extra_data` 列**，当前唯一可复用文本列是 `audit_payload`。目标行 `activity_id=3859423 / product_id=3816127512791089531` 的 `audit_payload` 为空字符串，`position('7293293346398011698' in coalesce(audit_payload,''))=0`，`position('origin_colonel_buyin_id' in coalesce(audit_payload,''))=0` | 半完成 | 当前状态表没有任何 product-level buyin 事实；它只能作为“如果后续有人写入补充信息时的次级兜底”，不能解释历史订单 buyin | 下一步不建议把历史订单 buyin 强塞进状态表；应优先补齐活动商品同步对真实上游字段的保存 |
| ProductService 解析路径 | 为什么最终命中 `COLONEL_ACTIVITY` 而不是商品事实层 | 代码链路清晰：1）`resolveColonelBuyinIdForNativeMapping(...)` 先查 `resolveColonelBuyinIdFromSnapshot(...)`，再查 `resolveColonelBuyinIdFromOperationState(...)`，再查 `resolveColonelBuyinIdFromActivity(...)`，最后才 `hydrateColonelActivityMeta(...)`；2）`resolveColonelBuyinIdFromSnapshot(...)` / `resolveColonelBuyinIdFromOperationState(...)` 只识别 `colonel_buyin_id / colonelBuyinId`，再退化到正则 `(?:colonel_buyin_id|colonelBuyinId)`；3）当前 `product_snapshot.raw_payload` 和 `product_operation_state.audit_payload` 都没有这两个 key，也没有 `origin_colonel_buyin_id`；4）`resolveColonelBuyinIdFromActivity(...)` 能从 `colonel_activity.colonel_buyin_id=7351155267604218149` 直接命中，因此运行态日志才会落成 `source=COLONEL_ACTIVITY` | 已完成 | 现在不是“优先级没生效”，而是前两级没有可命中的商品事实，第三极活动表自然接管 | 下一步需要区分“解析没命中”和“落库没保存”；当前主因在落库链路 |
| 落库链路 | 为什么商品事实层没有保存 `origin_colonel_buyin_id` | 根因在 DTO 收窄：`RealDouyinProductGateway.normalizeProductItem(...)` 从真实 `raw` map 组装 `ActivityProductItem` 时，只保留 `productId/title/price/.../detailUrl` 等固定字段；`DouyinProductGateway.ActivityProductItem` 结构本身也**没有 `origin_colonel_buyin_id` 字段**；随后 `ProductService.fillSnapshot(...)` 直接 `snapshot.setRawPayload(String.valueOf(item.toMap()))`，保存的是**裁剪后的** `item.toMap()`，不是原始 upstream item JSON。结果是：虽然真实接口给了 `origin_colonel_buyin_id=7293293346398011698`，但在进入 `product_snapshot.raw_payload` 之前就已经丢失 | 阻塞 | 当前更准确的三分支结论属于 **“接口有正确 buyin，但落库没保存”**，不是“商品事实层和接口都没有正确 buyin”，也不是单纯“解析路径有值但没命中” | 下一步若要最小修复，应先补活动商品同步对原始字段的保存，再谈解析器是否要补别名 |
| 三分支结论 | 应归入哪一类 | 本轮对三种可能性的判断如下：1）`商品事实层有正确 buyin，但解析没命中`：**否**，因为当前 `product_snapshot/raw_payload` 与 `product_operation_state/audit_payload` 都没有 `729329...`；2）`接口有正确 buyin，但落库没保存`：**是，本轮主结论**，因为真实接口明确返回 `origin_colonel_buyin_id=7293293346398011698`，但 DTO 和 `fillSnapshot(...)` 保存链路把它裁掉了；3）`商品事实层和接口都没有正确 buyin，历史订单不能自动回填`：**否**，接口层已有正确来源，只是本地未沉淀 | 已完成 | 这意味着当前还不该把 `3859423` 标记成“不可自动归因到永远”，但也不能跳过商品同步修复直接硬回填历史订单 | 下一步按“最小同步修复 + 最小解析别名修复”思路收口，不扩大到其他活动 |
| 最小修复建议 | 如果只是解析路径问题，应该怎么修 | 本轮不是纯解析问题，但若后续商品事实层已能保存 origin 字段，最小解析修复应是：`resolveColonelBuyinIdFromSnapshot(...)` / `resolveColonelBuyinIdFromOperationState(...)` 的 JSON key 和 regex 同时补识别 `origin_colonel_buyin_id / originColonelBuyinId`，并在日志里明确区分“直接 buyin”与“origin buyin”来源，避免继续误落 `COLONEL_ACTIVITY` | 下一步 | 仅做解析别名修复在当前 real-pre 还不够，因为本地事实层压根没保存这个字段 | 下一步应先修同步落库，再补解析别名 |
| 最小同步修复建议 | 如果是落库缺字段，活动商品同步最小修复是什么 | 最小修复点应落在活动商品同步，不动 DashboardService：1）`RealDouyinProductGateway` 为 `ActivityProductItem` 增加原始字段承载，至少补 `origin_colonel_buyin_id`，更稳妥的是附带完整 `rawPayload`；2）`ProductService.fillSnapshot(...)` 不再只保存 `item.toMap()` 的裁剪版，而是保存包含 `origin_colonel_buyin_id` 的原始商品 payload（建议 JSON，而不是当前 `String.valueOf(map)` 文本）；3）如需状态表兜底，可在后续人工审核/入库动作里把同一字段写入 `audit_payload`，但这不是第一落点 | 下一步 | 当前缺的不是 Dashboard 聚合，而是活动商品同步没有把关键原始字段沉淀成本地商品事实 | 下一步先做这一处最小修复，再重新刷新 `3859423` 商品并复核 `product_snapshot.raw_payload` 是否出现 `7293293346398011698` |
| 本轮结论 | 最终判断 | 本轮结论明确收口为：**真实活动商品接口里有正确 buyin（`origin_colonel_buyin_id=7293293346398011698`），但本地商品事实层没有保存下来，因此 ProductService 只能回退命中 `COLONEL_ACTIVITY=7351155267604218149`**。所以当前不是“历史订单永远不可自动归因”，而是“在补齐商品同步保存前，历史订单不能自动安全回填”。现阶段不能继续硬修历史订单，也不应扩到其他活动；先把 `3859423 / 3816127512791089531` 的商品事实层字段沉淀补齐，再复核 buyin 来源是否从 `COLONEL_ACTIVITY` 切回商品事实层 | 下一步 | 还没到可以安全回填历史订单的阶段 | 下一步继续只围绕 `3859423` 做最小同步修复验证，不扩活动范围 |

## 六点十三、2026-05-10 P1-5.5 3859423 / 3816127512791089531 `origin_colonel_buyin_id` 落库修复

固定 `as_of_time`：`2026-05-10 14:16:43`（Asia/Shanghai）。本节只围绕 `activity_id=3859423 / product_id=3816127512791089531`，不修改 `DashboardService`，不处理其他活动，不补 Mock 数据，也不真实更新历史订单归因。

| 模块 | 检查项 | 证据 | 结论 | 问题 | 下一步 |
| --- | --- | --- | --- | --- | --- |
| 代码修复 | `origin_colonel_buyin_id` 是否不再被 DTO 收窄丢失 | 已修改 `backend/src/main/java/com/colonel/saas/gateway/douyin/DouyinProductGateway.java`：`ActivityProductItem` 新增 `originColonelBuyinId` 与 `rawPayload`，`toMap()` 会保留 `origin_colonel_buyin_id / originColonelBuyinId`；已修改 `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinProductGateway.java`：`normalizeProductItem(...)` 透传 upstream `origin_colonel_buyin_id` 与完整 `raw` map；已修改 `backend/src/main/java/com/colonel/saas/service/ProductService.java`：`fillSnapshot(...)` 现以 JSON 写入 `raw_payload`，商品事实层 buyin 解析优先识别 `origin_colonel_buyin_id / originColonelBuyinId / colonel_buyin_id / colonelBuyinId`，整体优先级仍是 `product_snapshot / product_operation_state -> colonel_activity -> ActivityApi.detail`。补充修复：`LINKED` 商品再次调用真实转链入口时保持状态为 `LINKED`，但允许沉淀新的 `promotion_link` 与 upsert native mapping，避免“上游转链已成功、本地状态机回滚导致 mapping 不落库” | 已完成 | 本轮只修“商品事实层保真 + native mapping 幂等沉淀”，不动 DashboardService，也不放开历史订单真回放 | 下一步继续用 real-pre 运行态取证，确认修复是否已落到 DB 和 replay 诊断 |
| 单测与编译 | 最小修复是否通过本地验证 | 本地已通过：`mvn -q -DskipTests compile`；`mvn -q "-Dtest=ProductServiceTest,PickSourceMappingServiceTest,AttributionServiceTest,OrderAttributionReplayServiceTest" test`。补充覆盖：`backend/src/test/java/com/colonel/saas/gateway/douyin/GatewayRecordTest.java` 校验 `ActivityProductItem.toMap()` 保留 `origin_colonel_buyin_id`；`backend/src/test/java/com/colonel/saas/service/ProductServiceTest.java` 校验商品事实层 `origin_colonel_buyin_id` 优先于活动层，且命中商品事实层时不会回退 `colonel_activity / ActivityApi.detail` | 已完成 | 本轮运行了用户要求的编译与定向测试，但没有扩大到无关模块 | 下一步仅以 real-pre 运行态补证，不新增页面或 Dashboard 逻辑改动 |
| 活动商品刷新 | 修复前 / 操作 | 修复前基线：`pick_source_mapping` 中 `activity_id=3859423 AND source_type='NATIVE'` 仍为 `1` 条，`promotion_link` 为 `1` 条，且唯一 native 映射 `colonel_buyin_id=7351155267604218149`；刷新前同商品 `product_snapshot.raw_payload` 仍是旧的 `String.valueOf(map)` 形式，`position('origin_colonel_buyin_id' in raw_payload)=0`、`position('7293293346398011698' in raw_payload)=0`。操作：重建 `saas-backend-real-pre-1` 后，用管理员 Token 调用 `GET /api/colonel/activities/3859423/products?count=20&refresh=true` | 已完成 | 刷新动作已执行到 real-pre 新代码，且 `sync_time` 更新到 `2026-05-10 07:48:09.241385`，说明这行快照已被本轮 refresh 重新覆盖 | 下一步看 refresh 后 `product_snapshot.raw_payload` 是否已带上 origin buyin |
| 活动商品刷新 | 修复后 | refresh 后复核：1）`GET /api/douyin/activity-product-list?activityId=3859423&count=20` 的真实 `remoteResponse.data.data` 中，`product_id=3816127512791089531` 仍明确带 `origin_colonel_buyin_id=7293293346398011698`；2）DB 中 `product_snapshot.raw_payload` 已从旧的 Map 字符串切换为 JSON，并出现 `origin_colonel_buyin_id=7293293346398011698`、`originColonelBuyinId=7293293346398011698`，`position('origin_colonel_buyin_id' in raw_payload)=155`、`position('7293293346398011698' in raw_payload)=181`；3）该行 `raw_payload` 同时保留了 `origin_activity_id=3906136 / origin_institution_id=3555765809504767219 / origin_institution_name=星芒联创`，说明上游活动商品原始字段已成功沉淀到商品事实层 | 机制已修复 | 这一步已经证明 buyin 不再只存在于上游响应里，而是正式进入了 `product_snapshot.raw_payload` | 下一步用同商品真实转链重试验证运行态 buyin 来源是否从 `COLONEL_ACTIVITY` 切到 `PRODUCT_SNAPSHOT` |
| 真实转链 | 是否能为 `3816127512791089531` 重新生成 / 修正 native 映射 | 第一次重试时，旧代码已真实调用上游 `buyin.instPickSourceConvert` 并输出 `Native mapping resolved ... colonelBuyinId=7293293346398011698, source=PRODUCT_SNAPSHOT`，但随后因 `LINKED` 状态进入本地回滚，未落库。补充幂等修复后，再用 `channel_staff / admin123` 调用 `POST /api/colonel/activities/3859423/products/3816127512791089531/promotion-links`，返回 `code=200`，`pickSource=v.MxZLIw`、`shortId=BJ4B67UT`、`promoteLink=https://haohuo.jinritemai.com/...pick_source=v.MxZLIw`；容器日志同步证明：`gateway=RealDouyinPromotionGateway`、`Douyin API call success, method=buyin.instPickSourceConvert`、`Native mapping resolved ... colonelBuyinId=7293293346398011698, source=PRODUCT_SNAPSHOT` | 机制已修复 | buyin 来源已经从上一轮的 `COLONEL_ACTIVITY=7351155267604218149` 切换为商品事实层 `PRODUCT_SNAPSHOT=7293293346398011698` | 下一步复核 native mapping 数量与字段变化，确认 upsert 没有重复插入 |
| Native mapping | upsert 后数量和字段是否正确 | 修复前：`pick_source_mapping native count=1`，唯一行 `colonel_buyin_id=7351155267604218149 / pick_source=v.MxZLIw / promotion_link_id=26057cbd-1543-4c18-9525-3bfc977c7f6b / create_time=2026-05-10 06:41:19.55264`，`promotion_link count=1`。修复后：`promotion_link count=2`，新增 `promotion_link_id=251d0e7d-7866-443f-9321-e2cf31bc848d / created_at=2026-05-10 07:58:21.66903`；`pick_source_mapping native count` 仍为 `1`，没有重复插入，原 native 行通过 upsert 修正为 `colonel_buyin_id=7293293346398011698 / promotion_link_id=251d0e7d-7866-443f-9321-e2cf31bc848d / user_id=fd701815-f7ed-4046-9db6-0ba2fee42eab / source_type=NATIVE / pick_source=v.MxZLIw` | 机制已修复 | 本轮没有删除 native mapping；upsert 选择修正同一 `activity_id + product_id + user_id + pick_source` 映射，因此数量保持 `1`，但 buyin 已改为商品事实层正确值 | 下一步继续通过 dry-run 判断历史订单是否可安全回填，不直接真更新历史订单 |
| Dry-run replay | fixed-time 缺口订单诊断是否变化 | 固定 `as_of_time=2026-05-10 14:16:43`，当前 `3859423` 的 `COLONEL_MAPPING_NOT_FOUND` 订单为 **22 单**（本轮按 `orderIds` 精确指定 replay 样本，避免口径漂移）。对这 22 单执行 `POST /api/orders/replay-attribution` 且 `dryRun=true`，结果从修复前 `scanned=22 / nativeKeyMatched=2 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=2 / colonelBuyinIdMismatch=2 / ambiguousMapping=0 / stillUnattributed=20` 变为修复后 `scanned=22 / attributed=2 / unattributed=20 / updated=0 / nativeKeyMatched=2 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=2 / colonelBuyinIdMismatch=0 / ambiguousMapping=0 / stillUnattributed=20` | 半完成 | `colonelBuyinIdMismatch` 已从 `2` 降到 `0`，说明 native key 已与历史订单 extra_data 中的 buyin 对齐；但 `safeToUpdate` 仍为 `0`，因为 mapping 的 `create_time=2026-05-10 06:41:19.55264` 晚于两条历史订单 `2026-05-09 22:08:13 / 2026-05-10 01:04:11` | 下一步如需真实回填，只能走业务授权回填，或等待/证明存在 `mapping.created_at <= order.create_time` 的真实映射；当前仍不允许真更新历史订单 |
| 本轮结论 | buyin 来源、native 数量、历史订单可回填性 | 本轮可以明确分两层：1）**机制已修复**：真实活动商品接口的 `origin_colonel_buyin_id=7293293346398011698` 已经能够经 `RealDouyinProductGateway -> ActivityProductItem -> ProductService.fillSnapshot -> product_snapshot.raw_payload` 全链路落库；真实转链重试时 `ProductService` 已输出 `source=PRODUCT_SNAPSHOT`，native mapping 也已 upsert 为 `colonel_buyin_id=7293293346398011698`。2）**历史订单仍不能安全回填**：`promotion_link` 已从 `1` 增至 `2`，native mapping 数量仍为 `1` 且 buyin 已修正，dry-run 中 `colonelBuyinIdMismatch=0`，但 `safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=2` 未变，因此不真实更新历史订单 | 半完成 | 这轮修的是 native 映射机制，不是历史订单回填结果；当前不能把“商品事实层已沉淀 origin buyin、native mapping 已修正”写成“历史单已可安全归因” | 下一步可以扩展到 `3864871 / 3559407 / 3601935` 做同样的“活动商品刷新 + 商品事实层 buyin 落库 + 真实转链 / dry-run”验证，但每个活动仍必须保留 `mapping.created_at <= order.create_time` 安全判断，不做强行历史真回填 |

## 六点十四、2026-05-10 P1-5.6 多活动复制验证

固定 `as_of_time`：`2026-05-10 16:22:50`（Asia/Shanghai）。本节按顺序处理 `3864871 -> 3559407 -> 3601935`，不修改 `DashboardService`，不做页面开发，不补 Mock 数据，不真实更新历史订单归因，不移除 `mapping.created_at <= order.create_time` 安全判断，也不从订单 `extra_data` 直接生成正式映射。

| 活动 | 刷新与商品事实层 | 审核 / 分配 / 转链 | native mapping 数量 | promotion_link 数量 | dry-run replay 结果 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| `3864871` | 刷新前 `product_snapshot=20 / product_operation_state=20 / originSnapshot=20`；先执行 `GET /api/colonel/activities/3864871/products?count=20&refresh=true`，再用真实缺口商品 `productInfo=3814460365216022541` 精确刷新 1 条；刷新后 `product_snapshot=21 / product_operation_state=21 / originSnapshot=21`。目标商品 `product_snapshot.raw_payload` 已保存 `origin_colonel_buyin_id=7553102195101679912`，字段位置 `origin_key_pos=1817 / origin_value_pos=1843` | 选择真实缺口商品 `3814460365216022541`；`biz_staff` 审核通过后为 `APPROVED / auditStatus=2`，`biz_leader` 分配给 `biz_staff` 后为 `ASSIGNED`，`channel_staff` 真实转链成功，返回 `pickSource=v.MxZLIw / shortId=7MO266W4`；容器日志确认 `Native mapping resolved ... colonelBuyinId=7553102195101679912, source=PRODUCT_SNAPSHOT`，转链后状态为 `LINKED` | `1`，映射为 `activity_id=3864871 / product_id=3814460365216022541 / colonel_buyin_id=7553102195101679912 / source_type=NATIVE / user_id=fd701815-f7ed-4046-9db6-0ba2fee42eab / create_time=2026-05-10 08:19:44.262816` | `1`，`promotion_link_id=6099dc18-b740-409c-b728-11a4b994787b / created_at=2026-05-10 08:19:44.264792` | 对该活动 `52` 条 `COLONEL_MAPPING_NOT_FOUND` 缺口订单按 `orderIds + dryRun=true` 重放：`scanned=52 / attributed=19 / unattributed=33 / updated=0 / nativeKeyMatched=19 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=19 / colonelBuyinIdMismatch=0 / ambiguousMapping=0 / stillUnattributed=33` | 机制通过：商品事实层 origin buyin 可沉淀，真实转链时 buyin 来源为 `PRODUCT_SNAPSHOT`，且 dry-run mismatch 为 `0`。历史订单不可真实回填的原因是映射创建时间晚于这 19 条历史订单创建时间，因此安全闸正确拦截 |
| `3559407` | 刷新前 `product_snapshot=0 / product_operation_state=0 / originSnapshot=0`；执行 `GET /api/colonel/activities/3559407/products?count=20&refresh=true` 后仍为 `total=0 / items=0`；raw 联调口 `GET /api/douyin/activity-product-list?activityId=3559407&count=20` 返回上游 `code=10000 / msg=success`，但 `itemCount=0`，没有可沉淀的 `origin_colonel_buyin_id` | 因上游活动商品数组为空，本轮没有真实活动商品可审核、分配或转链；不从该活动历史订单 `extra_data` 反推正式映射，也不补 Mock 商品 | `0` | `0` | 对该活动 `11` 条缺口订单按 `orderIds + dryRun=true` 重放：`scanned=11 / attributed=0 / unattributed=11 / updated=0 / nativeKeyMatched=0 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=0 / colonelBuyinIdMismatch=0 / ambiguousMapping=0 / stillUnattributed=11` | 上游字段缺失/空样本：当前不是本地映射机制失败，而是活动商品接口没有返回可操作商品；本轮只记录事实，不硬修 |
| `3601935` | 刷新前 `product_snapshot=0 / product_operation_state=0 / originSnapshot=0`；先执行 `GET /api/colonel/activities/3601935/products?count=20&refresh=true` 返回 20 条，再对缺口商品 `3727753425866326247 / 3584204968237914771 / 3678030419829326262 / 3673814714024067436` 分别用 `productInfo` 精确刷新，各返回 1 条；刷新后 `product_snapshot=24 / product_operation_state=24 / originSnapshot=24`。目标商品 `3727753425866326247` 的 `product_snapshot.raw_payload` 已保存 `origin_colonel_buyin_id=7495710528322994483`，字段位置 `origin_key_pos=1327 / origin_value_pos=1353` | 选择真实缺口商品 `3727753425866326247`；`biz_staff` 审核通过后为 `APPROVED / auditStatus=2`，`biz_leader` 分配给 `biz_staff` 后为 `ASSIGNED`，`channel_staff` 真实转链成功，返回 `pickSource=v.MxZLIw / shortId=NMTN4XPO`；容器日志确认 `Native mapping resolved ... colonelBuyinId=7495710528322994483, source=PRODUCT_SNAPSHOT`，转链后状态为 `LINKED` | `1`，映射为 `activity_id=3601935 / product_id=3727753425866326247 / colonel_buyin_id=7495710528322994483 / source_type=NATIVE / user_id=fd701815-f7ed-4046-9db6-0ba2fee42eab / create_time=2026-05-10 08:21:58.161954` | `1`，`promotion_link_id=0ae366ca-a345-4856-866c-b7d100439f0a / created_at=2026-05-10 08:21:58.162675` | 对该活动 `22` 条 `COLONEL_MAPPING_NOT_FOUND` 缺口订单按 `orderIds + dryRun=true` 重放：`scanned=22 / attributed=13 / unattributed=9 / updated=0 / nativeKeyMatched=13 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=13 / colonelBuyinIdMismatch=0 / ambiguousMapping=0 / stillUnattributed=9` | 机制通过：商品事实层 origin buyin 可沉淀，真实转链时 buyin 来源为 `PRODUCT_SNAPSHOT`，且 dry-run mismatch 为 `0`。历史订单不可真实回填的原因是映射创建时间晚于这 13 条历史订单创建时间，因此安全闸正确拦截 |

本轮汇总：`3864871` 与 `3601935` 均复制验证了 P1-5.5 的机制，即 `origin_colonel_buyin_id -> product_snapshot.raw_payload -> ProductService PRODUCT_SNAPSHOT -> source_type=NATIVE mapping -> dry-run replay` 可闭环；两者 `safeToUpdate=0` 均不是 mismatch，而是 `mapping.created_at > order.create_time`。`3559407` 当前上游活动商品为空，按“上游字段缺失/空样本”记录，不做硬修。

## 六点十五、2026-05-10 P1-5.7 3223881 活动商品同步覆盖不足

固定 `as_of_time`：`2026-05-10 16:37:00`（Asia/Shanghai）。本节所有订单统计均按 `deleted=0 AND create_time <= as_of_time` 口径复核；不修改 `DashboardService`，不做页面开发，不补 Mock 数据，不真实更新历史订单归因，不移除 `mapping.created_at <= order.create_time` 安全判断，也不从订单 `extra_data` 直接生成正式映射。

| 模块 | 检查项 | 证据 | 结论 | 问题 | 下一步 |
| --- | --- | --- | --- | --- | --- |
| 活动 3223881 | 刷新前基线 | 固定 `as_of_time` 下，`3223881` active 订单 `518`、覆盖 `52` 个 `product_id`；`activity_id + product_id` 在 `product_snapshot` 命中 `3` 对，在 `product_operation_state` 命中 `3` 对；刷新前 `product_snapshot active=42 / product_operation_state active=42`，最近时间均为 `2026-05-10 02:22:13`。刷新前订单有但 `snapshot/state` 都没有的 `product_id` 为：`3537096404726072094`、`3628900951219374557`、`3654328403345202274`、`3654690265219875575`、`3680856536437096694`、`3683607059154207006`、`3702358205666558234`、`3705870893919109250`、`3715543787360092408`、`3719436513763786821`、`3724633230951973112`、`3724769725054583081`、`3738203208183579102`、`3741694608481059164`、`3742608861140484636`、`3743008879286550951`、`3745254399715443024`、`3749320838286016797`、`3750956293179965772`、`3751915679243174046`、`3753051240788000810`、`3756381077309096027`、`3758983247795716416`、`3759499401865855273`、`3761226629263786060`、`3764314199833051613`、`3773417426075648227`、`3773783488050888757`、`3781613383091093723`、`3784765167708013664`、`3785828659483508888`、`3791812556113444948`、`3792172197347459105`、`3795884322540617757`、`3799810444361859089`、`3805359898598965344`、`3806816270645592075`、`3807577757601366481`、`3809243878469533964`、`3809622718282858580`、`3810117451220386063`、`3810515207965507638`、`3811070085586616551`、`3811855802550059470`、`3812023628615254105`、`3814081914181124118`、`3815740587626332371`、`3816693344516571373`、`3816870915661234594` | 半完成 | 3223881 不是“完全未同步”，而是“已同步但只覆盖到第一页附近的少量商品对” | 下一步只补活动商品同步覆盖，不动 Dashboard / 页面 / 历史订单真回填 |
| 活动 3223881 | 分页 / 过滤 / 字段类型检查 | real-pre raw 联调口 `GET /api/douyin/activity-product-list?activityId=3223881&count=20` 返回 `page1Count=20` 且带 `next_cursor`；继续用 `cursor` 请求第二页，`page2Count=20` 且仍带 `next_cursor`，说明接口明确支持分页。第一页状态样本同时包含 `0/1`，第二页状态样本包含 `1`，证明默认调用并未被“只取单一状态”过滤掉。代码复核确认：上游 `count` 最大就是 `20`，问题不在“每页 20 太小”，而在此前 `/colonel/activities/{activityId}/products?refresh=true` 只拉单页、没有沿 `next_cursor` 全量翻页；`activity_id / product_id` 在订单表、快照表、状态表本地都按字符串落库，刷新后命中数能从 `3 -> 36`，本轮未发现字段类型不一致导致的本地 join 失配 | 已定位根因 | 根因是业务 refresh 链路没有把上游分页吃完；不是 DashboardService，不是页面，也不是 Mock 数据问题 | 下一步保持 refresh 走 cursor 全量分页；剩余命中缺口再按“上游当前未返回 / 历史订单已存在”单独记录 |
| 活动 3223881 | 代码修复 | 已将 `backend/src/main/java/com/colonel/saas/controller/ColonelActivityController.java` 的 `refresh=true` 分支改为调用 `ProductService.refreshActivitySnapshots(...)`；该方法固定按 cursor 模式全量翻页，逐页 `upsertSnapshots(...)`，直到 `next_cursor` 为空或无新商品。定向测试通过：`mvn "-Dtest=ProductServiceTest,ColonelActivityControllerTest,OrderAttributionReplayServiceTest" test` | 已完成 | 本轮只修活动商品 refresh 覆盖不足，不扩大到看板、页面或历史真回填逻辑 | 下一步用 real-pre 运行态复核刷新前后差异 |
| 活动 3223881 | 刷新后复核 | 重建 `saas-backend-real-pre-1` 后执行 `GET /api/colonel/activities/3223881/products?count=20&refresh=true`；业务视图返回 `total=1999 / hasMore=true / nextCursor=20`。DB 复核：`product_snapshot active 42 -> 1999`、`product_operation_state active 42 -> 1999`，最近落库时间分别更新到 `2026-05-10 08:42:53.747654 / 2026-05-10 08:42:53.750066`。固定 `as_of_time` 的同一批订单集上，`activity_id + product_id` 命中数从 `3 -> 36`（`product_snapshot` 与 `product_operation_state` 一致）；刷新后仍未命中的 `product_id` 收敛为 `16` 个：`3628900951219374557`、`3654328403345202274`、`3654690265219875575`、`3705870893919109250`、`3719436513763786821`、`3724769725054583081`、`3742608861140484636`、`3743008879286550951`、`3750956293179965772`、`3751915679243174046`、`3753051240788000810`、`3758983247795716416`、`3759499401865855273`、`3773783488050888757`、`3784765167708013664`、`3791812556113444948`。`product_snapshot.raw_payload` 当前 `1999/1999` 行都已保存 `origin_colonel_buyin_id` | 机制已修复 | 3223881 的主要覆盖缺口已从“只覆盖 3 个订单商品”收敛为“剩余 16 个商品对仍未从上游活动商品列表返回” | 下一步把剩余 16 个商品对按“上游当前未返回”继续跟踪，不从订单 `extra_data` 直接倒灌正式映射 |
| 活动 3223881 | 刷新后命中商品实操 | 选择刷新后命中的真实商品 `product_id=3814081914181124118`（固定订单集 `orderCount=9 / unattributedOrderCount=9`，`product_snapshot.raw_payload.origin_colonel_buyin_id=7109679864001364265`）。随后用 `biz_staff / admin123` 审核通过：商品状态 `PENDING_AUDIT -> APPROVED`、`auditStatus=2`、`selectedToLibrary=true`；再用 `biz_leader / admin123` 分配给 `biz_staff`：状态 `APPROVED -> ASSIGNED`、`assigneeId=126459be-7350-456d-a844-8f5626787d3f`；最后用 `channel_staff / admin123` 转链：返回 `pickSource=v.MxZLIw / shortId=IPTPVLCU / promoteLink=...pick_source=v.MxZLIw`，商品详情更新为 `bizStatus=LINKED / promotionLinkStatus=READY / promotionLinkCount=1` | 已完成 | refresh 后命中的真实商品可以继续走“审核 / 分配 / 转链”业务闭环，不需要页面改造 | 下一步复核 native mapping 与 dry-run replay 诊断位 |
| 活动 3223881 | native mapping 复核 | `pick_source_mapping` 新增 / upsert 结果：`activity_id=3223881 / product_id=3814081914181124118 / source_type=NATIVE / colonel_buyin_id=7109679864001364265 / pick_source=v.MxZLIw / user_id=fd701815-f7ed-4046-9db6-0ba2fee42eab / create_time=2026-05-10 08:45:55.411238`。同商品 `product_snapshot.raw_payload.origin_colonel_buyin_id=7109679864001364265`，与 native 映射 `colonel_buyin_id` 一致。容器日志明确记录：`Native mapping resolved ... colonelBuyinId=7109679864001364265, source=PRODUCT_SNAPSHOT` | 已完成 | 本轮 3223881 的 native mapping 来源已经明确落在 `PRODUCT_SNAPSHOT`，不是 `COLONEL_ACTIVITY` fallback，也不是从订单 `extra_data` 反推生成 | 下一步仅做 dry-run replay，不真实回填历史订单 |
| 活动 3223881 | 缺口订单 dry-run replay | 固定 `as_of_time` 下，`3223881` 当前未归因缺口订单为 `91` 单（`70` 单 `COLONEL_MAPPING_NOT_FOUND` + `21` 单 `COLONEL_MAPPING_AMBIGUOUS`）。对这 `91` 单按 `orderIds + dryRun=true` 执行 `POST /api/orders/replay-attribution`，结果：`scanned=91 / attributed=9 / unattributed=82 / updated=0 / nativeKeyMatched=9 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=9 / colonelBuyinIdMismatch=0 / ambiguousMapping=0 / stillUnattributed=82` | 半完成 | 本轮新增的 native 映射已经足以让 `9` 单命中 native key，但 `safeToUpdate=0`，因为映射创建时间晚于这 9 条历史订单创建时间；安全闸按预期阻止真实回填 | 下一步继续保留 `mapping.created_at <= order.create_time` 安全判断；3223881 历史订单仍不做真更新 |
| 本轮结论 | P1-5.7 是否收口 | 3223881 的主问题已从“活动商品同步覆盖不足”明确收口为“此前业务 refresh 只拉单页，现已改为 cursor 全量分页”；真实运行态结果是：`product_snapshot/product_operation_state 42 -> 1999`、固定订单集命中 `3/52 -> 36/52`、`raw_payload.origin_colonel_buyin_id 1999/1999`、并完成 1 个刷新命中商品的 `审核 -> 分配 -> 转链 -> source_type=NATIVE / PRODUCT_SNAPSHOT` 验证。剩余 16 个 `product_id` 仍未被上游活动商品列表返回，且 91 条历史缺口订单 dry-run 中 `safeToUpdate=0`，因此本轮**不真实更新历史订单归因** | 半完成 | 本轮修的是 3223881 的活动商品同步覆盖和 native 映射验证，不是历史订单真回填；不能把 `dryRun attributed=9` 误写成“历史单已修复” | 下一步将剩余 16 个未覆盖商品对按上游返回继续跟踪；本轮结果已满足 P1-5.7 的“覆盖不足定位 + 全量分页刷新 + native mapping 验证 + dry-run 诊断”目标 |

## 六点十六、2026-05-10 P1-6 缺口订单 replay 策略验收

固定 `as_of_time`：`2026-05-10 16:50:55`（Asia/Shanghai）。本节仅做 real-pre 现状统计与 dry-run replay 验收，不修改 `DashboardService`，不做页面开发，不补 Mock 数据，不真实更新历史订单归因，不移除 `mapping.created_at <= order.create_time` 安全判断，也不从订单 `extra_data` 直接生成正式映射。

| 活动 | active 订单数 | 当前缺口订单数 | 订单覆盖 product_id 数 | `product_snapshot` 命中 | `product_operation_state` 命中 | replay 结果 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `3859423` | `537` | `111` | `47` | `4/47`；活动 active 快照 `20` 条 | `4/47`；活动 active 状态 `20` 条 | `scanned=111 / nativeKeyMatched=2 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=2 / colonelBuyinIdMismatch=0 / ambiguousMapping=0 / stillUnattributed=109` | 当前仍有 `43` 个订单商品 `product_id` 未进入 `snapshot/state`，说明上游活动商品覆盖仍明显不足 |
| `3864871` | `56` | `52` | `18` | `1/18`；活动 active 快照 `21` 条 | `1/18`；活动 active 状态 `21` 条 | `scanned=52 / nativeKeyMatched=19 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=19 / colonelBuyinIdMismatch=0 / ambiguousMapping=0 / stillUnattributed=33` | native 映射机制能命中 `19` 单，但仍全部被历史安全闸拦截 |
| `3601935` | `31` | `23` | `5` | `4/5`；活动 active 快照 `24` 条 | `4/5`；活动 active 状态 `24` 条 | `scanned=23 / nativeKeyMatched=13 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=13 / colonelBuyinIdMismatch=0 / ambiguousMapping=0 / stillUnattributed=10` | 活动商品覆盖已接近完整，但历史缺口仍不能真回填 |
| `3223881` | `532` | `104` | `52` | `36/52`；活动 active 快照 `1999` 条 | `36/52`；活动 active 状态 `1999` 条 | `scanned=104 / nativeKeyMatched=9 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=9 / colonelBuyinIdMismatch=0 / ambiguousMapping=0 / stillUnattributed=95` | P1-5.7 分页刷新生效后命中率明显提升，但仍有 `16` 个订单商品 `product_id` 不在上游活动商品列表返回内 |
| `3559407` | `11` | `11` | `2` | `0/2`；活动 active 快照 `0` 条 | `0/2`；活动 active 状态 `0` 条 | `scanned=11 / nativeKeyMatched=0 / safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=0 / colonelBuyinIdMismatch=0 / ambiguousMapping=0 / stillUnattributed=11` | 上游 `alliance.colonelActivityProduct` 仍是 `success + items=0` 空样本，本地没有可沉淀商品事实层 |

本轮 5 个活动汇总：active 订单 `1167` 单，当前缺口订单 `301` 单；`nativeKeyMatched=43`、`safeToUpdate=0`、`unsafeBecauseCreatedAfterOrder=43`、`colonelBuyinIdMismatch=0`、`ambiguousMapping=0`、`stillUnattributed=258`。

按当前 replay 验收口径，将 `301` 单缺口订单做互斥分类：

| 分类 | 订单数 | 判定口径 |
| --- | --- | --- |
| 可自动回填候选 | `0` | `nativeKeyMatched` 命中后，当前没有任何订单满足 `mapping.created_at <= order.create_time` |
| 机制命中但历史不可回填 | `43` | dry-run 已能命中 native 映射，但全部落在 `unsafeBecauseCreatedAfterOrder`，安全闸生效 |
| native key 不一致 | `0` | 当前 5 个活动 dry-run 汇总 `colonelBuyinIdMismatch=0` |
| ambiguous 多用户冲突 | `0` | 当前 5 个活动 dry-run 汇总 `ambiguousMapping=0` |
| 上游活动商品列表未覆盖 | `126` | 缺口订单的 `activity_id + product_id` 在 `product_snapshot` 与 `product_operation_state` 中都不存在；典型包括 `3559407` 全量 `11` 单、`3859423` 的 `65` 单、`3864871` 的 `33` 单、`3223881` 的 `17` 单 |
| 缺 activity_id | `0` | 本轮指定的 5 个活动样本内，未发现当前缺口订单缺少活动 ID 的情况 |
| 缺 product_id | `0` | 本轮指定的 5 个活动样本内，未发现当前缺口订单缺少商品 ID 的情况 |
| 无法自动归因 | `132` | 在排除上游活动商品未覆盖后，剩余仍 `stillUnattributed` 的订单；当前表现为“订单有活动和商品，商品事实层部分已在，但还没有可用 native 映射可命中” |

本轮结论固定为：

1. 当前 **不存在** `safeToUpdate > 0` 的订单；5 个活动汇总 `safeToUpdate=0`。
2. 因为 `safeToUpdate=0`，本轮**不做任何历史订单真实回填**，统一记录为“`mapping.created_at <= order.create_time` 安全闸生效”。
3. 当前 replay 策略已经完成验收：能明确分辨“机制可命中但历史不可回填”和“上游活动商品列表未覆盖 / 仍无可用映射”两类缺口，不需要通过 `DashboardService` 或页面层做掩盖性处理。
4. 可以进入 `P1-7 DashboardService` 分层聚合升级，但前提必须写清楚：`P1-6` 的结论是“replay 机制和安全闸已验明”，不是“历史缺口订单已清零”；`3559407` 的上游空样本和其他活动剩余未覆盖商品仍需继续在 `docs/09` 跟踪。

## 六点十七、2026-05-10 P1-7 DashboardService 分层聚合升级

本轮不真实回填历史订单，不移除 `mapping.created_at <= order.create_time` 安全判断，不把 `unsafeBecauseCreatedAfterOrder` 订单算成已归因，也不把 `nativeKeyMatched` 直接算成渠道业绩；核心订单指标仍保持以 `colonelsettlement_order` 为事实源，不处理独家达人 / 独家商家的高级规则，不补 Mock 数据，不做页面大改版。

本轮后端聚合口径已拆成四层：

1. 订单事实层：`colonelsettlement_order`
2. 商品事实层：`product_snapshot + product_operation_state`
3. 映射事实层：`pick_source_mapping + promotion_link`
4. 异常解释层：`attribution_remark + Dashboard replay 诊断分类`

代码侧本轮新增 / 改造结果：

- `DashboardService` 已从单纯订单汇总升级为分层聚合 summary：
  - 保留 `orderCount / orderAmount / serviceFee / attributedOrderCount / unattributedOrderCount`
  - 新增：
    - `unsafeBecauseCreatedAfterOrderCount`
    - `upstreamProductUncoveredCount`
    - `cannotAutoAttributionCount`
    - `nativeKeyMismatchCount`
    - `ambiguousMappingCount`
  - `channelPerformance / colonelPerformance` 仍只统计 `attribution_status=ATTRIBUTED` 的真实已归因订单，未把 `nativeKeyMatched` 或 `unsafeBecauseCreatedAfterOrder` 混入业绩
- `DashboardService` 新增活动商品维度聚合：
  - `activityId`
  - `productId`
  - `productName`
  - `productCover`
  - `bizStatus`
  - `assigneeName`
  - `orderCount`
  - `orderAmount`
  - `unattributedOrderCount`
  - 并补出 `mappingCount / promotionLinkCount` 作为映射事实层辅助信息
- `DashboardController` 新增 `GET /api/dashboard/activity-products`
  - 分页返回 `activity_id + product_id` 维度聚合结果
- 订单接口已补最小穿透筛选契约：
  - `GET /api/orders`
  - `GET /api/orders/unattributed`
  - `GET /api/orders/stats`
  - 当前新增支持：
    - `activityId`
    - `timeField=createTime|settleTime`
    - `dashboardDiagnosis`
- 订单页前端只做了最小透传级改动：
  - 路由 query 中的 `activityId / timeField / dashboardDiagnosis` 现在会继续带到订单列表和统计接口
  - 没有做页面大改版

解释层诊断分类当前固定为：

- `MECHANISM_HIT_HISTORY_UNSAFE`
- `UPSTREAM_PRODUCT_UNCOVERED`
- `CANNOT_AUTO_ATTRIBUTION`
- `NATIVE_KEY_MISMATCH`
- `AMBIGUOUS_MAPPING`

其中：

- `3559407` 这类上游 `success` 但 `items=0` 的活动，会继续落在 `UPSTREAM_PRODUCT_UNCOVERED` / 上游空样本解释层，不做硬归因
- `unsafeBecauseCreatedAfterOrder` 只进入“机制命中但历史不可回填”，不会进入“已归因订单数”或渠道 / 招商业绩

本轮定向验证结果：

- `mvn -q -DskipTests compile`：通过
- `mvn -q "-Dtest=DashboardServiceTest,OrderAttributionReplayServiceTest,AttributionServiceTest" test`：通过

本轮新增 / 更新测试覆盖点：

- `DashboardServiceTest`
  - `unsafeBecauseCreatedAfterOrder` 不计入已归因
  - `nativeKeyMatched` 但 `safeToUpdate=0` 只进入“机制命中但历史不可回填”
  - 商品事实层缺失时进入“上游活动商品列表未覆盖”
  - Dashboard summary 的核心订单指标继续与订单事实层聚合一致
- `OrderAttributionReplayServiceTest`
  - 持续保护 `safeToUpdate=0 / unsafeBecauseCreatedAfterOrder=1` 安全闸口径
- `AttributionServiceTest`
  - 持续保护 native key / native fallback 的匹配语义

本轮结论：

1. P1-7 已把 `DashboardService` 从“单表订单概览”升级到“订单事实 + 商品事实 + 映射事实 + 异常解释”的分层聚合。
2. 本轮没有把历史缺口订单伪装成已归因；`unsafeBecauseCreatedAfterOrder` 仍是解释层，不是业绩层。
3. 当前可以继续进入 real-pre 口径复核和最小前台穿透联调，但仍必须维持 P1-6 结论：**replay 机制和安全闸已验明，不代表历史缺口订单已清零。**

## 六点十八、2026-05-10 P1-7.1 real-pre 运行态复核 + 前端 build 验证

本轮不新增业务功能，不真实回填历史订单，不修改 replay 安全判断，不把 `unsafeBecauseCreatedAfterOrder` 算成已归因，也不把 `nativeKeyMatched` 直接算成渠道 / 招商业绩。real-pre 已重新 `docker compose --build -d`，`GET /api/actuator/health` 返回 `status=UP`。

### 1. summary 对表结果

使用管理员口径调用 `GET /api/dashboard/summary`，同时用 PostgreSQL 对 `colonelsettlement_order` 与异常解释层同口径聚合复核，当前结果一致：

| 指标 | API | PostgreSQL | 结论 |
| --- | --- | --- | --- |
| `orderCount` | `3105` | `3105` | 一致 |
| `orderAmount` | `6177864` | `6177864` | 一致 |
| `serviceFee` | `97991` | `97991` | 一致 |
| `attributedOrderCount` | `2364` | `2364` | 一致 |
| `unattributedOrderCount` | `741` | `741` | 一致 |
| `unsafeBecauseCreatedAfterOrderCount` | `0` | `0` | 一致；未把 unsafe 历史单算成已归因 |
| `upstreamProductUncoveredCount` | `398` | `398` | 一致 |
| `cannotAutoAttributionCount` | `208` | `208` | 一致 |
| `nativeKeyMismatchCount` | `0` | `0` | 一致 |
| `ambiguousMappingCount` | `115` | `115` | 一致 |

结论：`/dashboard/summary` 当前已能与订单事实层和解释层聚合真实对表。

### 2. activity-products 对表结果

本轮先修正了一个 real-pre 运行态偏差：原 SQL 在 `activity-products` 聚合时直接左连接 `pick_source_mapping / promotion_link`，会把存在多映射 / 多链接的商品订单数放大；现已改为相关子查询计数，避免订单事实层重复放大。修正后 `GET /api/dashboard/activity-products?page=1&size=2000` 与 PostgreSQL 对表一致：

- `activityProductsTotal=268`，PostgreSQL 同为 `268`

重点活动复核结果：

| 活动 | API rows | DB rows | API orderCount | DB orderCount | API unattributed | DB unattributed | topProduct | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `3223881` | `53` | `53` | `576` | `576` | `148` | `148` | `3811489772686409810` | 一致 |
| `3559407` | `2` | `2` | `11` | `11` | `11` | `11` | `3810204346914832817` | 一致；仍属上游空样本活动 |
| `3601935` | `5` | `5` | `34` | `34` | `23` | `23` | `3727753425866326247` | 一致 |
| `3859423` | `48` | `48` | `581` | `581` | `155` | `155` | `3816624485520507207` | 一致；已消除此前 join 放大 |
| `3864871` | `18` | `18` | `60` | `60` | `52` | `52` | `3814460365216022541` | 一致 |

结论：`/dashboard/activity-products` 当前已能与 PostgreSQL 聚合真实对表。

### 3. Dashboard -> 订单页参数穿透复核

前端代码已确认会从路由 query 中继续消费并透传：

- `route.query.activityId`
- `route.query.timeField`
- `route.query.dashboardDiagnosis`

并在订单页请求参数中继续传给：

- `GET /api/orders`
- `GET /api/orders/stats`

本轮运行态复核结果分两类：

1. **activityId + productId + timeField 穿透有效**
   - 样本：`activityId=3859423 / productId=3816624485520507207 / timeField=settleTime`
   - `GET /api/orders/stats` 返回 `totalOrders=155 / unattributedOrders=65`
   - `GET /api/orders` 返回 `total=155`
   - 与 `activity-products` 中同一聚合行 `orderCount=155 / unattributedOrderCount=65` 一致

2. **dashboardDiagnosis 穿透仍未完全对齐**
   - 样本：`attributionStatus=UNATTRIBUTED / dashboardDiagnosis=UPSTREAM_PRODUCT_UNCOVERED / timeField=settleTime`
   - `GET /api/dashboard/summary` 给出的解释层数量是 `398`
   - 但 `GET /api/orders/stats` 返回 `totalOrders=291 / unattributedOrders=291`
   - `GET /api/orders` 返回 `total=291`
   - 说明“订单页 activity/product 穿透已通”，但“按 `dashboardDiagnosis` 穿透时，与 summary 解释层数字仍未完全一致”

结论：订单页穿透当前为**部分生效**，还不能把 `dashboardDiagnosis` 穿透写成完全通过。

### 4. frontend build

本轮 `frontend npm run build` 已通过。构建前有一个无关噪音项：

- `src/views/data/index.vue` 中存在未使用变量 `trendChartRuntime`

已删除该未使用变量后重新执行，构建成功；当前仍保留既有大 chunk warning，但不阻断 build。

### 5. 本轮结论

1. `summary`：**真实对表通过**
2. `activity-products`：**真实对表通过**
3. 订单页穿透：
   - `activityId / productId / timeField`：**通过**
   - `dashboardDiagnosis`：**未完全通过**
4. `frontend build`：**通过**
5. 是否可以进入 `P1-8 Dashboard 全链路真实化验收`：
   - **暂不建议直接进入**
   - 原因不是订单 / 商品聚合失真，而是 `dashboardDiagnosis -> /orders` 的穿透数字仍未与 summary 解释层完全对齐；在这个问题收口前，不宜把“全链路真实化验收”写成通过

当前阶段更准确的结论是：P1-7 主体分层聚合已经 real-pre 对表通过，P1-7.1 也已证明核心 summary 和 activity-products 可用；剩余阻塞点收敛为“诊断分类穿透一致性”，而不是历史订单真实回填问题。

## 七、当前结论

真实 SDK 联调的正确姿势不是“把现有 Test 替换掉”，而是：

1. 固化本地 Mock 基线
2. 逐个 Gateway 对照契约联调
3. Token 刷新与授权主体确认通过后，进入活动、商品、转链和订单样本采集
4. 每完成一个 Gateway 就做全链路回归

这样才能保证项目既能继续演示，也能稳步迈向真实环境。
