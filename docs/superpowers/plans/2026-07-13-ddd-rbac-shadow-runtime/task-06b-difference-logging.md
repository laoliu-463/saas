# Phase 2 Task 6B：差异日志与 503 语义

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

- [ ] **Step 4: Add the coordinator without production request consumers**

```java
@Service
public class AuthorizationRuntimeService {

    private final AuthorizationFacade authorizationFacade;
    private final AuthorizationRuntimeProperties properties;
    private final AuthorizationDifferenceLogger differenceLogger;

    public AuthorizationRuntimeService(
            AuthorizationFacade authorizationFacade,
            AuthorizationRuntimeProperties properties,
            AuthorizationDifferenceLogger differenceLogger) {
        this.authorizationFacade = authorizationFacade;
        this.properties = properties;
        this.differenceLogger = differenceLogger;
    }

    public AuthorizationRuntimeDecision evaluate(
            AuthorizationPrincipal principal,
            String domainCode,
            String permissionCode,
            boolean legacyAllowed) {
        AuthorizationRuntimeMode mode = properties.modeFor(domainCode);
        if (mode == AuthorizationRuntimeMode.LEGACY) {
            return new AuthorizationRuntimeDecision(
                    principal.userId(), domainCode, permissionCode, mode,
                    legacyAllowed, null, legacyAllowed,
                    AuthorizationComparison.NOT_EVALUATED);
        }
        try {
            AuthorizationDecision newDecision =
                    authorizationFacade.authorize(principal, permissionCode);
            AuthorizationComparison comparison = compare(
                    legacyAllowed, newDecision.allowed());
            boolean effectiveAllowed = mode == AuthorizationRuntimeMode.ENFORCE
                    ? newDecision.allowed()
                    : legacyAllowed;
            AuthorizationRuntimeDecision result = new AuthorizationRuntimeDecision(
                    principal.userId(), domainCode, permissionCode, mode,
                    legacyAllowed, newDecision, effectiveAllowed, comparison);
            differenceLogger.log(result);
            return result;
        } catch (AuthorizationUnavailableException unavailable) {
            if (mode == AuthorizationRuntimeMode.ENFORCE) {
                throw unavailable;
            }
            AuthorizationRuntimeDecision result = new AuthorizationRuntimeDecision(
                    principal.userId(), domainCode, permissionCode, mode,
                    legacyAllowed, null, legacyAllowed,
                    AuthorizationComparison.NEW_UNAVAILABLE);
            differenceLogger.log(result);
            return result;
        }
    }

    public AuthorizationRuntimeDecision require(
            AuthorizationPrincipal principal,
            String domainCode,
            String permissionCode,
            boolean legacyAllowed) {
        AuthorizationRuntimeDecision decision = evaluate(
                principal, domainCode, permissionCode, legacyAllowed);
        if (!decision.effectiveAllowed()) {
            throw new ForbiddenException("无权限访问该接口");
        }
        return decision;
    }

    private AuthorizationComparison compare(boolean legacyAllowed, boolean newAllowed) {
        if (legacyAllowed && newAllowed) return AuthorizationComparison.BOTH_ALLOW;
        if (!legacyAllowed && !newAllowed) return AuthorizationComparison.BOTH_DENY;
        return legacyAllowed
                ? AuthorizationComparison.OLD_ALLOW_NEW_DENY
                : AuthorizationComparison.OLD_DENY_NEW_ALLOW;
    }
}
```

Phase 2 must not inject this service into Controller, `RoleGuardAspect`, `DataScopeAspect`, or business application services. That prohibition is enforced in Task 8.

- [ ] **Step 5: Add structured, secret-free logging**

`AuthorizationDifferenceLogger.log` emits one event with these fields only: `comparison`, `mode`, `userId`, `domain`, `permission`, `newReason`, `newScope`, and MDC `traceId`. It must not emit token, role codes, permissions JSON, password, request body, headers, before/after snapshots, or Redis values.

```java
@Component
public class AuthorizationDifferenceLogger {

    private static final Logger log =
            LoggerFactory.getLogger(AuthorizationDifferenceLogger.class);

    public void log(AuthorizationRuntimeDecision decision) {
        String reason = decision.newDecision() == null
                ? "UNAVAILABLE"
                : decision.newDecision().reason().name();
        String scope = decision.newDecision() == null
                ? "DENY"
                : decision.newDecision().scope().name();
        log.info(
                "AUTHZ_SHADOW comparison={} mode={} userId={} domain={} permission={} newReason={} newScope={} traceId={}",
                decision.comparison(), decision.mode(), decision.userId(),
                decision.domainCode(), decision.permissionCode(), reason, scope,
                MDC.get("traceId"));
    }
}
```

- [ ] **Step 6: Add 503 result and handler**

Add `SERVICE_UNAVAILABLE(503, "服务暂时不可用")` to `ResultCode` and:

```java
@ExceptionHandler(AuthorizationUnavailableException.class)
public ResponseEntity<ApiResult<Void>> handleAuthorizationUnavailable(
        AuthorizationUnavailableException exception) {
    log.warn("授权事实暂时不可用: cause={}",
            exception.getCause() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getCause().getClass().getSimpleName());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResult.of(
                    ResultCode.SERVICE_UNAVAILABLE.getCode(),
                    "授权事实暂时不可用",
                    null,
                    "AUTHORIZATION_UNAVAILABLE"));
}
```

- [ ] **Step 7: Run GREEN and commit**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationRuntimeServiceTest,AuthorizationDifferenceLoggerTest,GlobalExceptionHandlerTest"
Pop-Location
git add backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationComparison.java backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationRuntimeDecision.java backend/src/main/java/com/colonel/saas/domain/user/infrastructure/AuthorizationDifferenceLogger.java backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationRuntimeService.java backend/src/main/java/com/colonel/saas/common/result/ResultCode.java backend/src/main/java/com/colonel/saas/common/exception/GlobalExceptionHandler.java backend/src/test/java/com/colonel/saas/domain/user/application/AuthorizationRuntimeServiceTest.java backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationDifferenceLoggerTest.java backend/src/test/java/com/colonel/saas/common/exception/GlobalExceptionHandlerTest.java
git commit -m "feat(auth): classify shadow authorization differences"
```

Expected: mode truth table, difference classification and 503 mapping PASS.

