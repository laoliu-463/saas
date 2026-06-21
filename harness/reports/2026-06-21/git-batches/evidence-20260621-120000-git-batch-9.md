# Evidence 2026-06-21 12:00 - GIT-BATCH-9

## Scope

- Env: `real-pre`
- Branch: `feature/ddd/DDD-VERIFY-001`
- Base before commit: `af41640d`
- Change scope: user-domain port extraction batch
  - Move `SysUserQueryApplicationService` persistence details behind `UserQueryLookup`.
  - Add `SysUserQueryLookupAdapter` to own `QueryWrapper`, mapper calls, and role ID grouping.
  - Move `SysUserRoleAssignmentApplicationService` persistence details behind `UserRoleAssignmentStore`.
  - Add application-layer boundary tests for query, assignable-user, and role-assignment services.

## Static Checks

- code-review-graph:
  - `get_minimal_context`: risk high `0.85`, 61 test gaps from the broad dirty worktree.
  - `detect_changes` on six user-domain files: risk high `0.85`; review priority included `applyQueryDataScope`.
- Source review result:
  - Data-scope decision remains in application service via `DataScopePolicy`.
  - SQL wrapper construction moves to infrastructure adapter without changing request filters.
- `git diff --cached --check`: PASS.

## Tests

- Targeted test command:
  - `mvn -f backend/pom.xml "-Dtest=SysUserQueryApplicationServiceTest,SysUserQueryLookupAdapterTest,SysUserQueryApplicationServiceBoundaryTest,SysUserRoleAssignmentApplicationServiceTest,SysUserRoleAssignmentApplicationServiceBoundaryTest,UserAssignableApplicationServiceBoundaryTest,SysUserServiceAssignableBoundaryTest,ColonelSaasApplicationTests" test`
  - Result: PASS, 33 tests, 0 failures, 0 errors, 0 skipped.
- Full backend test command:
  - `mvn -f backend/pom.xml clean test`
  - Result: PASS, 2432 tests, 0 failures, 0 errors, 3 skipped.
- Package command:
  - `mvn -f backend/pom.xml -DskipTests "-Djacoco.skip=true" package`
  - Result: PASS.

## Runtime

- Docker build source: clean temp worktree with staged patch applied.
- Docker image: `sha256:0eb853435b587e565cb4877e8325c0d587db9bc66fb9bda12df9992c1ed4e2e3`
- Restart:
  - `docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml -p saas-active up -d --no-build backend-real-pre`
  - Result: PASS.
- Container final state:
  - `Image=sha256:0eb853435b587e565cb4877e8325c0d587db9bc66fb9bda12df9992c1ed4e2e3`
  - `StartedAt=2026-06-21T03:58:40.580291965Z`
  - `Status=running`
  - `Health=healthy`

## Health And Smoke

- `verify-local.ps1 -Env real-pre -Scope backend`: PASS
- Direct health: `GET /api/system/health` -> `{"status":"UP"}`
- Runtime unauthenticated smoke:
  - `GET /api/users?pageNo=1&pageSize=5` -> 401

## Result

- Conclusion: PASS for build, full tests, local restart, health, and low-risk smoke.
- Remote deploy: not executed; user did not request remote deployment.
- Remaining risk: authenticated real-pre success-path E2E for `/api/users`, `/api/users/assignable`, and role assignment was not executed because no explicit test token/account was used.
