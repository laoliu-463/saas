# 数据平台今日卡片口径对账审查报告

- 时间：2026-06-09 12:55:00 CST
- 环境：real-pre，本地 Docker
- 分支：feature/auth-system
- 基线提交：7f72e51c
- 范围：只读 SQL 对账、代码调用链审查、前后端 targeted tests；未执行写库、清库、订单同步修改、全量业绩重算。
- 结论：PARTIAL_PASS

## 1. 页面输入值

| 指标 | 页面值 |
|---|---:|
| 付费订单额 | ¥72638.56 |
| 付费订单数 | 3101 |
| 预估服务费收益 | ¥1321.36 |
| 退款订单额 | ¥6626.03 |
| 退款订单数 | 259 |
| 退款服务费收益 | ¥123.87 |
| 今日订单数（创建轨） | 2884 |
| 今日 GMV（创建轨） | ¥67126.01 |
| 今日服务费净收·预估金额 | ¥1212.78 |
| 今日提成·预估金额 | ¥362.72 |

## 2. 调用链定位

### 2.1 顶部“今日订单数 / 今日 GMV / 今日服务费净收 / 今日提成”

调用链：

`frontend/src/views/data/index.vue` -> `frontend/src/api/data.ts:getMetrics` -> `GET /dashboard/metrics` -> `DataController.getMetrics` -> `DataApplicationService.getMetrics/buildMetrics` -> `PerformanceMetricsQueryService.aggregateRange` -> `performance_records pr JOIN colonelsettlement_order co`

代码证据：

- 前端卡片字段：
  - `frontend/src/views/data/index.vue:138` 显示 `metricLabels.orders`，值取 `todayOrderCount ?? totalOrders`。
  - `frontend/src/views/data/index.vue:159` 显示 `metricLabels.amount`，值取 `todayGmv ?? totalAmount`。
  - `frontend/src/views/data/index.vue:180` 显示 `metricLabels.fee`，值取 `metrics.serviceFee`。
  - `frontend/src/views/data/index.vue:201` 显示 `metricLabels.profit`，值取 `displayCommissionMetric`。
  - `frontend/src/views/data/index.vue:407` 起，`createTime` 文案是“今日订单数（创建轨）/ 今日 GMV（创建轨）/ 今日服务费净收·预估金额 / 今日提成·预估金额”。
- API：
  - `frontend/src/api/data.ts:105`：`getMetrics` 调 `/dashboard/metrics`。
- 后端：
  - `DataController.java:179`：`/dashboard/metrics`。
  - `DataApplicationService.java:874`：同时构建 settle 和 estimate 两条轨。
  - `DataApplicationService.java:883`：estimate 轨固定调用 `buildMetrics("createTime", ...)`。
  - `DataApplicationService.java:914`：有 `performance_records` 时走 `PerformanceMetricsQueryService.aggregateRange(...)`。
- SQL：
  - `PerformanceMetricsQueryService.java:90`：`timeColumn = resolveTimeColumn(timeField)`。
  - `PerformanceMetricsQueryService.java:93`：`WHERE pr.is_valid = TRUE`。
  - `PerformanceMetricsQueryService.java:101` 至 `108`：创建轨使用 `pr.pay_amount`、`pr.estimate_service_fee`、`pr.estimate_tech_service_fee`、`pr.estimate_service_fee_expense`、`pr.estimate_service_profit`、`pr.estimate_recruiter_commission`、`pr.estimate_channel_commission`。
  - `PerformanceMetricsQueryService.java:263`：`createTime` 实际映射到 `co.create_time`，不是合同里的 `order_create_time`。

字段结论：

- 今日订单数（创建轨）：`COUNT(*)`，条件 `pr.is_valid = TRUE AND co.create_time in today`。
- 今日 GMV（创建轨）：`SUM(pr.pay_amount)`，条件同上。
- 今日服务费净收：后端最终设置为 `CommissionService.serviceFeeNetCent(serviceFeeIncome, techServiceFee, expense)`，即 `estimate_service_fee - estimate_tech_service_fee - estimate_service_fee_expense`。当前今日明细里 expense 为 0，因此等于 `SUM(pr.estimate_service_profit)`。
- 今日提成：leader/admin 口径为 `estimate_recruiter_commission + estimate_channel_commission`；staff 角色只展示自己的单项提成。

