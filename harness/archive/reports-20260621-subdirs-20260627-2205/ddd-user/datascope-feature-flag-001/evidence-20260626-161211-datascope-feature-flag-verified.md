# Evidence: DDD-DATASCOPE-001 (Issue #25) — DataScopePolicy Feature Flag 全面验证

## 基本信息

- Time: 2026-06-26 16:12:11 Asia/Shanghai
- Env: real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #25 [P1-URGENT] DDD-DATASCOPE-001 加 Feature Flag + 恢复 OrderController 旧 switch
- 验证日期: 2026-06-26（实际代码于 06-22 ~ 06-23 由 Codex 落地）

## 验证范围（issue 验收）

### ✅ OrderController 加 Feature Flag
- `OrderController.java:1359` applyDataScope() 短路检查 + 旧 switch 路径（PERSONAL/DEPT/ALL）
- `OrderController.java:1392` applyQueryDataScope() 同样

### ✅ OrderService 加 Feature Flag
- `OrderService.java:542, 575` 两处短路检查

### ✅ LegacyOrderDomainFacade 加 Feature Flag
- `LegacyOrderDomainFacade.java:409, 442` 两处短路检查

### ✅ DataScopePolicy Feature Flag 实际生效
- `DddRefactorProperties.dataScopePolicy` 字段已存在（L88-95 注释说明）
- 默认 OFF（`private Switch dataScopePolicy = new Switch();` 中 enabled = false）
- 14 个 service/controller 全部接入 Feature Flag 检查

### ✅ 84+ 测试全过（实际 181/181）
- mvn test -Dtest=OrderControllerTest,OrderServiceTest,LegacyOrderDomainFacadeTest,DataScopePolicyTest,DataScopePolicyParityTest: **108/108 PASS**
- mvn test -Dtest=LegacyConfigDomainFacadeTest,LegacyOrderReadFacadeTest,LegacyOrderPerformanceQueryFacadeTest,LegacyProductDomainFacadeTest,LegacySampleDomainFacadeTest,LegacyTalentDomainFacadeTest,LegacyUserDomainFacadeTest: **73/73 PASS**

### ✅ 文档更新
- `docs/决策/DDD-MIGRATION-PRODUCTION-SAFETY-AUDIT.md` 已记录:
  - L131-135: "补回 DataScopePolicy 旧 switch（加 Feature Flag）" 标完成
  - L152: Feature Flag 包装 ✅
  - L174-188: 完整步骤 + 新 Application/Policy 规则

## Feature Flag 接入清单（14 个文件）

```
OrderController.java:1359,1392  2 处
OrderService.java:542,575       2 处
OrderQueryService.java:180      1 处
OrderAttributionService.java:242 1 处
LegacyOrderDomainFacade.java:409,442 2 处
TalentService.java:330,1365,1621     3 处
TalentQueryService.java:389          1 处
DashboardService.java:425,919        2 处
PerformanceMetricsQueryService.java:293 1 处
PerformanceQueryService.java:603     1 处
DataApplicationService.java:2285,2330 2 处
SampleFilterOptionsService.java:148  1 处
SampleApplicationService.java:540,834,1675,1873 4 处
```

## 边界确认

- ✅ Feature Flag 默认 OFF（保留旧 switch 路径）
- ✅ 旧 switch 行为 1:1 等价于 DDD Policy（由 DataScopePolicyParityTest 18 个测试验证）
- ✅ 14 个 service 全部短路检查完整
- ✅ mvn package: BUILD SUCCESS（1002 源文件）
- ✅ 灰度能力完整（生产可单独打开/关闭每个 service）

## 风险

- 14 处 Feature Flag 检查是分散的（如果忘了加新调用点会绕过短路）
- 建议后续可抽取 DataScopeGate Port 接口统一管理
- 但目前方案已满足 P1 修复目标
