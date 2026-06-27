# Evidence: DDD100-ANALYTICS-GUARD (Issue #59) — 分析模块只读边界架构测试

## 基本信息

- Time: 2026-06-27 11:33:25 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #59 [DDD100-ANALYTICS-GUARD] 分析模块不重算归因架构测试
- 类型: 架构护栏 (architectural guard)
- 阻塞: #56 (DDD100-ANALYTICS-SOURCE) — 独立于 #56 实施, 可独立运行

## 现有架构测试 (不重复造轮子)

`backend/src/test/java/com/colonel/saas/architecture/DddAnalyticsReadOnlyBoundaryTest.java` (61 行, 已存在)

### 2 个 @Test 覆盖

#### 1. `analyticsSources_shouldNotWriteBusinessFacts`
- 防止 3 个分析模块 service 直接调用 insert/update/delete/deleteById/updateById/insertBatch/batchUpdate
- 覆盖文件:
  - `src/main/java/com/colonel/saas/service/DashboardService.java`
  - `src/main/java/com/colonel/saas/service/data/DataApplicationService.java`
  - `src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java`

#### 2. `analyticsSources_shouldNotInvokeBusinessCommandOrRecalculationServices`
- 防止分析模块调用 9 个 command-side 服务:
  - OrderSyncService
  - OrderAttributionReplayService
  - PerformanceCalculationService
  - PerformanceBackfillService
  - SampleApplicationService
  - SampleCommandService
  - SampleLifecycleService
  - ProductQuickSampleService
  - upsertFromOrder (command-side 方法)

## 验证证据

- mvn test -Dtest=DddAnalyticsReadOnlyBoundaryTest: **BUILD SUCCESS** (2/2 PASS)
- Total time: 8.4s
- jacoco: 1003 classes analyzed

## 边界确认

- ✅ DashboardService 保持只读
- ✅ DataApplicationService 保持只读
- ✅ PerformanceMetricsQueryService 保持只读
- ✅ 不调用任何 command-side 服务
- ✅ 不调用任何 recalculation 服务 (PerformanceCalculationService / PerformanceBackfillService)

## 与 DDD 政策一致

- 灰度默认 OFF: 分析模块从 DddRefactorProperties.getDataScopePolicy() 灰度开关读取
- 1:1 行为等价: 不改业务事实写入逻辑
- Spring 不入 policy 层: Analytics 不在 domain/analytics/policy/, 由 application/ 收口 (见 #56)

## 后续

- #56 DDD100-ANALYTICS-SOURCE: dashboard 指标来源与只读边界 (实现 query 层)
- #57 DDD100-ANALYTICS-DATA: DataApplication 查询、导出 query 层
- #58 DDD100-ANALYTICS-E2E: 看板 API/SQL 对账与 admin/group/self 差异
- #59 GUARD (本 issue) 守护 #56-#58 实施不破坏只读边界

## 验收

- [x] 行为与现有 API 兼容 (架构测试 PASS)
- [x] 覆盖本 slice 验证路径 (2/2 test)
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (后续 #56-#58 实施时 GUARD 守门)
