package com.colonel.saas.domain.user.domain;

public enum AuthorizationComparison {
    NOT_EVALUATED,
    BOTH_ALLOW,
    BOTH_DENY,
    OLD_ALLOW_NEW_DENY,
    OLD_DENY_NEW_ALLOW,
    NEW_UNAVAILABLE
}
