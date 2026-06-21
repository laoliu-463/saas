# Evidence: DDD-USER-PRODUCT-SERVICE-FACADE

## Scope

- Time: 2026-06-21 14:51 CST
- Env: local real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- Base commit: `e14dcbb3`
- Remote deploy: not executed
- Worktree clean: no. Existing DDD worktree contains unrelated historical changes; this slice was not committed or pushed.

## Change Summary

- Added `UserDomainFacade.loadUserChannelCodesByIds`.
- Implemented channel-code lookup in `LegacyUserDomainFacade` through `UserBasicLookup`, without exposing full `UserOptionResponse`.
- Updated `ProductService` to consume:
  - `loadUserDisplayLabelsByIds` for user display labels.
  - `loadUserOwnershipReferencesByIds` for assigned owner existence and organization reference.
  - `loadUserChannelCodesByIds` for `pick_extra` channel code construction.
  - `getUserName` for promotion-link operator fallback.
- Added `DddUserFacadeProductServiceBoundaryTest` to prevent `ProductService` from reintroducing `getUserById`, `getUsersByIds`, or `UserOptionResponse`.
- Updated `UBIQUITOUS_LANGUAGE.md` with **渠道编码** and the `pick_extra` terminology boundary.

## Verification

- RED boundary test: `mvn -f backend/pom.xml test "-Dtest=DddUserFacadeProductServiceBoundaryTest"` failed as expected while `ProductService` still imported `UserOptionResponse`.
- First focused run exposed one leftover compile reference to `user.realName()` in promotion-link creation; fixed before continuing.
- Second focused run exposed a wrong test fixture expectation for channel code; corrected expected value to `ch_lead`.
- Focused verification passed: `DddUserFacadeProductServiceBoundaryTest`, `ProductServiceActivityAssignTest`, `DddConfig003ConfigRoutingTest`, `LegacyUserDomainFacadeTest` = 22 tests, 0 failures, 0 errors.
- Product regression passed: `DddUserFacadeProductServiceBoundaryTest,DddConfig003ConfigRoutingTest,LegacyUserDomainFacadeTest,ProductService*Test,ProductControllerTest,ColonelActivityControllerTest` = 94 tests, 0 failures, 0 errors.
- U-7 expanded regression passed: 369 tests, 0 failures, 0 errors.
- Package passed: `mvn -f backend/pom.xml -DskipTests package` = BUILD SUCCESS.
- Backend restart passed: `restart-compose.ps1 -Env real-pre -Scope backend`.
- Local health passed: `verify-local.ps1 -Env real-pre -Scope backend`, health returned `{"status":"UP"}` after one retry.
- code-review-graph incremental update passed: 122 files re-parsed, 702 nodes and 7915 edges updated, FTS rebuilt.
- Old cross-domain facade scan passed: `rg -n "userDomainFacade\.(getUserById|getUsersByIds)" backend/src/main/java` returned no matches.

## Result

PASS for this U-7 slice. Cross-business-domain production use of `userDomainFacade.getUserById/getUsersByIds` is now cleared.

Overall DDD goal remains PARTIAL because `UserDomainFacade` still exposes compatibility DTOs for user-domain master-data dropdowns, and `DataScopeResolver` / `PermissionChecker` consumption is not fully unified.
