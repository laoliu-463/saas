package com.colonel.saas.dto.logistics;

import com.colonel.saas.gateway.logistics.query.LogisticsGatewayHealthStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LogisticsGatewayHealthResponse {
    private String provider;
    private boolean enabled;
    private boolean configured;
    private LogisticsGatewayHealthStatus status;
    private String message;
    private LocalDateTime checkedAt;
}
