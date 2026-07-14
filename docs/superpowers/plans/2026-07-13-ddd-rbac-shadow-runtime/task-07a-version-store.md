# Phase 2 Task 7A：授权版本存储与事务事件

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

## Task 7: 在授权事实事务内递增 authzVersion

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationVersionStore.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/event/AuthorizationVersionChangedEvent.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationVersionApplicationService.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationVersionStoreAdapter.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/AuthorizationVersionCacheEvictListener.java`
- Create: `backend/src/main/java/com/colonel/saas/mapper/AuthorizationVersionMapper.java`
- Create: `backend/src/main/java/com/colonel/saas/mapper/projection/AuthorizationVersionChangeRow.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/application/SysUserRoleAssignmentApplicationService.java:32-75`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/application/SysRoleApplication.java:40-144`
- Modify: `backend/src/main/java/com/colonel/saas/auth/service/SysMenuService.java:219-266`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationB.java:34-147`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplication.java:26-74`
- Create: `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationVersionStoreIntegrationTest.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/user/application/AuthorizationVersionApplicationServiceTest.java`
- Modify: corresponding five existing application/service tests

- [ ] **Step 1: Write failing store and transaction tests**

The PostgreSQL integration test must prove:

```java
@Test
void incrementUser_returnsPreviousAndCurrentVersion() {
    UUID userId = insertUser(4L);

    List<AuthorizationVersionStore.VersionChange> changes =
            store.incrementUser(userId);

    assertThat(changes).containsExactly(
            new AuthorizationVersionStore.VersionChange(userId, 4L, 5L));
    assertThat(selectVersion(userId)).isEqualTo(5L);
}

@Test
void incrementUsersByRole_updatesOnlyActiveRelations() {
    UUID roleId = insertRole();
    UUID assigned = insertUser(2L);
    UUID unrelated = insertUser(2L);
    assignRole(assigned, roleId, 0);

    assertThat(store.incrementUsersByRole(roleId))
            .extracting(AuthorizationVersionStore.VersionChange::userId)
            .containsExactly(assigned);
    assertThat(selectVersion(assigned)).isEqualTo(3L);
    assertThat(selectVersion(unrelated)).isEqualTo(2L);
}
```

`AuthorizationVersionApplicationServiceTest` is a Spring transaction integration test with `TransactionTemplate` and a mocked `AuthorizationSnapshotCache`: commit a transaction that calls `incrementUser` and verify the `@TransactionalEventListener(AFTER_COMMIT)` evicts only the old key; run a second transaction with `setRollbackOnly()` and verify no additional eviction occurs. A separate plain unit test calls the version service without a transaction and expects `IllegalStateException`.

Each existing write-service test must verify the correct version method is invoked exactly once only when the authorization fact actually changes.

- [ ] **Step 2: Run RED**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationVersionStoreIntegrationTest,AuthorizationVersionApplicationServiceTest,SysUserRoleAssignmentApplicationServiceTest,SysRoleApplicationTest,SysMenuServiceTest,SysUserCRUDApplicationBTest,SysUserGroupMembershipApplicationTest"
Pop-Location
```

Expected: FAIL because version store/service are absent.

- [ ] **Step 3: Add version store contracts and PostgreSQL UPDATE RETURNING**

```java
public interface AuthorizationVersionStore {

    List<VersionChange> incrementUser(UUID userId);
    List<VersionChange> incrementUsersByRole(UUID roleId);

    record VersionChange(UUID userId, long previousVersion, long currentVersion) {
    }
}
```

`AuthorizationVersionMapper` uses PostgreSQL CTEs so mutation and returned versions are one statement:

```java
@Select("""
        WITH changed AS (
            UPDATE sys_user
               SET authz_version = authz_version + 1,
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{userId}
               AND deleted = 0
         RETURNING id AS user_id,
                   authz_version - 1 AS previous_version,
                   authz_version AS current_version
        )
        SELECT user_id, previous_version, current_version FROM changed
        """)
List<AuthorizationVersionChangeRow> incrementUser(@Param("userId") UUID userId);
```

```java
@Select("""
        WITH changed AS (
            UPDATE sys_user u
               SET authz_version = authz_version + 1,
                   update_time = CURRENT_TIMESTAMP
             WHERE u.deleted = 0
               AND EXISTS (
                   SELECT 1
                     FROM sys_user_role ur
                    WHERE ur.user_id = u.id
                      AND ur.role_id = #{roleId}
                      AND ur.deleted = 0
               )
         RETURNING u.id AS user_id,
                   u.authz_version - 1 AS previous_version,
                   u.authz_version AS current_version
        )
        SELECT user_id, previous_version, current_version
          FROM changed
         ORDER BY user_id
        """)
List<AuthorizationVersionChangeRow> incrementUsersByRole(@Param("roleId") UUID roleId);
```

The adapter rejects null IDs with an empty result and maps rows to immutable `VersionChange` values.

