# Evidence: DDD User Facade Sample Filter

## Meta

- Time: 2026-06-21 12:54:52 +08:00
- Env: local real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- Commit: `7b6589b1df31f32e820699c82fa92e483d7354c9`
- Scope: Gate 3 backend/domain change, U-7 UserDomainFacade cross-domain DTO cleanup
- Remote deploy: not executed; not requested

## Change

- Added `UserDomainFacade.loadUserDisplayLabelsByIds(Collection<UUID>)`.
- Implemented display-label formatting in `LegacyUserDomainFacade` using `UserBasicLookup`.
- `SampleFilterOptionsService` now consumes `loadUserDisplayLabelsByIds` for channel and recruiter option labels.
- Added `DddUserFacadeSampleFilterBoundaryTest` to prevent this consumer from re-importing `UserOptionResponse`.
- Updated `SampleFilterOptionsServiceTest` and `LegacyUserDomainFacadeTest`.
- Updated `UBIQUITOUS_LANGUAGE.md` with **用户显示标签**.
- Updated `DOMAIN_STATUS.md` with this U-7 stage result and remaining risk.

## Evidence

- RED check before production change:
  - `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeSampleFilterBoundaryTest"`
  - Result: failed as expected because `SampleFilterOptionsService` still imported `UserOptionResponse`.
- Focused tests after change:
  - `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeSampleFilterBoundaryTest,SampleFilterOptionsServiceTest,LegacyUserDomainFacadeTest"`
  - Result: PASS, 14 tests, 0 failures, 0 errors, 0 skipped.
- Expanded user-facade regression:
  - `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeSampleFilterBoundaryTest,SampleFilterOptionsServiceTest,LegacyUserDomainFacadeTest,DddUserFacadeExclusiveMerchantBoundaryTest,ExclusiveMerchantQueryServiceTest,DddUserFacadeOperationLogBoundaryTest,OperationLogServiceTest,CurrentUserPasswordAuditIntegrationTest"`
  - Result: PASS, 28 tests, 0 failures, 0 errors, 0 skipped.
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
  - Result: PASS, incremental update, 155 files re-parsed, 165 nodes and 1645 edges updated.
- Source check:
  - `rg -n "UserOptionResponse|getUserById|getUsersByIds|loadUserDisplayLabelsByIds" ...`
  - Result: production `SampleFilterOptionsService` only calls `userDomainFacade.loadUserDisplayLabelsByIds(userIds)`.

## Conclusion

PARTIAL.

This slice passed RED/GREEN tests, expanded regression, build, backend restart, health, and graph verification. The broader U-7 cleanup remains open because Product / Talent / Merchant / Data consumers still read full user DTOs in some paths, and this dirty worktree was not staged, committed, or pushed in this slice.

## Remaining Risk

- `UserDomainFacade` still exposes full DTO methods for compatibility.
- Authenticated real-pre `/samples/filter-options` API/UI validation was not executed in this slice; coverage is backend service, boundary, facade integration, and health.
- Existing dirty worktree contains many unrelated DDD changes; this report only claims the sample-filter facade slice.
