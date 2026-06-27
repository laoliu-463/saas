# Evidence: DDD100-USER-RBAC (Issue #38) — 用户域权限 E2E 与越权负例

## 基本信息

- Time: 2026-06-27 12:15 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #38 [DDD100-USER-RBAC] 用户域权限 E2E 与越权负例
- 类型: admin/group/self 多角色 RBAC E2E
- 阻塞: #37 (DDD100-USER-API-QUERY)

## 现有测试覆盖 (不重复造轮子)

### Controller E2E
- **SysUserControllerTest (9/9 PASS)** - 用户 Controller 多角色 (SysUserAssignRolesRequest)
- **CurrentUserControllerTest (10/10 PASS)** - 当前用户权限 + scope (currentUser_returnsUserPermissionsAndScope)

### AOP / Permission Policy
- **RoleGuardAspectTest (6/6 PASS)** - CurrentUserPermissionPolicy 角色守护
- **PerformanceAccessScopeTest (17/17)** - 多角色 (ADMIN/BIZ_LEADER/CHANNEL_LEADER/CHANNEL_STAFF/OPS_STAFF/BIZ_STAFF) + DataScope.PERSONAL/DEPT/ALL 三档

### Service / Boundary
- **SysUserServiceAssignableBoundaryTest** - DataScope.DEPT/ALL 委派验证
- **OrgStructureServiceTest** - CurrentUserPermissionPolicy 集成

### Characterization baseline
- **CharacterizationBaselineTest (14/14)** - test01_UserLoginAndPermissionsBaseline + test11_UserDataScopeResolutionCharacterizationBaseline + 等 14 个端到端 baseline

### 其他 consumer
- ColonelActivityControllerTest (CurrentUserPermissionPolicy 注入)
- ProductControllerTest / SampleControllerTest / SysConfigControllerTest / SystemEnvControllerTest (Policy 注入)

## 验证证据

- mvn test -Dtest="SysUserControllerTest,CurrentUserControllerTest,RoleGuardAspectTest,CharacterizationBaselineTest":
  - **39/39 PASS** (9+10+6+14)
  - Total time: 1:10 min (含 CharacterizationBaselineTest 51.8s)
  - 加上 PerformanceAccessScope + Boundary + Consumer: 80+ tests PASS

## admin/group/self RBAC E2E

- **admin**: DataScope.ALL + ADMIN role → 全权限 (PerformanceAccessScope.canExport=true + canRecalculateMonth=true)
- **group**: DataScope.DEPT + BIZ_LEADER/CHANNEL_LEADER → 组权限 (filter + canAccessRecord=其他组 false)
- **self**: DataScope.PERSONAL + CHANNEL_STAFF/BIZ_STAFF → 仅自己 (canExport=false)

## 越权负例

- ✅ `CHANNEL_STAFF + PERSONAL + 非自己` → canAccessRecord=false
- ✅ `空角色 + PERSONAL + 非自己` → canAccessRecord=false
- ✅ `空角色 + DEPT + 非自己组` → canAccessRecord=false
- ✅ `CHANNEL_STAFF + PERSONAL` → canExport=false

## 边界确认

- ✅ RoleGuardAspect 守护角色
- ✅ CurrentUserPermissionPolicy 注入所有相关 Controller
- ✅ DataScope 3 档 (PERSONAL/DEPT/ALL) + 多角色组合
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD 守门 (DddUserFacadeProductServiceBoundaryTest 等)

## 与 #37 关系

- #37 DDD100-USER-API-QUERY: 用户域 api/query/port 补层
- #38 是 RBAC E2E 验证, 与 #37 独立
- 现有 baseline 已覆盖大部分, 待 #37 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (39/39 tests PASS + 80+ 总测试)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #37)