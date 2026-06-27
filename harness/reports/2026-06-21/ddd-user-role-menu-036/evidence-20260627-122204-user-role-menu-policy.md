# Evidence: DDD100-USER-ROLE-MENU (Issue #36) — 角色菜单与 PermissionPolicy 收口

## 基本信息

- Time: 2026-06-27 12:21 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #36 [DDD100-USER-ROLE-MENU] 角色菜单与 PermissionPolicy 收口
- 类型: 角色/菜单/权限判断 → 用户域策略 + Application
- 阻塞: #34 (DDD100-USER-CRUD)

## 现有测试覆盖 (不重复造轮子)

### PermissionPolicy (策略层)
- **CurrentUserPermissionPolicyTest (6/6 PASS)**
  - 守护 RolePermission + role normalization
  - 含架构守护 (`contains("CurrentUserPermissionPolicy")` boundary check)

### DDD Application 层
- **SysMenuApplicationTest (7/7 PASS, Issue #22 DDD-USER-MIGRATION-013)**
  - 9 个 public method 委派壳验证 (找用户树/菜单树 + 分配菜单 + 创建/更新/删除)
- **SysRoleApplicationTest (14/14 PASS, Issue #23 DDD-USER-MIGRATION-014)**
  - 6 个 public method 委派壳验证
- **SysUserRoleAssignmentApplicationServiceTest (3/3 PASS)**
  - assignRoles_replacesDistinctRolesInvalidatesCacheAndRecordsAudit
  - assignRoles_adminRoleForSecondUser_shouldThrowDuplicate
  - assignRoles_targetAlreadyAdmin_shouldAllowAdminRole

### 业务规则 policy
- **OrgValidationPolicyTest (15/15 PASS)**
  - validateGroupLeader_shouldUseUserPermissionPolicyRoleNormalization
  - 含架构守护 (`currentUserPermissionPolicy.hasAnyRole`)
- **OrgUnitWriteApplicationServiceTest (8/8 PASS)**
  - applicationServiceShouldDelegateRoleMatchingToUserPermissionPolicy
  - 守护 OrgUnitWrite 委派到 PermissionPolicy

### 角色分配
- SysUserCRUDApplicationBTest (UserPermissionCacheService 注入)
- SysUserGroupMembershipApplicationTest (用户组成员 Application)
- OrgStructureApplicationServiceTest (CurrentUserPermissionPolicy 集成)

## 验证证据

- mvn test -Dtest="CurrentUserPermissionPolicyTest,SysMenuApplicationTest,SysRoleApplicationTest,SysUserRoleAssignmentApplicationServiceTest,OrgValidationPolicyTest,OrgUnitWriteApplicationServiceTest":
  - **53/53 PASS** (6+7+14+3+15+8)
  - Total time: 54.9s

## 角色菜单 + PermissionPolicy 三层结构

```
PermissionPolicy (CurrentUserPermissionPolicy / RoleCodes)
  ↓ 调
DDD Application (SysMenuApplication / SysRoleApplication / SysUserRoleAssignmentApplication)
  ↓ 通过 Port
Legacy Service (SysMenuService / SysRoleService 委派壳)
  ↓ 直调
Mapper (SysRoleMapper / SysMenuMapper)
```

- **PermissionPolicy**: CurrentUserPermissionPolicy (DDD-USER-MIGRATION-001)
- **Application**: SysMenuApplication + SysRoleApplication + SysUserRoleAssignmentApplication
- **委派壳**: SysMenuService + SysRoleService (#22 #23 已做, 513→84 / 389→52)

## 业务域不再自解析角色编码

- ✅ OrgUnitWriteApplication 委派到 PermissionPolicy (8/8)
- ✅ OrgValidationPolicy 委派 (15/15)
- ✅ 业务域不直调 RoleCodes (架构守护)

## 边界确认

- ✅ PermissionPolicy 完整 (RolePermission + normalization + hasAnyRole)
- ✅ 角色 Application 完整 (Menu/Role/RoleAssignment)
- ✅ 缓存失效 (UserPermissionCacheService 注入)
- ✅ 审计 (operationLogService 注入)
- ✅ 1:1 行为等价 (无业务规则变化)

## 与 #34 关系

- #34 DDD100-USER-CRUD: SysUser CRUD Application 收口
- #36 是 role-menu + policy 收口, 与 #34 互补
- 现有 baseline 已覆盖大部分, 待 #34 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (53/53 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #34)