# Evidence - DDD User Facade: ColonelActivityController

## Basic Info

- Time: 2026-06-21 13:37 Asia/Shanghai
- Environment: local `real-pre`
- Branch: `feature/ddd/DDD-VERIFY-001`
- Commit: `02428d39`
- Scope: backend user-domain facade boundary
- Workspace: dirty before and after this slice; historical DDD changes are present and were not reverted.

## Change

- `ColonelActivityController.resolveUserName` now consumes `UserDomainFacade.loadUserDisplayNamesByIds`.
- Removed production dependency on `UserOptionResponse` in `ColonelActivityController`.
- Added `DddUserFacadeColonelActivityBoundaryTest` to lock the source boundary.
- Added controller behavior coverage proving the activity assignee display resolver uses display-name scalar data and never calls `getUserById`.

## Verification

- RED before production patch: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeColonelActivityBoundaryTest"` failed because `ColonelActivityController` still imported and consumed `UserOptionResponse`.
- Focused tests: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeColonelActivityBoundaryTest,ColonelActivityControllerTest"` passed, 17 tests / 0 failures / 0 errors.
- Expanded regression: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeColonelActivityBoundaryTest,ColonelActivityControllerTest,DddUserFacadeDataApplicationBoundaryTest,DataControllerTest,LegacyUserDomainFacadeTest,DddUserFacadeTalentQueryBoundaryTest,TalentQueryServiceTest,DddUserFacadeSampleFilterBoundaryTest,SampleFilterOptionsServiceTest,DddUserFacadeExclusiveMerchantBoundaryTest,ExclusiveMerchantQueryServiceTest,DddUserFacadeOperationLogBoundaryTest,OperationLogServiceTest,CurrentUserPasswordAuditIntegrationTest,OrderSyncServiceTest"` passed, 153 tests / 0 failures / 0 errors.
- Note: expanded regression still prints Redis connection noise from test-profile scheduled work in `LegacyUserDomainFacadeTest`; Surefire result is PASS.
- Package: `mvn -f backend/pom.xml -DskipTests package` passed and rebuilt `backend/target/colonel-saas.jar`.
- Docker restart: `restart-compose.ps1 -Env real-pre -Scope backend` passed; backend image rebuilt and `backend-real-pre` recreated.
- Health check: `verify-local.ps1 -Env real-pre -Scope backend` passed; `/api/system/health` returned `{"status":"UP"}`.
- code-review-graph: incremental update passed, 124 files re-parsed, 52 nodes and 1153 edges updated.
- Boundary scan: production controller contains `loadUserDisplayNamesByIds` and no longer contains `UserOptionResponse` / `getUserById` in the checked path.

## Business Validation

- Activity list assignment resolver behavior is covered by `ColonelActivityControllerTest.list_shouldPassDisplayNameResolverWithoutFullUserDto`.
- The tested observable behavior is `assigneeName = 招商负责人` from the user-domain display-name scalar output.
- No activity assignment rule, activity product sync rule, or product library rule was changed.

## Not Executed

- Remote deployment: not executed; user did not request remote deploy.
- Authenticated real-pre UI/E2E: not executed in this slice; no credentialed scenario was part of the change.

## Remaining Risk

- Overall U-7 is not complete. Remaining full-user-DTO consumers still include Product / TalentService / Merchant / SampleApplication / ExclusiveMerchantApplicationService paths.
- This evidence only proves the `ColonelActivityController` slice.

## Conclusion

PARTIAL. This slice passed tests, package, backend restart, health check, graph update, and documentation evidence. The broader DDD user-domain facade cleanup remains in progress.
