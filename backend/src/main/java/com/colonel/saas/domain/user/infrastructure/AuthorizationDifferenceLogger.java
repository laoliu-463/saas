package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.domain.AuthorizationRuntimeDecision;
import com.colonel.saas.domain.user.port.AuthorizationDifferenceSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class AuthorizationDifferenceLogger implements AuthorizationDifferenceSink {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationDifferenceLogger.class);
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final String INVALID_TRACE_ID = "INVALID";

    @Override
    public void log(AuthorizationRuntimeDecision decision) {
        String reason = decision.newDecision() == null
                ? "UNAVAILABLE"
                : decision.newDecision().reason().name();
        String scope = decision.newDecision() == null
                ? "DENY"
                : decision.newDecision().scope().name();
        String rawTraceId = MDC.get("traceId");
        String traceId = rawTraceId != null && SAFE_TRACE_ID.matcher(rawTraceId).matches()
                ? rawTraceId
                : INVALID_TRACE_ID;
        log.info(
                "AUTHZ_SHADOW comparison={} mode={} userId={} domain={} permission={} newReason={} newScope={} traceId={}",
                decision.comparison(),
                decision.mode(),
                decision.userId(),
                decision.domainCode(),
                decision.permissionCode(),
                reason,
                scope,
                traceId);
    }
}
