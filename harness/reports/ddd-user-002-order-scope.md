# DDD-USER-002 Evidence Report

## 元信息

| 项 | 值 |
| --- | --- |
| 时间 | 2026-06-10 10:40 CST |
| 环境 | real-pre（本地 Docker） |
| 分支 | feature/auth-system |
| 任务 | DDD-USER-002: 订单域数据范围改走 UserDomainFacade |

## 变更摘要

### 目标
订单域不再直接注入 SysUserMapper 获取人员数据，改为通过 UserDomainFacade。

### 改动文件

**生产代码：**
- `UserDomainFacade.java` — 新增 `getUserName(UUID)` 和 `loadUserNamesByIds(Collection<UUID>)` 方法
- `LegacyUserDomainFacade.java` — 实现新增方法，委派 SysUserMapper（仍为用户域内部）
- `OrderSyncPersistenceService.java` — 移除 SysUserMapper 注入，改为 UserDomainFacade；`getUser()` → `getUserName()`，`loadUsersByIds()` → `loadUserNamesByIds()`
- `OrderSyncService.java` — 调用方适配：`Map<UUID, SysUser>` → `Map<UUID, String>`，移除 SysUser import
- `OrderAttributionReplayService.java` — `resolveUserName()` 委派 `persistenceService.getUserName()`，移除 SysUser import

**测试代码：**
- `OrderSyncPersistenceServiceTest.java` — 构造函数参数 SysUserMapper → UserDomainFacade
- `OrderSyncServiceTest.java` — mock `loadUserNamesByIds` 替代 `loadUsersByIds`
- `OrderAttributionReplayServiceTest.java` — mock `getUserName` 替代 `getUser`
- `MapperDomainRegistry.java` — 新增 `.domain.user.` 包识别为 USER 域
- `cross-domain-mapper-legacy-whitelist.txt` — 移除 OrderSyncPersistenceService|SysUserMapper 条目

**未变更**：任何 Controller API 路径、响应结构、数据库表。

## 构建与测试

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| `mvn compile` | PASS | BUILD SUCCESS |
| `mvn test-compile` | PASS | BUILD SUCCESS |
| `OrderSyncServiceTest` | PASS | 36 tests, 0 failures |
| `OrderAttributionReplayServiceTest` | PASS | 3 tests, 0 failures |
| `OrderSyncPersistenceServiceTest` | PASS | 9 tests, 0 failures |
| `DddCrossDomainMapperGuardTest` | PASS | 3 tests, 0 failures, 1 skipped |
| `DddRefactorPropertiesTest` | PASS | 2 tests, 0 failures |
| 全量非集成测试 | PARTIAL | 1774 tests, 0 failures, 29 errors（均为 Testcontainers Docker 不可用导致） |

## 跨域注入变化

| 变化 | Before | After |
| --- | --- | --- |
| OrderSyncPersistenceService → SysUserMapper | 直接注入 | **已移除**，改走 UserDomainFacade |
| LegacyUserDomainFacade → SysUserMapper | 不存在 | 新增（用户域内部，不算跨域） |

## 权限行为验证

数据范围过滤逻辑（applyDataScope/applyQueryDataScope）未改动，保持与重构前完全一致。
本次仅替换了用户名称查询的数据源路径，不影响 admin/all、recruiter/self、leader/group 的过滤结果。

## Docker / 健康检查

未重启容器（本次改动不涉及运行中 backend 的行为；开关默认 false，线上无变化）。

## 部署远端

否。

## 结论

**PASS**

- OrderSyncPersistenceService 不再直接注入 SysUserMapper。
- 所有订单域用户名称查询改走 UserDomainFacade。
- 接口响应、数据范围过滤结果、金额公式均未变化。
- 跨域守卫测试通过，白名单已更新。
- 全量测试 0 failures，29 errors 均为 Docker 环境不可用（预存问题）。

## 剩余风险

1. OrderController/OrderService 中的 `applyDataScope` 内联逻辑暂保留，后续任务（DDD-USER-002 扩展）可考虑统一到 OrderDataScopePolicy。
2. OrderQueryService 仍通过 SQL JOIN sys_user 表获取用户信息（不走 Mapper），属于数据查询而非权限依赖，后续可迁移到 Facade。

## 下一步

执行 **DDD-USER-003**（寄样域数据范围改走 UserDomainFacade）。
