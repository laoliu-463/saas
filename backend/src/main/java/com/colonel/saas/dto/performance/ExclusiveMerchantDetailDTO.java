package com.colonel.saas.dto.performance;

import lombok.Data;

@Data
public class ExclusiveMerchantDetailDTO {
    private String partnerId;
    private String partnerName;
    private boolean exclusive;
    private String recruiterId;
    private String recruiterName;
    private String effectiveMonth;
    private String expireMonth;
    private String status;
}
