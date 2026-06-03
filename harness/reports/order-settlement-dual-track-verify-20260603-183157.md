# ORDER-SETTLEMENT-DUAL-TRACK-VERIFY 本地订单双轨结算核查报告

## 1. 任务目标

核查本地订单结算逻辑是否真正实现预估/结算双轨。涵盖：DB 字段、同步映射、业绩计算、接口返回、测试覆盖五层。

## 2. Git Intake

- **branch**: `feature/auth-system`
- **commit**: `78bdf8fa docs(harness): GIT-BATCH-4-REPORTS record batch 4 state`
- **dirty**: 5 modified（`JobLockKeys.java`、`OrderSyncJob.java`、`OrderSyncService.java`、`application.yml`、`OrderControllerTest.java`、`OrderSyncJobTest.java`、harness 状态文件）+ 9 untracked（`OrderSyncServiceTest.java` + 6 报告 + 1 报告）

## 3. 双轨标准

本次采用核查任务规格书中的 A~G 八项标准：

| 编号 | 标准 | 说明 |
|------|------|------|
| A | 订单表字段完整 | 9 个字段全部存在 |
| B | 订单同步映射完整 | 6468/2704 分别映射 estimate/effective |
| C | 2704 结算补充逻辑 | update 时只补 effective_*/settle_*，保留 estimate_* |
| D | 6468 订单事实源 | 写 estimate/pay 字段，不乱填 effective |
| E | 业绩表字段完整 | 8 个双轨字段全部存在 |
| F | 计算公式双轨 | estimate/effective 两套独立提成计算 |
| G | 查询接口双轨 | 接口返回双轨字段或双行 |

## 4. 订单表字段证据

### 4.1 colonelsettlement_order（生产表）

| 字段 | 是否存在 | 类型 | 证据 |
|------|----------|------|------|
| `order_amount`（语义等价 `pay_amount`） | ✅ 存在 | BIGINT | `init-db.sql:631` 注释 `-- [V1.3] 订单金额（分），即 pay_amount`；`OrderDualTrackAmountResolver.applyToOrder:172` 写入 `order.setOrderAmount(payAmount)` |
| `settle_amount` | ✅ 存在 | BIGINT | `init-db.sql:633`；DB 查询确认 |
| `estimate_service_fee` | ✅ 存在 | BIGINT | `init-db.sql:634`；DB 查询确认 |
| `effective_service_fee` | ✅ 存在 | BIGINT | `init-db.sql:635`；DB 查询确认 |
| `estimate_tech_service_fee` | ✅ 存在 | BIGINT | `init-db.sql:636`；DB 查询确认 |
| `effective_tech_service_fee` | ✅ 存在 | BIGINT | `init-db.sql:637`；DB 查询确认 |
| `pay_time` | ✅ 存在 | TIMESTAMP | `create-colonel-order-settlement.sql:2`；DB 查询确认 |
| `settle_time` | ✅ 存在 | TIMESTAMP | `init-db.sql` 已有；DB 查询确认 |
| `order_create_time` | ✅ 存在 | TIMESTAMP | `create-colonel-order-settlement.sql:3`；DB 查询确认 |
| `pay_amount`（独立列） | ❌ 不存在 | — | DB 查询未返回此列；代码统一使用 `order_amount` 作为语义等价字段 |

**结论**: 9 个语义字段中 8 个有独立列，`pay_amount` 通过 `order_amount` 语义等价覆盖。**A 标准基本满足**。

### 4.2 colonel_order_settlement（新表，未使用）

`create-colonel-order-settlement.sql:8-48` 定义了完整双轨表，包含 `pay_amount` 独立列。DB 查询确认该表存在但 **0 行数据**，当前未接入同步链路。

### 4.3 真实数据分布

`colonelsettlement_order` 当前 **0 行**（本地 real-pre 无历史订单数据）。

## 5. 订单同步映射证据

