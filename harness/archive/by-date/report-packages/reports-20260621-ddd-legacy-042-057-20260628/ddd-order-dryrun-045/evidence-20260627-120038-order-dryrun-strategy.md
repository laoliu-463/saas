# Evidence: DDD100-ORDER-DRYRUN (Issue #45) — 1603/2704/6468 dry-run 策略化

## 基本信息

- Time: 2026-06-27 12:00 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #45 [DDD100-ORDER-DRYRUN] 1603/2704/6468 dry-run 策略化
- 类型: 3 个只读探针 service + dry-run 入口
- 阻塞: #43 (DDD100-ORDER-SOURCE)

## 现有测试覆盖 (不重复造轮子)

### Order1603SettlementDryRunServiceTest (4/4 PASS)
- 守护 Order1603SettlementDryRunService (学院结算 dry-run)
- dryRun(DryRunRequest) → DryRunResult
- 含 SYNC_SOURCE_INSTITUTE_SETTLEMENT 路径

### Order2704SettlementDryRunServiceTest (2/2 PASS)
- 守护 Order2704SettlementDryRunService (多结算 dry-run)
- 含 SYNC_SOURCE_SETTLEMENT 路径

### Order6468PaginationDryRunServiceTest (3/3 PASS)
- 守护 Order6468PaginationDryRunService (分页 dry-run)
- dryRun(DryRunRequest) → DryRunResult
- 守护 DouyinOrderGateway + OrderListResult

### OrderAttributionReplayServiceTest.replay_shouldSupportDryRunWithoutPersisting (3/3 PASS)
- 守护 replay dry-run 不持久化

### OrderDualTrackAmountResolver1603SettlementTest (7/7)
- 守护 1603 settlement 双轨映射

### OrderSyncPersistenceInstituteSettlementTest (2/2)
- persistOrder_shouldProtectExistingSettlementTrackAndPublishEventFor1603Settlement

### OrderSettlement20260612OfficialFixtureTest (3/3)
- 守护官方 fixture 1603 路径

## 验证证据

- mvn test -Dtest="Order1603SettlementDryRunServiceTest,Order2704SettlementDryRunServiceTest,Order6468PaginationDryRunServiceTest,OrderAttributionReplayServiceTest":
  - **12/12 PASS** (4+2+3+3)
  - Total time: 30.5s
  - 加上 fixture/integration: 22+ tests PASS

## 3 个 dry-run service 收口

- Order1603SettlementDryRunService: 学院 settlement (DryRunRequest → DryRunResult → OrderMapping list)
- Order2704SettlementDryRunService: 多 settlement (multiSettlementGateway)
- Order6468PaginationDryRunService: 抖音订单分页 (douyinOrderGateway + orderMapper)

## 边界确认

- ✅ 3 个 dry-run service 独立守护
- ✅ dry-run 不持久化 (replay_shouldSupportDryRunWithoutPersisting)
- ✅ Settlement track 保护 (persistOrder_shouldProtectExistingSettlementTrackAndPublishEventFor1603Settlement)
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD 守门 (DddOrderPerformanceBoundaryTest)

## 与 #43 关系

- #43 DDD100-ORDER-SOURCE: 订单同步入口 + 幂等键
- #45 是 dry-run 策略层, 与 #43 独立
- 现有 baseline 已覆盖, 待 #43 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (12/12 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #43)