### 2.2 付费/退款卡片

当前源码和当前部署 bundle 未发现“付费订单额”“退款订单额”“退款服务费收益”等活跃中文卡片文案。已搜索：

- `frontend/src`
- `backend/src`
- `frontend/dist`
- `saas-active-frontend-real-pre-1:/usr/share/nginx/html`

可定位到的相关链路是订单汇总页：

`frontend/src/views/data/OrderList.vue` -> `frontend/src/api/data.ts:getOrderSummary` -> `GET /data/orders/summary` -> `DataController.getOrderSummary` -> `DataApplicationService.getOrderSummary/buildOrderSummary` -> `colonelsettlement_order`

风险点：

- `frontend/src/views/data/OrderList.vue:388` 将 `{ label: '付款时间', value: 'createTime' }` 展示给用户。
- 后端 `DataApplicationService.resolveTimeColumn("createTime")` 实际解析为 `create_time`。
- 也就是说，订单汇总页存在“前端叫付款时间，后端按创建时间查”的文案/语义错配。

当前无法仅凭源码确认页面上“付费/退款卡片”的接口来源。若该卡片确实在浏览器中出现，需要补充浏览器 Network 证据：请求 URL、参数、响应字段和当前登录用户的数据范围。

## 3. 只读 SQL 对账

数据库上下文：

- 数据库：`saas_real_pre`
- DB 时区：UTC
- 只读事务时间：`2026-06-09 04:56:33.201571+00`
- 今日边界：`2026-06-09 00:00:00 <= t < 2026-06-10 00:00:00`
- 说明：real-pre 是活库，事务外数据仍在增长；本节数值来自同一个 `REPEATABLE READ READ ONLY` 快照。

### 3.1 当前明细复算值 vs 页面值

| 口径 | DB 数量 | 页面数量 | 数量差 | DB 金额 | 页面金额 | 金额差 | DB 服务费净收 | 页面服务费净收 | 净收差 | DB 提成 | 页面提成 | 提成差 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| create_valid_code | 3112 | 2884 | +228 | ¥72042.37 | ¥67126.01 | +¥4916.36 | ¥1311.25 | ¥1212.78 | +¥98.47 | ¥392.26 | ¥362.72 | +¥29.54 |
| paid_status1_pay_time | 3112 | 3101 | +11 | ¥72042.37 | ¥72638.56 | -¥596.19 | ¥1311.25 | ¥1321.36 | -¥10.11 | ¥392.26 | 未给 | 未比对 |
| refund_status4_pay_time | 268 | 259 | +9 | ¥6817.13 | ¥6626.03 | +¥191.10 | ¥127.25 | ¥123.87 | +¥3.38 | ¥0.00 | 未给 | 未比对 |

阶段性判断：

- 当前 all-scope 明细不能复现用户提供的页面快照值。
- 因 real-pre 数据持续变化，且没有当前登录用户 token / dataScope / Network 响应，不能下最终结论说页面旧值一定来自缓存或 SQL 错误。

### 3.2 创建轨包含/排除退款

| order_status | is_valid | 数量 | pay_amount | estimate_service_fee | estimate_tech_service_fee | 公式净收 | performance_records 净收 | 提成 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | true | 3112 | ¥72042.37 | ¥1406.99 | ¥95.74 | ¥1311.25 | ¥1311.25 | ¥392.26 |
| 4 | false | 268 | ¥6817.13 | ¥134.84 | ¥7.59 | ¥127.25 | ¥0.00 | ¥0.00 |

结论：

- 当前创建轨看板 SQL 只取 `pr.is_valid = TRUE`，因此不包含 status=4 的失效/关闭订单。
- status=4 明细如果按公式算服务费净收是 ¥127.25，但在 `performance_records` 中 `estimate_service_profit` 和提成都已置 0。

