# Evidence 2026-06-21 11:30 - GIT-BATCH-8

## Scope

- Env: `real-pre`
- Branch: `feature/ddd/DDD-VERIFY-001`
- Base before commit: `b56ce343`
- Change scope: user-domain DDD boundary batch
  - Move legacy `SysUserService.findPage/findDeptMembers` query logic to `SysUserQueryApplicationService`.
  - Route `SysUserService` query endpoints through the user-domain application service.
  - Move assignable-user lookup behind `UserAssignableCandidateLookup` / `UserAssignmentLookup` ports.
  - Remove Spring annotations and utility imports from `domain/user/policy`; wire policies from `DomainPolicyConfig`.

## Static Checks

- `git diff --cached --check`: PASS
- `rg "@(Component|Service|Repository)|import org\\.springframework" backend/src/main/java/com/colonel/saas/domain/user/policy`: no matches
- `code-review-graph`: minimal context timed out once; later semantic search returned no matching policy/query nodes, so source inspection was used.

## Tests

- Targeted test command:
  - `mvn -f backend/pom.xml "-Dtest=DddPolicyLayerNoSpringDependencyTest,SysUserQueryApplicationServiceTest,SysUserServiceAssignableBoundaryTest,SysUserCRUDApplicationATest,UserAssignableApplicationServiceTest,UserAssignmentPolicyTest,UserAssignmentPolicyBoundaryTest,CurrentUserPermissionPolicyTest,OrgAssignmentPolicyTest,OrgEnrichmentPolicyTest,OrgValidationPolicyTest,UserAccessPolicyTest,UserChannelCodePolicyTest,UserCredentialPolicyTest,ColonelSaasApplicationTests" test`
  - Result: PASS, 103 tests, 0 failures, 0 errors, 0 skipped.
- Full backend test command:
  - `mvn -f backend/pom.xml clean test`
  - Result: PASS, 2430 tests, 0 failures, 0 errors, 3 skipped.
- Package command:
  - `mvn -f backend/pom.xml -DskipTests "-Djacoco.skip=true" package`
  - Result: PASS.

## Runtime

- Docker build source: clean temp worktree with staged patch applied.
- Docker build:
  - `docker build -t colonel-saas/backend:real-pre ...\saas-user-boundary-stage-20260621111657\backend`
  - Result: PASS
  - Image: `sha256:8d7bbc737bae55c29c41f07fe7415173edce01f7865ee3b53bdb1c0013ba75d6`
- Restart:
  - `docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml -p saas-active up -d --no-build backend-real-pre`
  - Result: PASS
- Container final state:
  - `Image=sha256:8d7bbc737bae55c29c41f07fe7415173edce01f7865ee3b53bdb1c0013ba75d6`
  - `StartedAt=2026-06-21T03:28:43.999128594Z`
  - `Status=running`
  - `Health=healthy`

## Health And Smoke

- `verify-local.ps1 -Env real-pre -Scope backend`: PASS
- Direct health: `GET http://127.0.0.1:8081/api/system/health` -> `{"status":"UP"}`
- Runtime unauthenticated smoke:
  - `GET /api/users?pageNo=1&pageSize=5` -> 401
  - `GET /api/users/assignable` -> 401
- Backend startup logs: Spring Boot started with `real-pre`; no startup exception observed in tail.

## Result

- Conclusion: PASS for build, tests, local restart, health, and low-risk runtime smoke.
- Remote deploy: not executed; user did not request remote deployment.
- Remaining risk: authenticated real-pre API/UI E2E for `/users` query and `/users/assignable` success paths was not executed because no login token/test account was used in this batch.
