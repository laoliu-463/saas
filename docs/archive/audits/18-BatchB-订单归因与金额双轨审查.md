# Batch B 审查报告：订单归因与金额双轨

审查日期：2026-05-24
审查批次：B（订单归因与金额双轨）
对照文档：
- `docs/archive/audits/17-V1全域代码审查清单.md` 三（订单域）、四（状态机）
- `docs/04-上线验收清单.md` P0-4、P0-5、P0-7

---

## 一、审查范围与方法

### 代码锚点

| 文件 | 行数 | 核心职责 |
|------|------|----------|
| `AttributionService.java` | 591 | 归因优先级链、独家覆盖、认领冲突守卫 |
| `OrderDualTrackAmountResolver.java` | 180 | 双轨金额解析（预估轨 + 结算轨） |
| `OrderSyncService.java` | 463 | 订单同步编排、gateway→entity 映射 |
| `OrderSyncPersistenceService.java` | 183 | 去重、upsert、归因后置任务 |
| `CommissionService.java` | 389 | 提成公式（招商/渠道/毛利） |
| `OrderCommissionPolicy.java` | 24 | 订单是否计入业绩的判定 |
| `PerformanceCalculationService.java` | 137 | 业绩记录 upsert |
| `PerformanceMetricsQueryService.java` | 337 | Dashboard 聚合查询（performance_records 路径） |
| `DashboardService.java` | 702 | Dashboard 聚合（直接 SQL 路径 + 诊断标签） |
| `OrderQueryService.java` | 552 | 订单详情/列表查询 |
| `RealDouyinOrderGateway.java` | 368 | 抖音 API 对接、金额提取 |

### 方法

按 `17-V1全域代码审查清单` 三（订单域）和 四（状态机）逐项断言，对照当前代码实际行为输出"符合/矛盾/待补证"判定。

---

## 二、逐项审查结果

### 2.1 P0-4：`pick_source` 渠道归因正确

**文档期望**：
1. 至少 1 条真实订单带回 `pick_source`
2. 该 `pick_source` 能命中 `pick_source_mapping`
3. `mapping.created_at <= order.create_time` 安全闸
4. 订单默认渠道归因正确
5. 订单列表、业绩、Dashboard 对应口径一致

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 归因优先级链存在 | `AttributionService.resolveAttribution()` | 符合。优先级：独家商家 → 独家达人 → 原生团长映射（buyinId+活动+商品，三级 fallback）→ pick_source 映射 → 未归因 |
| `pick_source_mapping` 匹配 | `PickSourceMappingService.findByPickSource()` | 符合。按 `pick_source` 字段查询映射表 |
| `mapping.created_at <= order.create_time` 安全闸 | `DashboardService.diagnosisCategoryCaseSql()` 380-392 行 | **矛盾**。该安全闸仅作为 Dashboard 诊断标签 `MECHANISM_HIT_HISTORY_UNSAFE` 输出，**不是归因阻断闸**。`psm.create_time > order.create_time` 的订单仍然可以被写入 `ATTRIBUTED` 状态 |
| 归因写入 | `AttributionService.resolveAttribution()` → `order.setAttributionStatus("ATTRIBUTED")` | 符合。成功匹配后写入 ATTRIBUTED |
| 口径一致性 | Dashboard 排行榜过滤 `co.attribution_status = 'ATTRIBUTED'` | 部分符合。排行榜只展示已归因，但汇总总数不排除未归因 |

**判定**：**待补证 + 矛盾 1 项**

- 安全闸仅为诊断标签，不阻断归因。代码行为与审查清单四的断言"unsafeBecauseCreatedAfterOrder 不写成已归因，不进入业绩"**直接矛盾**。
- 真实 `pick_source` 样本仍待补证（当前 `E2E-ORDER-01` 为 SKIP 状态）。

---

### 2.2 P0-5：活动默认招商归因正确

