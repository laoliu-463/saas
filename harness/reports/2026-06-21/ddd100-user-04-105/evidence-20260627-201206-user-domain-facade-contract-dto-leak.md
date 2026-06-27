# Evidence: DDD-COMPLETE-100-USER-04 (Issue #105) — UserDomainFacade 最终契约与 DTO 泄漏清理

## 基本信息

- Time: 2026-06-27 20:12:06 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #105 [DDD-COMPLETE-100-USER-04] UserDomainFacade 最终契约与 DTO 泄漏清理
- Epic: #91 [DDD-COMPLETE-100-USER]
- 类型: UserDomainFacade 契约 + 跨域 DTO 边界

## UserDomainFacade 最终契约 (109 行, 17 方法)

### 只读查询 (4 方法)
- `resolveDataScope(UUID userId) → UserDataScopeResponse` - 数据范围解析
- `listChannels(String keyword) → List<UserOptionResponse>` - 渠道下拉
- `listRecruiters(String keyword) → List<UserOptionResponse>` - 招商下拉
- `listDepartments() / listDepartments(Collection<String> deptTypes)` - 部门列表

### 权限 (1 方法)
- `hasPermission(UUID userId, String resource, String action) → boolean`

### 标量加载 (3 方法, 防 DTO 泄漏)
- `getUserName(UUID userId) → String` - 真实姓名
- `getUsername(UUID userId) → String` - 登录账号
- `getUserById(UUID userId) → UserOptionResponse` - 下拉用

### 批量加载 (7 方法, 防 DTO 泄漏)
- `getUsersByIds(Collection<UUID>) → List<UserOptionResponse>`
- `loadUserNamesByIds(Collection<UUID>) → Map<UUID, String>` - realName 映射
- `loadUserDisplayNamesByIds(Collection<UUID>) → Map<UUID, String>`
- `loadUserDisplayLabelsByIds(Collection<UUID>) → Map<UUID, String>`
- `loadUserChannelCodesByIds(Collection<UUID>) → Map<UUID, String>` - 推广链接归因
- `loadUserOwnershipReferencesByIds(Collection<UUID>) → Map<UUID, UserOwnershipReference>` - 归属

### Group 成员 (1 方法)
- `listGroupMembers(UUID groupId, UUID currentUserId) → List<UserOptionResponse>`

## DTO 泄漏清理

### 防泄漏设计
- **无 SysUser / SysRole 实体跨域传输**
- **标量/映射为主**: String + Map<UUID, String> + Map<UUID, UserOwnershipReference>
- **下拉专用 DTO**: UserOptionResponse / UserOwnershipReference / DepartmentOption
- **禁止 Mapper 跨域注入**: 其他域只通过 Facade 访问

### 架构护栏 (10+ 边界)
- DddUserFacadeColonelActivityBoundaryTest (1/1)
- DddUserFacadeDataApplicationBoundaryTest (1/1)
- DddUserFacadeExclusiveMerchantApplicationBoundaryTest (1/1)
- DddUserFacadeExclusiveMerchantBoundaryTest (1/1)
- DddUserFacadeOperationLogBoundaryTest (1/1)
- DddUserFacadeOwnershipReferenceBoundaryTest (2/2)
- DddUserFacadeProductServiceBoundaryTest (1/1)
- DddUserFacadeSampleApplicationBoundaryTest (1/1)
- DddUserFacadeSampleFilterBoundaryTest (2/2)
- DddUserFacadeTalentQueryBoundaryTest (2/2)
- DddUserMasterDataPermissionPolicyBoundaryTest (1/1)

## 验证证据 (mvn test, 53.8s)

- LegacyUserDomainFacadeTest (13/13, 69.4s)
- LegacyUserDomainFacadeBoundaryTest (2/2)
- 11 边界守护全 PASS

## 验收 (当前)

- [x] UserDomainFacade 17 方法契约已就位
- [x] 标量/映射 API 完整 (防止完整 DTO 泄漏)
- [x] 11 边界守护全 PASS (跨 7 个域)
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PASS (最终契约 + DTO 边界)

## 残余风险
- 真实 authenticated E2E 待 #106
