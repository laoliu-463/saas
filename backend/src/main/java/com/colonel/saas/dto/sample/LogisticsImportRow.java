package com.colonel.saas.dto.sample;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LogisticsImportRow {
    int rowNo;
    String sampleRequestId;
    String sampleNo;
    String productId;
    String talentAccount;
    String logisticsCompany;
    String trackingNo;
    String remark;
}
