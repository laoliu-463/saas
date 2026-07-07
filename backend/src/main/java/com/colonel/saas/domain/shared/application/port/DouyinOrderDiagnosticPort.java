package com.colonel.saas.domain.shared.application.port;

import com.colonel.saas.domain.shared.application.dto.DouyinOrderRawProbeQuery;

import java.util.Map;

/**
 * 抖音订单联调诊断端口。
 */
public interface DouyinOrderDiagnosticPort {

    Map<String, Object> instituteOrdersRawResponse(DouyinOrderRawProbeQuery query);
}
