# Evidence - DDD User Facade: Ownership Reference

## Basic Info

- Time: 2026-06-21 13:46 Asia/Shanghai
- Environment: local `real-pre`
- Branch: `feature/ddd/DDD-VERIFY-001`
- Commit: `02428d39`
- Scope: backend user-domain facade boundary
- Workspace: dirty before and after this slice; existing staged and unstaged DDD changes were preserved.

## Change

- Added user-domain term **负责人归属组织单元** to `CONTEXT.md` and `UBIQUITOUS_LANGUAGE.md`.
- Added `UserOwnershipReference`, a lightweight user-domain facade DTO containing user ID and primary organization unit.
- Added `UserDomainFacade.loadUserOwnershipReferencesByIds`.
- Migrated `TalentService.overrideTalentAssignment` and `MerchantService.overrideMerchantAssignment` away from full `UserOptionResponse`.
- Preserved behavior:
  - Merchant assignment still writes `ownerDeptId` from the target user's primary organization unit.
  - Talent assignment still keeps the existing claim `deptId` write behavior unchanged.

## Verification

- RED before production patch: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeOwnershipReferenceBoundaryTest"` failed because `TalentService` still imported `UserOptionResponse`.
- Focused tests: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeOwnershipReferenceBoundaryTest,TalentServiceTest,MerchantServiceTest,LegacyUserDomainFacadeTest"` passed, 80 tests / 0 failures / 0 errors.
- Expanded regression: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeOwnershipReferenceBoundaryTest,TalentServiceTest,MerchantServiceTest,LegacyUserDomainFacadeTest,DddUserFacadeColonelActivityBoundaryTest,ColonelActivityControllerTest,DddUserFacadeDataApplicationBoundaryTest,DataControllerTest,DddUserFacadeTalentQueryBoundaryTest,TalentQueryServiceTest,DddUserFacadeSampleFilterBoundaryTest,SampleFilterOptionsServiceTest,DddUserFacadeExclusiveMerchantBoundaryTest,ExclusiveMerchantQueryServiceTest,DddUserFacadeOperationLogBoundaryTest,OperationLogServiceTest,CurrentUserPasswordAuditIntegrationTest,OrderSyncServiceTest"` passed, 222 tests / 0 failures / 0 errors.
- Package: `mvn -f backend/pom.xml -DskipTests package` passed and rebuilt `backend/target/colonel-saas.jar`.
- Docker restart: `restart-compose.ps1 -Env real-pre -Scope backend` passed; backend image rebuilt and `backend-real-pre` recreated.
- Health check: `verify-local.ps1 -Env real-pre -Scope backend` passed; `/api/system/health` returned `{"status":"UP"}`.
- code-review-graph: incremental update passed, 128 files re-parsed, 306 nodes and 5602 edges updated.

## Business Validation

- `MerchantServiceTest.overrideMerchantAssignment_shouldUpdateOwnerAndDeptId` proves `ownerDeptId` still follows the target user organization unit.
- `TalentServiceTest.overrideTalentAssignment_shouldExpireClaimsAndCreateNewManualClaim` proves the target user existence check and existing talent assignment flow still pass.
- `LegacyUserDomainFacadeTest.loadUserOwnershipReferencesShouldReturnDeptIdWithoutFullUserDto` proves the user-domain facade can expose the required ownership reference without full user DTO.

## Not Executed

- Remote deployment: not executed; user did not request remote deploy.
- Authenticated real-pre UI/E2E: not executed in this slice; no credentialed scenario was part of the change.

## Remaining Risk

- Overall U-7 is not complete. Remaining full-user-DTO consumers are in `ExclusiveMerchantApplicationService`, `ProductService`, and `SampleApplicationService`.
- This evidence only proves the `TalentService` / `MerchantService` ownership-reference slice.

## Conclusion

PARTIAL. This slice passed RED/GREEN verification, expanded regression, package, backend restart, health check, graph update, and documentation evidence. The broader DDD user-domain facade cleanup remains in progress.
