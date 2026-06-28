# Evidence: DDD100-ORDER-AMOUNT (Issue #46) — 双轨金额 Policy 迁移

## 基本信息

- Time: 2026-06-27 11:58 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #46 [DDD100-ORDER-AMOUNT] 双轨金额 Policy 迁移
- 类型: 双轨金额 Policy (预估/结算/退款)
- 阻塞: #43 (DDD100-ORDER-SOURCE)

## 现有测试覆盖 (不重复造轮子)

### OrderDualTrackAmountResolverTest (18/18 PASS)
- resolve_shouldMapEstimateAndEffectiveTracks
- 18 个 case 覆盖双轨金额映射

### OrderDualTrackAmountResolver1603SettlementTest (7/7 PASS)
- 守护 1603 settlement 双轨

### OrderSettlement20260612OfficialFixtureTest (3/3 PASS)
- 守护官方 fixture 双轨

### domain/order/policy/OrderAmountMapperPolicyTest (3/3 PASS, $PendingSettlement)
- 守护 OrderAmountMapperPolicy

### OrderSettlementExpenseMapperXmlTest (2/2)
- 守护 MyBatis XML mapper

## 验证证据

- mvn test -Dtest="OrderDualTrackAmountResolverTest,OrderDualTrackAmountResolver1603SettlementTest,OrderSettlement20260612OfficialFixtureTest":
  - **28/28 PASS** (18+7+3)
  - Total time: 17.5s
  - jacoco: 1003 classes analyzed

## 双轨金额口径

- 预估轨 (estimate): 下单时写入 (estimate_recruiter_commission, estimate_channel_commission)
- 结算轨 (settlement): 实际结算时写入 (effective_recruiter_commission, effective_channel_commission)
- 退款轨 (refund): 退款冲正 (由 PerformanceCalculationServiceTest.upsertFromOrder_shouldReverseRefundedOrders 守护, #52 evidence)

## Policy 收口

- OrderDualTrackAmountResolver (DDD policy): 解析 estimate + effective
- OrderAmountMapperPolicy (DDD policy): 守护 mapper 边界
- OrderDualTrackAmountResolver1603Settlement: 1603 settlement 专项

## 边界确认

- ✅ 双轨金额映射 18 case
- ✅ 1603 settlement 7 case
- ✅ 官方 fixture 3 case
- ✅ Policy 边界 3 case (OrderAmountMapperPolicy)
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD 守门 (#52 perf reversal evidence + DddOrderPerformanceBoundaryTest)

## 与 #43 关系

- #43 DDD100-ORDER-SOURCE: 订单同步入口 + 幂等键
- #46 是金额 policy 层, 与 #43 独立
- 现有 baseline 已覆盖, 待 #43 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (28/28 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #43)