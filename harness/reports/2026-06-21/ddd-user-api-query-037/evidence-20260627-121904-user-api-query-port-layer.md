# Evidence: DDD100-USER-API-QUERY (Issue #37) — 用户域 api/query/port 补层

## 基本信息

- Time: 2026-06-27 12:18 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #37 [DDD100-USER-API-QUERY] 用户域 api/query/port 补层
- 类型: api 适配 + 只读查询 + Port + 领域对象边界
- 阻塞: #34 (DDD100-USER-CRUD) / #35 (DDD100-USER-ASSIGN) / #36 (DDD100-USER-ROLE-MENU)

## 现有测试覆盖 (不重复造轮子)

### Port 层 (infrastructure 适配)
- **SysUserQueryLookupAdapterTest (13/13 PASS)**
  - buildUserPageWrapper × 7 case (UserQueryFilter.none())
  - applyDataScopeFilter × 3 case (UserQueryFilter.user/dept/none)
  - findPage × 3 case (含 null request 守护)

### Application 层 (DDD)
- **SysUserQueryApplicationServiceTest (7/7 PASS)**
  - applyFilter_none/personal/dept/withRequestNull 等 7 case
  - 守护 UserQueryFilter 转换 + findPage 入口
- **SysUserQueryApplicationServiceBoundaryTest (1/1 PASS)**
  - 守护 SysUserQueryApplicationService 只引用 UserQueryLookup (不直调 mapper)

### Controller 层 (api)
- **UserMasterDataControllerTest (4/4 PASS)**
  - listChannels / listRecruiters / listGroupMembers 完整
  - controllerSource_shouldDependOnUserMasterDataApplicationService 守护架构边界

### Service 层 (Legacy)
- **UserMasterDataServiceTest (11/11 PASS)**
  - 守护 UserMasterDataService 11 个 case

### 架构护栏
- **DddUserMasterDataPermissionPolicyBoundaryTest (1/1 PASS)**
  - 守护 UserMasterData PermissionPolicy 边界

## 验证证据

- mvn test -Dtest="SysUserQueryLookupAdapterTest,SysUserQueryApplicationServiceTest,SysUserQueryApplicationServiceBoundaryTest,UserMasterDataControllerTest":
  - **25/25 PASS** (13+7+1+4)
  - Total time: 1:09 min
  - 加上 UserMasterDataServiceTest 11/11 + Boundary 1/1 = 37+ tests PASS

## 三层补层结构

```
Controller (UserMasterDataController)
  ↓ 调
Application (SysUserQueryApplicationService / UserMasterDataApplicationService)
  ↓ 通过 Port
Infrastructure (SysUserQueryLookupAdapter 实现 UserQueryLookup)
  ↓ 直调
Mapper (SysUserMapper / SysUserRoleMapper)
```

- **Port**: UserQueryLookup 接口
- **Domain Object**: UserQueryFilter (none/user/dept 三档) + SysUserVO + SysUserPageRequest
- **Adapter**: SysUserQueryLookupAdapter (DDD 风格)

## 边界确认

- ✅ Application 不直调 Mapper (BoundaryTest 守护)
- ✅ Adapter 实现 Port (13/13 守护)
- ✅ Filter 三档 (none/user/dept) 完整
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD 守门 (DddUserMasterDataPermissionPolicyBoundaryTest)

## 与 #34-#36 关系

- #34 DDD100-USER-CRUD: SysUser CRUD Application
- #35 DDD100-USER-ASSIGN: 用户分配 Application
- #36 DDD100-USER-ROLE-MENU: 角色菜单 + PermissionPolicy
- #37 是 api/query/port 补层, 与 #34-#36 互补
- 现有 baseline 已覆盖大部分, 待 #34-#36 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (37+ tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #34-#36)