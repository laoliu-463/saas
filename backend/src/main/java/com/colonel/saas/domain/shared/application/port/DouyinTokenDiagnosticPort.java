package com.colonel.saas.domain.shared.application.port;

import com.colonel.saas.domain.shared.application.dto.DouyinTokenCreateProbeCommand;
import com.colonel.saas.domain.shared.application.dto.DouyinTokenCreateProbeResult;

import java.util.Map;

/**
 * 抖音 Token 联调诊断端口。
 */
public interface DouyinTokenDiagnosticPort {

    Map<String, Object> institutionInfo(String appId);

    DouyinTokenCreateProbeResult probeCreateToken(DouyinTokenCreateProbeCommand command);
}
