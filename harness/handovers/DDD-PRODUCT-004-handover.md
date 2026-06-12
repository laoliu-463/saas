# Handover: DDD-PRODUCT-004

## Done

- New `CopyPromotionApplicationService` under `domain/product/application`.
- New `DouyinConvertPort` under `domain/product/application/port`.
- New `DouyinPromotionGatewayConvertAdapter` implements the port on top of
  legacy `DouyinPromotionGateway`.
- `ProductService` now depends on `DouyinConvertPort` instead of
  `DouyinPromotionGateway`; dead legacy helpers removed.
- `ColonelActivityProductController` routes promotion-link generation through
  `CopyPromotionApplicationService`.

## Tests

- Targeted PRODUCT-004 tests pass (45/45).
- Architecture guard `ProductServicePromotionPortArchitectureTest` confirms no
  direct `DouyinPromotionGateway` injection in `ProductService`.

## Constraints honored

- API / response shape unchanged.
- pick_source mapping save path preserved.
- copy_template fallback unchanged when missing.

## Next

- **DDD-PERF-004**: implement `PerformanceQueryFacade` on
  `feature/ddd/DDD-PERF-004`.
- **DDD-SLIM-PRODUCT-001**: slim ProductService display rules once the
  product-domain helpers have been re-evaluated.

## Risk

- Baseline `mvn clean test` carries pre-existing failures outside this task's
  scope; must be triaged before CLEAN / VERIFY.
- `tests/e2e/20-v1-channel-chain.spec.ts` and `21-v1-recruiter-chain.spec.ts`
  were updated to expect new feedback toast strings and product-detail hover;
  run E2E on real-pre before next phase.

## Report

`harness/reports/ddd-product-004-copy-promotion.md`