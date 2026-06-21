# Evidence: U-7 DataApplicationService User Facade Boundary

## Summary

- Time: 2026-06-21 13:24 +08:00
- Env: local `real-pre`
- Branch: `feature/ddd/DDD-VERIFY-001`
- Commit: `74246398`
- Scope: backend DDD U-7 small slice
- Conclusion: `PARTIAL`

## Change

- Added `UserDomainFacade.loadUserDisplayNamesByIds`.
- Implemented it in `LegacyUserDomainFacade` through `UserBasicLookup`.
- Defined **用户展示名称** in `CONTEXT.md` and `UBIQUITOUS_LANGUAGE.md`: real name first, username fallback.
- `DataApplicationService` now consumes display-name scalars for order-detail channel/recruiter names.
- `DataApplicationService` no longer imports or iterates `UserOptionResponse`.
- Added architecture boundary test: `DddUserFacadeDataApplicationBoundaryTest`.

## Verification

- RED evidence: `DddUserFacadeDataApplicationBoundaryTest` initially failed while `DataApplicationService` still referenced `UserOptionResponse` / `getUsersByIds`.
- Focused tests: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeDataApplicationBoundaryTest,DataControllerTest,LegacyUserDomainFacadeTest"` -> PASS, 54 tests.
- Expanded user-facade regression: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeDataApplicationBoundaryTest,DataControllerTest,LegacyUserDomainFacadeTest,DddUserFacadeTalentQueryBoundaryTest,TalentQueryServiceTest,DddUserFacadeSampleFilterBoundaryTest,SampleFilterOptionsServiceTest,DddUserFacadeExclusiveMerchantBoundaryTest,ExclusiveMerchantQueryServiceTest,DddUserFacadeOperationLogBoundaryTest,OperationLogServiceTest,CurrentUserPasswordAuditIntegrationTest,OrderSyncServiceTest"` -> PASS, 136 tests.
- Build: `mvn -f backend/pom.xml -DskipTests package` -> PASS.
- Restart: `restart-compose.ps1 -Env real-pre -Scope backend` -> PASS, backend container recreated.
- Health: `verify-local.ps1 -Env real-pre -Scope backend` -> PASS, `/api/system/health` returned `{"status":"UP"}`.
- Docker status: `backend-real-pre` healthy.
- code-review-graph: incremental update PASS, 118 files re-parsed, FTS rebuilt.

## Not Verified

- Remote real-pre deployment: not requested, not executed.
- Authenticated browser/E2E data-order-detail smoke: not executed in this slice.
- Full backend test suite: not executed; this slice used focused and expanded user-facade regression.

## Risks

- Worktree remains dirty from multiple DDD slices; no staging, commit, or push performed.
- Other cross-domain consumers still read full user DTOs: Product / TalentService / Merchant / SampleApplication / ColonelActivityController / ExclusiveMerchantApplicationService.
- `DataApplicationService` still contains other DDD concerns outside this slice; this change only contracts user-profile consumption for order-detail display.
