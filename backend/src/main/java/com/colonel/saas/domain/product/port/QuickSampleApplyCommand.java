package com.colonel.saas.domain.product.port;

import java.util.List;
import java.util.UUID;

/**
 * 商品域快速寄样命令 — 由商品域构建并传递给 {@link ProductSampleApplicationPort}。
 */
public record QuickSampleApplyCommand(
        UUID relationId,
        String productId,
        UUID channelId,
        List<String> talentIds,
        String spec,
        int quantity,
        String remark,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String requestSource,
        UUID userId,
        UUID channelUserId,
        Object roleCodes,
        String productSnapshotTitle,
        Long productSnapshotPrice,
        String activityId,
        UUID assigneeId,
        boolean externalEnabled,
        boolean externalSupported,
        String skuId
) {
}
