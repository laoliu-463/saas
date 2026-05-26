package com.colonel.saas.gateway.logistics;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class LogisticsSubscribeResult {
    String provider;
    String companyCode;
    String trackingNo;
    boolean success;
    String returnCode;
    String message;
    Map<String, Object> rawResponse;
}
