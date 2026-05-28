package com.colonel.saas.dto.talent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 达人归属负责人覆盖请求 DTO。
 * <p>
 * 用于将达人资源的当前负责人强制更改为新负责人，需提供变更原因以便审计追溯。
 * 关联业务领域：达人域（Talent）。
 * </p>
 */
public record OverrideAssigneeRequest(
        /** 新负责人的用户 ID，必填 */
        @NotNull(message = "新负责人ID不能为空")
        UUID newUserId,

        /** 归属覆盖原因，用于审计记录，必填 */
        @NotBlank(message = "归属覆盖原因不能为空")
        String reason
) {
}
