# Evidence: DDD100-PERF-SUMMARY (Issue #53) — 汇总刷新与业绩事件

## 基本信息

- Time: 2026-06-27 11:45:40 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #53 [DDD100-PERF-SUMMARY] 汇总刷新与业绩事件
- 类型: 业绩汇总刷新入口 + 业绩事件生产条件
- 阻塞: #50 GENERATE / #51 ATTRIBUTION / #52 REVERSAL (Codex 在做)

## 现有测试覆盖

### PerformanceSummaryServiceTest (9/9 PASS)
- 守护 PerformanceSummaryService 汇总入口
- 覆盖 query 场景 + 多 group 分组

### PerformanceBackfillServiceTest (3/3 PASS)
- 守护 PerformanceBackfillService
- 覆盖 backfill / reconcileInvalidatedPerformance

### DashboardPerformanceSummaryServiceTest (2/2 PASS)
- 守护 DashboardPerformanceSummaryService
- 覆盖 dashboard 汇总 + JDBC template

## 验证证据

- mvn test -Dtest="PerformanceSummaryServiceTest,PerformanceBackfillServiceTest,DashboardPerformanceSummaryServiceTest":
  - **14/14 PASS** (9+3+2)
  - Total time: 12.2s
  - jacoco: 1003 classes analyzed

## 边界确认

- ✅ PerformanceSummaryService 汇总入口保持
- ✅ PerformanceBackfillService 异步/重算入口保持
- ✅ DashboardPerformanceSummaryService JDBC 写入保持
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD #59 守护只读边界

## 与 #50-#52 关系

- #50 GENERATE: 业绩生成边界 (Codex)
- #51 ATTRIBUTION: 提成策略 (Codex)
- #52 REVERSAL: 退款冲正 (Codex)
- #53 是汇总层验证, #50-#52 是实现层
- 现有 baseline 已覆盖, 待 #50-#52 完成后用本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (14/14 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #50-#52)
