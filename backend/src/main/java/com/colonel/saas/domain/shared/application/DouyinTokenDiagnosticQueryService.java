package com.colonel.saas.domain.shared.application;

import com.colonel.saas.domain.shared.application.dto.DouyinTokenCreateProbeCommand;
import com.colonel.saas.domain.shared.application.dto.DouyinTokenCreateProbeResult;
import com.colonel.saas.domain.shared.application.port.DouyinTokenDiagnosticPort;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 抖音 Token 联调诊断查询服务。
 */
@Service
public class DouyinTokenDiagnosticQueryService {

    private final DouyinTokenDiagnosticPort douyinTokenDiagnosticPort;

    public DouyinTokenDiagnosticQueryService(DouyinTokenDiagnosticPort douyinTokenDiagnosticPort) {
        this.douyinTokenDiagnosticPort = douyinTokenDiagnosticPort;
    }

    public Map<String, Object> institutionInfo(String appId) {
        return douyinTokenDiagnosticPort.institutionInfo(appId);
    }

    public DouyinTokenCreateProbeResult probeCreateToken(DouyinTokenCreateProbeCommand command) {
        return douyinTokenDiagnosticPort.probeCreateToken(command);
    }
}