### 3.3 创建轨字段：`create_time` vs `order_create_time`

| 指标 | create_time | order_create_time | 差异 |
|---|---:|---:|---:|
| 数量 | 3112 | 3112 | 0 |
| 金额 | ¥72042.37 | ¥72042.37 | ¥0.00 |
| 服务费净收 | ¥1311.25 | ¥1311.25 | ¥0.00 |

结论：

- 今天当前快照中，两列聚合结果一致。
- 但代码实际使用 `create_time`，与本任务合同要求的 `order_create_time` 不一致。这是代码-领域合同风险，不是当前数值差异的直接证据。

### 3.4 付款净额 vs 创建轨

| 指标 | 付款状态净额（status=1 - status=4） | 创建轨有效订单 | 差异 |
|---|---:|---:|---:|
| 数量 | 2844 | 3112 | -268 |
| 金额 | ¥65225.24 | ¥72042.37 | -¥6817.13 |
| 服务费净收 | ¥1184.00 | ¥1311.25 | -¥127.25 |

结论：

- “付款轨状态净额”与“创建轨有效订单”不是同一指标。
- 当前差异正好等于 status=4 失效/关闭订单的金额和公式净收；不能用 `付费 - 退款` 去校验创建轨有效订单卡片。

### 3.5 汇总表对账

表存在性查询结果：

- 存在：`colonelsettlement_order`
- 存在：`performance_records`
- 存在：`colonel_order_settlement`，但 count = 0
- 不存在：`agg_daily_performance_create`
- 不存在：`agg_daily_performance_settle`

结论：

- 当前看板不是从 `agg_daily_performance_create` 取值。
- 因汇总表不存在，无法执行“汇总表与明细 diff”。责任层不是“汇总表今日行漂移”，而是“当前实现没有这张汇总表/旧设计未落地”。

## 4. 状态口径审查

代码状态规则：

- `OrderCommissionPolicy.STATUS_CANCELLED = 4`
- `OrderCommissionPolicy.STATUS_REFUNDED = 5`
- `countsTowardCommission` 对 4/5 返回 false。
- `PerformanceCalculationService` 对 reversed 订单执行 `zeroCommissions`，并设置 `is_valid=false`。

数据库事实：

- 当前 `colonelsettlement_order` 今日只有 status=1 和 status=4。
- 当前没有 status=5。
- 今日 status=4 的 `flow_point` 为 NULL，`settle_status` 为 UNPAID，`attribution_status` 为 UNATTRIBUTED。
- `colonelsettlement_order` 当前没有 `expire_time` / `refund_time` 字段。
- `colonel_order_settlement` 有 `expire_time` 字段，但该表当前为空。

对“退款订单数 259”的阶段性判断：

- 不能证明它是“今日发生退款动作的订单”，因为当前可用主表没有退款动作时间字段，且当前库没有 status=5。
- 如果该卡片来自当前订单事实表，它更像是 status=4 的失效/关闭订单状态口径，不应直接写成“今日退款动作”。
- 页面文案建议用“今日失效/退款订单（状态口径）”或在具备 refund_time/expire_time 后改为“今日退款订单（退款时间口径）”。

## 5. 提成口径审查

配置域：

- `system_config.commission.business_default_ratio = 0.15`
- `system_config.commission.channel_default_ratio = 0.15`
- `commission_config` 当前为空，无用户/活动级覆盖。

抽样和汇总证据：

- 抽样 20 条有效订单中，`estimate_service_profit = estimate_service_fee - estimate_tech_service_fee - estimate_service_fee_expense`。
- 抽样记录 recruiter/channel 比例均为 0.1500。
- 当前快照：服务费净收 ¥1311.25，招商提成 ¥196.13 左右，渠道提成 ¥196.13 左右，合计 ¥392.26。
- 按合计净收一次性乘 30% 约为 ¥393.38；实际合计为 ¥392.26，说明不是“先汇总再四舍五入”，而是按订单/桶四舍五入后再求和。

结论：

