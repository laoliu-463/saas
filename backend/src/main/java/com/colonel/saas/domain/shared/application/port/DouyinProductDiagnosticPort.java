package com.colonel.saas.domain.shared.application.port;

import com.colonel.saas.domain.shared.application.dto.DouyinActivityProductProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinProductSkuView;

import java.util.List;
import java.util.Map;

/**
 * 抖音商品联调诊断端口。
 */
public interface DouyinProductDiagnosticPort {

    Map<String, Object> activityProducts(DouyinActivityProductProbeQuery query);

    List<DouyinProductSkuView> productSkus(String productId);
}
