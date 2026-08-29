# Phase 2 Task 8：激活边界守卫

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

## Task 8: Replace dormancy guard with an activation-boundary guard

**Files:**

- Delete: `backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationDormancyContractTest.java`
- Create: `backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationActivationBoundaryContractTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/config/DddAuthorizationRuntimeDefaultsContractTest.java`

- [ ] **Step 1: Write the failing boundary tests**

The replacement guard must scan `src/main/java` and enforce these exact rules:

```java
private static final Set<String> RUNTIME_SERVICE_CONSUMER_ALLOWLIST = Set.of(
        "com/colonel/saas/domain/user/application/AuthorizationRuntimeService.java");

private static final Set<String> PRINCIPAL_FACADE_CONSUMER_ALLOWLIST = Set.of(
        "com/colonel/saas/domain/user/facade/AuthorizationPrincipalFacade.java",
        "com/colonel/saas/domain/user/application/AuthorizationPrincipalApplicationService.java",
        "com/colonel/saas/security/JwtAuthenticationFilter.java");

private static final Set<String> SNAPSHOT_PORT_CONSUMER_ALLOWLIST = Set.of(
        "com/colonel/saas/domain/user/port/AuthorizationSnapshotStore.java",
        "com/colonel/saas/domain/user/application/AuthorizationApplicationService.java",
        "com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapter.java",
        "com/colonel/saas/domain/user/infrastructure/VersionedAuthorizationSnapshotStore.java");

private static final Set<String> AUTHORIZATION_FACADE_CONSUMER_ALLOWLIST = Set.of(
        "com/colonel/saas/domain/user/facade/AuthorizationFacade.java",
        "com/colonel/saas/domain/user/application/AuthorizationApplicationService.java",
        "com/colonel/saas/domain/user/application/AuthorizationRuntimeService.java");
```

The tests assert:

```java
@Test
void phaseTwoRuntimeMustHaveNoBusinessRequestConsumer() throws IOException {
    assertThat(findConsumers("AuthorizationRuntimeService"))
            .containsExactlyElementsOf(RUNTIME_SERVICE_CONSUMER_ALLOWLIST);
}

@Test
void principalResolutionMustStayAtSecurityBoundary() throws IOException {
    assertThat(findConsumers("AuthorizationPrincipalFacade"))
            .containsExactlyInAnyOrderElementsOf(PRINCIPAL_FACADE_CONSUMER_ALLOWLIST);
}

@Test
void controllersAndAspectsMustNotReadSnapshotPortDirectly() throws IOException {
    assertThat(findConsumers("AuthorizationSnapshotStore"))
            .containsExactlyInAnyOrderElementsOf(SNAPSHOT_PORT_CONSUMER_ALLOWLIST);
}

@Test
void authorizationFacadeMustHaveOneRuntimeCoordinator() throws IOException {
    assertThat(findConsumers("AuthorizationFacade"))
            .containsExactlyInAnyOrderElementsOf(AUTHORIZATION_FACADE_CONSUMER_ALLOWLIST);
}
```

As in the Phase 1 guard, add detector self-tests under `@TempDir` so comments/strings limitations remain visible and the guard cannot silently become a no-op.

- [ ] **Step 2: Run RED**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=DddAuthorizationActivationBoundaryContractTest,DddAuthorizationRuntimeDefaultsContractTest"
Pop-Location
```

Expected: FAIL before the old dormancy guard is replaced and exact consumers are established.

- [ ] **Step 3: Implement the guard and retain the accepted lexical limitation**

Rename the test class/file, replace the Phase 1 foundation allowlist with the three Phase 2 consumer allowlists, and retain this explicit comment:

```java
// This lexical guard catches direct source references only. It is an architecture
// regression detector, not a runtime security boundary; reflection and dynamic
// loading remain covered by integration/security tests.
```

The YAML contract also asserts that no checked-in profile contains a domain configured as `ENFORCE`.

- [ ] **Step 4: Run the complete focused RBAC suite**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=DddAuthorizationSchemaContractTest,DddAuthorizationActivationBoundaryContractTest,DddAuthorizationRuntimeDefaultsContractTest,AuthorizationDecisionPolicyTest,AuthorizationApplicationServiceTest,AuthorizationPrincipalApplicationServiceTest,JwtTokenProviderAuthorizationVersionTest,JwtAuthenticationFilterTest,VersionedAuthorizationSnapshotStoreTest,RedisAuthorizationSnapshotCacheAdapterTest,AuthorizationRuntimeServiceTest,AuthorizationDifferenceLoggerTest,AuthorizationVersionStoreIntegrationTest,AuthorizationVersionApplicationServiceTest,SysAuthorizationSnapshotStoreAdapterTest,AuthorizationSnapshotStoreIntegrationTest,AuthorizationSchemaMigrationIntegrationTest,GlobalExceptionHandlerTest,DddArchitectureRedlineGuardTest"
Pop-Location
```

Expected: PASS with zero failures/errors. Record exact tests/skips from Surefire XML; do not copy an expected count into evidence.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationDormancyContractTest.java backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationActivationBoundaryContractTest.java backend/src/test/java/com/colonel/saas/config/DddAuthorizationRuntimeDefaultsContractTest.java
git commit -m "test(auth): guard phase two activation boundaries"
```