**文档期望**：
1. 默认招商归因只按活动绑定招商，不混入个别品负责人覆盖
2. 活动/商品负责人链路清楚
3. 业绩列表、看板中的招商归属一致

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 活动绑定招商 | `AttributionService.resolveAttribution()` → 活动级 `colonelUserId` | 符合。V1 口径下招商归因走活动绑定链路 |
| 独家覆盖受开关控制 | `exclusive.enabled` 配置，默认 `false` | 符合。独家功能默认关闭，不会混入 V1 归因 |
| 招商字段一致性 | 业绩记录 `recruiterUserId`、Dashboard 过滤 `colonel_user_id` | 符合。字段链路一致 |

**判定**：**符合（代码口径一致）**

- V1 默认关闭独家，招商归因仅走活动绑定链路，无混入风险。
- 仍缺"活动负责人 ↔ 订单 `colonelUserId` ↔ Dashboard 招商"的专项对表样本。

---

### 2.3 P0-7：双轨金额与提成正确

#### 2.3.1 金额存储单位

**文档期望**（`04-上线验收清单.md` 381-384 行）：
1. `RealDouyinOrderGateway.toSettlementItem()` 的 7 个金额字段已全部接入 `fenToYuanOrNull()`，Gateway 层完成 ÷100 转换
2. `OrderSettlementSyncService.mapSettlement` 经 `mapFenAmount` → `fenToStoredMinor` 入库，`source_amount_unit=FEN`
3. entity 层金额字段为 `Long`（元），Controller / 前端不得再除 100

**代码事实**：

| 断言 | 实际情况 | 结论 |
|------|----------|------|
| `toSettlementItem()` 方法 | **不存在**。`RealDouyinOrderGateway.java` 只有 `toOrderItem()`，不做 ÷100 转换 | **矛盾** |
| `fenToYuanOrNull()` 方法 | **不存在于任何已提交代码中**。仅存在于 git stash（commit `bb2c87e`）的 `DouyinMoneyConverter.java`，从未合并 | **矛盾** |
| `OrderSettlementSyncService` | **不存在**。实际映射由 `OrderSyncService.mapOrder()` 完成 | **矛盾** |
| `source_amount_unit` 字段 | **不存在**。仅存在于 stash 中的未提交 entity | **矛盾** |
| entity 层金额单位 | `OrderDualTrackAmountResolver.java` 第 8 行注释"金额单位：分"，entity 存储 **fen**（分），不是元 | **矛盾** |
| Controller / 前端 ÷100 | 多处 inline `/100` 转换：`TalentQueryService:750`、`SampleController:1758`、`PerformanceExportService:90` | 符合实际代码（与文档期望相反） |

**实际金额流**：
```
抖音 API (fen) → RealDouyinOrderGateway.toOrderItem() (Long, fen)
  → OrderDualTrackAmountResolver.resolve() (fen)
  → applyToOrder() (entity Long 字段, fen)
  → 数据库 colonelsettlement_order (fen)
  → Controller / 前端 inline ÷100 展示 (元)
```

**判定**：**严重矛盾**

P0-7 文档描述的架构（Gateway 层 ÷100 → entity 存元 → 前端不除）**从未在已提交代码中实现**。实际架构是 entity 存 fen、展示时 ÷100。

**风险评估**：
- 当前架构**功能正确**——只要所有展示层都做 ÷100，用户看到的金额就是正确的。
- 但文档与代码不一致会导致：新开发者误解单位、新接口可能忘记 ÷100。
- `DouyinMoneyConverter.fenToStoredMinor()` 是 no-op（fen→yuan→fen 回到原值），即使合并也不会改变行为。

