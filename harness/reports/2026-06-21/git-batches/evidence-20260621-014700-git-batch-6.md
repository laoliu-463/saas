# GIT-BATCH-6 Evidence - User Domain Legacy Shells

- Time: 2026-06-21 01:47 CST
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Base commit: e770502551ec9418c918b2d70cf6eb33240c3245
- Candidate scope: 64 staged files
- Remote deploy: not requested, not executed

## Scope

- Migrated legacy user/org shells to user-domain application services:
  `OrgStructureService`, auth `SysDeptService`, auth `SysUserService`,
  and legacy `com.colonel.saas.service.SysDeptService`.
- Added user-domain application services for org structure, org unit
  directory/write, sys dept, sys user CRUD, group membership, role assignment,
  and assignable-user workflows.
- Added user-domain policies, ports, and infrastructure adapters required by
  the candidate dependency closure.
- Added focused boundary, parity, policy, and application-service tests.

## Evidence Chain

- code-review-graph first:
  - `list_graph_stats` succeeded earlier in this batch.
  - `semantic_search_nodes` for the target user-domain symbols returned no
    function matches, so source import tracing was used.
  - `get_minimal_context` timed out at 120s for this slice; this is recorded as
    tool limitation, not a code conclusion.
- Dependency closure:
  - Created temp worktree from base commit and applied only staged candidate
    patch.
  - Temp worktree: `C:\Users\CAOJIA~1\AppData\Local\Temp\saas-gitbatch6-20260621014008`
  - First clean validation exposed missing role-assignment application service.
  - Second clean validation exposed a test input bug (`List.of(null)`).
  - Final staged patch compiled and tested independently from the dirty main
    worktree.

## Tests

- Command:
  `mvn -f backend/pom.xml clean test -Dtest=OrgStructureServiceTest,SysDeptServiceBoundaryTest,SysUserServiceAssignableBoundaryTest,SysUserServiceTest,OrgStructureApplicationServiceTest,OrgUnitDirectoryApplicationServiceTest,OrgUnitWriteApplicationServiceTest,SysDeptApplicationServiceBoundaryTest,SysUserCRUDApplicationATest,SysUserCRUDApplicationBTest,SysUserGroupMembershipApplicationTest,SysUserRoleAssignmentApplicationServiceTest,UserAssignableApplicationServiceTest,DataScopePolicyParityTest,DataScopePolicyTest,OrgAssignmentPolicyTest,OrgEnrichmentPolicyTest,OrgValidationPolicyTest,UserAccessPolicyTest,UserAssignmentPolicyTest,UserChannelCodePolicyTest,SysDeptServiceTest`
- Location: temp candidate worktree
- Result: PASS
- Main/test compile: 809 main sources, 344 test sources
- Test result: 205 run, 0 failures, 0 errors, 0 skipped

## Build And Restart

- Package command: `mvn -f backend/pom.xml -DskipTests package`
- Package result: PASS
- JAR: temp worktree `backend/target/colonel-saas.jar`
- Safety check: `safety-check.ps1 -Env real-pre -Scope backend -DryRun` PASS
- Docker build:
  `docker build -t colonel-saas/backend:real-pre <temp>\backend`
- Image digest: `sha256:34f9aa7dd3f3d784b88ed52ff7446331794efff6ecbcdf5c2652c14e53a706c9`
- Restart command:
  `docker compose --env-file D:\Projects\SAAS\.env.real-pre -f D:\Projects\SAAS\docker-compose.real-pre.yml -p saas-active up -d --no-build backend-real-pre`
- Restart result: backend recreated; postgres and redis stayed running.

## Health And Business Verification

- Harness health: `verify-local.ps1 -Env real-pre -Scope backend` PASS
- Backend health response: `{"status":"UP"}`
- Docker status: `saas-active-backend-real-pre-1` healthy on `127.0.0.1:8081`
- Startup evidence: active profile `real-pre`; `app.test.enabled=false`;
  `douyin.test.enabled=false`.
- Business validation executed through user/org domain tests:
  - org structure assignment, enrichment, validation
  - org unit directory/write
  - sys dept legacy shell
  - sys user CRUD, group membership, role assignment
  - assignable-user and recruiter checks
  - data-scope and access-policy parity
- Authenticated real-pre UI/API E2E: NOT RUN. No stable test account/token was
  used in this batch, so it is not reported as PASS.

## Harness

- `harness/scripts/check-harness-limits.ps1`: PASS
- Evidence/retro directory after this batch remains under 10 files.

## Conclusion

PASS for candidate dependency closure, clean compile, focused tests, package,
local backend image rebuild, container restart, and health check.

Residual risk: authenticated real-pre E2E for user management screens was not
executed in this batch.
