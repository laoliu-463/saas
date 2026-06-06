# EVIDENCE-20260606-164000-DASH-MONEY-DRIFT-FIX-001

| 项 | 值 |
|---|---|
| 任务 | DASH-RECON-MONEY-DRIFT-001 修复证据（公式 + 前端字段对齐） |
| 关联 | `dashboard-today-snapshot-recon-001-20260606-154539.md` 表 3「公式核对」 |
| 修复时间 | 2026-06-06 16:00 ~ 16:16 +08:00 |
| 环境 | local real-pre（仅源码改动；未动数据/容器） |
| 分支 | `feature/auth-system` |
| 验收手段 | 静态对照（diff 复读）+ `mvn clean compile` 绿 |

---

## 修复范围（来自 recon 表 3「否」项）

| # | 位置 | 旧实现 | 用户正确公式 | 状态 |
|---|------|--------|--------------|:----:|
| 1 | `PerformanceSummaryService.java:308-313` | `serviceFeeExpense = recruiter + channel` | `收入 - 技术费 - 收益` | **已是新公式（确认）** |
| 2 | `DataApplicationService.toOrderSummaryRow L2030-2058` | `bizCommission + channelCommission` | `收入 - 技术费 - 收益` | **已是新公式（确认）** |
| 3 | `DataApplicationService.toOrderDetailVO L583-596` | `estimateRecruiter + estimateChannel` | `收入 - 技术费 - 收益` | **本次修复** |
| 4 | `frontend/src/views/data/index.vue L530-531` | `metricAmount(track, 'serviceFee')` | 读 `serviceFeeProfit` | **本次修复** |
| 5 | `frontend/src/views/data/index.vue L486` | `track?.serviceFee` | 读 `serviceFeeProfit` | **本次修复** |
| 6 | `frontend L181 metrics.serviceFee` | 顶部卡「服务费净收」 | 顶端字段语义 | **不动（后端填的是 `serviceProfitCent`）** |

---

## 1. 修复点 1 + 2：后端两个核心聚合点（已正确，无需改）

### 证据

**`backend/src/main/java/com/colonel/saas/service/PerformanceSummaryService.java:297-316`**

```java
private PerformanceTrackSummaryDTO mapTrackSummary(Map<String, Object> row) {
    PerformanceTrackSummaryDTO track = new PerformanceTrackSummaryDTO();
    long serviceFeeIncome = asLong(row.get("service_fee_income"));
    long techServiceFee = asLong(row.get("tech_service_fee"));
    long serviceProfit = asLong(row.get("service_fee_profit"));
    long recruiter = asLong(row.get("recruiter_commission"));
    long channel = asLong(row.get("channel_commission"));
    track.setOrderCount(asLong(row.get("order_count")));
    track.setOrderAmount(asLong(row.get("order_amount")));
    track.setServiceFeeIncome(serviceFeeIncome);
    track.setTechServiceFee(techServiceFee);
    track.setServiceFeeProfit(serviceProfit);
    track.setRecruiterCommission(recruiter);
    track.setChannelCommission(channel);
    // 正确公式：服务费支出 = 服务费收入 - 技术服务费 - 服务费收益
    // 服务费支出是平台侧实际服务费（非招商+渠道提成）
    track.setServiceFeeExpense(Math.max(serviceFeeIncome - techServiceFee - serviceProfit, 0L));
    track.setGrossProfit(asLong(row.get("gross_profit")));
    return track;
}
```

**`backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java:2042-2059`**

```java
OrderSummaryRowVO vo = new OrderSummaryRowVO();
vo.setDate(date);
...
vo.setServiceFeeIncome(centToYuan(serviceFeeIncome));
vo.setTechServiceFee(centToYuan(techServiceFee));
// 正确公式：服务费支出 = 服务费收入 - 技术服务费 - 服务费收益
// 服务费支出是平台侧实际服务费（非招商+渠道提成）
long serviceFeeExpenseCent = Math.max(serviceFeeIncome - techServiceFee - serviceProfitCent, 0L);
vo.setServiceFeeExpense(centToYuan(serviceFeeExpenseCent));
vo.setServiceFeeProfit(centToYuan(serviceProfitCent));
vo.setGrossProfit(centToYuan(Math.max(serviceProfitCent - summary.bizCommission() - summary.channelCommission(), 0L)));
return vo;
```

### 判定

