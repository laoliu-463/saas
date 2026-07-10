# Y-4 Optimistic Lock Conflict Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure commission-rule update and delete operations return a conflict instead of reporting success when MyBatis-Plus optimistic locking updates zero rows.

**Architecture:** Keep `CommissionRule` on the existing `VersionedEntity` path and reuse `OptimisticLockSupport` at the service write boundary. Do not change API contracts, rule selection, schema, or commission formulas. Add focused service behavior tests before the minimal implementation.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, JUnit 5, Mockito, AssertJ, Maven.

---

### Task 1: Add failing conflict behavior tests

**Files:**
- Modify: `backend/src/test/java/com/colonel/saas/service/CommissionRuleServiceTest.java`

- [x] **Step 1: Add update conflict coverage**

```java
@Test
void update_shouldThrowConflictWhenOptimisticLockUpdatesNoRows() {
    UUID id = UUID.randomUUID();
    CommissionRule existing = activeRule(
            CommissionRuleService.DIMENSION_ACTIVITY,
            "A-1",
            CommissionRuleService.TYPE_CHANNEL,
            "0.18");
    existing.setId(id);
    existing.setVersion(5);
    CommissionRule request = activeRule(
            CommissionRuleService.DIMENSION_PRODUCT,
            "P-1",
            CommissionRuleService.TYPE_RECRUITER,
            "0.25");
    when(commissionRuleMapper.selectById(id)).thenReturn(existing);
    when(commissionRuleMapper.updateById(any(CommissionRule.class))).thenReturn(0);

    assertThatThrownBy(() -> service.update(id, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("提成规则已被他人修改");
}
```

- [x] **Step 2: Add delete conflict coverage**

```java
@Test
void delete_shouldThrowConflictWhenOptimisticLockUpdatesNoRows() {
    UUID id = UUID.randomUUID();
    CommissionRule existing = activeRule(
            CommissionRuleService.DIMENSION_ACTIVITY,
            "A-1",
            CommissionRuleService.TYPE_CHANNEL,
            "0.18");
    existing.setId(id);
    existing.setVersion(5);
    when(commissionRuleMapper.selectById(id)).thenReturn(existing);
    when(commissionRuleMapper.updateById(any(CommissionRule.class))).thenReturn(0);

    assertThatThrownBy(() -> service.delete(id))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("提成规则已被他人修改");
}
```

- [x] **Step 3: Run the tests and confirm RED**

Run:

```powershell
mvn -f backend/pom.xml -q -DforkCount=0 test "-Dtest=CommissionRuleServiceTest#update_shouldThrowConflictWhenOptimisticLockUpdatesNoRows+delete_shouldThrowConflictWhenOptimisticLockUpdatesNoRows"
```

Expected: both tests fail because the service currently returns normally when `updateById` returns `0`.

### Task 2: Enforce optimistic-lock results

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/CommissionRuleService.java`

- [x] **Step 1: Import the existing guard**

```java
import com.colonel.saas.common.exception.OptimisticLockSupport;
```

- [x] **Step 2: Guard update and delete writes**

Replace each bare mapper update with:

```java
OptimisticLockSupport.requireUpdated(
        commissionRuleMapper.updateById(existing),
        "提成规则已被他人修改，请刷新后重试");
```

- [x] **Step 3: Run focused GREEN verification**

Run:

```powershell
mvn -f backend/pom.xml -q -DforkCount=0 test "-Dtest=CommissionRuleServiceTest,OptimisticLockSupportTest"
```

Expected: Maven exits `0`; conflict and existing create/update/delete/resolve tests pass.

### Task 3: Verify Y-4 and runtime loading

**Files:**
- Update after proof: `docs/ddd-completion-evidence-matrix.md`
- Update after proof: `harness/rules/state/snapshots/DOMAIN_STATUS.md`
- Update after proof: `harness/rules/state/snapshots/KNOWN_ISSUES.md`
- Update after proof: `harness/reports/latest-evidence-20260710.md`

- [x] **Step 1: Run the Y-4 evidence set**

```powershell
mvn -f backend/pom.xml -q -DforkCount=0 test "-Dtest=DddPerformanceCommissionRuleVersionContractTest,DddPerformanceConfigConsumptionContractTest,DddConfig003ConfigRoutingTest,CommissionRuleServiceTest,CommissionServiceTest,OptimisticLockSupportTest"
```

- [x] **Step 2: Run compile and architecture guards**

```powershell
mvn -f backend/pom.xml -q -DskipTests compile
mvn -f backend/pom.xml -q -DforkCount=0 test "-Dtest=DddArchitectureRedlineGuardTest"
```

- [x] **Step 3: Package, reload local real-pre backend, and check health**

Because `agent-do.ps1` currently violates the registered dirty batch boundary, run its fixed component scripts explicitly and record the bypass:

```powershell
mvn -f backend/pom.xml -DskipTests package
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\restart-compose.ps1 -Env real-pre -Scope backend
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\verify-local.ps1 -Env real-pre -Scope backend
```

- [x] **Step 4: Update evidence only after all proving commands finish**

Restore Y-4 to `DONE` only if the conflict tests, Y-4 evidence set, compile, backend package, container restart, and health all pass. Keep real-pre migration and real order E2E as explicit remaining risks.

- [x] **Step 5: Commit dependency-safe Y-4 batches**

Execution review found that the Java contract test depends on the migration content, so the original two-file commit plan was not self-contained. The completed order is:

```text
4bb8ce1c fix(performance): add commission rule version migration
3ed74608 fix(performance): enforce commission rule optimistic lock
```

Additional TDD review coverage proved that a client-supplied create version could be persisted; creation now forces version 1 and the regression test submits version 99.
