package com.colonel.saas.domain.shared.infrastructure;

import com.colonel.saas.domain.shared.application.dto.DouyinOrderRawProbeQuery;
import com.colonel.saas.domain.shared.application.port.DouyinOrderDiagnosticPort;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抖音订单联调诊断端口的 Gateway 适配器。
 */
@Component
public class DouyinOrderDiagnosticGatewayAdapter implements DouyinOrderDiagnosticPort {

    private final DouyinOrderGateway douyinOrderGateway;

    public DouyinOrderDiagnosticGatewayAdapter(DouyinOrderGateway douyinOrderGateway) {
        this.douyinOrderGateway = douyinOrderGateway;
    }

    @Override
    public Map<String, Object> instituteOrdersRawResponse(DouyinOrderRawProbeQuery query) {
        return douyinOrderGateway.listInstituteOrders(
                new DouyinOrderGateway.DouyinOrderQueryRequest(
                        query.startTime(),
                        query.endTime(),
                        query.count(),
                        query.cursor()))
                .rawResponse();
    }
}
