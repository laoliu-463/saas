package com.colonel.saas.service;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.domain.logistics.application.LogisticsGatewayHealthApplicationService;
import com.colonel.saas.dto.logistics.LogisticsGatewayHealthResponse;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestRequest;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestResponse;
import com.colonel.saas.gateway.logistics.query.Kuaidi100LogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.KuaidiNiaoLogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.MockLogisticsQueryGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 物流网关健康检查与诊断服务（DDD 委派壳，DDD-LOGISTICS-001 Slice 1）。
 *
 * <p>本类为 1-line 委派壳，所有真实业务逻辑已搬至
 * {@link LogisticsGatewayHealthApplicationService}。
 * 现有调用方（{@code AdminLogisticsGatewayController}）继续通过本类调用，行为零变化。</p>
 */
@Service
public class LogisticsGatewayHealthService {

    private final LogisticsGatewayHealthApplicationService applicationService;

    public LogisticsGatewayHealthService(
            LogisticsProperties properties,
            MockLogisticsQueryGateway mockGateway,
            KuaidiNiaoLogisticsQueryGateway kuaidiNiaoGateway,
            Kuaidi100LogisticsQueryGateway kuaidi100Gateway,
            @Value("${app.test.enabled:false}") boolean testEnabled,
            @Lazy LogisticsGatewayHealthApplicationService applicationService) {
        this.applicationService = applicationService;
        // 保留构造器参数以兼容直接 new 的旧测试代码（newService helper）
        // 业务执行不再使用这些字段
    }

    public LogisticsGatewayHealthResponse diagnoseCurrentProvider() {
        return applicationService.diagnoseCurrentProvider();
    }

    public LogisticsGatewayHealthResponse diagnoseProvider(String providerName) {
        return applicationService.diagnoseProvider(providerName);
    }

    public LogisticsGatewayTestResponse testQuery(LogisticsGatewayTestRequest request) {
        return applicationService.testQuery(request);
    }

    /**
     * 保留静态工具方法作为兼容入口。
     * 实际实现在 {@link LogisticsGatewayHealthApplicationService#maskTrackingNo(String)}。
     */
    public static String maskTrackingNo(String trackingNo) {
        return LogisticsGatewayHealthApplicationService.maskTrackingNo(trackingNo);
    }
}
