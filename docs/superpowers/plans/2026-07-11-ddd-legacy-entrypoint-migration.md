# DDD Legacy Entrypoint Migration Wave 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route four independent legacy entrypoints through DDD application/port boundaries while preserving the original big-Service behavior and all external contracts.

**Architecture:** Each slice keeps the inbound Controller/Job unchanged at the protocol level, inserts a small application service, and delegates through a capability port to a Legacy adapter backed by the existing Service. The order slice reuses the already-established `OrderSyncApplicationService`, which itself delegates to `OrderSyncService`; no `ddd.refactor.*` default, business rule, DTO, SQL, transaction, permission, or state machine changes are allowed.

**Tech Stack:** Java 17, Spring Boot 3.2, JUnit 5, Mockito, AssertJ, Maven, PostgreSQL/Redis runtime via the existing Harness.

---

## Scope and isolation

- Baseline branch: `codex/ddd-legacy-entrypoint-migration`.
- Baseline test: `mvn test "-Dtest=*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test"` → 258 tests, 0 failures, 0 errors, 1 skipped.
- Runtime Gate: Gate 4, because order and dashboard entrypoints are included.
- No remote real-pre deployment.
- Maven, Docker, database probes, final DDD acceptance, and Harness execution are serialized by the coordinator.
- Implementers must not modify `ProductService`, `TalentService`, `OrderSyncService`, `DashboardService`, SQL, DTOs, configuration defaults, or shared state/report files.

| Slice | Branch | Worktree | Exclusive write set |
| --- | --- | --- | --- |
| Talent release | `codex/ddd-entrypoint-talent-release` | `.worktrees/ddd-entrypoint-talent-release` | talent release application/port/adapter, `TalentClaimReleaseJob`, talent release tests |
| Order HTTP sync | `codex/ddd-entrypoint-order-http-sync` | `.worktrees/ddd-entrypoint-order-http-sync` | `OrderController`, order controller/contract tests only |
| Product sync job | `codex/ddd-entrypoint-product-sync-job` | `.worktrees/ddd-entrypoint-product-sync-job` | product sync application/port/adapter, `ProductActivitySyncJob`, product sync tests |
| Dashboard query | `codex/ddd-entrypoint-dashboard-query` | `.worktrees/ddd-entrypoint-dashboard-query` | analytics query application/port/adapter, `DashboardController`, dashboard tests |

### Task 1: Talent claim-release job boundary

**Files:**
- Create: `backend/src/main/java/com/colonel/saas/domain/talent/application/port/TalentClaimReleasePort.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/talent/application/TalentClaimReleaseApplicationService.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/talent/infrastructure/LegacyTalentClaimReleaseAdapter.java`
- Modify: `backend/src/main/java/com/colonel/saas/job/TalentClaimReleaseJob.java`
- Create: `backend/src/test/java/com/colonel/saas/architecture/DddTalentLegacyEntrypointMigrationTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/job/TalentClaimReleaseJobTest.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/talent/infrastructure/LegacyTalentClaimReleaseAdapterTest.java`

- [ ] **Step 1: Write the failing architecture test**

```java
@Test
void claimReleaseJobShouldDependOnTalentApplicationBoundary() throws IOException {
    String source = Files.readString(Paths.get(System.getProperty("user.dir"))
            .resolve("src/main/java/com/colonel/saas/job/TalentClaimReleaseJob.java"));
    assertThat(source)
            .contains("TalentClaimReleaseApplicationService")
            .contains("talentClaimReleaseApplicationService.releaseExpiredClaims(now)")
            .doesNotContain("private final TalentService")
            .doesNotContain("talentService.releaseExpiredClaims(now)");
}
```

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=DddTalentLegacyEntrypointMigrationTest" test`

Expected: FAIL because `TalentClaimReleaseJob` still injects and calls `TalentService`.

- [ ] **Step 3: Add the application port, application service, and Legacy adapter**

```java
@FunctionalInterface
public interface TalentClaimReleasePort {
    void releaseExpiredClaims(LocalDateTime now);
}
```

```java
@Service
public class TalentClaimReleaseApplicationService {
    private final TalentClaimReleasePort talentClaimReleasePort;

    public TalentClaimReleaseApplicationService(TalentClaimReleasePort talentClaimReleasePort) {
        this.talentClaimReleasePort = talentClaimReleasePort;
    }

    public void releaseExpiredClaims(LocalDateTime now) {
        talentClaimReleasePort.releaseExpiredClaims(now);
    }
}
```

