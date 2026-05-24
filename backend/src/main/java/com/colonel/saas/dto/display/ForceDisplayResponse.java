package com.colonel.saas.dto.display;

import java.util.UUID;

public record ForceDisplayResponse(
        boolean success,
        String productId,
        UUID displayRelationId) {
}
