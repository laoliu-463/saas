package com.colonel.saas.dto.talent;

import lombok.Data;

@Data
public class ClaimShippingAddress {
    private String recipientName;
    private String recipientPhone;
    private String recipientAddress;

    public boolean isComplete() {
        return hasText(recipientName) && hasText(recipientPhone) && hasText(recipientAddress);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