```java
@Component
public class LegacyTalentClaimReleaseAdapter implements TalentClaimReleasePort {
    private final TalentService talentService;

    public LegacyTalentClaimReleaseAdapter(TalentService talentService) {
        this.talentService = talentService;
    }

    @Override
    public void releaseExpiredClaims(LocalDateTime now) {
        talentService.releaseExpiredClaims(now);
    }
}
```

- [ ] **Step 4: Route the job and update behavioral tests**

Replace only the constructor dependency and the single call in `TalentClaimReleaseJob`; retain lock key, TTL, clock capture, logging, and `finally` release unchanged. Update `TalentClaimReleaseJobTest` to mock `TalentClaimReleaseApplicationService`, and add an adapter test that verifies exact `LocalDateTime` pass-through to `TalentService`.

- [ ] **Step 5: Run GREEN and commit**

Run: `mvn "-Dtest=DddTalentLegacyEntrypointMigrationTest,TalentClaimReleaseJobTest,LegacyTalentClaimReleaseAdapterTest" test`

Expected: all selected tests PASS.

Commit: `refactor(talent): route claim release job through application port`

### Task 2: Order HTTP sync entrypoints

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/controller/OrderController.java`
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddOrderSyncEntrypointContractTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/OrderSyncControllerTest.java`

- [ ] **Step 1: Change the contract test first**

Replace the two legacy-call fragments in `DddOrderSyncEntrypointContractTest` with these required fragments while retaining every admin-role and dry-run assertion:

```java
"orderSyncApplicationService.execute(",
"OrderSyncExecutionContext.TASK_INSTITUTE_HOT",
"OrderSyncCommand.historical(start, end, userId)",
"OrderSyncExecutionContext.manual(\"OrderController\")"
```

Also assert that the source no longer contains these calls inside the two HTTP methods:

```java
"orderSyncService.syncInstituteOrdersHotRecent()"
"orderSyncService.syncByTimeRange(start, end)"
```

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=DddOrderSyncEntrypointContractTest" test`

Expected: FAIL because both HTTP methods still call `OrderSyncService` directly.

- [ ] **Step 3: Route both methods through the existing application service**

Inject `OrderSyncApplicationService` into `OrderController`. Preserve operation-log payloads and convert the application result back to the existing response type:

```java
OrderSyncService.SyncResult result = orderSyncApplicationService.execute(
        OrderSyncCommand.scheduledIncremental(),
        OrderSyncExecutionContext.scheduled(OrderSyncExecutionContext.TASK_INSTITUTE_HOT))
        .toLegacySyncResult();
```

```java
OrderSyncService.SyncResult result = orderSyncApplicationService.execute(
        OrderSyncCommand.historical(start, end, userId),
        OrderSyncExecutionContext.manual("OrderController"))
        .toLegacySyncResult();
```

Do not remove `OrderSyncService` yet because `getLastSyncTime()` remains a separate later slice.

- [ ] **Step 4: Update controller tests without weakening behavior assertions**

Mock `OrderSyncApplicationService`, return an `OrderSyncResult` created from the same legacy fixtures, and verify exact `OrderSyncCommand` fields and execution-context task/source. Keep HTTP paths, role annotations, response fields, and operation-log assertions unchanged.

- [ ] **Step 5: Run GREEN and commit**

Run: `mvn "-Dtest=DddOrderSyncEntrypointContractTest,OrderControllerTest,OrderSyncControllerTest,OrderSyncApplicationServiceTest" test`

Expected: all selected tests PASS.

Commit: `refactor(order): route HTTP sync through application service`

### Task 3: Product activity-sync job boundary

**Files:**
- Create: `backend/src/main/java/com/colonel/saas/domain/product/application/port/ProductActivitySyncPort.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/product/application/ProductActivitySyncApplicationService.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/product/infrastructure/LegacyProductActivitySyncAdapter.java`
- Modify: `backend/src/main/java/com/colonel/saas/job/ProductActivitySyncJob.java`
- Create: `backend/src/test/java/com/colonel/saas/architecture/DddProductActivitySyncEntrypointTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/job/ProductActivitySyncJobTest.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/product/infrastructure/LegacyProductActivitySyncAdapterTest.java`

