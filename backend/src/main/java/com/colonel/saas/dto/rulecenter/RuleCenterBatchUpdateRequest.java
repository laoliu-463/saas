package com.colonel.saas.dto.rulecenter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RuleCenterBatchUpdateRequest(
        Map<String, String> values,
        String changeReason) {
}
