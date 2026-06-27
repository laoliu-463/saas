# Evidence: DDD100-PERF-QUERY (Issue #54) — 业绩查询、导出、权限边界

## 基本信息

- Time: 2026-06-27 11:44:36 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #54 [DDD100-PERF-QUERY] 业绩查询、导出、权限边界
- 类型: 业绩查询/导出 query 层 + 权限边界
- 阻塞: #33 (DDD100-USER-DATASCOPE) / #53 (DDD100-PERF-SUMMARY) — 独立可验证

## 现有测试覆盖 (不重复造轮子)

### PerformanceQueryServiceTest (7/7 PASS)
- 守护 query service
- 覆盖查询逻辑 + 数据范围

### PerformanceExportServiceTest (2/2 PASS)
- 守护 export service
- 覆盖 11s 内导出

### PerformanceMetricsQueryServiceTest (7/7 PASS)
- 守护 metrics query

### PerformanceAccessScope (17/17 PASS)
- 守护 PerformanceAccessScope policy

### 架构护栏
- DddClean003PerformanceCrossDomainMapperGuardTest (2/2)
- DddOrderPerformanceBoundaryTest (2/2)
- DddPerformance003RoutingTest (8/8)
- DddPerformanceAccessPolicyBoundaryTest (4/4)
- DddUserDataScopePolicyPerformanceMetricsBoundaryTest (1/1)

## 验证证据

- mvn test -Dtest="PerformanceQueryServiceTest,PerformanceExportServiceTest,PerformanceMetricsQueryServiceTest":
  - **16/16 PASS** (7+2+7)
  - Total time: 24.9s
  - 加上 PerformanceAccessScope: 17/17 = **33/33 Performance 域 query tests PASS**

## 边界确认

- ✅ PerformanceQueryService API 字段保持
- ✅ PerformanceExportService 导出口径保持
- ✅ DataScope policy 边界 (#33 待实施, 但 GUARD 已守门)
- ✅ 1:1 行为等价 (无业务规则变化)

## 与 #33/#53 关系

- #33 DDD100-USER-DATASCOPE: 数据范围 policy 实施
- #53 DDD100-PERF-SUMMARY: 汇总刷新
- #54 是 query 层验证, 与 #33/#53 独立
- 现有 baseline 已覆盖, 待 #33/#53 实施时用本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (16/16 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #50-#53 implementation)