#### 2.3.2 双轨金额解析

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 预估轨 + 结算轨分离 | `OrderDualTrackAmountResolver.resolve()` 返回 `DualTrackAmounts(estimateServiceFee, effectiveServiceFee, estimateTechServiceFee, effectiveTechServiceFee, ...)` | 符合 |
| 重同步保留预估值 | `mergeEstimateSnapshot()`: 当 incoming 有效轨为空时保留 existing | 符合 |
| 结算轨优先用于旧字段兼容 | `applyToOrder()`: `settleColonelCommission = effective > 0 ? effective : estimate` | 符合 |
| `createTime` vs `settleTime` 分离 | `OrderSyncService.mapOrder()` 297 行: `order.setCreateTime(fromEpochSecond)` / `order.setSettleTime(fromEpochSecond)` | 符合 |
| Dashboard 按 `settle_time` 过滤 | `PerformanceMetricsQueryService.resolveTimeColumn()` → `settle_time` | 符合 |
| 预估轨/结算轨列选择 | `isEstimateTrack()`: `timeColumn == create_time` 用 estimate_* 列，否则用 effective_* 列 | 符合 |

**判定**：**符合（双轨机制正确）**

#### 2.3.3 提成公式

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| `serviceFeeNet = serviceFeeIncome - techServiceFee` | `CommissionService` | 符合 |
| `bizCommission = serviceFeeNet * bizRatio` | `CommissionService` | 符合 |
| `channelCommission = serviceFeeNet * channelRatio` | `CommissionService` | 符合 |
| `grossProfit = serviceFeeNet - bizCommission - channelCommission` | `CommissionService` | 符合 |
| 比例解析链 | `CommissionRuleService` → 活动级配置 → 全局配置 → 默认 0.15 | 符合 |
| 配置键 | `commission.business_default_ratio` / `commission.channel_default_ratio` | 符合，`E2E-CONFIG-01` 已验证可读写 |
| 金额精度 | `multiplyCent()` 方法处理 fen 级精度 | 符合 |

**判定**：**符合（公式和配置链正确）**

#### 2.3.4 Dashboard 聚合

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 双路径聚合 | `hasPerformanceRecords()` 判断走 performance_records 还是直接 SQL | 符合 |
| performance_records 路径 | `aggregateRange()`: JOIN colonelsettlement_order，`pr.is_valid = TRUE` | 符合 |
| 排行榜过滤已归因 | `co.attribution_status = 'ATTRIBUTED' AND pr.final_channel_user_id IS NOT NULL` | 符合 |
| 汇总数不排除未归因 | `aggregateDashboardSummary()`: 只要求 `pr.is_valid = TRUE`，不检查 attribution_status | **潜在问题** |

**判定**：**部分符合**

- 排行榜口径正确（只展示已归因）。
- 汇总数包含 UNATTRIBUTED 订单的业绩，与审查清单四断言"不进入业绩"矛盾。

---

### 2.4 订单去重（`order_id` dedup）

**文档期望**（清单 三 订单域）：主同步和按订单号补拉是否按 `order_id` 去重

**代码事实**：

| 层级 | 机制 | 代码位置 | 结论 |
|------|------|----------|------|
| 第 1 层 | Redis 分布式 claim | `OrderSyncPersistenceService.persistOrder()`: `orderSyncDedupClaimMapper.claim(orderId, id)` | 符合 |
| 第 2 层 | DB 查重 | `orderMapper.findByOrderId()` | 符合 |
| 第 3 层 | INSERT IGNORE | `insertIgnoreByOrderId()` | 符合 |
| 更新时保留原始时间 | `order.setCreateTime(existing.getCreateTime())` | `persistOrder()` 更新路径 | 符合 |
| 乐观锁 | 更新时带 version 条件 | `persistOrder()` | 符合 |

**判定**：**符合（三层去重机制健全）**

---

### 2.5 归因状态机

**文档期望**（清单 四）：
- `ATTRIBUTED / UNATTRIBUTED` + `attribution_remark` 诊断
- `unsafeBecauseCreatedAfterOrder` 不写成已归因，不进入业绩

**代码事实**：

