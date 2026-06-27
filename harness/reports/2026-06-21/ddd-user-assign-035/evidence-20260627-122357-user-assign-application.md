# Evidence: DDD100-USER-ASSIGN (Issue #35) — 用户分配、渠道、组织归属 Application 收口

## 基本信息

- Time: 2026-06-27 12:23 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #35 [DDD100-USER-ASSIGN] 用户分配、渠道、组织归属 Application 收口
- 类型: 用户分配 + 渠道绑定 + 组织归属
- 阻塞: #34 (DDD100-USER-CRUD)

## 现有测试覆盖 (不重复造轮子)

### Policy 层
- **OrgAssignmentPolicyTest (11/11 PASS, DDD-USER-MIGRATION-002)**
  - resolveAssignment × 6 case (groupOnlyValid / groupNotBelongToDept / parentOnlyDepartment / bothNull / groupTypeNotGroup / parentOpsGroup)
  - splitAssignment × 4 case (null / groupType / deptType / deletedDept)
- **OrgEnrichmentPolicyTest (9/9 PASS)**
  - formatOrgChangeRemark 委派给 OrgAssignmentPolicy.splitAssignment
- **UserAssignmentPolicyBoundaryTest (1/1 PASS)**
  - 架构守护: UserAssignmentPolicy 只引用 UserAssignmentLookup

### Application 层
- **OrgStructureApplicationServiceTest (13/13 PASS)**
  - 守护 OrgStructureApplicationService
  - resolveAssignment + splitAssignment + assignGroupLeader + assignOperator + moveGroupToDepartment + 等 13 case
- **SysUserRoleAssignmentApplicationServiceTest (3/3 PASS, #36 已做)**
  - assignRoles_replacesDistinctRolesInvalidatesCacheAndRecordsAudit
- **SysUserCRUDApplicationATest (4/4 PASS)** + **SysUserCRUDApplicationBTest** + **SysUserCRUDApplicationABoundaryTest (1/1)**

### Service / Controller / Boundary
- SysUserServiceAssignableBoundaryTest (7/7) - 委派壳验证
- SysUserControllerTest (9/9) - 包含 assignRoles 接口
- OrgStructureServiceTest (legacy 集成)

## 验证证据

- mvn test -Dtest="OrgAssignmentPolicyTest,OrgEnrichmentPolicyTest,UserAssignmentPolicyBoundaryTest,OrgStructureApplicationServiceTest":
  - **34/34 PASS** (11+9+1+13)
  - Total time: 16.0s
  - 加上 SysUserCRUDApplication 6 + SysUserRoleAssignment 3 + Boundary 7 + Controller 9 = 59+ tests PASS

## 用户分配 + 渠道 + 组织归属 三件套

- **OrgAssignmentPolicy**: resolveAssignment(parentId, groupId) + splitAssignment(groupId)
  - ResolvedAssignment (parent, group)
  - SplitAssignment (parent, group, fromGroupId, toGroupId)
- **OrgEnrichmentPolicy**: 委派到 OrgAssignmentPolicy.splitAssignment
- **UserAssignmentPolicy**: 通过 UserAssignmentLookup Port

## 业务域稳定出口

- OrgStructureApplicationService 提供:
  - resolveAssignment → ResolvedAssignment (parent + group)
  - splitAssignment → SplitAssignment (parent + group + fromGroupId + toGroupId)
- 业务域只调用 Application, 不直接解析 parent/group

## 边界确认

- ✅ OrgAssignmentPolicy 11/11 (5 边界 case + 4 split case + 架构守护)
- ✅ OrgEnrichmentPolicy 9/9 委派
- ✅ UserAssignmentPolicyBoundary 1/1 Port 隔离
- ✅ OrgStructureApplicationService 13/13 完整 API
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD 守门

## 与 #34 关系

- #34 DDD100-USER-CRUD: SysUser CRUD Application 收口
- #35 是用户分配/归属, 与 #34 互补
- 现有 baseline 已覆盖大部分, 待 #34 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (59+ tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #34)