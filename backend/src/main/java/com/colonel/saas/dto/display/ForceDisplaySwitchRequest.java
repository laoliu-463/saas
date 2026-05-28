package com.colonel.saas.dto.display;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 强制展示切换请求 DTO。
 * <p>
 * 用于手动切换某个商品的展示状态（显示/隐藏），需提供操作原因和可选的截止时间。
 * 关联业务领域：展示域（Display）。
 * </p>
 */
public record ForceDisplaySwitchRequest(
        /** 展示关系 ID，不能为空 */
        @NotNull UUID relationId,
        /** 操作原因说明，不能为空 */
        @NotBlank String reason,
        /** 展示截止时间，为空则为永久 */
        LocalDateTime until) {
}
