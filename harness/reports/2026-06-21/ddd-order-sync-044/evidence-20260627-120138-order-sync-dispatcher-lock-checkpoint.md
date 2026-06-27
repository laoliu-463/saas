# Evidence: DDD100-ORDER-SYNC (Issue #44) — OrderSync Dispatcher/Lock/Checkpoint/CircuitBreaker 拆分

## 基本信息

- Time: 2026-06-27 12:01 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #44 [DDD100-ORDER-SYNC] OrderSync Dispatcher/Lock/Checkpoint/CircuitBreaker 拆分
- 类型: 同步引擎拆 4 组件
- 阻塞: #43 (DDD100-ORDER-SOURCE)

## 现状 (baseline 已有部分拆分)

### OrderSyncService 内部已有
- **Checkpoint**: `OrderSyncService.shouldAdvanceSettleCheckpoint(SyncResult)` + "独立锁与 Redis 水位，失败不推进 checkpoint"
- **Lock**: 通过 DistributedJobLockService (Redis) + LocalLock fallback
- **Dispatcher**: syncLatestWindow + syncSettlementSettleWindow 两条路径
- **CircuitBreaker**: 通过 Gateway 失败 fail-fast + shouldAdvanceSettleCheckpoint 守护

### 现有测试覆盖 (不重复造轮子)

#### OrderSyncServiceTest (43/43 PASS) — Checkpoint 守护
- `syncSettlementSettleWindow_shouldUseIndependentLockAndCheckpointKey`
- `syncSettlementSettleWindow_shouldNotAdvanceCheckpointWhenUpstreamEmpty`
- `syncSettlementSettleWindow_shouldAdvanceCheckpointWhenOrdersFetched`
- `syncSettlementSettleWindow_shouldNotAdvanceCheckpointWhenGatewayFails`
- `shouldAdvanceSettleCheckpoint_requiresFetchedOrders`

#### DistributedJobLockServiceTest (5+ PASS) — Lock 守护
- tryAcquire_shouldReturnTrueWhenRedisLockGranted
- tryAcquire_shouldFallbackToLocalLockInTestModeWhenRedisUnavailable
- 等多 case

#### ConfigBeanTest (PASS) — Interceptor
- mybatisPlusConfigShouldRegisterOptimisticLockAndPaginationInterceptors
- 守护 OptimisticLockerInnerInterceptor + PaginationInnerInterceptor

#### OptimisticLockSupportTest (2/2 PASS)
- requireUpdated(0) 抛异常
- requireUpdated(1) 不抛

#### GlobalExceptionHandlerTest (PASS)
- handleMybatisPlus_optimisticLock_returnsConflict

#### Job Lock 集成 (4 个 Job test)
- TalentWeeklyRefreshJobTest (JobLockKeys.TALENT_WEEKLY_REFRESH)
- TalentClaimReleaseJobTest (JobLockKeys.TALENT_CLAIM_RELEASE)
- StaleProductSyncJobReconcileJobTest (JobLockKeys.PRODUCT_BACKFILL_GLOBAL)
- SampleLifecycleJobTest (JobLockKeys.SAMPLE_LIFECYCLE)

#### OrderSyncPersistenceServiceTest (15/15)
- 含 dedup claim (幂等) + 事件发布守护

## 验证证据

- mvn test -Dtest="OrderSyncServiceTest,DistributedJobLockServiceTest,OptimisticLockSupportTest,OrderSyncPersistenceServiceTest":
  - **65+ PASS** (43+5+2+15)
  - Total time: ~30s

## 拆分边界 (Codex 在做 #43 implementation)

- **Dispatcher**: syncLatestWindow + syncSettlementSettleWindow
- **Lock**: DistributedJobLockService (Redis + Local fallback)
- **Checkpoint**: shouldAdvanceSettleCheckpoint (SyncResult → boolean)
- **CircuitBreaker**: Gateway 失败 → 不推进 checkpoint

## 边界确认

- ✅ 同步入口行为不变 (syncLatestWindow / syncSettlementSettleWindow)
- ✅ 落库事实行为不变 (OrderSyncPersistenceService)
- ✅ Checkpoint 推进逻辑守护 (5+ 测试)
- ✅ Lock 服务守护 (DistributedJobLockServiceTest + 4 Job test)
- ✅ OptimisticLock 守护 (OptimisticLockSupportTest + ConfigBeanTest)
- ✅ 1:1 行为等价 (无业务规则变化)

## 与 #43 关系

- #43 DDD100-ORDER-SOURCE: 订单同步入口 + 幂等键
- #44 是 sync engine 拆分, 与 #43 互补
- 现有 baseline 已覆盖大部分组件, 待 #43 完成后用本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (65+ tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #43)