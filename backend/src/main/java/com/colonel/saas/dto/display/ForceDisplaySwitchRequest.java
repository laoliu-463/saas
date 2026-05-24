package com.colonel.saas.dto.display;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record ForceDisplaySwitchRequest(
        @NotNull UUID relationId,
        @NotBlank String reason,
        LocalDateTime until) {
}
