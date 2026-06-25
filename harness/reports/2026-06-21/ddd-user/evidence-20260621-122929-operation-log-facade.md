# Evidence 2026-06-21 12:29 - DDD User Facade Operation Log

## Metadata

- Env: real-pre
- Scope: backend
- Branch: feature/ddd/DDD-VERIFY-001
- Gate: Gate 3 - Domain Change
- Remote deploy: not requested, not executed
- Worktree: dirty before and after this slice; no commit or push performed

## Scope

- Primary domain: 用户域 U-7
- Consumer: `OperationLogService`
- Boundary: audit log only needs 操作人账号, not full `UserOptionResponse`

## Changes

- Added `UserDomainFacade.getUsername(UUID)` for login account lookup.
- Implemented `LegacyUserDomainFacade.getUsername` through `UserBasicLookup`.
- Changed `OperationLogService.resolveUsername` to call `getUsername`.
- Added `DddUserFacadeOperationLogBoundaryTest`.
- Updated `OperationLogServiceTest` and `LegacyUserDomainFacadeTest`.
- Updated `UBIQUITOUS_LANGUAGE.md` with **操作人账号**.
- Updated `DOMAIN_STATUS.md` for this U-7 slice.

## Evidence Chain

- RED test before fix:
  - `DddUserFacadeOperationLogBoundaryTest` failed on `UserOptionResponse` import.
  - `OperationLogServiceTest` failed because production still called `getUserById`.
- Focused tests after fix:
  - Command: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeOperationLogBoundaryTest,OperationLogServiceTest"`
  - Result: PASS, 7 tests, 0 failures, 0 errors.
- User facade and audit regression:
  - Command: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeOperationLogBoundaryTest,OperationLogServiceTest,LegacyUserDomainFacadeTest,LegacyUserDomainFacadeBoundaryTest,CurrentUserPasswordAuditIntegrationTest,OperationLogControllerTest,OperationLogRetentionAcceptanceTest,OperationLogInterceptorTest"`
  - Result: PASS, 27 tests, 0 failures, 0 errors.
  - Evidence: `CurrentUserPasswordAuditIntegrationTest` confirms `operation_log.username` remains login account.
- Package:
  - Command: `mvn -f backend/pom.xml -DskipTests package`
  - Result: PASS.
- Runtime:
  - Command: `restart-compose.ps1 -Env real-pre -Scope backend`
  - Result: backend image rebuilt and backend container recreated.
- Health:
  - Command: `verify-local.ps1 -Env real-pre -Scope backend`
  - Result: PASS, `/api/system/health` returned `{"status":"UP"}`.
- Graph:
  - Command: code-review-graph incremental update with minimal postprocess.
  - Result: PASS.
- Source checks:
  - `OperationLogService` no longer imports `UserOptionResponse`.
  - Scoped trailing whitespace check: PASS.

## Conclusion

PASS for this U-7 operation-log facade-consumer slice.

## Residual Risk

- `UserDomainFacade` still has other cross-domain consumers that read full `UserOptionResponse`.
- `DataScopeResolver` / `PermissionChecker` consumption remains not fully unified.
- No remote deployment was requested or executed.
