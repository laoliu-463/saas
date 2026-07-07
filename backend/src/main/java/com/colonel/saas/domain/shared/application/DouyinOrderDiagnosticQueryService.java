package com.colonel.saas.domain.shared.application;

import com.colonel.saas.domain.shared.application.dto.DouyinOrderRawProbeQuery;
import com.colonel.saas.domain.shared.application.port.DouyinOrderDiagnosticPort;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 抖音订单联调诊断查询服务。
 */
@Service
public class DouyinOrderDiagnosticQueryService {

    private final DouyinOrderDiagnosticPort douyinOrderDiagnosticPort;

    public DouyinOrderDiagnosticQueryService(DouyinOrderDiagnosticPort douyinOrderDiagnosticPort) {
        this.douyinOrderDiagnosticPort = douyinOrderDiagnosticPort;
    }

    public Map<String, Object> instituteOrdersRawResponse(DouyinOrderRawProbeQuery query) {
        return douyinOrderDiagnosticPort.instituteOrdersRawResponse(query);
    }
}