- `362.72` 从比例上符合“招商 + 渠道总提成”的样式。
- leader/admin 顶部卡片展示总提成；biz staff 展示招商提成；channel staff 展示渠道提成。

## 6. 缓存审查

代码证据：

- `DataApplicationService.METRICS_CACHE_TTL = 30s`
- `DataApplicationService.ORDER_SUMMARY_CACHE_TTL = 30s`
- `ShortTtlCacheService` 是 JVM 本地短 TTL 缓存。
- Redis 只用于失效通知 pub/sub，不持久保存卡片值。

运行证据：

- Redis 需要认证，本轮未探测密码、未输出密钥。
- 前端 `/healthz` 返回 `ok`。
- real-pre frontend/backend/postgres/redis Docker health 均为 `healthy`。
- 后端 `/api/actuator/health` 需要授权，`/actuator/health` 当前 404；健康证据以 Docker health 为准。

结论：

- 30 秒内旧值可能存在。
- Redis 或前端长期缓存导致这些页面值长期不刷新，目前没有证据支持。
- 当前页面值与 SQL 快照不一致，更高可信解释是：页面值来自不同时间点、不同 dataScope、不同接口，或截图不是当前已部署源码对应的卡片。

## 7. 责任层判断

| 问题 | 责任层 | 证据 | 结论 |
|---|---|---|---|
| 用 `付费 - 退款` 对比创建轨卡片 | 指标理解/前端分组 | 创建轨有效订单不等于付款状态净额 | 这是口径混比，不是直接 SQL 错误 |
| 订单汇总页 `createTime` 显示“付款时间” | 前端文案 | `OrderList.vue:388`，部署 bundle 同样存在 | 应改文案或新增真实 payTime 轨 |
| 创建轨代码用 `create_time` 而非 `order_create_time` | 后端 SQL/领域合同 | `resolveTimeColumn(createTime) -> create_time` | 今天无数值差异，但合同风险存在 |
| 今日服务费净收字段 | 后端聚合 | `serviceFee = income - tech - expense` | 今天与 `estimate_service_profit` 一致；若 expense 非 0，需确认合同是否允许扣 expense |
| 今日提成字段 | 后端聚合/前端角色展示 | `commission = recruiter + channel`，staff 单项 | 当前符合“总提成”解释 |
| `agg_daily_performance_create` 对账 | 数据模型/旧设计 | 表不存在 | 当前不是汇总表漂移 |
| 退款订单是否为退款动作 | 数据模型/状态口径 | 无 refund_time/expire_time 主表字段，status=4 非 status=5 | 不能叫“退款时间口径” |
| 页面值精确复现失败 | 证据不足 | 无当前登录用户 token/Network 响应，且 real-pre 数据持续变化 | 不能下最终根因 |

## 8. 最小修复建议

如果确认这些卡片本身计算正确、只是口径混放：

1. 前端分组改成三组：
   - 今日创建轨：订单数、GMV、预估服务费净收、预估提成。
   - 今日付款轨：付款订单数、付款订单额、付款服务费收益。
   - 今日失效/退款轨：失效/退款订单数、失效/退款订单额、冲正/归零说明。
2. 不再用“付费 - 退款”解释“创建轨”卡片。
3. `OrderList.vue` 的 `{ label: '付款时间', value: 'createTime' }` 改为“创建时间”，或后端新增真实 `payTime` 轨并映射 `pay_time`。
4. status=4 在未确认 refund_time 前，不建议文案写“今日退款订单（退款时间口径）”；更稳妥写“今日失效/退款订单（状态口径）”。
5. “媒介”文案本轮未在相关链路发现新增使用；继续统一为“渠道”。

如果业务确实需要付款净额卡：

1. 后端单独提供付款轨 DTO，不复用创建轨卡片字段。
2. SQL 显式使用 `pay_time`，并分别返回：
   - paid：`order_status = 1`
   - invalid/refund：`order_status in (4,5)` 或业务确认后的状态集
   - net：paid - invalid/refund
3. 前端卡片文案写“今日付款净额（付款时间口径）”。

如果要修合同风险：

