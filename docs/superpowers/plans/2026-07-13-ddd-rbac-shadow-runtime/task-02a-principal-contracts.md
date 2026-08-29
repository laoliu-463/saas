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

