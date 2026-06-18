package com.colonel.saas.domain.product.facade.dto;

import java.util.UUID;

/**
 * 团长合作方摘要 DTO。
 */
public record PartnerBriefDTO(
        String partnerId,
        UUID id,
        String colonelBuyinId,
        String partnerName,
        String contactName,
        String contactPhone,
        String avatarUrl,
        String source
) {
}