1. 单独开任务评估 `create_time` 与 `order_create_time` 的真实语义。
2. 同步修 `PerformanceCalculationService.setOrderCreateTime(order.getCreateTime())` 的来源。
3. 更新后端 SQL、测试和历史数据兼容策略。

## 9. 已补充测试

后端：

- `backend/src/test/java/com/colonel/saas/service/PerformanceMetricsQueryServiceTest.java`
  - 新增 `aggregateRange_shouldFilterInvalidRecordsAndKeepCreateTrackSeparateFromPayTrack`
  - 断言创建轨 SQL 必须包含 `pr.is_valid = TRUE`
  - 断言创建轨使用 `co.create_time`
  - 断言创建轨不使用 `co.pay_time`

前端：

- `frontend/src/views/data/index.test.ts`
  - 新增顶部卡片文案测试
  - 断言显示“今日订单数（创建轨）/ 今日 GMV（创建轨）/ 今日服务费净收·预估金额 / 今日提成·预估金额”
  - 断言不显示“今日付款净额”

已有测试覆盖：

- `PerformanceCalculationServiceTest` 已覆盖退款/失效订单 `is_valid=false`、提成和毛利归零。
- `PerformanceCalculationServiceTest` 已覆盖预估服务费收益 = 服务费收入 - 技术服务费。

## 10. 验证结果

后端 targeted tests：

```text
mvn -f backend/pom.xml "-Dtest=PerformanceMetricsQueryServiceTest,PerformanceCalculationServiceTest,DataControllerTest" test
Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

前端 targeted tests：

```text
npm --prefix frontend run test -- src/views/data/index.test.ts src/views/data/dashboard-metrics.test.ts src/api/data.test.ts
Test Files: 3 passed
Tests: 9 passed
```

Smoke：

- Docker health：frontend/backend/postgres/redis 均为 healthy。
- 前端 `/healthz`：`ok`。
- 后端外部无鉴权 health 未暴露：`/actuator/health` 404，`/api/actuator/health` 401。

未执行项：

- 未执行容器重启：本轮没有生产代码改动，只有测试与审查报告变更；重启不能提升只读对账证据质量。
- 未执行远端部署：用户未要求远端部署，且本轮是只读审查。
- 未执行写库 rebuild：任务明确禁止真实修复和全量重算。

## 11. 阶段性结论

结论：PARTIAL_PASS。

已确认：

- 顶部“今日订单数/今日 GMV/今日服务费净收/今日提成”走创建轨 estimate 数据，来源是 `/dashboard/metrics` -> `performance_records`，并过滤 `pr.is_valid = TRUE`。
- 今日 GMV 使用 `pr.pay_amount`，当前代码时间字段使用 `co.create_time`。
- 今日服务费净收当前等于 `estimate_service_fee - estimate_tech_service_fee - estimate_service_fee_expense`；今日 expense 为 0，所以与 `SUM(estimate_service_profit)` 一致。
- 今日提成对 leader/admin 是招商+渠道总提成，当前比例为 15% + 15%，按订单/桶四舍五入后求和。
- 当前 `agg_daily_performance_create` 不存在，页面不从该汇总表取数。
- `付费 - 退款` 与“创建轨有效订单”不是同一口径，不能互相校验。
- 订单汇总页存在明确前端文案问题：`createTime` 被展示成“付款时间”。

未最终确认：

- 用户给出的页面值没有在当前 all-scope DB 快照中精确复现。
- “付费订单额/退款订单额”卡片的真实接口未从当前源码和当前部署 bundle 中定位到，需要补充浏览器 Network 请求和当前登录用户数据范围。
- 不能证明差异来自 Redis/前端缓存；当前只支持 30 秒本地短缓存可能性。

建议单独开修复任务：

1. 修前端 `OrderList.vue` 时间筛选文案，或新增真实 `payTime` 轨。
2. 明确 `create_time` 与 `order_create_time` 的领域语义，并决定是否修后端 SQL。
3. 若产品需要付款净额，新增独立付款轨/退款轨卡片，不复用创建轨卡片。
