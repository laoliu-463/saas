# Evidence: DDD-COMPLETE-100-USER-03 (Issue #104) — DataScopeResolver 与 PermissionChecker 统一出口

## 基本信息

- Time: 2026-06-27 20:09:52 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #104 [DDD-COMPLETE-100-USER-03] DataScopeResolver 与 PermissionChecker 统一出口
- Epic: #91 [DDD-COMPLETE-100-USER]
- 类型: DataScope + Permission 统一出口

## 现有架构

### DataScope 体系
- **DataScopePolicy** (domain/user/policy/) - 策略核心
- **DataScopeAspect** (domain/user/infrastructure/aspect/) - AOP 切面
- **DataScopePolicyConfig** (config/) - 配置
- **DataScope enum** (common/enums/) - ALL/DEPT/PERSONAL 枚举
- **DataScope annotation** (domain/user/api/) - 注解

### Permission 体系
- **CurrentUserPermissionPolicy** (domain/user/policy/) - 当前用户权限
- **PermissionEventHasher** (domain/user/) - Hash 工具
- **PermissionCacheRefreshListener** (listener/) - Cache 刷新

## 验证证据 (mvn test, 12.9s)

### DataScope + Permission 核心
| 测试 | PASS |
|---|---|
| DataScopeAspectTest | 5/5 |
| DataScopePolicyTest | 21/21 |
| DataScopePolicyParityTest | 18/18 |
| CurrentUserPermissionPolicyTest | 6/6 |
| PermissionEventHasherTest | 2/2 |
| PermissionCacheRefreshListenerTest | 3/3 |
| **总计** | **55/55 PASS** |

### 跨域边界守护 (10+)
- DddUserAuthDataScopePolicyBoundaryTest (2/2)
- DddUserDataScopePolicyDashboardBoundaryTest (1/1)
- DddUserDataScopePolicyDataApplicationBoundaryTest (1/1)
- DddUserDataScopePolicyOrderAttributionBoundaryTest (1/1)
- DddUserDataScopePolicyPerformanceMetricsBoundaryTest (1/1)
- DddUserDataScopePolicySampleApplicationBoundaryTest (2/2)
- DddUserDataScopePolicySampleFilterOptionsBoundaryTest (1/1)
- DddUserDataScopeRemainingConsumerGuardTest (3/3)
- DddUserMasterDataPermissionPolicyBoundaryTest (1/1)
- DddUserPermissionPolicySamplePortBoundaryTest (3/3)

## 统一出口

### DataScope 出口
1. **DataScopePolicy.apply()** - 统一解析入口
2. **DataScopeAspect** - AOP 拦截 (避免散落实现)
3. **@DataScope annotation** - 声明式使用
4. **DddRefactorProperties.getDataScopePolicy().isEnabled()** - 灰度开关 (默认 OFF)

### Permission 出口
1. **CurrentUserPermissionPolicy** - 当前用户权限检查
2. **PermissionEventHasher** - Event hash (幂等键)
3. **PermissionCacheRefreshListener** - Cache 失效 (@TransactionalEventListener)

## 防止重复实现

- 架构护栏 (10+ boundary test) 防止其他模块重写 DataScope/Permission
- AOP 统一拦截 vs 散落 if/else
- 灰度开关确保灰度切换时 1:1 行为等价

## 验收 (当前)

- [x] DataScope 统一出口 (Policy + Aspect + Annotation)
- [x] Permission 统一出口 (CurrentUserPermissionPolicy + Hasher + Cache)
- [x] 跨域边界守护 10+ 全 PASS
- [x] 灰度开关默认 OFF (#25 evidence)
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PASS (统一出口完整)

## 残余风险
- 灰度切换流程待 #107
