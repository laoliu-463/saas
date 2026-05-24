package com.colonel.saas.dto.display;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ForceDisplayCancelRequest(@NotNull UUID relationId) {
}
