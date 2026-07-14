package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.domain.AuthorizationRuntimeDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationDifferenceLogger {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationDifferenceLogger.class);

    public void log(AuthorizationRuntimeDecision decision) {
        String reason = decision.newDecision() == null
                ? "UNAVAILABLE"
                : decision.newDecision().reason().name();
        String scope = decision.newDecision() == null
                ? "DENY"
                : decision.newDecision().scope().name();
        log.info(
                "AUTHZ_SHADOW comparison={} mode={} userId={} domain={} permission={} newReason={} newScope={} traceId={}",
                decision.comparison(),
                decision.mode(),
                decision.userId(),
                decision.domainCode(),
                decision.permissionCode(),
                reason,
                scope,
                MDC.get("traceId"));
    }
}
