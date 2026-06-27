# DDD100 Product Promotion #66 Evidence

## Metadata

- Issue: #66 `[DDD100-PRODUCT-PROMOTION] 转链、归因映射 Port 与事件证据`
- Env: real-pre
- Branch: `feature/ddd/DDD-VERIFY-001`
- Code commit: `c42e0989`
- Remote deploy: not requested / not executed

## Change Summary

- Removed the duplicate unused product-domain `DouyinConvertPort` and adapter, keeping the single application port used by promotion-link orchestration.
- Added `ProductPromotionLinkCompletedEvent` and `ProductDomainEventPublisher.publishPromotionLinkCompleted`.
- Changed `PickSourceMappingService.saveOrUpdate` to return the persisted or recovered mapping id.
- Updated `ProductService` to publish completion evidence only after `promotion_link`, `pick_source_mapping`, status, and operation log writes succeed.
- Preserved API shape, DB schema, default real-pre config, and legacy idempotent completed-result behavior.

## Verification

- RED: `mvn -q -f backend/pom.xml "-Dtest=PickSourceMappingServiceTest,ProductDomainEventPublisherTest,DddProduct003ProductRoutingTest" test` failed before the event existed.
- Targeted PASS: `ProductServicePromotionLinkFlowTest,PickSourceMappingServiceTest,PromotionLinkIdempotencyServiceTest,ProductDomainEventPublisherTest,DddProduct003ProductRoutingTest,CopyPromotionApplicationServiceTest,DouyinPromotionGatewayConvertAdapterTest,ProductServicePromotionPortArchitectureTest,ProductControllerTest,ColonelActivityProductControllerCopyPromotionTest,ProductServiceColonelBuyinIdTest`.
- Compile PASS: `mvn -q -f backend/pom.xml -DskipTests compile`.
- Harness PASS: `agent-do.ps1 -Env real-pre -Scope backend -ContentMaintenance off -Message "issue #66 product promotion link event"`.
- Harness covered backend package, Docker rebuild/recreate, backend health check, and real-pre P0 preflight.
- Business validation output: `runtime/qa/out/real-pre-preflight-20260627-154010`.

## Evidence Files

- Agent-do evidence: `harness/reports/2026-06-21/ddd-product-promotion-066/evidence-20260627-154012-agent-do.md`.
- Agent-do retro: `harness/reports/2026-06-21/ddd-product-promotion-066/retro-20260627-154044-agent-do.md`.

## Residual Risk

- This proves local promotion-link completion evidence and outbox/publisher behavior, not a fresh real order attribution sample.
- Real-pre order pick-source attribution and channel visibility remain the scope of #67.

## Conclusion

PASS for #66. The promotion-link slice now has a single conversion port, persisted mapping id evidence, and a completion domain event guarded by targeted tests and real-pre harness verification.
