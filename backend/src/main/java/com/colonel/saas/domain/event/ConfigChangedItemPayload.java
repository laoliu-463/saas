package com.colonel.saas.domain.event;

import java.util.List;

public record ConfigChangedItemPayload(
        String configKey,
        String group,
        String oldValue,
        String newValue,
        String valueType,
        int configVersion,
        List<String> consumerDomains) {
}
