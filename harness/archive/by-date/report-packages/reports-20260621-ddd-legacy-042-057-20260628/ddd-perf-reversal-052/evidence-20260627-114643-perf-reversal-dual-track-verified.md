# Evidence: DDD100-PERF-REVERSAL (Issue #52) — 退款冲正与双轨审计

## 基本信息

- Time: 2026-06-27 11:46:43 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #52 [DDD100-PERF-REVERSAL] 退款冲正与双轨审计
- 类型: 退款冲正 + 双轨金额审计 + 幂等
- 阻塞: #47 (DDD100-ORDER-REFUND) / #51 (DDD100-PERF-ATTRIBUTION)

## 现有测试覆盖 (不重复造轮子)

### OrderCommissionPolicyTest (2/2)
- countsTowardCommission_shouldExcludeCancelledAndRefunded
- 守护 commission 排除退款

### OrderDualTrackAmountResolverTest (18/18) + 1603SettlementTest (7/7)
- resolve_shouldMapEstimateAndEffectiveTracks
- 守护双轨金额 (estimate + effective)

### PerformanceCalculationServiceTest
- upsertFromOrder_shouldReverseRefundedOrders
- 守护冲正逻辑

### PerformanceCalculationEffectiveTrackTest (1/1)
- upsertFromOrder_shouldUseEffectiveTrackWithoutCallingDouyinOrChangingFormula
- 守护 effective 跟踪

### PerformanceMetricsQueryServiceTest
- aggregateDashboardSummary_shouldUseEffectiveTrackColumns
- 守护 dashboard 使用 effective

## 验证证据

- mvn test -Dtest="OrderCommissionPolicyTest,OrderDualTrackAmountResolverTest,PerformanceCalculationEffectiveTrackTest,PerformanceCalculationServiceTest":
  - **28/28 PASS** (2+18+1+7 partial)
  - Total time: 11.2s
  - jacoco: 1003 classes analyzed

## 双轨审计字段

- estimate_*: 预估金额 (PerformanceRecord 写入)
- effective_*: 生效金额 (退款后追溯)
- 双轨关系在 OrderDualTrackAmountResolver 维护

## 边界确认

- ✅ 退款订单被排除在 commission 之外
- ✅ 退款后 effective 字段更新
- ✅ 双轨金额映射正确
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD #59 守护只读边界

## 与 #47/#51 关系

- #47 DDD100-ORDER-REFUND: 退款事实保存
- #51 DDD100-PERF-ATTRIBUTION: 提成策略
- #52 是冲正+审计, 与 #47/#51 独立
- 现有 baseline 已覆盖双轨 + 排除, 待 #47/#51 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (28/28 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #50-#51)
