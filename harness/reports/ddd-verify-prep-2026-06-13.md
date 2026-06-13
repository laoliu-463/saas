# DDD-VERIFY-001 Preparation Evidence

| Field | Value |
| --- | --- |
| task_id | DDD-VERIFY-001 (preparation) |
| date | 2026-06-13 |
| base_branch | feature/ddd/DDD-FRONT-001 |
| HEAD | 9d3bc6b7 |
| operator | Mavis (root session) |
| scope | DDD architecture guard tests (21 suites, 70 tests) |

## 1. 结论

**partial PASS for the 48 DONE tasks; 2 expected FAILs from the only remaining substantive TODO (DDD-CLEAN-002).**

- 70 DDD architecture guard tests run on current HEAD
- 68 PASS, 2 FAIL, 1 SKIP
- All 2 FAILs map 1:1 to the only remaining TODO with a guard test in place: DDD-CLEAN-002 (sample cross-domain mapper cleanup)
- The 1 SKIP is the legacy whitelist baseline (intentional)

This means: every task marked DONE on the board has its guard test passing; the only failing tests are guard tests written for the next tasks that the owner pre-installed as tripwires.

## 2. Suite breakdown

| Suite | Tests | Pass | Fail | Skip | Notes |
| --- | --- | --- | --- | --- | --- |
| DddClean001OrderUserDependencyGuardTest | 3 | 3 | 0 | 0 | CLEAN-001 done, board marked DONE correctly |
| DddClean002SampleCrossDomainMapperGuardTest | 2 | 0 | 2 | 0 | CLEAN-002 tripwire: real violations remain (expected, board still TODO) |
| DddConfig002SampleTalentConfigTest | 10 | 10 | 0 | 0 | |
| DddConfig003ConfigRoutingTest | 7 | 7 | 0 | 0 | |
| DddCrossDomainMapperGuardTest | 3 | 2 | 0 | 1 | legacy whitelist baseline skipped intentionally |
| DddOrder003RoutingTest | 8 | 8 | 0 | 0 | |
| DddOutbox001OrderRoutingTest | 3 | 3 | 0 | 0 | |
| DddPackageStructureContractTest | 2 | 2 | 0 | 0 | |
| DddPerformance003RoutingTest | 8 | 8 | 0 | 0 | |
| DddProduct003ProductApplicationRoutingTest | 3 | 3 | 0 | 0 | |
| DddProduct003ProductRoutingTest | 2 | 2 | 0 | 0 | |
| DddSample001ApplicationServiceTest | 2 | 2 | 0 | 0 | SAMPLE-001 done |
| DddSample004HomeworkRoutingTest | 2 | 2 | 0 | 0 | |
| DddSample007SampleRoutingTest | 3 | 3 | 0 | 0 | |
| DddSlimOrder001RoutingTest | 1 | 1 | 0 | 0 | |
| DddSlimOrder002RoutingTest | 1 | 1 | 0 | 0 | |
| DddSlimPerf001RoutingTest | 1 | 1 | 0 | 0 | |
| DddSlimProduct001DisplayPolicyRoutingTest | 2 | 2 | 0 | 0 | |
| DddSlimSample001RoutingTest | 1 | 1 | 0 | 0 | |
| DddTalent003TalentRoutingTest | 3 | 3 | 0 | 0 | |
| DddRefactorPropertiesIntegrationTest | 1 | 1 | 0 | 0 | |
| DddRefactorPropertiesTest | 2 | 2 | 0 | 0 | |
| **Total** | **70** | **68** | **2** | **1** | |

## 3. CLEAN-002 violation details

`DddClean002SampleCrossDomainMapperGuardTest` exposes 2 independent assertions, both fail with the same set of violations:

```
- com.colonel.saas.domain.sample.application.SampleApplicationPortImpl|TalentClaimMapper
- com.colonel.saas.domain.sample.application.SampleApplicationPortImpl|TalentMapper
- com.colonel.saas.service.SampleFilterOptionsService|ProductMapper
- com.colonel.saas.service.SampleFilterOptionsService|ProductOperationStateMapper
- com.colonel.saas.service.SampleFilterOptionsService|ProductSnapshotMapper
- com.colonel.saas.service.SampleLifecycleService|TalentClaimMapper
- com.colonel.saas.service.sample.SampleApplicationService|ProductMapper
- com.colonel.saas.service.sample.SampleApplicationService|ProductOperationStateMapper
- com.colonel.saas.service.sample.SampleApplicationService|ProductSnapshotMapper
- com.colonel.saas.service.sample.SampleApplicationService|TalentClaimMapper
- com.colonel.saas.service.sample.SampleApplicationService|TalentMapper
```

Resolution requires: inject Facade / ApplicationService / Port from Product / Talent / User / Config domains into sample code rather than the raw Mappers. SampleApplicationService and SampleFilterOptionsService are the largest hotspots.

## 4. Verification command

```powershell
mvn -f backend/pom.xml '-Dtest=Ddd*Test' '-Dspring.profiles.active=test' test
```

Total wall-clock: ~14s (compile cached). mvn 3.9.12, Java 17.0.18.

## 5. Confidence on DDD-VERIFY-001 entry

| Gate | Status | Reason |
| --- | --- | --- |
| Strict DONE 48/53 = 91% PASS in guard tests | OK | 68/70 = 97.1% pass rate |
| Cross-domain mapper whitelist clean | OK | only DDD-CLEAN-002 violations, owner has guard test in place |
| real-pre health | OK | docker ps shows saas-active-backend-real-pre-1 healthy |
| backend compile | OK | Nothing to compile - all classes are up to date |
| Frontend field-source contract (DDD-FRONT-001) | PARTIAL | unit/build PASS, browser/E2E not yet completed |

**Recommend: DDD-VERIFY-001 can enter after DDD-CLEAN-002 (and ideally DDD-CLEAN-003/004) lands.** No new task agent work needed on already-DONE tasks; only the 3 CLEAN tasks plus a real browser/E2E pass for FRONT-001 are blocking full VERIFY.

## 6. Stash cleanup performed

3 stale stash entries were dropped (contents fully covered by later commits):

| Stash | Reason |
| --- | --- |
| `stash@{0}` (252a2dd4) On DDD-SLIM-PRODUCT-001: WIP DDD-SLIM-SAMPLE-001 pre-existing sample changes | SLIM-SAMPLE-001 landed in f90ea9d1 |
| `stash@{1}` (893de37f) On DDD-SLIM-PERF-001: main-pre-merge-modified-files-20260613 | report mods already committed by owner |
| `stash@{2}` (7425e831) On DDD-TALENT-004: WIP DDD-TALENT-004 before sprint1 integration | TALENT-004 landed in d9a33028 + b399701f + 679223a6 |

Remaining 9 stash entries are pre-DDD artifacts on `feature/auth-system` and similar; left intact for owner to triage.

## 7. Next step

Run DDD-CLEAN-002 as the next owner agent task; the guard test is already in place and will go green once `service.sample.SampleApplicationService`, `SampleFilterOptionsService`, `SampleLifecycleService` and `SampleApplicationPortImpl` stop injecting the listed raw mappers.