# Evidence: DDD User Facade Exclusive Merchant

## Meta

- Time: 2026-06-21 12:42:30 +08:00
- Env: local real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- Commit: `7b6589b1df31f32e820699c82fa92e483d7354c9`
- Scope: Gate 3 backend/domain change, U-7 UserDomainFacade cross-domain DTO cleanup
- Remote deploy: not executed; not requested

## Change

- `ExclusiveMerchantQueryService` no longer imports `UserOptionResponse`.
- `ExclusiveMerchantQueryService.resolveUserName` now calls `UserDomainFacade.getUsername(userId)`.
- Added `DddUserFacadeExclusiveMerchantBoundaryTest` to prevent this consumer from reading full user DTOs again.
- Updated `ExclusiveMerchantQueryServiceTest` to mock `getUsername` instead of `getUserById`.
- Updated `UBIQUITOUS_LANGUAGE.md` with **负责人账号**.
- Updated `DOMAIN_STATUS.md` with the U-7 stage status and remaining risk.

## Evidence

- RED check before production change:
  - `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeExclusiveMerchantBoundaryTest,ExclusiveMerchantQueryServiceTest"`
  - Result: failed as expected because production still imported `UserOptionResponse` and called `getUserById`.
- Focused tests after change:
  - `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeExclusiveMerchantBoundaryTest,ExclusiveMerchantQueryServiceTest"`
  - Result: PASS, 6 tests, 0 failures, 0 errors, 0 skipped.
- User facade and audit regression:
  - `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeExclusiveMerchantBoundaryTest,ExclusiveMerchantQueryServiceTest,LegacyUserDomainFacadeTest,LegacyUserDomainFacadeBoundaryTest,DddUserFacadeOperationLogBoundaryTest,OperationLogServiceTest,CurrentUserPasswordAuditIntegrationTest"`
  - Result: PASS, 25 tests, 0 failures, 0 errors, 0 skipped.
  - Note: Spring test shutdown logged a Redis `LettuceConnectionFactory is STOPPING` scheduled-task error; Surefire result remained PASS.
- Build:
  - `mvn -f backend/pom.xml -DskipTests package`
  - Result: PASS, `backend/target/colonel-saas.jar` rebuilt.
- Runtime restart:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\restart-compose.ps1 -Env real-pre -Scope backend`
  - Result: backend image rebuilt and `backend-real-pre` container recreated.
- Health:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\verify-local.ps1 -Env real-pre -Scope backend`
  - Result: PASS, `http://127.0.0.1:8081/api/system/health` returned `{"status":"UP"}`.
- Code graph:
  - `build_or_update_graph_tool(full_rebuild=false, postprocess=minimal)`
  - Result: PASS, incremental update, 143 files re-parsed, 26 nodes and 323 edges updated.
- Source check:
  - `rg -n "UserOptionResponse|getUserById|getUsername" ...ExclusiveMerchantQueryService...`
  - Result: production service only contains `userDomainFacade.getUsername(userId)`; boundary test asserts no `UserOptionResponse`.

## Conclusion

PARTIAL.

This slice passed test, build, restart, health, and graph verification. The broader U-7 goal remains open because Product / Sample / Talent / Merchant / Data consumers still read full user DTOs in some paths, and this dirty worktree was not staged, committed, or pushed in this slice.

## Remaining Risk

- `UserDomainFacade` still exposes full DTO methods for existing consumers.
- Authenticated real-pre business UI/API validation for exclusive merchant display was not executed in this slice; coverage is currently unit plus boundary tests and backend health.
- Existing dirty worktree contains many unrelated DDD changes; this report only claims the exclusive-merchant facade slice.
