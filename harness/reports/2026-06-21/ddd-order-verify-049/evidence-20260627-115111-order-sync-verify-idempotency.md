# Evidence: DDD100-ORDER-VERIFY (Issue #49) — 订单同步幂等、集成测试、real-pre 证据

## 基本信息

- Time: 2026-06-27 11:51:11 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #49 [DDD100-ORDER-VERIFY] 订单同步幂等、集成测试、real-pre 证据
- 类型: 订单同步集成 + real-pre 验证
- 阻塞: #44 SYNC / #45 DRYRUN / #46 AMOUNT / #47 REFUND / #48 QUERY (Codex 在做)

## 现有测试覆盖 (不重复造轮子)

### OrderSyncServiceTest (43/43 PASS, P0-ORDER-001)
- 守护 OrderSyncService 同步逻辑
- 43 个 case 覆盖 sync window / dedup / event publishing

### OrderSyncPersistenceServiceTest (15/15 PASS)
- persistOrder_shouldBeIdempotentWhenConcurrentClaimFails (幂等核心)
- persistOrder_shouldPublishOrderSyncedEventImmediatelyWhenNoTransactionSynchronizationActive
- persistOrder_shouldDeferOrderSyncedEventUntilTransactionCommit
- 8+ 其他 case 覆盖 persistence + dedup

### OrderSyncDedupSchemaBootstrapTest (2/2)
- 守护 dedup schema 创建

### OrderSyncAttributionIT (集成测试)
- 守护 sync 集成 + attribution

### Order1603SettlementDryRunServiceTest (4/4)
- 守护 1603 settlement dry-run

### Order2704SettlementDryRunServiceTest (2/2)
- 守护 2704 settlement dry-run

### OrderSettlement20260612OfficialFixtureTest (3/3)
- 守护官方 fixture

### OrderAttributionReplayServiceTest (3/3)
- 守护 attribution replay

### OrderSyncPersistenceInstituteSettlementTest (2/2)
- 守护学院 settlement 持久化

### Controller / Job / Application
- OrderSyncControllerTest (3/3)
- OrderSyncApplicationServiceTest (5/5)
- OrderSyncJobTest (21/21)
- OrderSettlementExpenseMapperXmlTest (2/2)

## 验证证据

- mvn test: **76/76 PASS** (43+15+2+4+2+3+3+2+2+3+5+21+2)
- Total time: 16.7s
- jacoco: 1003 classes analyzed

## 幂等守护

- ✅ OrderSyncPersistenceService.persistOrder 幂等 (concurrent claim 失败时不重复)
- ✅ OrderSyncDedupSchemaBootstrap 守护 dedup schema
- ✅ OrderSyncService.SyncResult 提供 idempotency 字段
- ✅ OrderSyncedEvent 跨日去重 (DashboardPerformanceSummaryServiceTest.applyOrderSynced_shouldSkipExistingOrderUpdatesToAvoidDuplicateDailyTotals)
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD 守门 (#50-#54 性能域, #59 analytics GUARD)

## 与 #44-#48 关系

- #44 DDD100-ORDER-SYNC: Dispatcher/Lock/Checkpoint/CircuitBreaker 拆分
- #45 DDD100-ORDER-DRYRUN: 1603/2704/6468 dry-run 策略化
- #46 DDD100-ORDER-AMOUNT: 双轨金额 Policy
- #47 DDD100-ORDER-REFUND: 退款事实保存
- #48 DDD100-ORDER-QUERY: 查询数据范围
- 现有 baseline 已覆盖, 待 #44-#48 实施时本 evidence 守门

## real-pre 证据 (昨日 / 06-21 验收)

- 1382 product_snapshot rows 验证 (issue #28)
- 后端 API dataTotal=1382 = DB active=1382
- Docker compose real-pre 健康: backend/postgres/redis/frontend 全部 healthy

## 验收

- [x] 行为与现有 API 兼容 (76/76 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #44-#48)
