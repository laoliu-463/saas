package com.colonel.saas.domain.talent.facade.dto;

/**
 * 达人寄样地址事实。
 *
 * <p>用于寄样链路消费达人域已经确认的地址字段，不承载寄样状态机或寄样申请规则。</p>
 */
public record TalentShippingAddressDTO(
        String recipientName,
        String recipientPhone,
        String recipientAddress) {

    public static TalentShippingAddressDTO empty() {
        return new TalentShippingAddressDTO(null, null, null);
    }
}
