# Evidence Report

## Metadata

- Time: 2026-06-20 23:56:57 +08:00
- Environment: real-pre
- Scope: backend
- Slice: DDD-USER-MIGRATION-009 / SysUserGroupMembershipApplication live-path wiring
- Branch: feature/ddd/DDD-VERIFY-001
- Commit: bfb17058
- Worktree: dirty
- Deploy remote: false

## Change Summary

- Added `SysUserGroupMembershipApplication`.
- Moved `SysUserService.assignUsersToGroup` and `removeUsersFromGroup` implementation to the user-domain application service.
- Kept `SysUserService` public APIs and controller call sites unchanged.
- Added `SysUserGroupMembershipApplicationTest` for assign, remove, skip and missing-user paths.
- Fixed the blocking user-domain directory contract drift:
  - `OrgUnitDirectoryLookup` now has default `findActiveById` and `findChildren`.
  - `OrgUnitDirectoryApplicationService` now exposes `getById` and `findGroupsByParent`.

## Evidence

- PASS: `mvn -f backend/pom.xml -DskipTests clean package`.
- PASS: `mvn -f backend/pom.xml "-Dtest=OrgUnitDirectoryApplicationServiceTest,SysUserGroupMembershipApplicationTest,SysUserCRUDApplicationATest,SysUserCRUDApplicationBTest,SysUserServiceTest,SysUserControllerTest" "-DfailIfNoTests=false" test`.
- Test result: 47 tests, 0 failures, 0 errors, 0 skipped.
- PASS: backend container rebuilt and restarted with `harness/scripts/commands/restart-compose.ps1 -Env real-pre -Scope backend`.
- PASS: local backend health with `verify-local.ps1 -Env real-pre -Scope backend`.
- Health response: `{"status":"UP"}`.
- PASS: stale Maven/Surefire processes were identified and cleaned before the final clean build.

## Tooling Notes

- `code-review-graph get_minimal_context` timed out after 300s.
- `code-review-graph detect_changes` timed out after 300s.
- Because graph output was unavailable, local source reads and Maven/Docker evidence were used for this slice.
- Full `agent-do.ps1` was not executed because the worktree contains broad pre-existing uncommitted DDD changes and the harness path may invoke git push flow.

## Runtime State

- Docker: `backend-real-pre` rebuilt and healthy on `127.0.0.1:8081`.
- Business validation: covered by backend unit/controller tests for user-domain CRUD, group membership, user service and user controller paths.
- Frontend build: not executed; this slice did not modify frontend code.
- Authenticated real-pre E2E: not executed.
- Commit/push: not executed due broad dirty worktree and no user request to commit.

## Retro Summary

- No Harness rule or script upgrade is required from this slice.
- Repeated Maven/Surefire process residue can corrupt `target/classes`; before future clean builds, check for active Maven Java processes if test output looks inconsistent.
- Next user-domain slice should be `SysUserRoleAssignmentApplication` for `assignRoles`, `validateRoleIds` and `replaceUserRoles`.

## Conclusion

PARTIAL

## Residual Risk

- The local service is healthy, but no authenticated UI/API E2E verified group member mutation against real-pre data.
- GitHub issue state was not synchronized from this session.
- The worktree remains dirty with many pre-existing DDD changes outside this slice.
