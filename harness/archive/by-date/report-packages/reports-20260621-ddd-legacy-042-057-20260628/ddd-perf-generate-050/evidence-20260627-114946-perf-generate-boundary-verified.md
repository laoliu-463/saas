# Evidence: DDD100-PERF-GENERATE (Issue #50) — performance_records 生成边界收口

## 基本信息

- Time: 2026-06-27 11:49:46 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #50 [DDD100-PERF-GENERATE] performance_records 生成边界收口
- 类型: 业绩域消费订单事实, 不修改订单原始事实
- 阻塞: #43 (DDD100-ORDER-SOURCE) / #46 (DDD100-ORDER-AMOUNT)

## 现有测试覆盖 (不重复造轮子)

### PerformanceCalculationServiceTest (8/8 PASS)
- upsertFromOrder_shouldCalculateDualTrackCommissions
- upsertFromOrder_shouldNotDeductTechServiceFeeAgainFromEffectiveIncome
- upsertFromOrder_shouldReverseCancelledOrders
- upsertFromOrder_shouldReverseRefundedOrders
- upsertFromOrder_shouldPreserveExistingRecordAndKeepUnsettledAmountZero

### PerformanceCalculationEffectiveTrackTest (1/1)
- upsertFromOrder_shouldUseEffectiveTrackWithoutCallingDouyinOrChangingFormula

### PerformanceBackfillServiceTest (3/3)
- 守护 backfill 入口

### CommissionServiceTest (7/7)
- 守护 commission 比例消费

### domain/performance/application/PerformanceCalculationApplicationServiceTest (1/1)
- 守护 DDD Application Service 入口

### CharacterizationBaselineTest.test05_OrderAttributionAndCalculationsBaseline
- 锁 upsertFromOrder 公式 edge cases (zero/large/eff)

## 验证证据

- mvn test: **20/20 PASS** (8+1+3+7+1)
- Total time: 10.5s
- jacoco: 1003 classes analyzed

## performance_records 生成边界

- ✅ 业绩域只读 order 事实, 不修改
- ✅ 双轨金额写入 estimate + effective
- ✅ 退款/取消订单反转
- ✅ 不重复扣除技术服务费
- ✅ 保持现有记录 (unsettled_amount=0)
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD #59 守护只读边界

## 与 #43/#46 关系

- #43 DDD100-ORDER-SOURCE: 订单同步入口 + 幂等键
- #46 DDD100-ORDER-AMOUNT: 双轨金额 Policy
- #50 GENERATE: performance_records 生成
- 现有 baseline 已覆盖, 待 #43/#46 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (20/20 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #43/#46)
