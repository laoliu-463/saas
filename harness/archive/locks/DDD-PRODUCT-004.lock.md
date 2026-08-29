# Agent Lock: DDD-PRODUCT-004

| Field | Value |
| --- | --- |
| task_id | DDD-PRODUCT-004 |
| owner | Product Agent |
| branch | feature/ddd/DDD-PRODUCT-004 |
| status | COMPLETED |
| started | 2026-06-12T15:00:00+08:00 |
| completed | 2026-06-12T17:35:00+08:00 |

## Scope

Extract copy-promotion / link-conversion into
`CopyPromotionApplicationService` + `DouyinConvertPort` +
`DouyinPromotionGatewayConvertAdapter`. Replace direct
`DouyinPromotionGateway` injection in `ProductService` with the port, route
`ColonelActivityProductController` through `CopyPromotionApplicationService`.

## Out of scope

- Order attribution (DDD-ORDER-004)
- Performance enrichment (DDD-PERF-004)
- Quick sample port (DDD-PRODUCT-005, already done at 0498b08e)
- Frontend response shape changes

## Targeted tests

```
mvn -Dtest=CopyPromotionApplicationServiceTest,DouyinPromotionGatewayConvertAdapterTest,ProductServicePromotionPortArchitectureTest,ColonelActivityProductControllerCopyPromotionTest,ProductServiceLibraryViewTest,ProductServiceFilterTest,ProductServiceActivityAssignTest,ProductServiceActivityStatusIndependenceTest,ProductServiceColonelBuyinIdTest,ProductServiceShopScoreTest,DddConfig003ConfigRoutingTest test
```

Result: 45 tests run, 0 failures, 0 errors, 0 skipped.

## Baseline risk (carried forward)

Same as DDD-ORDER-004 — `mvn clean test` has pre-existing baseline failures
outside this task's scope. Must be triaged before CLEAN / VERIFY.