- [ ] **Step 1: Write the failing architecture test**

```java
@Test
void productActivitySyncJobShouldUseProductApplicationBoundary() throws IOException {
    String source = Files.readString(Paths.get(System.getProperty("user.dir"))
            .resolve("src/main/java/com/colonel/saas/job/ProductActivitySyncJob.java"));
    assertThat(source)
            .contains("ProductActivitySyncApplicationService")
            .contains("productActivitySyncApplicationService.refreshActivitySnapshots(")
            .doesNotContain("private final ProductService")
            .doesNotContain("productService.refreshActivitySnapshots(");
}
```

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=DddProductActivitySyncEntrypointTest" test`

Expected: FAIL because the job still depends on `ProductService`.

- [ ] **Step 3: Add a narrow pass-through port and adapter**

```java
@FunctionalInterface
public interface ProductActivitySyncPort {
    ProductService.ActivityProductRefreshResult refreshActivitySnapshots(
            DouyinProductGateway.ActivityProductQueryRequest request);
}
```

```java
@Service
public class ProductActivitySyncApplicationService {
    private final ProductActivitySyncPort productActivitySyncPort;

    public ProductActivitySyncApplicationService(ProductActivitySyncPort productActivitySyncPort) {
        this.productActivitySyncPort = productActivitySyncPort;
    }

    public ProductService.ActivityProductRefreshResult refreshActivitySnapshots(
            DouyinProductGateway.ActivityProductQueryRequest request) {
        return productActivitySyncPort.refreshActivitySnapshots(request);
    }
}
```

```java
@Component
public class LegacyProductActivitySyncAdapter implements ProductActivitySyncPort {
    private final ProductService productService;

    public LegacyProductActivitySyncAdapter(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public ProductService.ActivityProductRefreshResult refreshActivitySnapshots(
            DouyinProductGateway.ActivityProductQueryRequest request) {
        return productService.refreshActivitySnapshots(request);
    }
}
```

- [ ] **Step 4: Route only the job call and preserve operational controls**

Replace the job dependency and call target only. Do not change enable flags, lock ordering, activity-level locks, QPS sleep, page-size limits, completion handling, `touchLastSyncAt`, exception handling, or logging fields. Update the job test and add an adapter delegation test.

- [ ] **Step 5: Run GREEN and commit**

Run: `mvn "-Dtest=DddProductActivitySyncEntrypointTest,ProductActivitySyncJobTest,LegacyProductActivitySyncAdapterTest" test`

Expected: all selected tests PASS.

Commit: `refactor(product): route activity sync job through application port`

### Task 4: Dashboard read boundary

**Files:**
- Create: `backend/src/main/java/com/colonel/saas/domain/analytics/application/port/DashboardQueryPort.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/analytics/application/DashboardQueryApplicationService.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/analytics/infrastructure/LegacyDashboardQueryAdapter.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/DashboardController.java`
- Create: `backend/src/test/java/com/colonel/saas/architecture/DddDashboardEntrypointMigrationTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/DashboardControllerTest.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/analytics/infrastructure/LegacyDashboardQueryAdapterTest.java`

- [ ] **Step 1: Write the failing architecture test**

```java
@Test
void dashboardControllerShouldUseAnalyticsApplicationBoundary() throws IOException {
    String source = Files.readString(Paths.get(System.getProperty("user.dir"))
            .resolve("src/main/java/com/colonel/saas/controller/DashboardController.java"));
    assertThat(source)
            .contains("DashboardQueryApplicationService")
            .contains("dashboardQueryApplicationService.getSummary(")
            .contains("dashboardQueryApplicationService.getActivityProductBreakdown(")
            .doesNotContain("private final DashboardService dashboardService");
}
```

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=DddDashboardEntrypointMigrationTest" test`

Expected: FAIL because `DashboardController` still injects `DashboardService`.

- [ ] **Step 3: Add the read port, application service, and Legacy adapter**

The port preserves the existing response types and exact parameter order:

```java
public interface DashboardQueryPort {
    DashboardService.Summary getSummary(
            LocalDateTime startTime, LocalDateTime endTime,
            UUID userId, UUID deptId, DataScope dataScope);

    DashboardService.ActivityProductPage getActivityProductBreakdown(
            LocalDateTime startTime, LocalDateTime endTime,
            UUID userId, UUID deptId, DataScope dataScope,
            long page, long size);
}
```

