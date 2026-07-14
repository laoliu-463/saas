package com.colonel.saas.domain.user.application;

import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.config.AuthorizationRuntimeProperties;
import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationPrincipal;
import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import com.colonel.saas.domain.user.api.AuthorizationUnavailableException;
import com.colonel.saas.domain.user.domain.AuthorizationComparison;
import com.colonel.saas.domain.user.domain.AuthorizationRuntimeDecision;
import com.colonel.saas.domain.user.facade.AuthorizationFacade;
import com.colonel.saas.domain.user.infrastructure.AuthorizationDifferenceLogger;
import org.springframework.stereotype.Service;

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
                    principal.userId(),
                    domainCode,
                    permissionCode,
                    mode,
                    legacyAllowed,
                    null,
                    legacyAllowed,
                    AuthorizationComparison.NOT_EVALUATED);
        }

        try {
            AuthorizationDecision newDecision = authorizationFacade.authorize(
                    principal, permissionCode);
            AuthorizationComparison comparison = compare(
                    legacyAllowed, newDecision.allowed());
            boolean effectiveAllowed = mode == AuthorizationRuntimeMode.ENFORCE
                    ? newDecision.allowed()
                    : legacyAllowed;
            AuthorizationRuntimeDecision result = new AuthorizationRuntimeDecision(
                    principal.userId(),
                    domainCode,
                    permissionCode,
                    mode,
                    legacyAllowed,
                    newDecision,
                    effectiveAllowed,
                    comparison);
            differenceLogger.log(result);
            return result;
        } catch (AuthorizationUnavailableException unavailable) {
            if (mode == AuthorizationRuntimeMode.ENFORCE) {
                throw unavailable;
            }
            AuthorizationRuntimeDecision result = new AuthorizationRuntimeDecision(
                    principal.userId(),
                    domainCode,
                    permissionCode,
                    mode,
                    legacyAllowed,
                    null,
                    legacyAllowed,
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
        if (legacyAllowed && newAllowed) {
            return AuthorizationComparison.BOTH_ALLOW;
        }
        if (!legacyAllowed && !newAllowed) {
            return AuthorizationComparison.BOTH_DENY;
        }
        return legacyAllowed
                ? AuthorizationComparison.OLD_ALLOW_NEW_DENY
                : AuthorizationComparison.OLD_DENY_NEW_ALLOW;
    }
}
