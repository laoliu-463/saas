package com.colonel.saas.gateway.logistics;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LogisticsTrackCommand {
    String companyCode;
    String trackingNo;
    String phone;
    String from;
    String to;
    String resultV2;

    public static LogisticsTrackCommand of(String companyCode, String trackingNo) {
        return LogisticsTrackCommand.builder()
                .companyCode(companyCode)
                .trackingNo(trackingNo)
                .build();
    }
}
