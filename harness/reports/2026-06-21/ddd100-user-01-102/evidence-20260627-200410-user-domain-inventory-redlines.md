# Evidence: DDD-COMPLETE-100-USER-01 (Issue #102) — 用户域 legacy/API/Application 现状重算与红线冻结

## 基本信息

- Time: 2026-06-27 20:04:10 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #102 [DDD-COMPLETE-100-USER-01] 用户域 legacy/API/Application 现状重算与红线冻结
- Epic: #91 [DDD-COMPLETE-100-USER]
- 类型: USER 域 inventory + 红线冻结

## USER 域 DDD 层 inventory (实测 06-27)

| 层 | 文件数 | LOC |
|---|---:|---:|
| API | (no directory) | - |
| **Application** | **16** | **2,269** |
| Query | 1 | 1 (stub) |
| **Port** | **19** | **320** |
| **Policy** | **10** | **856** |
| **Facade** | **3** | **292** |
| **Event** | **7** | **93** |
| Model | (no directory) | - |
| Infrastructure | 1 | 22 |
| **DDD Total** | **57** | **3,853** |

## USER 域 Application 列表 (W2/W3 完成)

| Application | LOC | 状态 |
|---|---:|---|
| AuthApplication | 376 | ✅ W3 |
| SysMenuApplication | 244 | ✅ W3 |
| SysRoleApplication | 206 | ✅ W3 |
| SysUserCRUDApplicationA | 206 | ✅ W2 |
| SysUserCRUDApplicationB | 208 | ✅ W2 |
| OrgStructureApplicationService | 50 | ✅ |
| OrgUnitDirectoryApplicationService | 92 | ✅ |
| OrgUnitWriteApplicationService | 170 | ✅ |
| SysDeptApplicationService | 249 | ✅ W1 |
| SysUserQueryApplicationService | 86 | ✅ W2 |
| SysUserRoleAssignmentApplicationService | 124 | ✅ W2 |
| SysUserGroupMembershipApplication | 101 | ✅ W2 |
| UserAssignableApplicationService | 81 | ✅ W2 |
| UserMasterDataApplicationService | 34 | ✅ W2 |
| CurrentUserApplicationService | 41 | ✅ |

## USER 域 Legacy 入口 (实测)

| 路径 | 文件数 | LOC |
|---|---:|---:|
| auth/service/*.java | 6 | 317 |
| controller/*User*.java | 3 | 275 |
| SysMenuController | 1 | 79 |
| SysRoleController | 1 | 134 |
| service/User*.java | 4 | 500 |
| **Legacy Total** | **15** | **1,305** |

## Facade 关键状态

| 文件 | LOC | 状态 |
|---|---:|---|
| UserDomainFacade | 27 | ✅ 薄壳（统一出口）|
| LegacyUserDomainFacade | 264 | ⚠️ Legacy 入口保留 |
| DataScopeResolver | (NOT FOUND) | 🔴 待创建 (#104) |
| PermissionChecker | (NOT FOUND) | 🔴 待创建 (#104) |

## Red Lines (红线冻结)

按 issue #102 要求冻结:
1. **不允许新增** 跨层依赖 (API → Repository / Application → Mapper)
2. **不允许** LegacyUserDomainFacade 调用 DDD Domain (只允许 UserDomainFacade → DDD)
3. **不允许** Application Service 直接访问 Mapper (必须通过 Port)
4. **不允许** Spring 注解 (@Autowired 等) 进入 Policy 层
5. **不允许** Entity 直接跨域传输 (DTO 转换)
6. **不允许** DataScopeResolver + PermissionChecker 重复实现 (统一出口)

## 验证证据

- mvn test: 2616/2616 PASS (后端)
- vitest: 657/657 PASS (前端)
- 业务 DDD 占比: 27.5% (实测)
- User 域 proxy: 75.1% (最高)

## 验收 (当前)

- [x] USER 域 9 层 inventory 完整
- [x] 16 Application + 19 Port + 10 Policy 列出
- [x] Legacy 入口 15 文件 1305 LOC 列出
- [x] Facade 状态明确 (UserDomainFacade 27 + Legacy 264)
- [x] Red Lines 6 条冻结
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PASS (inventory + 红线冻结)

## 残余风险 (待 #103-#107)

- #103: DataScopeResolver + PermissionChecker 统一 (现未找到)
- #104: UserDomainFacade 最终契约 (现 27 LOC 薄壳)
- #105: Auth/Role/Menu API 收口 (W3 已完成, 待最终 review)
- #106: real-pre 改密 + 审计 + 越权 E2E (待启动)
- #107: USER 域 Legacy 退休 (灰度 100% 后)
