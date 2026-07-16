package com.colonel.saas.domain.talent.facade.dto;

import java.util.UUID;

/**
 * 有效达人认领记录中的寄样收件地址。
 */
public record TalentClaimAddressDTO(
        UUID talentId,
        UUID ownerUserId,
        String recipientName,
        String recipientPhone,
        String recipientAddress) {
}
