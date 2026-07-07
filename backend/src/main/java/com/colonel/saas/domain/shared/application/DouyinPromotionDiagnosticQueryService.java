package com.colonel.saas.domain.shared.application;

import com.colonel.saas.domain.shared.application.dto.DouyinPromotionRawProbeCommand;
import com.colonel.saas.domain.shared.application.port.DouyinPromotionDiagnosticPort;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 抖音推广联调诊断查询服务。
 */
@Service
public class DouyinPromotionDiagnosticQueryService {

    private final DouyinPromotionDiagnosticPort douyinPromotionDiagnosticPort;

    public DouyinPromotionDiagnosticQueryService(DouyinPromotionDiagnosticPort douyinPromotionDiagnosticPort) {
        this.douyinPromotionDiagnosticPort = douyinPromotionDiagnosticPort;
    }

    public Map<String, Object> rawUpstreamPost(DouyinPromotionRawProbeCommand command) {
        return douyinPromotionDiagnosticPort.rawUpstreamPost(command);
    }
}
