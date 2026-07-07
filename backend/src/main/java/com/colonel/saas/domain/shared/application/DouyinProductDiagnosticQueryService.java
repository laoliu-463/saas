package com.colonel.saas.domain.shared.application;

import com.colonel.saas.domain.shared.application.dto.DouyinActivityProductProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinProductSkuView;
import com.colonel.saas.domain.shared.application.port.DouyinProductDiagnosticPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 抖音商品联调诊断查询服务。
 */
@Service
public class DouyinProductDiagnosticQueryService {

    private final DouyinProductDiagnosticPort douyinProductDiagnosticPort;

    public DouyinProductDiagnosticQueryService(DouyinProductDiagnosticPort douyinProductDiagnosticPort) {
        this.douyinProductDiagnosticPort = douyinProductDiagnosticPort;
    }

    public Map<String, Object> activityProducts(DouyinActivityProductProbeQuery query) {
        return douyinProductDiagnosticPort.activityProducts(query);
    }

    public List<DouyinProductSkuView> productSkus(String productId) {
        return douyinProductDiagnosticPort.productSkus(productId);
    }
}
