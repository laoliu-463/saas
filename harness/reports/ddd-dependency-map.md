# DDD Cross-Domain Dependency Map (DDD-BASE-003)

Update: 2026-06-10
Scope: `backend/src/main/java` - Service / Controller / Listener / Job `private final *Mapper` field injections
Guard: `DddCrossDomainMapperGuardTest` + `ddd/cross-domain-mapper-legacy-whitelist.txt`

## 1. Summary

| Metric | Count |
| --- | ---: |
| Frozen cross-domain Mapper injection edges | 48 |
| God Service (>800 lines) | 13 |
| Controller directly injecting Mapper | 3 |
| Data scope implementation paths | 3 |

**Rule**: 48 legacy cross-domain edges in whitelist are allowed; **new cross-domain Mapper injections are forbidden** (CI guarded by `DddCrossDomainMapperGuardTest`).

## 2. SysUserMapper Cross-Domain Injections (High Priority Migration)

| Consumer | Domain | Migration Target |
| --- | --- | --- |
| `OrderController` | Order | `UserDomainFacade` |
| `SampleApplicationService` | Sample | `UserDomainFacade` (DDD-USER-003) |
| `SampleFilterOptionsService` | Sample | `UserDomainFacade` |
| `ProductService` | Product | `UserDomainFacade` |
| `TalentService` / `TalentQueryService` | Talent | `UserDomainFacade` |
| `DataApplicationService` | Analytics | `UserDomainFacade` (DDD-USER-004) |
| `ExclusiveMerchantQueryService` | Performance | `UserDomainFacade` |
| `MerchantService` / `OperationLogService` | Ops/Infra | Future `UserQueryPort` |
| `ColonelActivityController` | Product API | `UserDomainFacade` |

User domain legal holders: `auth.*`, `UserDomainService`, `UserMasterDataService`, `SysDeptService` (auth package).

## 3. Order / Product / Talent / Sample Inter-Domain Connections

### Order domain consumes other domain Mappers

| Edge | Notes |
| --- | --- |
| `OrderService` -> `ProductMapper` / `ProductSnapshotMapper` | List enrichment, should use `ProductDomainFacade` |

### Product domain consumes other domain Mappers

| Edge | Notes |
| --- | --- |
| `ProductService` -> `ColonelsettlementOrderMapper` / `PromotionLinkMapper` / `SysUserMapper` | Order stats, promotion links, responsible person |
| `ProductQuickSampleService` -> `SampleRequestMapper` / `TalentMapper` / `TalentClaimMapper` | Quick sample should use `SampleApplicationPort` + `TalentDomainFacade` |
| `AttributionService` -> `PickSourceMappingMapper` / `TalentMapper` / `TalentClaimMapper` | Link attribution & talent validation |

### Sample domain consumes other domain Mappers

| Edge | Notes |
| --- | --- |
| `SampleApplicationService` -> `Product*` / `Talent*` / `SysUserMapper` | Gate checks should use Facade (DDD-SAMPLE-002) |
| `SampleFilterOptionsService` -> `Product*` / `SysUserMapper` | Filter options should use Facade |
| `SampleLifecycleService` -> `TalentClaimMapper` | Homework completion should use event (DDD-SAMPLE-004) |

### Talent domain consumes other domain Mappers

| Edge | Notes |
| --- | --- |
| `TalentService` -> `ColonelsettlementOrderMapper` / `SampleRequestMapper` / `SysUserMapper` | Independent evaluation, sample stats |
| `TalentQueryService` -> `SampleRequestMapper` / `SysUserMapper` | CRM list enrichment |

### Performance / Analytics domain consumes other domain Mappers

| Edge | Notes |
| --- | --- |
| `PerformanceQueryService` / `PerformanceBackfillService` / `PerformanceMonthRecalculationService` -> `ColonelsettlementOrderMapper` | Should use order fact cache or `OrderDomainFacade` |
| `DataApplicationService` -> 6 Mappers (order/performance/talent/merchant/user) | Typical God aggregation, Phase 8 event-driven + Facade |
| `DashboardService` -> `ColonelsettlementOrderMapper` | Dashboard should read summary table or `PerformanceQueryFacade` |

### Events / Gateways / Jobs

| Edge | Notes |
| --- | --- |
| `OrderSyncedEventListener` -> `TalentClaimMapper` | Protection period reset, should use `TalentDomainFacade` |
| `PerformanceRecordSyncListener` -> `ColonelsettlementOrderMapper` | Performance trigger backfill |
| `LogisticsTrackJob` -> `SampleRequestMapper` | Job layer directly accesses sample table |
| `Douyin*Gateway` -> `PickSourceMappingMapper` | Test/contract fixture, future Port-ification |

## 4. Controller Directly Injecting Mapper

| Controller | Injected Mapper | Risk |
| --- | --- | --- |
| `OrderController` | `ColonelsettlementOrderMapper` (same domain), `SysDeptMapper` (cross-domain: User) | Controller should only call Service; `SysDeptMapper` should use `UserDomainFacade` |
| `ColonelActivityController` | `SysUserMapper` (cross-domain: User) | Should use `UserDomainFacade` |
| `DouyinWebhookController` | `ObjectMapper` (Jackson utility, not MyBatis) | No risk |