| 来源接口 | 本地方法 | 字段映射 | 是否双轨 | 是否实际调用 |
|----------|----------|----------|----------|-------------|
| **2704** `buyin.colonelMultiSettlementOrders` | `OrderSyncService.mapOrder()` L517-548 → `OrderDualTrackAmountResolver.resolve()` L70-130 → `applyToOrder()` L165-187 | `pay_goods_amount` → `orderAmount`; `settled_goods_amount` → `settleAmount`; `estimated_commission` → `estimateServiceFee`; `settle_colonel_commission` → `effectiveServiceFee`; `estimated_tech_service_fee` → `estimateTechServiceFee`; `tech_service_fee` → `effectiveTechServiceFee` | ✅ 双轨 | ✅ 实际使用（`OrderSyncService.syncRangeWithMode:300-309` → `douyinOrderGateway.listSettlement()`） |
| **6468** `buyin.instituteOrderColonel` | `RealDouyinOrderGateway.listInstituteOrders()` L126-138 → `orderApi.listSettlement()` L131 | 同 `toOrderItem()` L359-396 映射逻辑 | 理论上双轨（共用 resolver） | ❌ **未被 OrderSyncService/OrderSyncJob 调用** |

### 5.1 2704 结算补充（C 标准）

- `OrderDualTrackAmountResolver.mergeEstimateSnapshot()` L135-150：更新时保留已有 `estimate_*`，只补 `effective_*`。
- `applyToOrder()` L180-186：兼容旧字段 `settleColonelCommission`，结算轨优先，为 0 时回退预估轨。
- **C 标准满足**。

### 5.2 effective_* 为空时处理

`OrderDualTrackAmountResolver.resolve()` L102-107:
```java
if (effectiveServiceFee <= 0 && estimateServiceFee > 0) {
    // 待结算单：结算轨常为 0，保留预估值不覆盖
}
```
明确注释"待结算单：结算轨常为 0，保留预估值不覆盖"——**effective_* 为空时保留 0，不用 estimate_* 填充**。**满足**。

### 5.3 6468 事实源（D 标准）

- `OrderApi.java:44` 定义 `buyin.instituteOrderColonel` 方法名。
- `RealDouyinOrderGateway.listInstituteOrders()` L126-138 实现。
- `DouyinController.java:470-482` 有 RAW 探针入口，live 模式实测返回 20 条真实订单（见 `KNOWN_ISSUES.md`）。
- **但 `OrderSyncService` 和 `OrderSyncJob` 从未调用 `listInstituteOrders()`**——同步链路只用 2704。
- **D 标准未满足**（6468 未接入同步链路）。

## 6. 业绩计算证据

### 6.1 业绩表字段

| 字段 | 是否存在 | 类型 | 证据 |
|------|----------|------|------|
| `estimate_service_profit` | ✅ | BIGINT | DB 查询确认 |
| `effective_service_profit` | ✅ | BIGINT | DB 查询确认 |
| `estimate_recruiter_commission` | ✅ | BIGINT | DB 查询确认 |
| `effective_recruiter_commission` | ✅ | BIGINT | DB 查询确认 |
| `estimate_channel_commission` | ✅ | BIGINT | DB 查询确认 |
| `effective_channel_commission` | ✅ | BIGINT | DB 查询确认 |
| `estimate_gross_profit` | ✅ | BIGINT | DB 查询确认 |
| `effective_gross_profit` | ✅ | BIGINT | DB 查询确认 |

**E 标准满足**。

### 6.2 计算公式

`PerformanceCalculationService.buildRecord()` L88-178:

| 公式 | 代码位置 | 是否正确 |
|------|----------|----------|
| `estimate_service_profit = estimate_service_fee - estimate_tech_service_fee` | L148-155 `commissionService.calculateTrack(estimateServiceFee, estimateTechServiceFee, 0L, ...)` | ✅ 预估轨 talentCommission=0 |
| `effective_service_profit = effective_service_fee - effective_tech_service_fee` | L157-164 `commissionService.calculateTrack(effectiveServiceFee, effectiveTechServiceFee, talentCommission, ...)` | ✅ 结算轨含达人佣金 |
| `estimate_recruiter_commission = serviceFeeNet * recruiterRate` | `CommissionService.calculateTrack()` L147-172 | ✅ 通过 `calculateByActivityBuckets` 实现 |
| `effective_recruiter_commission = serviceFeeNet * recruiterRate` | 同上 | ✅ |
| `estimate_channel_commission = serviceFeeNet * channelRate` | 同上 | ✅ |
| `effective_channel_commission = serviceFeeNet * channelRate` | 同上 | ✅ |
| `estimate_gross_profit = serviceFeeNet - recruiter - channel` | 同上 | ✅ |
| `effective_gross_profit = serviceFeeNet - recruiter - channel` | 同上 | ✅ |

