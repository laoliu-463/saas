# Evidence: DDD-COMPLETE-100-USER-06 (Issue #107) — 用户域 legacy service 退休与迁移率目标达成

## 基本信息

- Time: 2026-06-27 20:16:41 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #107 [DDD-COMPLETE-100-USER-06] 用户域 legacy service 退休与迁移率目标达成
- Epic: #91 [DDD-COMPLETE-100-USER]
- 类型: USER 域 Legacy 退休
- 类型 (按 SAAS DDD 政策): **BLOCKED** (灰度默认 OFF + Legacy 保留)

## USER 域 DDD 现状 (实测 06-27)

- **DDD domain LOC**: 5,078 (user 域, 全部 domain/*/user/*)
- **Legacy entry LOC**: 1,686 (user 域相关)
- **Business migration proxy**: **75.1%** (user 域, 所有域中最高)

## USER 域 Legacy service 现状

### auth/service (6 文件)
| 文件 | Size | 状态 |
|---|---:|---|
| AuthService | 1,371 B | 薄壳 (W3 已委派 AuthApplication) |
| OrgStructureService | 4,561 B | 薄壳 (W2 已委派 OrgStructure/OrgUnit Application) |
| SysDeptService | 7,088 B | 薄壳 (W1 已委派 SysDeptApplication) |
| SysMenuService | 2,280 B | 薄壳 (W3 已委派 SysMenuApplication) |
| SysRoleService | 1,774 B | 薄壳 (W3 已委派 SysRoleApplication) |
| SysUserService | 21,703 B | 薄壳 (W2 已委派 SysUserCRUDApplicationA/B) |

### service/User* (4 文件)
- UserDomainService (12,990 B) - 委派 UserDomainFacade
- UserMasterDataService (14,826 B) - 委派 UserMasterDataApplicationService
- UserPermissionCacheService (3,504 B) - 薄壳
- UserDomainEventPublisher (6,690 B) - 委派 DDD EventPublisher

**全部 USER 域 Legacy service 已降级为薄壳委派壳** (W1-W3 完成)。

## BLOCKED 按 SAAS DDD 政策

按 SAAS DDD 政策铁律:
1. **Legacy 保留不动** - 禁止删除任何 Legacy service
2. **灰度开关默认 OFF** - 18 Feature Flag 全 OFF
3. **1:1 行为等价** - Legacy 删除前必须灰度 100% + N 周回归证据

**当前条件不满足删除**:
- 18 Feature Flag 全 OFF
- 业务 DDD 整体 27.5% (未达 50% V3 目标)
- 用户域 75.1% (虽高, 但其他域未达)
- 灰度切换流程未启动

## 已完成的 DDD 收口

- ✅ 全部 USER 域 service 已降为薄壳 (6 文件 + 4 文件)
- ✅ Application 层 16 文件 2269 LOC (#102 inventory)
- ✅ Port 19 文件 320 LOC
- ✅ Policy 10 文件 856 LOC
- ✅ Facade 3 文件 292 LOC (UserDomainFacade 17 方法契约, #105)
- ✅ DataScope + Permission 统一出口 (#104)
- ✅ Auth/Role/Menu API → Application 委派完整 (#103)
- ✅ real-pre 改密/审计/越权 集成测试 (#106)

## 验收 (当前)

- [x] USER 域 Legacy service 全部降为薄壳
- [x] 75.1% DDD 占比 (最高域)
- [x] 6 个 W1-W3 切片完成
- [x] 1:1 行为等价 (无业务规则变化)
- [ ] Legacy 删除 BLOCKED (按 DDD 政策 + 灰度前置条件)

## 残余风险 (V4 sprint 计划)

### 灰度切换前置条件 (必须满足才能删 Legacy)
- 业务 DDD 整体 ≥ 50% (当前 27.5%, 需 ~16,800 LOC 迁移)
- 灰度 100% 持续 N 周 (回归证据)
- 0 业务口径变更
- LegacyUserDomainFacade 100% 不被生产路径调用

### V4 sprint 行动
- 逐域打开 flag (user → config → product → talent → sample → order → performance)
- real-pre 回归监控
- 灰度 100% 后逐个删除 LegacyService

## 结论

按 SAAS DDD 政策, **Legacy 删除严格 BLOCKED**, 但 USER 域已完成:
- 75.1% DDD 占比 (所有域最高)
- 全部 service 已降为薄壳
- 完整 DDD 分层 (16 Application + 19 Port + 10 Policy + 3 Facade)

User 域 DDD 收口 **已完成**, 删除 Legacy service 需 V4 sprint 灰度切换流程。
