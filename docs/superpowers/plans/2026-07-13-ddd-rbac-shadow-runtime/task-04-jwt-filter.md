# Phase 2 Task 4：JWT filter 版本校验

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

## Task 4: 在 JWT filter 中校验版本并建立可信主体

**Files:**

- Modify: `backend/src/main/java/com/colonel/saas/security/JwtAuthenticationFilter.java:63-285`
- Modify: `backend/src/test/java/com/colonel/saas/security/JwtAuthenticationFilterTest.java`

- [ ] **Step 1: Add failing 401/503/principal tests**

Create mocks for `AuthorizationPrincipalFacade` and `AuthorizationRuntimeProperties`, then construct the filter with all dependencies. Add these complete behavior cases:

```java
@Test
void shadowMode_rejectsTokenWithoutAuthorizationVersion() throws Exception {
    Claims claims = accessClaims(UUID.randomUUID(), null);
    when(runtimeProperties.requiresVersionValidation()).thenReturn(true);
    when(jwtTokenProvider.parseClaims("missing.version")).thenReturn(claims);
    when(jwtTokenProvider.getTokenHash("missing.version")).thenReturn("hash");
    when(authService.isTokenBlacklisted("hash")).thenReturn(false);

    MockHttpServletResponse response = filter("missing.version", "/products");

    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoInteractions(principalFacade);
}

@Test
void shadowMode_usesDatabasePrincipalAndPreservesLegacyAttributes() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID databaseDept = UUID.randomUUID();
    Claims claims = accessClaims(userId, 4L);
    when(runtimeProperties.requiresVersionValidation()).thenReturn(true);
    when(jwtTokenProvider.parseClaims("valid.version")).thenReturn(claims);
    when(jwtTokenProvider.getTokenHash("valid.version")).thenReturn("hash");
    when(authService.isTokenBlacklisted("hash")).thenReturn(false);
    AuthorizationPrincipal principal =
            new AuthorizationPrincipal(userId, databaseDept, "alice", 4L, false);
    when(principalFacade.requireCurrent(userId, 4L)).thenReturn(principal);

    MockHttpServletRequest request = authenticatedRequest("valid.version", "/products");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(request.getAttribute("authorizationPrincipal")).isEqualTo(principal);
    assertThat(request.getAttribute("deptId")).isEqualTo(databaseDept);
    assertThat(request.getAttribute("roleCodes")).isEqualTo(List.of("admin"));
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
            .isEqualTo(principal);
}

@Test
void shadowMode_returns503WhenPrincipalStoreIsUnavailable() throws Exception {
    UUID userId = UUID.randomUUID();
    Claims claims = accessClaims(userId, 4L);
    when(runtimeProperties.requiresVersionValidation()).thenReturn(true);
    when(jwtTokenProvider.parseClaims("store.down")).thenReturn(claims);
    when(jwtTokenProvider.getTokenHash("store.down")).thenReturn("hash");
    when(authService.isTokenBlacklisted("hash")).thenReturn(false);
    when(principalFacade.requireCurrent(userId, 4L))
            .thenThrow(new AuthorizationUnavailableException());

    MockHttpServletResponse response = filter("store.down", "/products");

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).contains("授权事实暂时不可用");
}
```

The helper `accessClaims` must continue setting `type`, `deptId`, `dataScope`, `roleCodes`, `username`, and `pendingActivation`; it additionally stubs `authzVersion`.

- [ ] **Step 2: Run RED**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=JwtAuthenticationFilterTest"
Pop-Location
```

Expected: FAIL because the filter has no principal/version dependencies or 503 path.

- [ ] **Step 3: Inject the principal facade and properties**

The production constructor becomes:

```java
@Autowired
public JwtAuthenticationFilter(
        JwtTokenProvider jwtTokenProvider,
        AuthService authService,
        AuthorizationPrincipalFacade principalFacade,
        AuthorizationRuntimeProperties runtimeProperties,
        ObjectMapper objectMapper,
        Environment environment) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.authService = authService;
    this.principalFacade = principalFacade;
    this.runtimeProperties = runtimeProperties;
    this.objectMapper = objectMapper;
    this.environment = environment;
}
```

Delete the three-argument test constructor. Tests must pass explicit mocks and `MockEnvironment`; production must have one unambiguous constructor.

- [ ] **Step 4: Resolve the principal after blacklist validation**

After extracting `userId` and legacy claims, execute exactly this mode split:

```java
Long tokenAuthzVersion = claims.get("authzVersion", Long.class);
AuthorizationPrincipal principal;
if (runtimeProperties.requiresVersionValidation()) {
    principal = principalFacade.requireCurrent(userId, tokenAuthzVersion);
} else {
    long legacyVersion = tokenAuthzVersion == null || tokenAuthzVersion < 1
            ? 1L
            : tokenAuthzVersion;
    principal = new AuthorizationPrincipal(
            userId,
            deptId,
            username,
            legacyVersion,
            Boolean.TRUE.equals(pendingActivation));
}
```

Use `principal.pendingActivation()` for the pending-activation policy, use `principal.deptId()` and `principal.username()` for request attributes, preserve token-derived `roleCodes/dataScope` only for the legacy chain, and set:

```java
UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
SecurityContextHolder.getContext().setAuthentication(authentication);
request.setAttribute("authorizationPrincipal", principal);
request.setAttribute("userId", principal.userId());
request.setAttribute("deptId", principal.deptId());
request.setAttribute("username", principal.username());
```

Do not compare token role strings with new permissions in this filter.

- [ ] **Step 5: Split exception handling so unavailability is not mislabeled 401**

The final catch order is:

```java
} catch (AuthorizationTokenRejectedException exception) {
    writeUnauthorized(response, exception.getMessage());
} catch (AuthorizationUnavailableException exception) {
    writeServiceUnavailable(response, exception.getMessage());
} catch (Exception exception) {
    writeUnauthorized(response, "Token 无效或已过期");
}
```

Add:

```java
private void writeServiceUnavailable(HttpServletResponse response, String msg) throws IOException {
    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ApiResult<Void> result = ApiResult.of(503, msg, null, "AUTHORIZATION_UNAVAILABLE");
    response.getWriter().write(objectMapper.writeValueAsString(result));
}
```

- [ ] **Step 6: Run GREEN and commit**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=JwtAuthenticationFilterTest,SecurityConfigTest"
Pop-Location
git add backend/src/main/java/com/colonel/saas/security/JwtAuthenticationFilter.java backend/src/test/java/com/colonel/saas/security/JwtAuthenticationFilterTest.java
git commit -m "feat(auth): validate authorization version on requests"
```

Expected: protected requests distinguish stale token 401 from store outage 503; legacy request attributes remain compatible.