| 断言 | 实际情况 | 结论 |
|------|----------|------|
| 双状态 | `ATTRIBUTED` / `UNATTRIBUTED` | 符合 |
| `attribution_remark` | 归因成功写归因路径，失败写诊断原因 | 符合 |
| `unsafeBecauseCreatedAfterOrder` 不写成已归因 | **矛盾**。该条件仅产生 Dashboard 诊断标签，不阻止 `ATTRIBUTED` 写入 | **矛盾** |
| `unsafeBecauseCreatedAfterOrder` 不进入业绩 | **矛盾**。`OrderCommissionPolicy.countsTowardPerformance()` 只排除 `STATUS_CANCELLED(4)`，不排除未归因或 unsafe 订单 | **矛盾** |

**关键发现**：

1. `unsafeBecauseCreatedAfterOrder` 在 `AttributionService` 中**没有阻断逻辑**。归因优先级链正常执行，如果 `pick_source_mapping` 或其他映射命中，即使 `mapping.created_at > order.create_time`，订单仍写入 `ATTRIBUTED`。

2. `PerformanceCalculationService.upsertFromOrder()` 对所有非取消订单创建 `is_valid = TRUE` 的业绩记录，**无论归因状态**。UNATTRIBUTED 订单也会产生有效业绩记录。

3. Dashboard 汇总数（`aggregateDashboardSummary()`）包含所有 `is_valid = TRUE` 的记录，即包含 UNATTRIBUTED 订单。只有排行榜单独过滤 `attribution_status = 'ATTRIBUTED'`。

**影响评估**：
- 如果未来有 `mapping.created_at > order.create_time` 的历史映射被用于归因，归因结果可能是错误的（映射创建时订单已存在，说明不是该映射带来的推广归因）。
- UNATTRIBUTED 订单计入汇总业绩，会导致"招商/渠道提成总额"包含无法归属到具体人员的金额。
- 当前 V1 阶段真实样本较少，实际影响有限；但不符合文档声明的安全语义。

---

### 2.6 独家达人/商家

**文档期望**（清单 四）：
- V1 默认 `exclusive.enabled=false`
- 只有显式开启后才把当前有效独家记录用于归因覆盖

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 默认关闭 | `AttributionService` 中 `@Value("${exclusive.enabled:false}")` | 符合 |
| 开关守卫 | 归因链中独家商家/达人逻辑受 `exclusiveEnabled` 守卫 | 符合 |
| 认领冲突守卫 | 达人有其他用户活跃认领时返回 `UNATTRIBUTED` + `TALENT_CLAIM_OWNER_CONFLICT` | 符合 |

**判定**：**符合**

---

### 2.7 订单同步时间双轨

**代码事实**：

| 字段 | 来源 | 结论 |
|------|------|------|
| `createTime` | `AppZone.fromEpochSecond(item.createTime())` — 抖音订单创建时间 | 符合 |
| `settleTime` | `AppZone.fromEpochSecond(item.settleTime())` — 抖音结算时间 | 符合 |
| Dashboard 过滤 | 默认 `settle_time`，支持 `timeField=createTime` 切换到预估轨 | 符合 |
| 业绩记录 | 同时存储 `orderCreateTime` 和 `settleTime` | 符合 |

**判定**：**符合**

---

## 三、矛盾汇总与风险评级

| # | 矛盾项 | 严重度 | 审查清单断言 | 代码实际行为 |
|---|--------|--------|-------------|-------------|
| B-1 | **P0-7 文档描述的金额架构从未实现** | **CRITICAL** | entity 存元、Gateway ÷100 | entity 存 fen、展示 ÷100 |
| B-2 | **unsafeBecauseCreatedAfterOrder 不阻断归因** | **HIGH** | 不写成已归因 | 仅诊断标签，仍可写入 ATTRIBUTED |
| B-3 | **UNATTRIBUTED 订单计入业绩** | **HIGH** | 不进入业绩 | `is_valid=TRUE`，计入汇总 |
| B-4 | **汇总数包含未归因订单** | **MEDIUM** | Dashboard 口径一致 | 汇总不排除 UNATTRIBUTED，排行榜排除 |

