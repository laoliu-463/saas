# Evidence - DDD100 User Assign Issue #35

## Metadata

| Field | Value |
|---|---|
| Date | 2026-06-27 |
| Env | local real-pre |
| Branch | `feature/ddd/DDD-VERIFY-001` |
| Report Base Commit | `9bde7fdd` before #35 commit |
| Worktree Status | DIRTY only with planned #35 files at report generation |
| Scope | backend user-domain assignment/org membership |
| Issue | #35 `[DDD100-USER-ASSIGN] 用户分配、渠道、组织归属 Application 收口` |

## Changed Files

- `backend/src/main/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplication.java`
- `backend/src/main/java/com/colonel/saas/domain/user/port/UserGroupMembershipStore.java`
- `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserGroupMembershipStoreAdapter.java`
- `backend/src/main/java/com/colonel/saas/mapper/SysUserMapper.java`
- `backend/src/test/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplicationTest.java`
- `backend/src/test/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplicationBoundaryTest.java`
- `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysUserGroupMembershipStoreAdapterTest.java`
- `backend/src/test/java/com/colonel/saas/auth/service/SysUserServiceAssignableBoundaryTest.java`
- `backend/src/test/java/com/colonel/saas/mapper/SysUserMapperTest.java`
- Harness status, changelog, issue index, evidence and retro files.

## Evidence Chain

- Phenomenon: #35 requires user assignment, channel binding, and organization ownership parsing to be closed into stable user-domain Application boundaries.
- Evidence: `UserAssignableApplicationService`, `UserChannelCodePolicy`, and `UserDomainFacade.loadUserOwnershipReferencesByIds` already had targeted coverage. The remaining gap was `SysUserGroupMembershipApplication` directly importing `SysUserMapper` / `SysUser`.
- Change: introduced `UserGroupMembershipStore` and `SysUserGroupMembershipStoreAdapter`, moved mapper/entity access into infrastructure, and kept `SysUserService.assignUsersToGroup/removeUsersFromGroup` as compatibility delegators.
- Null safety: added explicit `SysUserMapper.updateDeptById(id, deptId)` SQL so removing a group member can clear `dept_id` without relying on MyBatis null field strategy.

## Verification

| Check | Result | Evidence |
|---|---|---|
| code-review-graph | SKIP | MCP tool unavailable in this continuation; fallback used `rg` and direct file inspection |
| Targeted tests | PASS | `SysUserGroupMembershipApplicationTest`, boundary test, adapter test, `SysUserServiceAssignableBoundaryTest`, `UserAssignableApplicationServiceTest`, `UserChannelCodePolicyTest`, `LegacyUserDomainFacadeTest` |
| DB integration | PASS | `SysUserMapperTest` verifies `updateDeptById` updates and clears `dept_id` |
| Backend compile | PASS | `mvn -q -f backend/pom.xml -DskipTests compile` |
| real-pre safety check | PASS | `safety-check.ps1 -Env real-pre -Scope backend` |
| Backend health | PASS | `verify-local.ps1 -Env real-pre -Scope backend`, health returned `{"status":"UP"}` |
| Docker status | PASS | backend/frontend/postgres/redis all `Up` and `healthy` |
| Docker restart | SKIP | no runtime container artifact changed before commit; compile and health verified |
| Business E2E | SKIP | no live user/group mutation executed against real-pre accounts |

## Result

Status: PASS for #35 backend boundary slice.

## Remaining Risks

- User-domain Auth/Role/Menu application classes still have legacy Mapper/Entity imports; they are outside #35 and remain for #36/#37.
- Live real-pre group membership mutation E2E still needs a disposable account/window before executing writes.
- `SysUserController` / `SysDeptController` remain compatibility controllers; direct user-domain API/query layer split is later scope.
