# Phase 2 Task 2B：AuthorizationPrincipal 应用服务与数据库适配

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须在同任务 A 分片之后执行。

- [ ] **Step 4: Add the application service and database adapter**

```java
@Service
public class AuthorizationPrincipalApplicationService implements AuthorizationPrincipalFacade {

    private final AuthorizationPrincipalStore store;

    public AuthorizationPrincipalApplicationService(AuthorizationPrincipalStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizationPrincipal requireCurrent(UUID userId, Long tokenAuthzVersion) {
        if (userId == null || tokenAuthzVersion == null || tokenAuthzVersion < 1) {
            throw new AuthorizationTokenRejectedException();
        }
        try {
            AuthorizationPrincipal principal = store.loadLoginEligible(userId)
                    .orElseThrow(AuthorizationTokenRejectedException::new);
            if (principal.authzVersion() != tokenAuthzVersion.longValue()) {
                throw new AuthorizationTokenRejectedException();
            }
            return principal;
        } catch (AuthorizationTokenRejectedException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new AuthorizationUnavailableException(exception);
        }
    }
}
```

`SysAuthorizationPrincipalStoreAdapter` uses `SysUserMapper.selectById`, rejects deleted or non-login states, and maps only the five principal fields:

```java
@Component
public class SysAuthorizationPrincipalStoreAdapter implements AuthorizationPrincipalStore {

    private final SysUserMapper sysUserMapper;

    public SysAuthorizationPrincipalStoreAdapter(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Optional<AuthorizationPrincipal> loadLoginEligible(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !SysUserStatus.canLogin(user.getStatus())
                || user.getAuthzVersion() == null || user.getAuthzVersion() < 1) {
            return Optional.empty();
        }
        return Optional.of(new AuthorizationPrincipal(
                user.getId(),
                user.getDeptId(),
                user.getUsername(),
                user.getAuthzVersion(),
                SysUserStatus.isPendingActivation(user.getStatus())));
    }
}
```

Add the entity field:

```java
@TableField("authz_version")
private Long authzVersion = 1L;
```

- [ ] **Step 5: Run GREEN and commit**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationPrincipalApplicationServiceTest"
Pop-Location
git add backend/src/main/java/com/colonel/saas/entity/SysUser.java backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationPrincipal.java backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationTokenRejectedException.java backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationUnavailableException.java backend/src/main/java/com/colonel/saas/domain/user/facade/AuthorizationPrincipalFacade.java backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationPrincipalStore.java backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationPrincipalApplicationService.java backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationPrincipalStoreAdapter.java backend/src/test/java/com/colonel/saas/domain/user/application/AuthorizationPrincipalApplicationServiceTest.java
git commit -m "feat(auth): resolve versioned authorization principals"
```

Expected: principal tests PASS. Do not restart backend before Task 7 migration is explicitly approved and applied.
