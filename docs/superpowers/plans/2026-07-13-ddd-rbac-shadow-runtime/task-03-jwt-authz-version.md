# Phase 2 Task 3：JWT authzVersion

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

## Task 3: 把 authzVersion 写入 access/refresh token

**Files:**

- Modify: `backend/src/main/java/com/colonel/saas/security/JwtTokenProvider.java:116-204`
- Modify: `backend/src/main/java/com/colonel/saas/auth/service/AuthService.java:70-315`
- Create: `backend/src/test/java/com/colonel/saas/security/JwtTokenProviderAuthorizationVersionTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/auth/service/AuthServiceTest.java`

- [ ] **Step 1: Write failing JWT claim tests**

```java
@Test
void accessAndRefreshTokensContainAuthorizationVersion() {
    JwtTokenProvider provider = new JwtTokenProvider(
            "test-jwt-secret-for-authorization-version-32chars",
            7200,
            604800);
    UUID userId = UUID.randomUUID();

    String access = provider.generateAccessToken(
            userId, null, 1, List.of("biz_staff"), "alice", false, 9L);
    String refresh = provider.generateRefreshToken(userId, 9L);

    assertThat(provider.parseClaims(access).get("authzVersion", Long.class)).isEqualTo(9L);
    assertThat(provider.parseClaims(refresh).get("authzVersion", Long.class)).isEqualTo(9L);
}

@Test
void tokenGenerationRejectsNonPositiveAuthorizationVersion() {
    assertThatThrownBy(() -> provider.generateRefreshToken(UUID.randomUUID(), 0L))
            .isInstanceOf(IllegalArgumentException.class);
}
```

In `AuthServiceTest`, add cases proving:

```java
verify(jwtTokenProvider).generateAccessToken(
        eq(userId), any(), anyInt(), anyList(), anyString(), anyBoolean(), eq(7L));
verify(jwtTokenProvider).generateRefreshToken(userId, 7L);
```

and in non-legacy mode:

```java
when(runtimeProperties.requiresVersionValidation()).thenReturn(true);
when(claims.get("authzVersion", Long.class)).thenReturn(6L);
when(user.getAuthzVersion()).thenReturn(7L);

assertThatThrownBy(() -> authService.refreshToken(request))
        .isInstanceOf(BusinessException.class)
        .extracting("code")
        .isEqualTo(401);
```

- [ ] **Step 2: Run RED**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=JwtTokenProviderAuthorizationVersionTest,AuthServiceTest"
Pop-Location
```

Expected: FAIL on missing token method signatures and refresh-version check.

- [ ] **Step 3: Change JwtTokenProvider signatures and claims**

The final public signatures must be:

```java
public String generateAccessToken(
        UUID userId,
        UUID deptId,
        int dataScope,
        List<String> roleCodes,
        String username,
        boolean pendingActivation,
        long authzVersion)
```

```java
public String generateRefreshToken(UUID userId, long authzVersion)
```

Both methods validate `authzVersion >= 1` and add exactly this claim:

```java
.claim("authzVersion", authzVersion)
```

Remove the old overloads after all production and test call sites compile; do not leave an overload that silently invents version `1`.

- [ ] **Step 4: Make AuthService sign and refresh the current version**

Inject `AuthorizationRuntimeProperties` into the constructor. On login, read the mapped `SysUser.authzVersion` and fail if null/non-positive:

```java
long authzVersion = Optional.ofNullable(user.getAuthzVersion())
        .filter(version -> version > 0)
        .orElseThrow(() -> new AuthorizationUnavailableException());
```

Sign both token types with that value. During refresh, after loading the user:

```java
Long tokenAuthzVersion = claims.get("authzVersion", Long.class);
long currentAuthzVersion = Optional.ofNullable(user.getAuthzVersion())
        .filter(version -> version > 0)
        .orElseThrow(() -> new AuthorizationUnavailableException());
if (runtimeProperties.requiresVersionValidation()
        && (tokenAuthzVersion == null || tokenAuthzVersion.longValue() != currentAuthzVersion)) {
    throw new BusinessException(
            ResultCode.UNAUTHORIZED.getCode(),
            "授权令牌已失效，请重新登录");
}
```

New access and refresh tokens always carry `currentAuthzVersion`, even when runtime mode remains `LEGACY`.

- [ ] **Step 5: Run GREEN and commit**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=JwtTokenProviderAuthorizationVersionTest,AuthServiceTest"
Pop-Location
git add backend/src/main/java/com/colonel/saas/security/JwtTokenProvider.java backend/src/main/java/com/colonel/saas/auth/service/AuthService.java backend/src/test/java/com/colonel/saas/security/JwtTokenProviderAuthorizationVersionTest.java backend/src/test/java/com/colonel/saas/auth/service/AuthServiceTest.java
git commit -m "feat(auth): version access and refresh tokens"
```

Expected: login and refresh tests PASS; no token content or secret is printed.

