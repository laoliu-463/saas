package com.colonel.saas.domain.event;

public record ConfigChangedImpactPayload(
        boolean needCacheRefresh,
        boolean needManualRecalculate,
        boolean affectNewDataOnly) {
}
