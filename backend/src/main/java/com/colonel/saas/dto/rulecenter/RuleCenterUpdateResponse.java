package com.colonel.saas.dto.rulecenter;

import java.util.List;
import java.util.UUID;

public record RuleCenterUpdateResponse(
        UUID eventId,
        List<String> changedKeys,
        List<String> warnings) {
}