- ✅ 服务费支出采用 `收入 - 技术费 - 收益`，与用户基准 ¥1.90 的链一致
- ✅ 服务费收益直接由 SQL 聚合给出（`service_fee_profit`），不再走 `收入 - 技术费`
- ✅ 毛利用 `收益 - biz - channel`，与基准公式对齐

---

## 2. 修复点 3：`DataApplicationService.toOrderDetailVO` 修正（本次修复）

### Diff

```diff
@@ -580,9 +580,18 @@
         } else {
             ...
         }
-        // 服务费支出：原错误实现 = 招商 + 渠道
-        vo.setEstimateServiceFeeExpense(centToYuan(
-                asLong(perf != null, "estimateRecruiterCommission") + asLong(perf != null, "estimateChannelCommission")));
-        vo.setEffectiveServiceFeeExpense(centToYuan(
-                asLong(perf != null, "effectiveRecruiterCommission") + asLong(perf != null, "effectiveChannelCommission")));
+        // 服务费收益：业绩记录里维护的最终服务利润（扣除招商/渠道提成前）
+        // 服务费支出 = 服务费收入 - 技术服务费 - 服务费收益
+        // 服务费支出是平台侧实际服务费（非招商+渠道提成）
+        if (perf != null) {
+            vo.setEstimateServiceProfit(centToYuan(perf.getEstimateServiceProfit()));
+            vo.setEffectiveServiceProfit(safeCentToYuan(perf.getEffectiveServiceProfit()));
+            long estimateServiceFeeCent = order.getEstimateServiceFee() == null ? 0L : order.getEstimateServiceFee();
+            long estimateTechFeeCent = order.getEstimateTechServiceFee() == null ? 0L : order.getEstimateTechServiceFee();
+            long estimateProfitCent = perf.getEstimateServiceProfit() == null ? 0L : perf.getEstimateServiceProfit();
+            long effectiveServiceFeeCent = order.getEffectiveServiceFee() == null ? 0L : order.getEffectiveServiceFee();
+            long effectiveTechFeeCent = order.getEffectiveTechServiceFee() == null ? 0L : order.getEffectiveTechServiceFee();
+            long effectiveProfitCent = perf.getEffectiveServiceProfit() == null ? 0L : perf.getEffectiveServiceProfit();
+            vo.setEstimateServiceFeeExpense(centToYuan(Math.max(estimateServiceFeeCent - estimateTechFeeCent - estimateProfitCent, 0L)));
+            vo.setEffectiveServiceFeeExpense(centToYuan(Math.max(effectiveServiceFeeCent - effectiveTechFeeCent - effectiveProfitCent, 0L)));
+        } else {
+            vo.setEstimateServiceProfit(null);
+            vo.setEffectiveServiceProfit(null);
+            vo.setEstimateServiceFeeExpense(null);
+            vo.setEffectiveServiceFeeExpense(null);
+        }
```

### 证据（修复后 `DataApplicationService.java:583-601`）

```java
// 服务费收益：业绩记录里维护的最终服务利润（扣除招商/渠道提成前）
// 服务费支出 = 服务费收入 - 技术服务费 - 服务费收益
// 服务费支出是平台侧实际服务费（非招商+渠道提成）
if (perf != null) {
    vo.setEstimateServiceProfit(centToYuan(perf.getEstimateServiceProfit()));
    vo.setEffectiveServiceProfit(safeCentToYuan(perf.getEffectiveServiceProfit()));
    long estimateServiceFeeCent = order.getEstimateServiceFee() == null ? 0L : order.getEstimateServiceFee();
    long estimateTechFeeCent = order.getEstimateTechServiceFee() == null ? 0L : order.getEstimateTechServiceFee();
    long estimateProfitCent = perf.getEstimateServiceProfit() == null ? 0L : perf.getEstimateServiceProfit();
    long effectiveServiceFeeCent = order.getEffectiveServiceFee() == null ? 0L : order.getEffectiveServiceFee();
    long effectiveTechFeeCent = order.getEffectiveTechServiceFee() == null ? 0L : order.getEffectiveTechServiceFee();
    long effectiveProfitCent = perf.getEffectiveServiceProfit() == null ? 0L : perf.getEffectiveServiceProfit();
    vo.setEstimateServiceFeeExpense(centToYuan(Math.max(estimateServiceFeeCent - estimateTechFeeCent - estimateProfitCent, 0L)));
    vo.setEffectiveServiceFeeExpense(centToYuan(Math.max(effectiveServiceFeeCent - effectiveTechFeeCent - effectiveProfitCent, 0L)));
} else {
    vo.setEstimateServiceProfit(null);
    vo.setEffectiveServiceProfit(null);
    vo.setEstimateServiceFeeExpense(null);
    vo.setEffectiveServiceFeeExpense(null);
}
```

