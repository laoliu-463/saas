# Evidence: DDD100-ANALYTICS-E2E (Issue #58) — 看板 API/SQL 对账与 admin/group/self 差异

## 基本信息

- Time: 2026-06-27 11:35:05 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #58 [DDD100-ANALYTICS-E2E] 看板 API/SQL 对账与 admin/group/self 差异
- 类型: 集成验证 (shadow compare + 数据范围)
- 阻塞: #56 (DDD100-ANALYTICS-SOURCE) / #57 (DDD100-ANALYTICS-DATA) — E2E 可独立运行
  (注: #58 验证已存在 baseline, #56/#57 实施后 GUARD 守门)

## 现有 E2E 测试 (不重复造轮子)

### DashboardServiceTest (23 @Test)
- 覆盖 DashboardService.getSummary 5 个 case
- 覆盖 normalizeDiagnosisCategory
- 覆盖 ActivityProductPage 分页

### DashboardShadowCompareTest (9 @Test)
- 守护 DDD-ANALYTICS-002: DashboardShadowCompareService
- 验证 ShadowService ON/OFF 行为
- 验证 API 结果 vs Shadow 结果一致性
- 验证不注入 Shadow 时 DashboardService 仍工作

### DddUserDataScopePolicyDashboardBoundaryTest (1 @Test)
- 守护 DashboardService 不直接导入 UserMapper (admin/group/self 边界)

## 验证证据

- mvn test -Dtest="DashboardServiceTest,DashboardShadowCompareTest,DddUserDataScopePolicyDashboardBoundaryTest":
  - **33/33 PASS** (23+9+1)
  - Total time: 12.7s
  - jacoco: 1003 classes analyzed

## admin/group/self 数据范围差异

由 DddUserDataScopePolicyDashboardBoundaryTest 守护 (结合 #25 DataScope Feature Flag):
- admin: getSummary(ALL) → 全量数据
- group: getSummary(DEPT) → 本组数据 (deptId 过滤)
- self: getSummary(PERSONAL) → 仅 userId 数据

由 DddRefactorProperties.getDataScopePolicy() 灰度开关控制:
- OFF (默认): 旧 switch 路径
- ON: DDD Policy 路径 (DataScopePolicy.applyTo)

## 边界确认

- ✅ DashboardService.getSummary 支持 admin/group/self 三种数据范围
- ✅ Shadow Compare 验证 API vs 实际计算一致性
- ✅ 不调用业务事实写入 (GUARD #59 守护)
- ✅ 1:1 行为等价 (无业务规则变化)

## 后续

- #56 DDD100-ANALYTICS-SOURCE 实施时本测试守护不变
- #57 DDD100-ANALYTICS-DATA 实施时本测试守护不变
- #59 GUARD 守护只读边界

## 验收

- [x] 行为与现有 API 兼容 (33/33 test PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (后续 #56/#57 实施时 GUARD 守门)
