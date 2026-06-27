# DDD100 Product E2E #67 Evidence

## Metadata

- Issue: #67 `[DDD100-PRODUCT-E2E] 商品库、转链、映射 real-pre E2E`
- Env: local real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- Remote deploy: not requested / not executed
- Source code changed: no

## Scope

This slice verifies the product real-pre chain after #61-#66:

- Product upstream/token/environment readiness.
- Activity product upstream response and local product view.
- Product detail and product pages without runtime failure.
- Reusable `pick_source_mapping` evidence.
- Real order sync and attribution-field sample status.

## Verification Commands

- Targeted backend tests: `mvn -q -f backend/pom.xml "-Dtest=ProductServicePromotionLinkFlowTest,PickSourceMappingServiceTest,PromotionLinkIdempotencyServiceTest,ProductDomainEventPublisherTest,DddProduct003ProductRoutingTest,ProductControllerTest,ColonelActivityProductControllerCopyPromotionTest,ProductServiceColonelBuyinIdTest" test`
- real-pre preflight: `node runtime/qa/real-pre-preflight.cjs --evidence-dir runtime/qa/out/ddd-product-e2e-067-20260627-154858/preflight`
- real-pre E2E: `npx playwright test --project=real-pre-p0 tests/e2e/31-real-pre-product-chain.spec.ts tests/e2e/32-real-pre-order-attribution.spec.ts`
- Read-only SQL: `runtime/qa/out/ddd-product-e2e-067-20260627-154858/sql-evidence-verified.txt`

The first preflight attempt used a custom evidence directory before creating its child folder and failed to write `summary.json`. It was discarded as invalid setup evidence; the rerun above is the recorded result.

## Evidence Summary

- Backend targeted tests: PASS.
- Preflight: PASS; frontend `/login` 200, backend health UP, admin login PASS, `real-pre` env guard PASS, Douyin token readiness PASS, schema readiness PASS.
- Reusable promotion mapping preflight count: 5.
- Product chain step 31: PASS.
  - Activity probe HTTP 200, upstream OK, activityId `3929906`.
  - Raw upstream activity products count: 20.
  - Local business products count: 20.
  - Candidate product `3790002585986007208`, bizStatus `APPROVED`, selectedToLibrary true.
  - `/product` and `/system/douyin` runtimeError=false.
  - `/product/manage/products` external image check: 5 loaded, 0 failed.
- Order attribution step 32: PENDING.
  - Reusable mapping found: mapping `f678fe4d-f22a-4ae9-bee7-f713e07250ff`, pickSource `v.MAhq5U`.
  - Order sync window 30 minutes: totalFetched 42, created 4, updated 38, failed 0.
  - Order list total: 352413; sampled records: 10.
  - Attribution stats: ATTRIBUTED 0, PICK_SOURCE_EMPTY 10.
- Verified SQL:
  - Active mappings with promotion URL: 5.
  - Latest 100 orders: with_pick_source 0, with_user_id 0, latest_pay_time `2026-06-27 15:51:25`.
  - Latest 10 order samples are `UNATTRIBUTED` with `COLONEL_MAPPING_NOT_FOUND`.

## Evidence Paths

- Preflight summary: `runtime/qa/out/ddd-product-e2e-067-20260627-154858/preflight/summary.json`
- Step 31 summary: `runtime/qa/out/ddd-product-e2e-067-20260627-154858/steps/31-real-pre-product-chain/step-summary.json`
- Step 32 summary: `runtime/qa/out/ddd-product-e2e-067-20260627-154858/steps/32-real-pre-order-attribution/step-summary.json`
- SQL evidence: `runtime/qa/out/ddd-product-e2e-067-20260627-154858/sql-evidence-verified.txt`

## Conclusion

PARTIAL / PENDING.

The product library, upstream activity product read, product detail read, page smoke, and reusable promotion mapping are verified on real-pre. The real order attribution positive sample is not verified because current real-pre orders do not carry `pick_source`; this is recorded as PENDING instead of PASS.

## Residual Risk

- A future real order that was generated from a system promotion link is still required to prove `pick_source -> mapping -> channel_user_id/user_id` positive attribution.
- This run did not deploy remote real-pre and did not create a new upstream promotion link.
