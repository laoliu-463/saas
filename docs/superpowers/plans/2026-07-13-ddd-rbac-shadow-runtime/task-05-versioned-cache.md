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

- [ ] **Step 4: Add the cache port and Redis adapter**

```java
public interface AuthorizationSnapshotCache {
    Optional<AuthorizationSnapshot> get(UUID userId, long authzVersion);
    void put(AuthorizationSnapshot snapshot, Duration ttl);
    void evict(UUID userId, long authzVersion);
}
```

`RedisAuthorizationSnapshotCacheAdapter` must use `ObjectMapper.writeValueAsString/readValue`, `RedisTemplate<String,Object>.opsForValue()`, and the exact key builder:

```java
static String key(UUID userId, long authzVersion) {
    return "authz:snapshot:" + userId + ":" + authzVersion;
}
```

On malformed JSON it logs only key/user/version metadata, deletes that key, and returns `Optional.empty()`. Redis connectivity exceptions propagate to the decorator so it can distinguish cache failure from a normal miss.

- [ ] **Step 5: Add the @Primary decorator**

```java
@Primary
@Component
public class VersionedAuthorizationSnapshotStore implements AuthorizationSnapshotStore {

    private final SysAuthorizationSnapshotStoreAdapter databaseStore;
    private final AuthorizationSnapshotCache cache;
    private final AuthorizationRuntimeProperties properties;

    public VersionedAuthorizationSnapshotStore(
            SysAuthorizationSnapshotStoreAdapter databaseStore,
            AuthorizationSnapshotCache cache,
            AuthorizationRuntimeProperties properties) {
        this.databaseStore = databaseStore;
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public Optional<AuthorizationSnapshot> loadActiveSnapshot(UUID userId, long authzVersion) {
        try {
            Optional<AuthorizationSnapshot> cached = cache.get(userId, authzVersion);
            if (cached.isPresent()) {
                return cached;
            }
        } catch (RuntimeException cacheFailure) {
            // Redis is an optimization; PostgreSQL remains authoritative.
        }
        try {
            Optional<AuthorizationSnapshot> loaded =
                    databaseStore.loadActiveSnapshot(userId, authzVersion);
            loaded.ifPresent(snapshot -> {
                try {
                    cache.put(snapshot, properties.getSnapshotCacheTtl());
                } catch (RuntimeException ignored) {
                    // DB success remains usable; cache write failure is observable only in logs/metrics.
                }
            });
            return loaded;
        } catch (RuntimeException databaseFailure) {
            if (databaseFailure instanceof AuthorizationUnavailableException unavailable) {
                throw unavailable;
            }
            throw new AuthorizationUnavailableException(databaseFailure);
        }
    }
}
```

Implementation must log cache failures with exception class and key metadata; it must not log token, password, permissions JSON, or stack traces at INFO.

- [ ] **Step 6: Update Phase 1 tests and run GREEN**

Update all direct calls to pass the expected version from their fixtures. Update `AuthorizationApplicationServiceTest` to build an `AuthorizationPrincipal` instead of passing a UUID. Then run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=VersionedAuthorizationSnapshotStoreTest,RedisAuthorizationSnapshotCacheAdapterTest,AuthorizationApplicationServiceTest,SysAuthorizationSnapshotStoreAdapterTest,AuthorizationSnapshotStoreIntegrationTest"
Pop-Location
```

Expected: PASS; stale expected version returns empty and cannot read a newer snapshot.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/colonel/saas/domain/user/facade/AuthorizationFacade.java backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationSnapshotStore.java backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationSnapshotCache.java backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationApplicationService.java backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapter.java backend/src/main/java/com/colonel/saas/domain/user/infrastructure/RedisAuthorizationSnapshotCacheAdapter.java backend/src/main/java/com/colonel/saas/domain/user/infrastructure/VersionedAuthorizationSnapshotStore.java backend/src/main/java/com/colonel/saas/mapper/AuthorizationSnapshotMapper.java backend/src/test/java/com/colonel/saas/domain/user/application/AuthorizationApplicationServiceTest.java backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapterTest.java backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationSnapshotStoreIntegrationTest.java backend/src/test/java/com/colonel/saas/domain/user/infrastructure/VersionedAuthorizationSnapshotStoreTest.java backend/src/test/java/com/colonel/saas/domain/user/infrastructure/RedisAuthorizationSnapshotCacheAdapterTest.java
git commit -m "feat(auth): cache authorization snapshots by version"
```

