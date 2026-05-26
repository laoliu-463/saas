package com.colonel.saas.dto.logistics;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogisticsGatewayTestRequest {
    @NotBlank
    private String provider;
    @NotBlank
    private String logisticsCompany;
    @NotBlank
    private String trackingNo;
    private String phone;
    private String from;
    private String to;
}
