# Evidence: DDD100-ANALYTICS-SOURCE (Issue #56) — dashboard 指标来源与只读边界

## 基本信息

- Time: 2026-06-27 11:40:09 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #56 [DDD100-ANALYTICS-SOURCE] dashboard 指标来源与只读边界
- 类型: dashboard 指标来源冻结 (freeze source of truth)
- 阻塞: #50-#53 (performance 域, 由 Codex 在做, 我不撞)

## 验证

### #59 GUARD 守护
- DddAnalyticsReadOnlyBoundaryTest (2/2 PASS) 防止 dashboard service 写业务事实
- 覆盖 3 个分析模块 service: DashboardService / DataApplicationService / PerformanceMetricsQueryService

### dashboard 指标来源现状
- DashboardService 通过 DataScopePolicy 解析 admin/group/self 数据范围
- PerformanceMetricsQueryService 提供性能指标 query
- 两者由 Feature Flag 控制 (DddRefactorProperties.getDataScopePolicy, 默认 OFF)
- 数据来源: order_records / product_snapshot / performance_records (只读)

### 指标冻结
- 不重算归因: PerformanceCalculationService / PerformanceBackfillService 不在 analytics service 调用链
- 不重算业绩: 同上
- 不写业务事实: GUARD #59 守护

## 与 #50-#53 关系

#56 的"冻结来源"逻辑上独立于 performance implementation:
- #50-#53 实施 performance_records 生成/归属/冲正/汇总
- #56 只需要"performance_records 已存在, dashboard 从中只读"
- 当前 performance_records 已有数据 (#28 evidence 显示 1382 rows), dashboard 读取路径已通

## 验证证据

- mvn test -Dtest=DddAnalyticsReadOnlyBoundaryTest: 2/2 PASS
- 既有 DashboardServiceTest 23/23 + DashboardShadowCompareTest 9/9 = 32/32 PASS
- mvn test 全量: 2616/2616 PASS (昨日基准)

## 边界确认

- ✅ DashboardService 只读事实 + 汇总, 不重算归因
- ✅ DataApplicationService 只读事实 + 汇总
- ✅ PerformanceMetricsQueryService 只读
- ✅ 不调用 PerformanceCalculationService / BackfillService / OrderAttributionReplayService (GUARD #59 守护)
- ✅ 1:1 行为等价 (无业务规则变化)

## 后续

- #50-#53 (performance 域) 由 Codex 在做, 我不撞
- 后续 #59 GUARD 守门
- 后续 #58 E2E 验证已完成

## 验收

- [x] 行为与现有 API 兼容
- [x] 覆盖 parity / targeted / integration / E2E 验证路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (performance 域 Codex 在做)
