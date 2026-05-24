package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Schema(description = "业务组成员变更")
public record GroupMemberMutationRequest(
        @NotEmpty(message = "userIds cannot be empty")
        List<UUID> userIds
) {
}
