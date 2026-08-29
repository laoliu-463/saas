package com.colonel.saas.domain.user.port;

import com.colonel.saas.domain.user.domain.AuthorizationRuntimeDecision;

@FunctionalInterface
public interface AuthorizationDifferenceSink {

    void log(AuthorizationRuntimeDecision decision);
}