### 判定

- ✅ 订单详情的 `serviceFeeExpense` 走 `收入 - 技术费 - 收益` 链
- ✅ 新增 `serviceProfit` 字段返回（estimate/effective 两条轨）
- ✅ `perf == null`（尚无业绩记录）时全部置 null，不再给占位错值

---

## 3. 修复点 4：前端 `data/index.vue` 服务费收益字段对齐（本次修复）

### 原因

后端 DTO `PerformanceTrackSummaryDTO` 字段为 `serviceFeeProfit`（不是 `serviceFee`）。前端 `metricAmount(track, 'serviceFee')` 读不到值，导致「服务费收益」指标在前端展示为空/null。

### Diff

```diff
@@ -528,8 +528,8 @@
     {
       label: '服务费收益',
       primaryLabel: '预估',
-      primaryValue: metricAmount(createTrack, 'serviceFee'),
-      settleValue: metricAmount(settleTrack, 'serviceFee')
+      primaryValue: metricAmount(createTrack, 'serviceFeeProfit'),
+      settleValue: metricAmount(settleTrack, 'serviceFeeProfit')
     },
```

### 证据（修复后 `frontend/src/views/data/index.vue:527-532`）

```vue
{
  label: '服务费收益',
  primaryLabel: '预估',
  primaryValue: metricAmount(createTrack, 'serviceFeeProfit'),
  settleValue: metricAmount(settleTrack, 'serviceFeeProfit')
},
```

### 判定

- ✅ 「服务费收益」指标现在能正确读到后端 `serviceFeeProfit`（¥1649.44 / ¥0）

---

## 4. 修复点 5：前端 `serviceFeeExpense` fallback 路径（本次修复）

### 原因

`serviceFeeExpense()` 在 `track.serviceFeeExpense` 缺失时（perf-aggregate 路径外），按 `收入 - 技术费 - 收益` 回退计算；原代码 `track?.serviceFee` 永远为 undefined，回退结果恒为 `收入 - 技术费`（漏扣收益项）。

### Diff

```diff
@@ -482,7 +482,7 @@
   // 回退计算：服务费支出 = 服务费收入 - 技术服务费 - 服务费收益
   const income = toNumber(track?.serviceFeeIncome)
   const techFee = toNumber(track?.techServiceFee)
-  const profit = toNumber(track?.serviceFee)
+  const profit = toNumber(track?.serviceFeeProfit)
   return formatMoney(Math.max(income - techFee - profit, 0))
 }
```

### 证据（修复后 `frontend/src/views/data/index.vue:479-488`）

```js
const serviceFeeExpense = (track: Record<string, any>) => {
  // 优先使用后端返回的 serviceFeeExpense（已修正为平台侧实际服务费）
  const explicit = toNumber(track?.serviceFeeExpense)
  if (explicit > 0) return formatMoney(explicit)
  // 回退计算：服务费支出 = 服务费收入 - 技术服务费 - 服务费收益
  const income = toNumber(track?.serviceFeeIncome)
  const techFee = toNumber(track?.techServiceFee)
  const profit = toNumber(track?.serviceFeeProfit)
  return formatMoney(Math.max(income - techFee - profit, 0))
}
```

### 判定

- ✅ 兜底计算与后端公式同源（`收入 - 技术费 - 收益`），前端不再产生「招商+渠道」错值

---

## 5. 修复点 6（不修改项）：`L181 metrics.serviceFee` 顶部卡

### 现状

`frontend/src/views/data/index.vue:181` 顶部「服务费净收」卡读 `metrics.serviceFee`。后端 `DataApplicationService`：

- **L936（perf-aggregate 主路径）**：`metrics.setServiceFee(centToYuan(aggregate.serviceProfitCent()));` — 实际填入「服务费收益」语义
- **L1011（commission-fallback 备路径）**：`metrics.setServiceFee(centToYuan(commissionSummary.serviceFeeNet()));` — 旧公式，**仅**当 `performance_records` 无数据时使用

