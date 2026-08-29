# Phase 2 Task 5：版本化 Redis 授权快照缓存

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

## Task 5: Add versioned Redis snapshot caching

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationSnapshotCache.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/RedisAuthorizationSnapshotCacheAdapter.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/VersionedAuthorizationSnapshotStore.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/facade/AuthorizationFacade.java:7-10`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationSnapshotStore.java:8-11`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationApplicationService.java:14-43`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapter.java:18-43`
- Modify: `backend/src/main/java/com/colonel/saas/mapper/AuthorizationSnapshotMapper.java:14-44`
- Modify: Phase 1 authorization tests using the old signatures
- Create: `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/VersionedAuthorizationSnapshotStoreTest.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/RedisAuthorizationSnapshotCacheAdapterTest.java`

- [ ] **Step 1: Write failing cache behavior tests**

`VersionedAuthorizationSnapshotStoreTest` must prove all four paths:

```java
@Test
void cacheHitDoesNotQueryDatabase() {
    AuthorizationSnapshot snapshot = snapshot(userId, 3L);
    when(cache.get(userId, 3L)).thenReturn(Optional.of(snapshot));

    assertThat(store.loadActiveSnapshot(userId, 3L)).contains(snapshot);
    verifyNoInteractions(databaseStore);
}

@Test
void cacheMissLoadsDatabaseAndWritesVersionedEntry() {
    AuthorizationSnapshot snapshot = snapshot(userId, 3L);
    when(cache.get(userId, 3L)).thenReturn(Optional.empty());
    when(databaseStore.loadActiveSnapshot(userId, 3L)).thenReturn(Optional.of(snapshot));

    assertThat(store.loadActiveSnapshot(userId, 3L)).contains(snapshot);
    verify(cache).put(snapshot, Duration.ofMinutes(5));
}

@Test
void redisFailureFallsBackToDatabase() {
    AuthorizationSnapshot snapshot = snapshot(userId, 3L);
    when(cache.get(userId, 3L)).thenThrow(new RedisConnectionFailureException("down"));
    when(databaseStore.loadActiveSnapshot(userId, 3L)).thenReturn(Optional.of(snapshot));

    assertThat(store.loadActiveSnapshot(userId, 3L)).contains(snapshot);
}

@Test
void cacheAndDatabaseFailureReturnsUnavailable() {
    when(cache.get(userId, 3L)).thenThrow(new RedisConnectionFailureException("down"));
    when(databaseStore.loadActiveSnapshot(userId, 3L))
            .thenThrow(new DataAccessResourceFailureException("down"));

    assertThatThrownBy(() -> store.loadActiveSnapshot(userId, 3L))
            .isInstanceOf(AuthorizationUnavailableException.class);
}
```

`RedisAuthorizationSnapshotCacheAdapterTest` must assert the key is exactly `authz:snapshot:{userId}:{version}`, JSON contains no token/password fields, and corrupted JSON is deleted and treated as a miss.

- [ ] **Step 2: Run RED**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=VersionedAuthorizationSnapshotStoreTest,RedisAuthorizationSnapshotCacheAdapterTest"
Pop-Location
```

Expected: FAIL because cache port/decorator do not exist.

- [ ] **Step 3: Change facade and store signatures**

```java
public interface AuthorizationFacade {
    AuthorizationDecision authorize(AuthorizationPrincipal principal, String permissionCode);
}
```

```java
public interface AuthorizationSnapshotStore {
    Optional<AuthorizationSnapshot> loadActiveSnapshot(UUID userId, long authzVersion);
}
```

`AuthorizationApplicationService.authorize` validates the principal and passes both values:

```java
@Override
@Transactional(readOnly = true)
public AuthorizationDecision authorize(
        AuthorizationPrincipal principal,
        String rawPermissionCode) {
    PermissionCode permission = new PermissionCode(rawPermissionCode);
    if (principal == null) {
        return AuthorizationDecision.deny(
                permission, null, AuthorizationReason.SUBJECT_NOT_ACTIVE);
    }
    return store.loadActiveSnapshot(principal.userId(), principal.authzVersion())
            .map(snapshot -> policy.decide(permission, snapshot))
            .orElseGet(() -> AuthorizationDecision.deny(
                    permission, null, AuthorizationReason.SUBJECT_NOT_ACTIVE));
}
```

The mapper method becomes:

```java
List<AuthorizationSnapshotRow> findActiveSnapshotRows(
        @Param("userId") UUID userId,
        @Param("authzVersion") long authzVersion);
```

and the SQL adds:

```sql
AND u.authz_version = #{authzVersion}
```
