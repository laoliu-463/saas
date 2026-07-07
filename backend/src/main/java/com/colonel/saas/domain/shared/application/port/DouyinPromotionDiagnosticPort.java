package com.colonel.saas.domain.shared.application.port;

import com.colonel.saas.domain.shared.application.dto.DouyinPromotionRawProbeCommand;

import java.util.Map;

/**
 * 抖音推广联调诊断端口。
 */
public interface DouyinPromotionDiagnosticPort {

    Map<String, Object> rawUpstreamPost(DouyinPromotionRawProbeCommand command);
}
