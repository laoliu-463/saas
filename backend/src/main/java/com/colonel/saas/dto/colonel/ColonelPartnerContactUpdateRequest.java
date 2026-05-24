package com.colonel.saas.dto.colonel;

import jakarta.validation.constraints.Size;

public record ColonelPartnerContactUpdateRequest(
        @Size(max = 100) String contactName,
        @Size(max = 50) String contactPhone,
        @Size(max = 100) String contactWechat,
        @Size(max = 2000) String contactRemark) {
}
