package com.colonel.saas.dto.rulecenter;

import java.util.Map;

public record RuleCenterGroupUpdateRequest(
        Map<String, String> values,
        String changeReason) {
}
