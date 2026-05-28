package com.colonel.saas.dto.display;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 强制取消展示请求 DTO。
 * <p>
 * 用于手动取消某个商品的精选联盟展示关系，需指定展示关系 ID。
 * 关联业务领域：展示域（Display）。
 * </p>
 */
public record ForceDisplayCancelRequest(
        /** 展示关系 ID，不能为空 */
        @NotNull UUID relationId) {
}