---

## 四、建议

### CRITICAL — B-1：P0-7 文档与代码不一致

**当前状态**：entity 存 fen 是**功能正确**的实现，只是与文档描述不同。

**建议**：
1. 更新 `04-上线验收清单.md` P0-7 章节，删除关于 `toSettlementItem()`、`fenToYuanOrNull()`、`OrderSettlementSyncService`、`source_amount_unit` 的描述
2. 将文档口径修正为："entity 层金额字段为 Long（分），Controller / 前端 ÷100 展示"
3. 考虑在 entity 字段或 `OrderDualTrackAmountResolver` 类上加 `@Column` 注释或 Javadoc 明确标注"单位：分"
4. 决策点：是否需要引入 `source_amount_unit` 字段做防回归标记？当前所有金额来源都是抖音 API（fen），单一来源下风险较低

### HIGH — B-2：unsafeBecauseCreatedAfterOrder 安全闸

**当前状态**：仅作为 Dashboard 诊断标签，不阻断归因。

**建议**（二选一）：
1. **选项 A（推荐）**：在 `AttributionService.resolveAttribution()` 中增加前置检查：如果 `pick_source_mapping.created_at > order.create_time`，该映射不参与归因，返回 `UNATTRIBUTED` + `UNSAFE_MAPPING_CREATED_AFTER_ORDER`
2. **选项 B**：如果业务判断 V1 阶段此场景不会出现（所有映射都先于订单创建），则更新审查清单断言，将"不写成已归因"改为"Dashboard 诊断标记"，降低断言等级

### HIGH — B-3：UNATTRIBUTED 订单业绩计入

**当前状态**：`OrderCommissionPolicy.countsTowardPerformance()` 只排除取消订单。

**建议**（二选一）：
1. **选项 A（推荐）**：在 `countsTowardPerformance()` 中增加归因状态检查——UNATTRIBUTED 订单不计入业绩（`is_valid = FALSE`）
2. **选项 B**：保持当前行为（汇总包含所有有效订单），但更新 Dashboard 展示层，将"已归因业绩"和"未归因业绩"分开展示，让运营看到归因覆盖率

### MEDIUM — B-4：Dashboard 汇总口径

如果 B-3 采用选项 A，此问题自动解决。如果采用选项 B，需要在 Dashboard summary API 中增加 `unattributedAmount` 字段。

---

## 五、通过项汇总

| 审查项 | 结论 | 备注 |
|--------|------|------|
| P0-5 活动默认招商归因 | **通过** | V1 独家默认关闭，仅走活动绑定 |
| 订单去重（三层保护） | **通过** | claim + findByOrderId + insertIgnore |
| 更新时保留原始 createTime | **通过** | `persistOrder()` 更新路径 |
| 双轨金额解析（预估/结算） | **通过** | `DualTrackAmounts` 分离正确 |
| 重同步保留预估值 | **通过** | `mergeEstimateSnapshot()` |
| 提成公式 | **通过** | 4 步公式 + 配置链 + 精度处理 |
| 排行榜过滤已归因 | **通过** | `attribution_status = 'ATTRIBUTED'` |
| 独家开关默认关闭 | **通过** | `exclusive.enabled=false` |
| 认领冲突守卫 | **通过** | `TALENT_CLAIM_OWNER_CONFLICT` |
| createTime / settleTime 双轨 | **通过** | 两个时间字段独立存储和查询 |
| Dashboard 预估/结算轨切换 | **通过** | `timeField` 参数 + `isEstimateTrack()` |

---

## 六、后续批次预告

| 批次 | 目标 | 状态 |
|------|------|------|
| A | 权限与数据范围防回归 | 已完成（2026-05-22） |
| **B** | **订单归因与金额双轨** | **本报告** |
| C | 达人/寄样状态机 | 待执行 |
| D | 配置与缓存 | 待执行 |
| E | 性能与可维护性 | 待执行 |
