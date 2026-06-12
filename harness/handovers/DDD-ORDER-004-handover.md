# Handover: DDD-ORDER-004

## Done

- Default attribution policy + resolver landed under `domain/order`.
- `OrderAttributionRouter` switches to new path via `ddd.refactor.order-attribution.enabled`.
- `ProductDomainFacade` extended with `findProductAssigneeId` /
  `findActivityDefaultRecruiterId` for default recruiter resolution.
- Targeted tests green: `OrderDefaultAttributionPolicyTest`,
  `OrderDefaultAttributionResolverTest`, `OrderAttributionRouterTest`,
  `OrderSyncServiceTest`, `LegacyProductDomainFacadeTest`,
  `DddCrossDomainMapperGuardTest`, `DddSlimOrder002RoutingTest`.

## Constraints honored

- No exclusive-talent / exclusive-merchant logic in order sync.
- No final attribution / commission / gross profit calculation in order domain.
- pick_source mapping lookup lives in `OrderPickSourceMappingAdapter` so order
  domain stays free of cross-domain mapper injections.
- API / sync response shapes unchanged.

## Next

- **DDD-PERF-004**: wire order / data BFF performance enrichment through
  `PerformanceQueryFacade`.
- **DDD-PRODUCT-004**: commit `CopyPromotionApplicationService` +
  `DouyinConvertPort` work on `feature/ddd/DDD-PRODUCT-004`.

## Risk

- Baseline `mvn clean test` carries pre-existing failures outside this task's
  scope; must be triaged before CLEAN / VERIFY.

## Report

`harness/reports/ddd-order-004-2026-06-12.md`