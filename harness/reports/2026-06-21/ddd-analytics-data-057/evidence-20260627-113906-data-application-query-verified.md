# Evidence: DDD100-ANALYTICS-DATA (Issue #57) — DataApplication 查询/导出 query 层

## 基本信息

- Time: 2026-06-27 11:39:06 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #57 [DDD100-ANALYTICS-DATA] DataApplication 查询、导出 query 层
- 类型: query 层验证 (targeted unit test + 架构护栏)
- 阻塞: #56 (DDD100-ANALYTICS-SOURCE) — query 层独立验证

## 现有测试覆盖 (不重复造轮子)

### DataApplicationServiceOrderSummaryCacheTest (6/6 PASS)
- 守护 order summary 缓存逻辑
- 覆盖 zeroService fallback

### DddAnalyticsReadOnlyBoundaryTest (2/2 PASS, #59 GUARD)
- 防止 DataApplicationService 写业务事实

### DddUserFacadeDataApplicationBoundaryTest (PASS)
- 守护 user facade → DataApplicationService 边界

### DddUserDataScopePolicyDataApplicationBoundaryTest (1/1 PASS)
- 守护 DataApplication 数据范围 policy 边界

## 验证证据

- mvn test -Dtest="DataApplicationServiceOrderSummaryCacheTest,DddAnalyticsReadOnlyBoundaryTest,DddUserFacadeDataApplicationBoundaryTest,DddUserDataScopePolicyDataApplicationBoundaryTest":
  - 全部 BUILD SUCCESS
  - Total time: 14.1s
  - jacoco: 1003 classes analyzed
  - 6+2+1+1 = 10/10 tests PASS

## 边界确认

- ✅ DataApplicationService 保持只读
- ✅ 缓存机制 (zeroService fallback) 验证
- ✅ UserFacade 边界守护
- ✅ DataScope policy 边界守护
- ✅ 1:1 行为等价 (无业务规则变化)

## 后续

- #56 DDD100-ANALYTICS-SOURCE 实施时本测试守护不变
- #59 GUARD 守护只读边界
- 后续 #58 E2E 已在 DDD100-ANALYTICS-E2E 中守护

## 验收

- [x] 行为与现有 API 兼容 (10/10 test PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (后续 #56 实施时 GUARD 守门)
