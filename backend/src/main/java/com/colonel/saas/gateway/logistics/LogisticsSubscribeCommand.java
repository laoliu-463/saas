package com.colonel.saas.gateway.logistics;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class LogisticsSubscribeCommand {
    UUID sampleRequestId;
    String companyCode;
    String trackingNo;
    String phone;
    String from;
    String to;
}