**F 标准满足**。取消订单 `zeroCommissions()` L183-192 正确将双轨全部置零。

### 6.3 看板双轨查询

`PerformanceMetricsQueryService.aggregateRange()` L77-136:
- `estimateTrack` 布尔变量按 `timeField` 决定（L87, L251-253）。
- `timeField=create_time` → estimate 轨（`pay_amount`、`estimate_*`）。
- `timeField=settle_time` → effective 轨（`settle_amount`、`effective_*`）。

`PerformanceSummaryService`:
- `aggregateEstimate()` L111-129：SUM(pay_amount, estimate_service_fee, ...)
- `aggregateEffective()` L144-164：SUM(settle_amount, effective_service_fee, ...) + 追加 `settle_time IS NOT NULL OR effective_service_fee > 0` 过滤。

`PerformanceMetricsQueryService.aggregateDashboardSummary()` L203-245：
- ⚠️ **仅使用 settle_amount + effective_service_fee**，不支持 estimate 轨。
- Dashboard 排行榜 `queryLeaderboard()` L377-402 同样仅 effective。

## 7. 接口返回证据

| 接口 | 是否返回双轨 | 证据 |
|------|-------------|------|
| 订单详情 `OrderQueryService.getDetail()` | ✅ 返回双轨字段 | L122-125: `estimateServiceFee`、`effectiveServiceFee`、`estimateTechServiceFee`、`effectiveTechServiceFee` + `payAmount`/`settleAmount` |
| 订单列表 SQL | ✅ SELECT 含双轨 | L202-207: `co.order_amount`, `co.settle_amount`, `co.estimate_service_fee`, `co.effective_service_fee`, `co.estimate_tech_service_fee`, `co.effective_tech_service_fee` |
| 业绩列表 `PerformanceQueryService` | ✅ 返回 8 个双轨字段 | L411-448: SELECT + rs.getLong 全部 8 个字段 |
| 业绩汇总 `PerformanceSummaryService` | ✅ 双行 estimate/effective | `PerformanceSummaryResponse` 含 `estimate` + `effective` 两个 `PerformanceTrackSummaryDTO` |
| 看板指标 `PerformanceMetricsQueryService` | ⚠️ 按 timeField 选单轨 | `aggregateRange()` 按 estimateTrack 选列，不返回两行 |
| 看板 Summary | ⚠️ 仅 effective 轨 | `aggregateDashboardSummary()` L218-223 硬编码 `settle_amount` + `effective_service_fee` |
| 数据看板 `DataApplicationService` | ⚠️ 按 timeField 选单轨 | L1330-1343 `resolveOrderTrackColumns()` 按 timeField 选 estimate 或 effective 列 |
| 前端订单详情 `OrderDetailModal.vue` | ✅ 展示双轨 | L52-53: 预估服务费 / 结算服务费 |
| 前端数据看板 `data/index.vue` | ✅ 双轨切换 | Radio button 切换 createTime/settleTime，`resolveDualTrackMetrics()` + `pickDashboardTrack()` |
| 前端业绩 API `performance.ts` | ✅ 定义双轨类型 | L47-49: `estimate: PerformanceTrackSummary`, `effective: PerformanceTrackSummary` |
| 前端订单 API `order.ts` | ✅ 定义双轨字段 | L57-60: 4 个 estimate/effective 字段 |

**G 标准部分满足**：订单详情和业绩汇总接口真正返回双轨；看板/Dashboard 按时间维度切换单轨，不同时返回两轨。

## 8. 测试覆盖证据

