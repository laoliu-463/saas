# Phase 2 Task 6A：三态授权协调器

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

## Task 6: 实现唯一的三态授权协调器和差异分类

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationComparison.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationRuntimeDecision.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/AuthorizationDifferenceLogger.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationRuntimeService.java`
- Modify: `backend/src/main/java/com/colonel/saas/common/result/ResultCode.java:33-69`
- Modify: `backend/src/main/java/com/colonel/saas/common/exception/GlobalExceptionHandler.java:138-149`
- Create: `backend/src/test/java/com/colonel/saas/domain/user/application/AuthorizationRuntimeServiceTest.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationDifferenceLoggerTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/common/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write failing mode and difference tests**

`AuthorizationRuntimeServiceTest` must prove:

```java
@Test
void legacy_doesNotCallNewAuthorization() {
    when(properties.modeFor("sample")).thenReturn(AuthorizationRuntimeMode.LEGACY);

    AuthorizationRuntimeDecision decision = service.evaluate(
            principal, "sample", "sample:approve", true);

    assertThat(decision.effectiveAllowed()).isTrue();
    assertThat(decision.newDecision()).isNull();
    assertThat(decision.comparison()).isEqualTo(AuthorizationComparison.NOT_EVALUATED);
    verifyNoInteractions(authorizationFacade, differenceLogger);
}

@Test
void shadow_returnsLegacyAndClassifiesOldAllowNewDeny() {
    when(properties.modeFor("sample")).thenReturn(AuthorizationRuntimeMode.SHADOW);
    AuthorizationDecision denied = AuthorizationDecision.deny(
            new PermissionCode("sample:approve"),
            "sample",
            AuthorizationReason.PERMISSION_NOT_GRANTED);
    when(authorizationFacade.authorize(principal, "sample:approve")).thenReturn(denied);

    AuthorizationRuntimeDecision decision = service.evaluate(
            principal, "sample", "sample:approve", true);

    assertThat(decision.effectiveAllowed()).isTrue();
    assertThat(decision.comparison())
            .isEqualTo(AuthorizationComparison.OLD_ALLOW_NEW_DENY);
    verify(differenceLogger).log(decision);
}

@Test
void enforce_returnsNewDecisionAndNeverOrsWithLegacy() {
    when(properties.modeFor("sample")).thenReturn(AuthorizationRuntimeMode.ENFORCE);
    AuthorizationDecision denied = AuthorizationDecision.deny(
            new PermissionCode("sample:approve"),
            "sample",
            AuthorizationReason.PERMISSION_NOT_GRANTED);
    when(authorizationFacade.authorize(principal, "sample:approve")).thenReturn(denied);

    AuthorizationRuntimeDecision decision = service.evaluate(
            principal, "sample", "sample:approve", true);

    assertThat(decision.legacyAllowed()).isTrue();
    assertThat(decision.effectiveAllowed()).isFalse();
}

@Test
void require_mapsEffectiveDenyToForbidden() {
    when(properties.modeFor("sample")).thenReturn(AuthorizationRuntimeMode.ENFORCE);
    when(authorizationFacade.authorize(principal, "sample:approve"))
            .thenReturn(AuthorizationDecision.deny(
                    new PermissionCode("sample:approve"),
                    "sample",
                    AuthorizationReason.PERMISSION_NOT_GRANTED));

    assertThatThrownBy(() -> service.require(
            principal, "sample", "sample:approve", true))
            .isInstanceOf(ForbiddenException.class)
            .hasMessage("无权限访问该接口");
}

@Test
void shadowKeepsLegacyOnNewStoreOutageButEnforceReturns503() {
    when(authorizationFacade.authorize(principal, "sample:approve"))
            .thenThrow(new AuthorizationUnavailableException());
    when(properties.modeFor("sample"))
            .thenReturn(AuthorizationRuntimeMode.SHADOW)
            .thenReturn(AuthorizationRuntimeMode.ENFORCE);

    assertThat(service.evaluate(principal, "sample", "sample:approve", true)
            .comparison()).isEqualTo(AuthorizationComparison.NEW_UNAVAILABLE);
    assertThatThrownBy(() -> service.evaluate(
            principal, "sample", "sample:approve", true))
            .isInstanceOf(AuthorizationUnavailableException.class);
}
```

Add one `GlobalExceptionHandlerTest` asserting `AuthorizationUnavailableException` maps to HTTP/body 503 with `AUTHORIZATION_UNAVAILABLE` and does not expose the cause message.

- [ ] **Step 2: Run RED**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationRuntimeServiceTest,AuthorizationDifferenceLoggerTest,GlobalExceptionHandlerTest"
Pop-Location
```

Expected: FAIL because runtime decision types and 503 handler do not exist.

- [ ] **Step 3: Add stable difference and result types**

```java
package com.colonel.saas.domain.user.domain;

public enum AuthorizationComparison {
    NOT_EVALUATED,
    BOTH_ALLOW,
    BOTH_DENY,
    OLD_ALLOW_NEW_DENY,
    OLD_DENY_NEW_ALLOW,
    NEW_UNAVAILABLE
}
```

```java
package com.colonel.saas.domain.user.domain;

import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import java.util.UUID;

public record AuthorizationRuntimeDecision(
        UUID userId,
        String domainCode,
        String permissionCode,
        AuthorizationRuntimeMode mode,
        boolean legacyAllowed,
        AuthorizationDecision newDecision,
        boolean effectiveAllowed,
        AuthorizationComparison comparison) {
}
```

