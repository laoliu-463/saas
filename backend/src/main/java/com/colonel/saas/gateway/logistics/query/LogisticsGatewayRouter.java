package com.colonel.saas.gateway.logistics.query;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 物流查询网关路由器。
 * <p>
 * 根据配置 {@code logistics.provider} 将物流查询请求路由到对应的 provider 实现：
 * <ul>
 *   <li>{@code mock} - {@link MockLogisticsQueryGateway}，本地 Mock 测试</li>
 *   <li>{@code kuaidiniao} - {@link KuaidiNiaoLogisticsQueryGateway}，快递鸟 API</li>
 *   <li>{@code kuaidi100} - {@link Kuaidi100LogisticsQueryGateway}，快递100 API</li>
 * </ul>
 * 当 {@code app.test.enabled=true} 时强制使用 Mock provider。
 * 真实 provider 未配置凭证时不静默成功，返回 NOT_CONFIGURED 状态。
 * </p>
 *
 * <h3>设计决策</h3>
 * <p>
 * 标记为 {@code @Primary}，使得业务层注入 {@link LogisticsQueryGateway} 时自动使用此路由器，
 * 无需关心底层 provider 切换。
 * </p>
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

    /**
     * 构造路由器，注入所有可用的 provider 实现和配置。
     *
     * @param properties      物流配置属性（包含 provider 选择和各服务商凭证）
     * @param mockGateway     Mock 测试 provider
     * @param kuaidiNiaoGateway 快递鸟 provider
     * @param kuaidi100Gateway  快递100 provider
     * @param testEnabled     是否为测试环境（app.test.enabled），为 true 时强制使用 Mock
     */
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

    /** 委托给路由后的 provider 执行物流查询 */
    @Override
    public LogisticsQueryResult query(String logisticsCompany, String trackingNo) {
        LogisticsQueryGateway gateway = resolveGateway();
        return gateway.query(logisticsCompany, trackingNo);
    }

    /** 委托给路由后的 provider 执行物流查询 */
    @Override
    public LogisticsQueryResult query(LogisticsTrackCommand command) {
        LogisticsQueryGateway gateway = resolveGateway();
        return gateway.query(command);
    }

    /** 委托给路由后的 provider 判断是否可用 */
    @Override
    public boolean isSupported() {
        return resolveGateway().isSupported();
    }

    /** 返回路由后 provider 的名称 */
    @Override
    public String providerName() {
        return resolveGateway().providerName();
    }

    /**
     * 获取当前配置的 provider 名称（不做路由）。
     * <p>
     * 用于运维看板展示当前配置的物流服务商。
     * </p>
     *
     * @return provider 名称字符串（mock / kuaidiniao / kuaidi100）
     */
    public String configuredProvider() {
        if (testEnabled) {
            return "mock";
        }
        return normalizeProvider(properties.getProvider());
    }

    /**
     * 根据配置解析实际的 provider 实现。
     * <p>
     * 路由优先级：testEnabled=true 时强制 mock；否则按 logistics.provider 配置匹配。
     * 未匹配到已知 provider 时降级为 mock。
     * </p>
     */
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

    /** 标准化 provider 名称：trim + lowercase，空值默认为 "mock" */
    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "mock";
        }
        return provider.trim().toLowerCase();
    }

}