| 测试文件 | 覆盖内容 | 用例数 |
|----------|----------|--------|
| `OrderDualTrackAmountResolverTest.java` | 双轨字段解析 + 预估快照保留 | 2 |
| `PerformanceSummaryServiceTest.java` | estimate/effective 双轨 SQL 校验 | 2+ |
| `PerformanceMetricsQueryServiceTest.java` | estimate 轨 SQL 校验 | 1+ |
| `PerformanceQueryServiceTest.java` | 双轨 ResultSet mock (L228-235) | 1+ |
| `OrderQueryServiceTest.java` | 双轨金额映射验证 (L60-135) | 2+ |
| `DataControllerTest.java` | settle_amount / effective_service_fee / estimate_service_fee SQL 断言 (L254-262, L859-860) | 3+ |
| `OrderPaymentSchemaBootstrapTest.java` | DDL migration 执行验证 | 1 |

### 缺失测试

| 场景 | 是否覆盖 |
|------|----------|
| 待结算单 effective=0/null 不被 estimate 填充 | ⚠️ `OrderDualTrackAmountResolverTest` 间接覆盖（resolve 测试中 effective 有值），**无显式 negative case** |
| 2704 回补后 effective_* 正确更新 | ❌ 无集成测试 |
| 退款/失效两轨归零 | ⚠️ `PerformanceCalculationService` 有 `zeroCommissions()` 但无显式测试 |
| summary 返回 estimate + effective 两行 | ⚠️ `PerformanceSummaryServiceTest` 有 mock 但仅单轨 SQL 断言 |
| 6468 同步写入双轨 | ❌ 无（6468 未接入同步链路） |

## 9. 最终结论

### **B. 订单域字段双轨，但计算/接口仍单轨**

更精确地说：**B+ 级别——订单域与业绩域代码层面双轨架构已完整设计并实现，但存在以下缺口导致不能判定为 A**：

#### 满足项

| 标准 | 状态 |
|------|------|
| A. 订单表字段完整 | ✅ 满足（`order_amount` 等价 `pay_amount`） |
| C. 2704 结算补充保留 estimate_* | ✅ 满足 |
| E. 业绩表字段完整 | ✅ 满足 |
| F. 计算公式双轨 | ✅ 满足 |
| G. 查询接口双轨 | ⚠️ 部分满足 |

#### 未满足项

| 标准 | 缺口 | 影响 |
|------|------|------|
| B/D. 6468 未接入同步 | `OrderSyncService` 和 `OrderSyncJob` 只调 2704，从不调 `listInstituteOrders()`（6468） | 2704 返回 fetched=0（已知问题），导致无订单数据写入；即使有数据，2704 是结算接口，对未结算订单的 effective_* 可能不准 |
| G. Dashboard 单轨 | `aggregateDashboardSummary()` 硬编码 `settle_amount + effective_service_fee`；`DataApplicationService.resolveOrderTrackColumns()` 按 timeField 选单轨 | 看板无法同时展示预估/结算两轨对比 |
| 数据验证 | `colonelsettlement_order` 和 `performance_records` 均 0 行 | 无法通过真实数据验证双轨写入和计算是否正确 |

## 10. 对 ORDER-P0-DUAL-SOURCE-SYNC 的影响

### 10.1 6468 入库时应写哪些 estimate/pay 字段

当 6468 (`buyin.instituteOrderColonel`) 接入同步后，应写入：

| 字段 | 来源 | 说明 |
|------|------|------|
| `order_amount` | `pay_goods_amount` / `order_amount` | 订单实付金额（即 pay_amount） |
| `estimate_service_fee` | `estimated_commission` / colonel_order_info.estimated_commission | 预估服务费 |
| `estimate_tech_service_fee` | `estimated_tech_service_fee` / `estimate_platform_service_fee` | 预估技术服务费 |
| `pay_time` | `pay_success_time` / `create_time` | 付款时间 |
| `order_create_time` | `create_time` | 下单时间 |
| `product_id` / `product_name` | 标准映射 | 商品信息 |
| `talent_id` / `talent_name` | 标准映射 | 达人信息 |
| `pick_source` | 标准映射 | 推广来源 |

