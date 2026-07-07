package com.colonel.saas.domain.shared.infrastructure;

import com.colonel.saas.domain.shared.application.dto.DouyinPromotionRawProbeCommand;
import com.colonel.saas.domain.shared.application.port.DouyinPromotionDiagnosticPort;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抖音推广联调诊断端口的 Gateway 适配器。
 */
@Component
public class DouyinPromotionDiagnosticGatewayAdapter implements DouyinPromotionDiagnosticPort {

    private final DouyinPromotionGateway douyinPromotionGateway;

    public DouyinPromotionDiagnosticGatewayAdapter(DouyinPromotionGateway douyinPromotionGateway) {
        this.douyinPromotionGateway = douyinPromotionGateway;
    }

    @Override
    public Map<String, Object> rawUpstreamPost(DouyinPromotionRawProbeCommand command) {
        return douyinPromotionGateway.rawUpstreamPost(command.appId(), command.method(), command.payload());
    }
}
