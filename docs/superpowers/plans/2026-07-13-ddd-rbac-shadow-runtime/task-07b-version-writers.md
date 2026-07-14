# Phase 2 Task 7B：授权事实写入口接线

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

- [ ] **Step 4: Publish a transaction-bound event and evict after commit**

```java
public record AuthorizationVersionChangedEvent(
        List<AuthorizationVersionStore.VersionChange> changes,
        String cause,
        UUID actorUserId) {

    public AuthorizationVersionChangedEvent {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
```

```java
@Service
public class AuthorizationVersionApplicationService {

    private final AuthorizationVersionStore store;
    private final ApplicationEventPublisher publisher;

    public AuthorizationVersionApplicationService(
            AuthorizationVersionStore store,
            ApplicationEventPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    public void incrementUser(UUID userId, String cause, UUID actorUserId) {
        requireTransaction();
        publish(store.incrementUser(userId), cause, actorUserId);
    }

    public void incrementUsersByRole(UUID roleId, String cause, UUID actorUserId) {
        requireTransaction();
        publish(store.incrementUsersByRole(roleId), cause, actorUserId);
    }

    private void publish(
            List<AuthorizationVersionStore.VersionChange> changes,
            String cause,
            UUID actorUserId) {
        if (!changes.isEmpty()) {
            publisher.publishEvent(new AuthorizationVersionChangedEvent(
                    changes, cause, actorUserId));
        }
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "authorization version change requires an active transaction");
        }
    }
}
```

```java
@Component
public class AuthorizationVersionCacheEvictListener {

    private final AuthorizationSnapshotCache cache;

    public AuthorizationVersionCacheEvictListener(AuthorizationSnapshotCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVersionChanged(AuthorizationVersionChangedEvent event) {
        for (AuthorizationVersionStore.VersionChange change : event.changes()) {
            try {
                cache.evict(change.userId(), change.previousVersion());
            } catch (RuntimeException cacheFailure) {
                // Log identifiers and exception class only. DB version already prevents stale-token reuse.
            }
        }
    }
}
```

- [ ] **Step 5: Wire every confirmed authorization-fact writer in its existing transaction**

Use these exact causes and conditions:

```java
// SysUserRoleAssignmentApplicationService, immediately after replaceUserRoles
authorizationVersionService.incrementUser(id, "USER_ROLES_REPLACED", currentUserId);
```

```java
// SysRoleApplication.update, immediately after updateById
authorizationVersionService.incrementUsersByRole(id, "ROLE_UPDATED", currentUserId);
```

```java
// SysMenuService.assignMenusToRole, only inside oldHash != newHash
authorizationVersionService.incrementUsersByRole(
        roleId, "ROLE_MENU_PERMISSIONS_UPDATED", currentUserId);
```

```java
// SysUserCRUDApplicationB.update, only status or effective dept changed
if (!Objects.equals(previousStatus, updatedUser.status())
        || !Objects.equals(previousDeptId, updatedUser.deptId())) {
    authorizationVersionService.incrementUser(
            id, "USER_AUTHORIZATION_CONTEXT_UPDATED", currentUserId);
}
```

Add `@Transactional(rollbackFor = Exception.class)` to `update` and `resetPassword`. Reset password increments with cause `USER_PASSWORD_RESET` so existing access and refresh tokens are invalidated. Delete does not need a version increment because the authoritative principal lookup returns no login-eligible user after soft delete.

In both group-membership loops, after a real dept change and in the same outer transaction:

```java
authorizationVersionService.incrementUser(
        user.getId(), "USER_GROUP_MEMBERSHIP_UPDATED", currentUserId);
```

Do not put version increments in Mapper adapters or asynchronous listeners; callers need one visible transaction boundary.

- [ ] **Step 6: Run GREEN, including rollback assertions**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationVersionStoreIntegrationTest,AuthorizationVersionApplicationServiceTest,SysUserRoleAssignmentApplicationServiceTest,SysRoleApplicationTest,SysMenuServiceTest,SysUserCRUDApplicationBTest,SysUserGroupMembershipApplicationTest"
Pop-Location
```

Expected: PASS; rolled-back writes leave the previous version and do not evict cache.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationVersionStore.java backend/src/main/java/com/colonel/saas/domain/user/event/AuthorizationVersionChangedEvent.java backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationVersionApplicationService.java backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationVersionStoreAdapter.java backend/src/main/java/com/colonel/saas/domain/user/infrastructure/AuthorizationVersionCacheEvictListener.java backend/src/main/java/com/colonel/saas/mapper/AuthorizationVersionMapper.java backend/src/main/java/com/colonel/saas/mapper/projection/AuthorizationVersionChangeRow.java backend/src/main/java/com/colonel/saas/domain/user/application/SysUserRoleAssignmentApplicationService.java backend/src/main/java/com/colonel/saas/domain/user/application/SysRoleApplication.java backend/src/main/java/com/colonel/saas/auth/service/SysMenuService.java backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationB.java backend/src/main/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplication.java backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationVersionStoreIntegrationTest.java backend/src/test/java/com/colonel/saas/domain/user/application/AuthorizationVersionApplicationServiceTest.java backend/src/test/java/com/colonel/saas/domain/user/application/SysUserRoleAssignmentApplicationServiceTest.java backend/src/test/java/com/colonel/saas/domain/user/application/SysRoleApplicationTest.java backend/src/test/java/com/colonel/saas/auth/service/SysMenuServiceTest.java backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationBTest.java backend/src/test/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplicationTest.java
git commit -m "feat(auth): advance authorization version transactionally"
```