6468 **不应**写 `settle_amount`、`effective_service_fee`、`effective_tech_service_fee`、`settle_time`（这些字段留待 2704 补充）。

### 10.2 2704 回补时应写哪些 effective/settle 字段

| 字段 | 来源 | 说明 |
|------|------|------|
| `settle_amount` | `settled_goods_amount` | 结算金额 |
| `effective_service_fee` | `settle_colonel_commission` | 结算服务费 |
| `effective_tech_service_fee` | `tech_service_fee` / `settle_colonel_tech_service_fee` | 结算技术服务费 |
| `settle_time` | `settle_time` | 结算时间 |
| `order_status` | `order_status` / `flow_point` | 订单状态 |

2704 update 时**必须保留** `estimate_*` 首次快照（已通过 `mergeEstimateSnapshot()` 实现）。

### 10.3 禁止互相覆盖的字段

| 禁止行为 | 原因 |
|----------|------|
| 2704 update 覆盖 `estimate_service_fee` | 预估值应在首次入库时锁定 |
| 2704 update 覆盖 `order_amount`（pay_amount） | 付款金额由 6468 首次写入，不应被覆盖 |
| 6468 写入 `effective_*` | 6468 是事实源，不含结算数据 |
| 用 `estimate_*` 填充 `effective_*` | 待结算单 effective 必须保持 0/null |

### 10.4 双源同步修复是否需要同步改业绩计算

**需要**。当 6468 接入同步后：

1. 每次 6468 新订单入库 → 触发 `PerformanceCalculationService.upsertFromOrder()`，此时 `effective_*` 为 0，预估轨有值 → 业绩记录仅预估轨有数据 ✅（当前逻辑已正确）。
2. 2704 回补结算后 → 订单 `effective_*` 更新 → **必须重新触发 `upsertFromOrder()`** 以刷新业绩记录的结算轨 → 需确认当前 `OrderSyncPersistenceService.persistOrder()` 在 update 时是否触发业绩重算。
3. 提成比例可能在 6468 入库和 2704 回补之间发生变化 → 业绩版本号 `calculationVersion` 需递增（当前 `buildRecord()` L134 已实现）。

## 附录 A. 关键文件索引

| 文件 | 作用 |
|------|------|
| `backend/src/main/java/.../service/OrderDualTrackAmountResolver.java` | 双轨金额解析器（核心工具类） |
| `backend/src/main/java/.../service/OrderSyncService.java` | 订单同步服务（仅调 2704） |
| `backend/src/main/java/.../gateway/douyin/real/RealDouyinOrderGateway.java` | 网关实现（2704 + 6468 均有方法，但 6468 未被调用） |
| `backend/src/main/java/.../service/PerformanceCalculationService.java` | 业绩双轨计算（buildRecord L88-178） |
| `backend/src/main/java/.../service/CommissionService.java` | 提成计算（calculateTrack L147-172） |
| `backend/src/main/java/.../service/PerformanceSummaryService.java` | 业绩汇总双轨查询 |
| `backend/src/main/java/.../service/PerformanceMetricsQueryService.java` | 看板指标查询（部分双轨） |
| `backend/src/main/java/.../service/OrderQueryService.java` | 订单详情双轨返回 |
| `backend/src/main/java/.../service/data/DataApplicationService.java` | 数据看板（按 timeField 选单轨） |
| `backend/src/main/resources/db/init-db.sql` | DDL 含双轨字段定义 |
| `backend/src/main/resources/db/create-colonel-order-settlement.sql` | 新表 DDL（未使用） |
| `frontend/src/api/performance.ts` | 前端业绩 API 类型定义（双轨） |
| `frontend/src/api/order.ts` | 前端订单 API 类型定义（双轨） |
| `frontend/src/views/data/dashboard-metrics.ts` | 看板双轨解析工具 |
| `frontend/src/views/data/index.vue` | 数据看板页（双轨切换） |
| `frontend/src/views/orders/components/OrderDetailModal.vue` | 订单详情弹窗（展示双轨服务费） |
