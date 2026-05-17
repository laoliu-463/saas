package com.colonel.saas.gateway.logistics.fallback;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ManualFallbackLogisticsGateway implements LogisticsGateway {

    private static final String COMPANY_MANUAL = "MANUAL";
    private static final String STATUS_NOT_CONFIGURED = "NOT_CONFIGURED";
    private static final String REASON_NOT_CONFIGURED = "物流网关未配置，请保持人工跟进";

    @Override
    public LogisticsResult createShipment(LogisticsCommand command) {
        throw new BusinessException("物流网关未配置，请手动录入物流信息");
    }

    @Override
    public LogisticsStatusResult queryStatus(String trackingNo) {
        return new LogisticsStatusResult(
                trackingNo,
                COMPANY_MANUAL,
                STATUS_NOT_CONFIGURED,
                REASON_NOT_CONFIGURED,
                LocalDateTime.now()
        );
    }

    @Override
    public LogisticsTrackResult queryTrack(String companyCode, String trackingNo) {
        String normalizedCompany = StringUtils.hasText(companyCode) ? companyCode : COMPANY_MANUAL;
        return new LogisticsTrackResult(
                normalizedCompany,
                trackingNo,
                false,
                REASON_NOT_CONFIGURED,
                null,
                STATUS_NOT_CONFIGURED,
                false,
                null,
                List.of(),
                Map.of("provider", "manual-fallback", "configured", false)
        );
    }
}
