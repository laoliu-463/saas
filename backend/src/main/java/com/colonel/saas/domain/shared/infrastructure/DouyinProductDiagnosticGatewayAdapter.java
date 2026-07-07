package com.colonel.saas.domain.shared.infrastructure;

import com.colonel.saas.domain.shared.application.dto.DouyinActivityProductProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinProductSkuView;
import com.colonel.saas.domain.shared.application.port.DouyinProductDiagnosticPort;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 抖音商品联调诊断端口的 Gateway 适配器。
 */
@Component
public class DouyinProductDiagnosticGatewayAdapter implements DouyinProductDiagnosticPort {

    private final DouyinProductGateway douyinProductGateway;

    public DouyinProductDiagnosticGatewayAdapter(DouyinProductGateway douyinProductGateway) {
        this.douyinProductGateway = douyinProductGateway;
    }

    @Override
    public Map<String, Object> activityProducts(DouyinActivityProductProbeQuery query) {
        DouyinProductGateway.ActivityProductListResult result = douyinProductGateway.queryActivityProducts(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        query.appId(),
                        query.activityId(),
                        4L,
                        1L,
                        query.count(),
                        null,
                        null,
                        null,
                        null,
                        1L,
                        query.cursor(),
                        null));
        return result.toMap();
    }

    @Override
    public List<DouyinProductSkuView> productSkus(String productId) {
        return douyinProductGateway.queryProductSkus(productId).stream()
                .map(sku -> new DouyinProductSkuView(
                        sku.skuId(),
                        sku.skuName(),
                        sku.price(),
                        sku.stock(),
                        sku.cover()))
                .toList();
    }
}
