package com.colonel.saas.domain.event;

public enum DomainEventStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED,
    DEAD
}
