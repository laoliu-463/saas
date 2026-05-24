package com.colonel.saas.domain.user.event;

import org.slf4j.MDC;

import java.util.UUID;

public final class UserDomainEventHeaders {

    public static final String TRACE_ID_KEY = "traceId";

    private UserDomainEventHeaders() {
    }

    public static UUID newEventId() {
        return UUID.randomUUID();
    }

    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId == null ? "" : traceId;
    }
}
