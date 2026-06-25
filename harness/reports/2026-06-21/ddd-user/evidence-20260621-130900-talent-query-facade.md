# Evidence: U-7 TalentQueryService User Facade Boundary

## Summary

- Time: 2026-06-21 13:09 +08:00
- Env: local `real-pre`
- Branch: `feature/ddd/DDD-VERIFY-001`
- Commit: `74246398`
- Scope: backend DDD U-7 small slice
- Conclusion: `PARTIAL`

## Change

- `TalentQueryService` no longer imports or consumes `UserOptionResponse`.
- Talent list/detail claim owner rendering now consumes `UserDomainFacade.loadUserDisplayLabelsByIds`.
- Existing claim summary semantics are preserved: single owner name, multi-owner summary, current-user suffix, expired release hint.
- Added architecture boundary test: `DddUserFacadeTalentQueryBoundaryTest`.

## Verification

- RED evidence: `DddUserFacadeTalentQueryBoundaryTest` initially failed while `TalentQueryService` still referenced `UserOptionResponse` / `getUsersByIds`.
- Focused tests: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeTalentQueryBoundaryTest,TalentQueryServiceTest"` -> PASS, 21 tests.
- Expanded user-facade regression: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeTalentQueryBoundaryTest,TalentQueryServiceTest,DddUserFacadeSampleFilterBoundaryTest,SampleFilterOptionsServiceTest,LegacyUserDomainFacadeTest,DddUserFacadeExclusiveMerchantBoundaryTest,ExclusiveMerchantQueryServiceTest,DddUserFacadeOperationLogBoundaryTest,OperationLogServiceTest,CurrentUserPasswordAuditIntegrationTest"` -> PASS, 49 tests.
- Build: `mvn -f backend/pom.xml -DskipTests package` -> PASS.
- Restart: `restart-compose.ps1 -Env real-pre -Scope backend` -> PASS, backend container recreated.
- Health: `verify-local.ps1 -Env real-pre -Scope backend` -> PASS, `/api/system/health` returned `{"status":"UP"}`.
- Docker status: `backend-real-pre` healthy.
- code-review-graph: incremental update PASS, 110 files re-parsed, FTS rebuilt.
- Ubiquitous language: existing `用户显示标签` term already covers this slice; no glossary change needed.

## Not Verified

- Remote real-pre deployment: not requested, not executed.
- Authenticated browser/E2E talent list/detail smoke: not executed in this slice.
- Full backend test suite: not executed; this slice used focused and expanded user-facade regression.

## Risks

- Worktree remains dirty from multiple DDD slices; no staging, commit, or push performed.
- Other cross-domain consumers still read full user DTOs: Product / TalentService / Merchant / Data / SampleApplication / ColonelActivityController.
- This is a boundary contraction, not final `UserDomainFacade` closure.
