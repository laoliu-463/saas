# Evidence: DDD-COMPLETE-100-PRODUCT-02

- Issue: #131 商品同步/backfill 异步 job Application 最终收口
- Time: 2026-06-27 20:40 Asia/Shanghai
- Env: local real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- Local HEAD before #131 commit: `0c74aeed`
- Upstream before #131 commit: `origin/feature/ddd/DDD-VERIFY-001` = `b264d403`
- Remote deploy: not requested / not executed

## Change Scope

- Added `ProductActivityBackfillApplicationService`.
- Routed `ProductSyncAdminController` through the product Application boundary.
- API layer now exposes product Application DTOs, not `ProductActivityBackfillService` DTOs.
- Added mapping tests and architecture guard for the new boundary.
- No URL, role annotation, DB schema, real-pre config, confirm rule, or legacy execution semantics changed.

## Files

- `backend/src/main/java/com/colonel/saas/domain/product/application/ProductActivityBackfillApplicationService.java`
- `backend/src/main/java/com/colonel/saas/controller/ProductSyncAdminController.java`
- `backend/src/test/java/com/colonel/saas/domain/product/application/ProductActivityBackfillApplicationServiceTest.java`
- `backend/src/test/java/com/colonel/saas/controller/ProductSyncAdminControllerTest.java`
- `backend/src/test/java/com/colonel/saas/architecture/DddProduct003ProductRoutingTest.java`

## Boundary Evidence

- code-review-graph review context: 5 changed files, risk low, impacted files 4 at depth 1.
- Impact radius at depth 2 is high because admin controller connects to role guard / API surface; direct changed code remains product backfill boundary only.
- `DddProduct003ProductRoutingTest` now asserts controller contains `ProductActivityBackfillApplicationService` / `BackfillCommand` and does not contain `ProductActivityBackfillService` or `ProductSyncDryRunProbeService`.

## Verification

| Check | Result | Evidence |
| --- | --- | --- |
| Clean targeted tests | PASS | `D:\Projects\SAAS-product-131-verify`, `mvn "-Dtest=ProductActivityBackfillApplicationServiceTest,ProductSyncAdminControllerTest,DddProduct003ProductRoutingTest" test`, 15 tests, 0 failures |
| Current targeted tests | PASS | `D:\Projects\SAAS\backend`, same Maven test filter, 15 tests, 0 failures after concurrent commits settled |
| Clean backend package | PASS | `D:\Projects\SAAS-product-131-verify`, `mvn -f backend/pom.xml -DskipTests package` |
| Main backend main-code package | PASS | `mvn -f backend/pom.xml "-Dmaven.test.skip=true" "-Djacoco.skip=true" package` |
| Standard `agent-do` | NOT_RUN_EXTERNAL_DIRTY | would stage all dirty files through `git-push-safe`; unsafe while another agent owns order/user changes |
| Docker restart | PASS | `restart-compose.ps1 -Env real-pre -Scope backend`; backend image rebuilt and container recreated |
| Health check | PASS | `verify-local.ps1 -Env real-pre -Scope backend`; `/api/system/health` returned `{"status":"UP"}` |
| Business preflight | PASS | `npm run e2e:real-pre:p0:preflight`; output `runtime/qa/out/real-pre-preflight-20260627-203803`, status PASS |
| Async dry-run API | PASS_ROUTE_PARTIAL_DATA | job `product-backfill-b6d3a3c7-2df0-499f-827b-d25f8432a568`, dryRun=true, maxActivities=1, maxPages=1, status PARTIAL, fetched=20, inserted=0, updated=0 |

## Docker Status

- `backend-real-pre`: healthy, rebuilt for this run.
- `frontend-real-pre`: healthy.
- `postgres-real-pre`: healthy.
- `redis-real-pre`: healthy.

## Conclusion

#131 product Application boundary is complete and verified. The bounded dry-run status is `PARTIAL` by design because max pages was set to 1; it proves the async route/job-status chain works without business writes.

## Remaining Risk

- Standard `agent-do` was not used because it would commit another agent's dirty files.
- Current branch contains concurrent user/order/sample commits from other agents.
- Real promotion-link order `pick_source` positive sample remains PENDING for #135.
