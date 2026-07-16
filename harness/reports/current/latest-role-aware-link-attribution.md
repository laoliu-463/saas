# Evidence Report

## Metadata

- Time: 2026-07-16 16:32:57 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/role-aware-link-attribution
- Commit: b37088a1
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/AttributionAdminController.java
backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java
backend/src/main/java/com/colonel/saas/controller/OrderController.java
backend/src/main/java/com/colonel/saas/controller/ProductController.java
backend/src/main/java/com/colonel/saas/domain/order/application/OrderDefaultAttributionResolver.java
backend/src/main/java/com/colonel/saas/domain/order/infrastructure/OrderPickSourceMappingAdapter.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderAttributionInput.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicy.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionResult.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderLinkAttributionResolution.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java
backend/src/main/java/com/colonel/saas/domain/product/policy/PromotionAttributionOwnerPolicy.java
backend/src/main/java/com/colonel/saas/domain/shared/attribution/AttributionOwnerType.java
backend/src/main/java/com/colonel/saas/domain/shared/attribution/AttributionSource.java
backend/src/main/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacade.java
backend/src/main/java/com/colonel/saas/domain/user/facade/UserDomainFacade.java
backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserRoleCodeLookupAdapter.java
backend/src/main/java/com/colonel/saas/domain/user/port/UserRoleCodeLookup.java
backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java
backend/src/main/java/com/colonel/saas/entity/PickSourceMapping.java
backend/src/main/java/com/colonel/saas/entity/PromotionLink.java
backend/src/main/java/com/colonel/saas/service/AttributionOwnerReconciliationService.java
backend/src/main/java/com/colonel/saas/service/OrderAttributionReplayService.java
backend/src/main/java/com/colonel/saas/service/PickSourceMappingService.java
backend/src/main/java/com/colonel/saas/service/ProductService.java
backend/src/main/resources/db/alter-role-aware-promotion-link-attribution-20260716.sql
backend/src/main/resources/db/init-db.sql
backend/src/main/resources/db/migrate-all.sql
backend/src/main/resources/mapper/ColonelsettlementOrderMapper.xml
backend/src/test/java/com/colonel/saas/architecture/DddOrderDefaultAttributionInputContractTest.java
backend/src/test/java/com/colonel/saas/architecture/DddPerformanceAttributionTraceabilityContractTest.java
backend/src/test/java/com/colonel/saas/config/RealPreMigrationContractTest.java
backend/src/test/java/com/colonel/saas/controller/AttributionAdminControllerTest.java
backend/src/test/java/com/colonel/saas/controller/ColonelActivityProductControllerCopyPromotionTest.java
backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java
backend/src/test/java/com/colonel/saas/controller/ProductControllerTest.java
backend/src/test/java/com/colonel/saas/domain/order/application/OrderAttributionRouterTest.java
backend/src/test/java/com/colonel/saas/domain/order/application/OrderDefaultAttributionResolverTest.java
backend/src/test/java/com/colonel/saas/domain/order/infrastructure/OrderPickSourceMappingAdapterTest.java
backend/src/test/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicyTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScopeTest.java
backend/src/test/java/com/colonel/saas/domain/product/policy/PromotionAttributionOwnerPolicyTest.java
backend/src/test/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacadeBoundaryTest.java
backend/src/test/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacadeTest.java
backend/src/test/java/com/colonel/saas/mapper/ColonelsettlementOrderMapperXmlTest.java
backend/src/test/java/com/colonel/saas/service/AttributionOwnerReconciliationServiceTest.java
backend/src/test/java/com/colonel/saas/service/CharacterizationBaselineTest.java
backend/src/test/java/com/colonel/saas/service/OrderAttributionReplayServiceTest.java
backend/src/test/java/com/colonel/saas/service/PickSourceMappingServiceTest.java
backend/src/test/resources/db/mapper-integration-schema.sql
docs//351/242/206/345/237/237//344/270/232/347/273/251/345/237/237.md
docs//351/242/206/345/237/237//350/256/242/345/215/225/345/237/237.md
docs//351/252/214/346/224/266/real-pre/350/201/224/350/260/203/346/211/213/345/206/214.md
docs/00-V1/350/214/203/345/233/264/345/206/273/347/273/223/350/257/264/346/230/216.md
docs/02-V1/344/270/232/345/212/241/346/265/201/347/250/213/344/270/216/351/242/206/345/237/237/350/256/276/350/256/241.md
docs/03-/351/241/271/347/233/256/345/211/251/344/275/231/344/272/213/351/241/271/344/270/216/344/273/273/345/212/241/347/234/213/346/235/277.md
docs/04-/344/270/212/347/272/277/351/252/214/346/224/266/346/270/205/345/215/225.md
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution.md
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution-01-schema-link.md
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution-02-order-resolution.md
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution-03-performance-replay.md
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution-04-reconcile-frontend-docs.md
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution-05-verification-rollout.md
frontend/src/views/data/OrderDetailTab.test.ts
frontend/src/views/data/OrderDetailTab.vue
frontend/src/views/product/product-actions.test.ts
frontend/src/views/product/product-actions.ts
frontend/src/views/product/ProductLibrary.test.ts
frontend/src/views/product/ProductLibrary.vue
frontend/src/views/product/product-library-display.test.ts
frontend/src/views/product/product-library-display.ts
harness/reports/current/latest-role-aware-link-attribution-plan.md
harness/rules/instructions/domain/order-domain.md
harness/rules/instructions/domain/performance-domain.md
harness/scripts/commands/deploy-remote.ps1
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    52 seconds ago   Up 37 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   50 seconds ago   Up 15 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   53 seconds ago   Up 48 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      25 hours ago     Up 25 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 16 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 37 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 48 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 25 hours (healthy)     6379/tcp
campus_frontend                   Up 2 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