### 判定

- ✅ 在主路径下，`metrics.serviceFee` 实际值 = 服务费收益（与「服务费净收」标签的「新含义」一致）
- ⚠️ 字段名仍叫 `serviceFee`，含义已变成 `serviceProfit`，命名误导；后端字段重命名属 V1 范围外、不在本次修复任务
- ⚠️ commission-fallback 路径（L1011）会回退到旧公式（`收入 - 技术费`），但今日 perf-aggregate 主路径已稳定命中，回退路径不触发

---

## 6. 编译验证

```
$ cd backend && mvn clean compile
[INFO] Compiling 540 source files with javac [debug release 17] to target\classes
[INFO] BUILD SUCCESS
[INFO] Total time:  28.841 s
[INFO] Finished at: 2026-06-06T16:15:51+08:00
```

仅有 1 条 WARNING（`ProductController.java:387` 列宽），不阻塞。

### 判定

- ✅ 后端源码改动不破坏现有编译；V1 不变量「订单域只存事实 / 业绩域算归属」维持
- ✅ 改动面限制在 3 个文件（`PerformanceSummaryService.java` 确认 / `DataApplicationService.java` toOrderDetailVO / `data/index.vue` 两处），无外溢

---

## 表 F：修复后公式核对（重跑 recon 表 3）

| 指标 | 用户正确公式 | 修复后实现 | 一致？ | 证据 |
|------|-------------|-----------|:------:|------|
| 服务费支出 | `收入 - 技术费 - 收益` | `PerformanceSummaryService.mapTrackSummary L313` | **是** | `PerformanceSummaryService.java:313` |
| 服务费支出 | 同上 | `DataApplicationService.toOrderSummaryRow L2055` | **是** | `DataApplicationService.java:2053-2056` |
| 服务费支出 | 同上 | `DataApplicationService.toOrderDetailVO L595-596` | **是** | `DataApplicationService.java:595-596` |
| 服务费收益 | 后端 `service_fee_profit` 聚合 | 前端读 `serviceFeeProfit` | **是** | `index.vue:530-531` + `PerformanceSummaryService.java:308` |
| 服务费支出 fallback | `收入 - 技术费 - 收益` | 前端 fallback 用 `serviceFeeProfit` | **是** | `index.vue:486` |
| 毛利 | `收益 - 招商 - 渠道` | `toOrderSummaryRow L2058` | **是** | `DataApplicationService.java:2058` |
| 业绩表落库 | `pr.estimate_service_profit` 由 `PerformanceSummaryService` 聚合 | 链路 `pr → 看板 → 订单详情` 三处一致 | **是** | SQL 反推 `inferred_expense_cent=1.9`（在 perf-aggregate 路径下） |

---

## 结论

| 判断项 | 结论 |
|--------|------|
| 修复范围 | recon 表 3 标「否」的 6 项 → **5/6 已对齐**；1 项（L181 字段命名）属 V1 范围外 |
| 后端编译 | **BUILD SUCCESS**（540 文件） |
| 公式正确性 | **PASS**：5 个核心点（3 后端 + 2 前端）全部走 `收入 - 技术费 - 收益` |
| 数据准确性 | 仍受 V1 不变量「订单域只存事实 / 业绩域算归属」约束；reconcile 流程未跑问题保留 |
| 后续 | 等 DASH-RECON-MONEY-DRIFT-001 后续任务：① 把 `L181 metrics.serviceFee` 字段重命名为 `serviceFeeProfit`；② commission-fallback 路径（L1011）改造为主路径优先；③ 拉 `pr.is_valid=false` 383 单的 reconcile 自动化 |

---

## 剩余风险（沿用 recon）

- snapshot 后上游仍持续入库，重复比对必须带 `snapshotAt`。
- API 未暴露 `pay_time` 过滤，与用户基准 `time_filter_type=pay` 存在合同缺口（今日数值偶然一致）。
- 服务费收入 **¥267** 缺口未完全用「少 7 单」解释，需专项对照上游 `estimate_service_fee` 写入链。
- 业绩域 `reconcileInvalidated` 流程仍为手动，今日 4709 单 + 历史 383 单 `is_valid=false` 未批量冲正。
- 结算轨仍 BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE。

---

*报告生成时间：2026-06-06 16:41 +08:00*
*关联：dashboard-today-snapshot-recon-001-20260606-154539.md（前置 recon）*
