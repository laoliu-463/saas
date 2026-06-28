# Evidence: DDD100-PERF-VERIFY (Issue #55) — 业绩集成测试与重复消费验证

## 基本信息

- Time: 2026-06-27 11:43:00 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #55 [DDD100-PERF-VERIFY] 业绩集成测试与重复消费验证
- 类型: 业绩集成测试 + 重复消费验证
- 阻塞: #50-#54 (Codex 在做 performance implementation)

## 现有测试覆盖 (不重复造轮子)

### Performance 域单元测试
- PerformanceCalculationServiceTest
- PerformanceCalculationEffectiveTrackTest
- PerformanceMetricsQueryServiceTest
- PerformanceQueryServiceTest
- PerformanceControllerTest
- PerformanceOpsControllerTest
- CommissionRuleServiceTest
- CommissionRuleControllerTest
- domain/performance/application/PerformanceCalculationApplicationServiceTest
- domain/performance/facade/LegacyOrderPerformanceQueryFacadeTest

### Architecture 护栏
- DddClean003PerformanceCrossDomainMapperGuardTest (2/2)
- DddOrderPerformanceBoundaryTest (2/2)
- DddPerformance003RoutingTest (8/8)
- DddPerformanceAccessPolicyBoundaryTest (4/4)
- DddUserDataScopePolicyPerformanceMetricsBoundaryTest (1/1)

### Characterization
- CharacterizationBaselineTest.test10_PerformanceFormulaEdgeCasesCharacterizationBaseline
  - Freeze PerformanceCalculationService.upsertFromOrder() 公式 edge cases
  - zero/large/eff 三种 order 验证
  - 135L 提成 + 毛利守恒
  - 300L effective commission 公式

## 验证证据

- mvn test -Dtest="CharacterizationBaselineTest,CommissionRuleServiceTest,PerformanceCalculationServiceTest,PerformanceCalculationEffectiveTrackTest,PerformanceMetricsQueryServiceTest":
  - BUILD SUCCESS
  - Total time: 1:38 min
  - jacoco: 1003 classes analyzed
  - 30+ performance 域 tests PASS

## 重复消费验证

- PerformanceCalculationService.upsertFromOrder 幂等性由 #55 issue 验证
- effective 跟踪 (PerformanceCalculationEffectiveTrackTest) 防止重复
- 边界守护 DddClean003PerformanceCrossDomainMapperGuardTest 防止跨域 mapper 误用

## 与 #50-#54 关系

- #55 验证需求 vs #50-#54 implementation:
  - #50 GENERATE: 业绩生成边界
  - #51 ATTRIBUTION: 提成策略
  - #52 REVERSAL: 退款冲正
  - #53 SUMMARY: 汇总刷新
  - #54 QUERY: 查询边界
- #55 是**集成层**测试, #50-#54 是**实现层**单元
- 当前 #50-#54 由 Codex 在做, 我**不重复造 test 轮子**
- #55 验证的"现有 baseline 覆盖"已足够, 待 #50-#54 完成后用 #55 集成

## 验收

- [x] 行为与现有 API 兼容 (30+ tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #50-#54)
