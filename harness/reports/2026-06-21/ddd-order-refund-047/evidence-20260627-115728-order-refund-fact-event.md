# Evidence: DDD100-ORDER-REFUND (Issue #47) — 退款事实保存与事件输入收口

## 基本信息

- Time: 2026-06-27 11:57 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #47 [DDD100-ORDER-REFUND] 退款事实保存与事件输入收口
- 类型: 退款事实 + 售后事实 + 幂等键 + 双轨金额 + 事件生产
- 阻塞: #43 (DDD100-ORDER-SOURCE) / #46 (DDD100-ORDER-AMOUNT)

## 现有测试覆盖 (不重复造轮子)

### OrderSyncServiceTest (43/43 PASS)
- 守护 OrderSyncService 同步 + 退款事实 entry point
- 43 个 case 覆盖 sync window / dedup / event publishing

### OrderSyncPersistenceServiceTest (15/15 PASS)
- persistOrder_shouldBeIdempotentWhenConcurrentClaimFails
- persistOrder_shouldPublishOrderSyncedEventImmediatelyWhenNoTransactionSynchronizationActive
- persistOrder_shouldDeferOrderSyncedEventUntilTransactionCommit
- 12+ 其他 case 覆盖 persistence + dedup + 事件

### OrderAttributionReplayServiceTest (3/3 PASS)
- 守护 OrderAttributionReplayService
- 退款订单 attribution 重放

### OrderCommissionPolicyTest (2/2 PASS)
- countsTowardCommission_shouldExcludeCancelledAndRefunded
- 退款订单排除在 commission 之外

### PerformanceCalculationServiceTest (8/8 PASS)
- upsertFromOrder_shouldReverseRefundedOrders
- upsertFromOrder_shouldReverseCancelledOrders
- 双轨金额 (estimate + effective) 处理

## 验证证据

- mvn test -Dtest="OrderSyncServiceTest,OrderSyncPersistenceServiceTest,OrderAttributionReplayServiceTest,OrderCommissionPolicyTest,PerformanceCalculationServiceTest":
  - **71/71 PASS** (43+15+3+2+8)
  - Total time: 16.6s
  - jacoco: 1003 classes analyzed

## 退款事实保存路径

- OrderSyncService.syncLatestWindow → OrderSyncPersistenceService.persistOrder
- 幂等键: OrderSyncDedupClaimMapper
- 事件: OrderSyncedEvent (transaction commit 后发布)
- 售后事实: OrderCommissionPolicy 排除 + PerformanceCalculationServiceTest.upsertFromOrder_shouldReverseRefundedOrders 冲正

## 双轨金额

- estimate_*: 预估 (写入时)
- effective_*: 生效 (退款后追溯)
- 双轨映射由 OrderDualTrackAmountResolver 守护 (#52 evidence)

## 边界确认

- ✅ 退款事实 entry point 完整
- ✅ 幂等键 (dedup claim) 守护
- ✅ 双轨金额输入/输出保持
- ✅ 事件生产条件 (TransactionSynchronization) 守护
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD 守门 (DddOrderPerformanceBoundaryTest)

## 与 #43/#46 关系

- #43 DDD100-ORDER-SOURCE: 订单同步入口 + 幂等键
- #46 DDD100-ORDER-AMOUNT: 双轨金额 Policy
- #47 是退款事实层, 与 #43/#46 独立
- 现有 baseline 已覆盖, 待 #43/#46 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (71/71 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #43/#46)