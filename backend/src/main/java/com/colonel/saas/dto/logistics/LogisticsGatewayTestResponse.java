package com.colonel.saas.dto.logistics;

import com.colonel.saas.gateway.logistics.query.LogisticsGatewayHealthStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LogisticsGatewayTestResponse {
    private boolean success;
    private String provider;
    private LogisticsGatewayHealthStatus status;
    private String message;
    private boolean rawPayloadStored;
}