## 5. God Service Line Count (>800 lines)

| Class | Lines | Main Responsibility Mix |
| --- | ---: | --- |
| `ProductService` | 5107 | Display, sync, link conversion, pin, quick sample, order stats |
| `SampleApplicationService` | 3552 | Apply, review, ship, send-out, permissions |
| `TestDataService` | 2553 | Test data factory (non-production, acceptable) |
| `DataApplicationService` | 2132 | Analytics export, multi-domain Mapper aggregation |
| `TalentService` | 1619 | Claim, exclusive, order/sample integration |
| `TalentQueryService` | 1522 | CRM query, sample/user enrichment |
| `SysUserService` | 1352 | User CRUD + `applyDataScopeFilter` |
| `ProductDisplayRuleService` | 1313 | Display rule config |
| `OrderSyncService` | 1197 | Pagination sync, amounts, attribution, checkpoint |
| `DashboardService` | 1080 | Dashboard SQL aggregation |
| `OrderService` | 1009 | Order query display, product info projection |
| `MerchantService` | 896 | Merchant/recruitment onboarding |
| `PickSourceMappingService` | 887 | Link mapping management |

## 6. Three Data Scope Implementation Paths

| Path | Location | Usage |
| --- | --- | --- |
| AOP annotation | `DataScopeAspect` + `@DataScope` | Controller/Mapper method-level filter |
| User service | `SysUserService.applyDataScopeFilter` | User list/master data query |
| Performance-specific | `PerformanceAccessScope` + `PerformanceAccessContext` | Performance list/summary/export |

**Goal**: Unify via `UserDomainFacade.resolveDataScope()` outputting standard `DataScope`; business domains only consume parsed result (DDD-USER-001~004).

## 7. Domain Boundary Violation Checks

### Does Order domain call Sample domain persistence?

**Yes**. `OrderSyncPersistenceService` directly injects `SampleLifecycleService` and calls `completePendingHomeworkByOrder()` within the order persistence transaction.
- Risk: Sample domain side-effect mixed into order persistence transaction.
- Migration target: Change to `OrderSyncedEvent` consumption or explicit application service orchestration (DDD-EVENT-ORDER-SYNCED-001).

### Does Order domain calculate commission/profit?

**No**. `OrderCommissionPolicy` only provides eligibility check (whether to count toward commission), does not calculate amounts. `OrderDualTrackAmountResolver` parses upstream amount fields but does not compute commission rates. `OrderAttributionService` SQL references `settle_colonel_commission` only for display statistics, not commission calculation.

### Does Product domain directly write sample tables?

**Yes**. `ProductQuickSampleService` injects `SampleRequestMapper` to directly write sample application table.
- Migration target: Change to `SampleApplicationPort` (DDD-PRODUCT-002).

### Does Sample domain directly query Product/Talent/User Mappers?

**Yes**. `SampleApplicationService` injects `ProductMapper`, `ProductSnapshotMapper`, `TalentMapper`, `TalentClaimMapper`, `SysUserMapper`. `SampleFilterOptionsService` injects `ProductMapper`, `ProductSnapshotMapper`, `SysUserMapper`. `SampleLifecycleService` injects `TalentClaimMapper`.
- Migration target: Use `ProductDomainFacade`, `TalentDomainFacade`, `UserDomainFacade` (DDD-SAMPLE-002~004).

### Config domain SystemConfigMapper cross-domain injections

| Consumer | Cross-domain? |
| --- | --- |
| `SysConfigService` | No (config domain internal) |
| `BusinessRuleConfigService` | No (config domain internal) |
| `RuleCenterService` | No (config domain internal) |
| `TalentPresetTagsBootstrap` | Yes (talent reads config, already in whitelist) |

## 8. Rollback & Evolution

- New cross-domain injection -> `DddCrossDomainMapperGuardTest` fails, blocks merge.
- After Facade migration -> Remove corresponding edge from whitelist and update this report.
- Full whitelist: `backend/src/test/resources/ddd/cross-domain-mapper-legacy-whitelist.txt`

## 9. Migration Entry (DDD-USER-001 Landed)

| Component | Path | Notes |
| --- | --- | --- |
| `UserDomainFacade` | `domain/user/facade/UserDomainFacade.java` | Single facade interface |
| `LegacyUserDomainFacade` | `domain/user/facade/LegacyUserDomainFacade.java` | Delegates to `UserDomainService` + `UserMasterDataService` + `SysDeptService` |
| Regression test | `LegacyUserDomainFacadeTest` | admin/all, leader/group, member/self |

**Consumer migration order**: Order domain (USER-002) -> Sample domain (USER-003) -> Performance/Dashboard (USER-004) -> `DDD-CLEAN-001`.

## 10. Next Steps

1. DDD-USER-002: Order domain data scope via `UserDomainFacade`.
2. DDD-CONFIG-001 / DDD-PRODUCT-001 / DDD-TALENT-001: Parallel read-only Facades.
3. After Facade migration, remove corresponding cross-domain edges from whitelist.
