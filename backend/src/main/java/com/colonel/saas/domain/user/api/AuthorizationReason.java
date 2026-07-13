package com.colonel.saas.domain.user.api;

public enum AuthorizationReason {
    GRANTED,
    SUBJECT_NOT_ACTIVE,
    PERMISSION_NOT_GRANTED,
    DOMAIN_SCOPE_MISSING
}