`DashboardQueryApplicationService` delegates each method unchanged to the port. `LegacyDashboardQueryAdapter` implements the port by calling the same methods on `DashboardService` with no mapping, catch, retry, or fallback.

- [ ] **Step 4: Route the controller and retain cache/API behavior**

Inject `DashboardQueryApplicationService`. Keep role annotations, paths, request attributes, cache key, 30-second TTL, `PageResult` conversion, and nested legacy response types unchanged. Update controller tests to mock the application service and add an adapter delegation test.

- [ ] **Step 5: Run GREEN and commit**

Run: `mvn "-Dtest=DddDashboardEntrypointMigrationTest,DashboardControllerTest,LegacyDashboardQueryAdapterTest,DddAnalyticsReadOnlyBoundaryTest,DddUserDataScopePolicyDashboardBoundaryTest" test`

Expected: all selected tests PASS.

Commit: `refactor(analytics): route dashboard reads through application port`

### Task 5: Review and serial integration

**Files:**
- Modify only if evidence changes: `docs/ddd-completion-evidence-matrix.md`
- Modify: `harness/rules/state/snapshots/01-当前项目状态.md`
- Modify: `harness/rules/state/snapshots/DOMAIN_STATUS.md`
- Modify: `harness/rules/changelog.md`
- Generated by Harness: `harness/reports/evidence-*.md`, `harness/reports/retro-*.md`

- [ ] **Step 1: Perform two-stage review per branch**

For every branch, dispatch a fresh specification reviewer, resolve all Critical/Important findings, then dispatch a fresh code-quality reviewer. Reviewers must verify that no large Service, API contract, flag default, transaction, permission, SQL, DTO, state machine, lock, or retry behavior changed.

- [ ] **Step 2: Cherry-pick approved commits in fixed order**

Order: talent → order → product → dashboard. After every cherry-pick run `git diff --check` and the slice test command. Resolve no unrelated cleanup in the integration branch.

- [ ] **Step 3: Run the combined backend regression**

Run:

```powershell
mvn "-Dtest=DddTalentLegacyEntrypointMigrationTest,TalentClaimReleaseJobTest,LegacyTalentClaimReleaseAdapterTest,DddOrderSyncEntrypointContractTest,OrderControllerTest,OrderSyncControllerTest,OrderSyncApplicationServiceTest,DddProductActivitySyncEntrypointTest,ProductActivitySyncJobTest,LegacyProductActivitySyncAdapterTest,DddDashboardEntrypointMigrationTest,DashboardControllerTest,LegacyDashboardQueryAdapterTest,DddAnalyticsReadOnlyBoundaryTest,DddUserDataScopePolicyDashboardBoundaryTest" test
```

Expected: all selected tests PASS.

- [ ] **Step 4: Run DDD and Harness gates serially**

Run:

```powershell
mvn test "-Dtest=*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test"
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-ddd-acceptance.ps1 -MaxRedlineDebt 11 -FailOnUnexpectedDirty
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -Message "refactor: migrate DDD legacy entrypoints wave 1"
```

Expected: Maven build/tests, backend/frontend build as selected by Harness, Docker restart, local health, and applicable business probes produce actual evidence. External/sample-dependent checks may be `BLOCKED` or `PARTIAL`; they must not be relabeled `PASS`.

- [ ] **Step 5: Record state, commit reports separately, and push**

Update the DDD matrix and state files only with verified outcomes. Commit integration code separately from docs/reports. Push Gitee first, then GitHub. Do not deploy remote real-pre.

## Self-review result

- Spec coverage: four independent READY/NEEDS_GLUE slices preserve the approved Legacy chain and establish the repeatable migration pattern for later Controller/Job/Listener entries.
- Placeholder scan: clean; every production edit has an exact file, method, command, and expected result.
- Type consistency: every planned method uses existing `TalentService`, `OrderSyncApplicationService`, `ProductService`, and `DashboardService` signatures; external HTTP and job signatures remain unchanged.
- Out of scope: remaining Talent/Product/Order/Data/Outbox/frontend entrypoints form Wave 2+ and are not claimed complete by this plan or Wave 1 evidence.
