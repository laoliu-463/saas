# Phase 2 Task 2：数据库权威 AuthorizationPrincipal

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

## Task 2: 建立数据库权威的 AuthorizationPrincipal

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationPrincipal.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationTokenRejectedException.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationUnavailableException.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/facade/AuthorizationPrincipalFacade.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationPrincipalStore.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationPrincipalApplicationService.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationPrincipalStoreAdapter.java`
- Modify: `backend/src/main/java/com/colonel/saas/entity/SysUser.java:58-92`
- Test: `backend/src/test/java/com/colonel/saas/domain/user/application/AuthorizationPrincipalApplicationServiceTest.java`

- [ ] **Step 1: Write failing principal tests**

Cover exactly these cases:

```java
@Test
void requireCurrent_returnsDatabasePrincipalWhenVersionMatches() {
    UUID userId = UUID.randomUUID();
    AuthorizationPrincipal current = new AuthorizationPrincipal(userId, UUID.randomUUID(), "alice", 7L, false);
    when(store.loadLoginEligible(userId)).thenReturn(Optional.of(current));

    assertThat(service.requireCurrent(userId, 7L)).isEqualTo(current);
}

@Test
void requireCurrent_rejectsMissingOrStaleVersion() {
    UUID userId = UUID.randomUUID();
    when(store.loadLoginEligible(userId)).thenReturn(Optional.of(
            new AuthorizationPrincipal(userId, null, "alice", 8L, false)));

    assertThatThrownBy(() -> service.requireCurrent(userId, null))
            .isInstanceOf(AuthorizationTokenRejectedException.class);
    assertThatThrownBy(() -> service.requireCurrent(userId, 7L))
            .isInstanceOf(AuthorizationTokenRejectedException.class);
}

@Test
void requireCurrent_mapsStoreFailureToUnavailable() {
    UUID userId = UUID.randomUUID();
    when(store.loadLoginEligible(userId)).thenThrow(new DataAccessResourceFailureException("db unavailable"));

    assertThatThrownBy(() -> service.requireCurrent(userId, 1L))
            .isInstanceOf(AuthorizationUnavailableException.class)
            .hasMessage("授权事实暂时不可用");
}
```

- [ ] **Step 2: Run RED**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationPrincipalApplicationServiceTest"
Pop-Location
```

Expected: FAIL because principal contracts do not exist.

- [ ] **Step 3: Add the principal contracts**

```java
package com.colonel.saas.domain.user.api;

import java.util.Objects;
import java.util.UUID;

public record AuthorizationPrincipal(
        UUID userId,
        UUID deptId,
        String username,
        long authzVersion,
        boolean pendingActivation) {

    public AuthorizationPrincipal {
        Objects.requireNonNull(userId, "userId");
        if (authzVersion < 1) {
            throw new IllegalArgumentException("authzVersion must be positive");
        }
    }
}
```

```java
public interface AuthorizationPrincipalFacade {
    AuthorizationPrincipal requireCurrent(UUID userId, Long tokenAuthzVersion);
}
```

```java
public interface AuthorizationPrincipalStore {
    Optional<AuthorizationPrincipal> loadLoginEligible(UUID userId);
}
```

Both exception classes are final runtime exceptions with fixed external messages:

```java
public final class AuthorizationTokenRejectedException extends RuntimeException {
    public AuthorizationTokenRejectedException() {
        super("授权令牌已失效，请重新登录");
    }
}
```

```java
public final class AuthorizationUnavailableException extends RuntimeException {
    public AuthorizationUnavailableException() {
        super("授权事实暂时不可用");
    }

    public AuthorizationUnavailableException(Throwable cause) {
        super("授权事实暂时不可用", cause);
    }
}
```

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

