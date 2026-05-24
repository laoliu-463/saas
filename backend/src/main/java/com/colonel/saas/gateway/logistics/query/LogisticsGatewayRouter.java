package com.colonel.saas.gateway.logistics.query;

import com.colonel.saas.config.LogisticsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 按配置路由物流查询 provider：mock | kuaidiniao | kuaidi100。
 * 真实 provider 未配置时不静默成功，返回 NOT_CONFIGURED。
 */
@Slf4j
@Primary
@Component
public class LogisticsGatewayRouter implements LogisticsQueryGateway {

    private final LogisticsProperties properties;
    private final MockLogisticsQueryGateway mockGateway;
    private final KuaidiNiaoLogisticsQueryGateway kuaidiNiaoGateway;
    private final Kuaidi100LogisticsQueryGateway kuaidi100Gateway;
    private final boolean testEnabled;

    public LogisticsGatewayRouter(
            LogisticsProperties properties,
            MockLogisticsQueryGateway mockGateway,
            KuaidiNiaoLogisticsQueryGateway kuaidiNiaoGateway,
            Kuaidi100LogisticsQueryGateway kuaidi100Gateway,
            @Value("${app.test.enabled:false}") boolean testEnabled) {
        this.properties = properties;
        this.mockGateway = mockGateway;
        this.kuaidiNiaoGateway = kuaidiNiaoGateway;
        this.kuaidi100Gateway = kuaidi100Gateway;
        this.testEnabled = testEnabled;
    }

    @Override
    public LogisticsQueryResult query(String logisticsCompany, String trackingNo) {
        LogisticsQueryGateway gateway = resolveGateway();
        return gateway.query(logisticsCompany, trackingNo);
    }

    @Override
    public boolean isSupported() {
        return resolveGateway().isSupported();
    }

    @Override
    public String providerName() {
        return resolveGateway().providerName();
    }

    public String configuredProvider() {
        if (testEnabled) {
            return "mock";
        }
        return normalizeProvider(properties.getProvider());
    }

    private LogisticsQueryGateway resolveGateway() {
        if (testEnabled || "mock".equals(normalizeProvider(properties.getProvider()))) {
            return mockGateway;
        }
        String provider = normalizeProvider(properties.getProvider());
        return switch (provider) {
            case "kuaidiniao" -> kuaidiNiaoGateway;
            case "kuaidi100" -> kuaidi100Gateway;
            default -> mockGateway;
        };
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "mock";
        }
        return provider.trim().toLowerCase();
    }

}
