package com.colonel.saas.service;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.dto.logistics.LogisticsGatewayHealthResponse;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestRequest;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestResponse;
import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;
import com.colonel.saas.gateway.logistics.query.Kuaidi100LogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.KuaidiNiaoLogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.LogisticsGatewayHealthStatus;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryResult;
import com.colonel.saas.gateway.logistics.query.LogisticsStatusCode;
import com.colonel.saas.gateway.logistics.query.MockLogisticsQueryGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物流网关健康检查与诊断服务。
 * <p>
 * 负责对物流查询网关（快递鸟、快递100、Mock）进行配置校验、连通性测试和状态跟踪，
 * 为运维页面和管理后台提供物流通道的实时健康状态。
 * </p>
 *
 * <ul>
 *   <li>诊断当前活跃 provider 或指定 provider 的配置与健康状态</li>
 *   <li>执行真实/沙箱物流查询测试并缓存最近测试结果</li>
 *   <li>管理三种 provider 的启用状态、凭证完整性和沙箱模式</li>
 *   <li>对运单号进行脱敏处理，防止日志泄露敏感信息</li>
 * </ul>
 *
 * <p>所属领域：物流域</p>
 *
 * @see LogisticsProperties 物流配置属性
 * @see LogisticsGatewayHealthStatus 物流网关健康状态枚举
 * @see LogisticsQueryGateway 物流查询网关抽象接口
 */
@Slf4j
@Service
public class LogisticsGatewayHealthService {

    /** 物流配置属性，包含各 provider 的启用状态、凭证和沙箱设置 */
    private final LogisticsProperties properties;
    /** Mock 物流查询网关，用于测试环境 */
    private final MockLogisticsQueryGateway mockGateway;
    /** 快递鸟物流查询网关 */
    private final KuaidiNiaoLogisticsQueryGateway kuaidiNiaoGateway;
    /** 快递100物流查询网关 */
    private final Kuaidi100LogisticsQueryGateway kuaidi100Gateway;
    /** 是否为测试环境标识，为 true 时禁止请求真实物流 API */
    private final boolean testEnabled;

    /** 缓存各 provider 最近一次测试的健康状态，用于诊断报告中展示历史测试结果 */
    private final Map<String, LogisticsGatewayHealthStatus> lastTestStatus = new ConcurrentHashMap<>();

    /**
     * 构造函数，通过 Spring 依赖注入初始化所有物流网关实例。
     *
     * @param properties     物流配置属性
     * @param mockGateway    Mock 物流查询网关
     * @param kuaidiNiaoGateway 快递鸟物流查询网关
     * @param kuaidi100Gateway  快递100物流查询网关
     * @param testEnabled    是否为测试环境（从配置 app.test.enabled 读取）
     */
    public LogisticsGatewayHealthService(
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

    /**
     * 诊断当前配置的物流 provider 的健康状态。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>从配置中读取当前 provider 名称并标准化</li>
     *   <li>构建该 provider 的配置状态</li>
     *   <li>结合最近测试结果生成健康响应</li>
     * </ol>
     *
     * @return 当前 provider 的健康诊断结果
     */
    public LogisticsGatewayHealthResponse diagnoseCurrentProvider() {
        String provider = normalizeProvider(properties.getProvider());
        return buildHealthResponse(provider, resolveConfigured(provider));
    }

    /**
     * 诊断指定物流 provider 的健康状态。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>标准化 provider 名称</li>
     *   <li>构建该 provider 的配置状态</li>
     *   <li>结合最近测试结果生成健康响应</li>
     * </ol>
     *
     * @param providerName provider 名称（如 kuaidiniao、kuaidi100、mock）
     * @return 指定 provider 的健康诊断结果
     */
    public LogisticsGatewayHealthResponse diagnoseProvider(String providerName) {
        String provider = normalizeProvider(providerName);
        return buildHealthResponse(provider, resolveConfigured(provider));
    }

    /**
     * 执行物流网关的查询测试。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>标准化 provider 名称并解析配置状态</li>
     *   <li>若为 MOCK_ONLY，直接返回"未请求真实 API"</li>
     *   <li>若为 NOT_CONFIGURED，返回凭证未配置的错误信息</li>
     *   <li>若在测试环境（testEnabled=true），禁止请求真实 API</li>
     *   <li>解析对应的网关实例，构建物流查询命令并调用</li>
     *   <li>根据返回结果更新 lastTestStatus 缓存</li>
     *   <li>返回测试结果（成功/失败、沙箱/真实、耗时等）</li>
     * </ol>
     *
     * @param request 物流网关测试请求，包含 provider、物流公司编码、运单号等
     * @return 测试结果，包含成功状态、provider、健康状态、消息和是否存储了原始响应
     */
    public LogisticsGatewayTestResponse testQuery(LogisticsGatewayTestRequest request) {
        String provider = normalizeProvider(request.getProvider());
        ConfigState config = resolveConfigured(provider);
        if (config.status() == LogisticsGatewayHealthStatus.MOCK_ONLY) {
            return LogisticsGatewayTestResponse.builder()
                    .success(true)
                    .provider(provider)
                    .status(LogisticsGatewayHealthStatus.MOCK_ONLY)
                    .message("当前为 mock provider，未请求真实物流 API")
                    .rawPayloadStored(false)
                    .build();
        }
        if (config.status() == LogisticsGatewayHealthStatus.NOT_CONFIGURED) {
            return LogisticsGatewayTestResponse.builder()
                    .success(false)
                    .provider(provider)
                    .status(LogisticsGatewayHealthStatus.NOT_CONFIGURED)
                    .message(config.message())
                    .rawPayloadStored(false)
                    .build();
        }
        if (testEnabled) {
            return LogisticsGatewayTestResponse.builder()
                    .success(false)
                    .provider(provider)
                    .status(LogisticsGatewayHealthStatus.READY)
                    .message("test 环境禁止请求真实物流 API")
                    .rawPayloadStored(false)
                    .build();
        }

        long started = System.currentTimeMillis();
        LogisticsQueryGateway gateway = resolveGateway(provider);
        String maskedTracking = maskTrackingNo(request.getTrackingNo());
        log.info("Logistics gateway test: provider={}, company={}, trackingNo={}, testEnabled={}",
                provider, request.getLogisticsCompany(), maskedTracking, testEnabled);

        LogisticsQueryResult result = gateway.query(LogisticsTrackCommand.builder()
                .companyCode(request.getLogisticsCompany())
                .trackingNo(request.getTrackingNo())
                .phone(request.getPhone())
                .from(request.getFrom())
                .to(request.getTo())
                .build());
        long elapsed = System.currentTimeMillis() - started;
        log.info("Logistics gateway test finished: provider={}, success={}, statusCode={}, elapsedMs={}",
                provider, result.isSuccess(),
                result.getStatusCode() == null ? null : result.getStatusCode().name(),
                elapsed);

        if (result.getStatusCode() == LogisticsStatusCode.NOT_CONFIGURED) {
            return LogisticsGatewayTestResponse.builder()
                    .success(false)
                    .provider(provider)
                    .status(LogisticsGatewayHealthStatus.NOT_CONFIGURED)
                    .message(result.getErrorMessage())
                    .rawPayloadStored(false)
                    .build();
        }
        if (!result.isSuccess()) {
            lastTestStatus.put(provider, LogisticsGatewayHealthStatus.FAILED);
            return LogisticsGatewayTestResponse.builder()
                    .success(false)
                    .provider(provider)
                    .status(LogisticsGatewayHealthStatus.FAILED)
                    .message(result.getErrorMessage())
                    .rawPayloadStored(false)
                    .build();
        }

        LogisticsGatewayHealthStatus status = config.sandboxEnabled()
                ? LogisticsGatewayHealthStatus.SANDBOX_PASSED
                : LogisticsGatewayHealthStatus.REAL_CONNECTED;
        lastTestStatus.put(provider, status);
        return LogisticsGatewayTestResponse.builder()
                .success(true)
                .provider(provider)
                .status(status)
                .message(config.sandboxEnabled() ? "Sandbox 查询成功" : "真实查询成功")
                .rawPayloadStored(result.getRawPayload() != null && !result.getRawPayload().isEmpty())
                .build();
    }

    private LogisticsGatewayHealthResponse buildHealthResponse(String provider, ConfigState config) {
        LogisticsGatewayHealthStatus status = lastTestStatus.getOrDefault(provider, config.status());
        if (config.status() == LogisticsGatewayHealthStatus.NOT_CONFIGURED
                || config.status() == LogisticsGatewayHealthStatus.MOCK_ONLY) {
            status = config.status();
        } else if (status == LogisticsGatewayHealthStatus.FAILED
                || status == LogisticsGatewayHealthStatus.SANDBOX_PASSED
                || status == LogisticsGatewayHealthStatus.REAL_CONNECTED) {
            // keep last test result
        } else {
            status = config.status();
        }
        return LogisticsGatewayHealthResponse.builder()
                .provider(provider)
                .enabled(config.enabled())
                .configured(config.configured())
                .status(status)
                .message(config.message())
                .checkedAt(LocalDateTime.now())
                .build();
    }

    private ConfigState resolveConfigured(String provider) {
        if ("mock".equals(provider)) {
            return new ConfigState(true, true, LogisticsGatewayHealthStatus.MOCK_ONLY, false, "mock provider，不请求真实 API");
        }
        if ("kuaidiniao".equals(provider) || "kuaidi100".equals(provider)) {
            boolean enabled = "kuaidiniao".equals(provider)
                    ? properties.getKdn().isEnabled()
                    : properties.getKd100().isEnabled();
            boolean configured = "kuaidiniao".equals(provider)
                    ? isKdnConfigured()
                    : isKd100Configured();
            boolean sandbox = "kuaidiniao".equals(provider)
                    ? properties.getKdn().isSandboxEnabled()
                    : properties.getKd100().isSandboxEnabled();
            if (!enabled) {
                return new ConfigState(false, configured, LogisticsGatewayHealthStatus.NOT_CONFIGURED,
                        sandbox, providerLabel(provider) + " 未启用");
            }
            if (!configured) {
                return new ConfigState(true, false, LogisticsGatewayHealthStatus.NOT_CONFIGURED,
                        sandbox, providerLabel(provider) + " 凭证未配置");
            }
            return new ConfigState(true, true, LogisticsGatewayHealthStatus.READY,
                    sandbox, providerLabel(provider) + " 凭证已配置，待联调验证");
        }
        return new ConfigState(false, false, LogisticsGatewayHealthStatus.NOT_CONFIGURED,
                false, "未知 provider: " + provider);
    }

    private boolean isKdnConfigured() {
        return StringUtils.hasText(properties.getKdn().getEbusinessId())
                && StringUtils.hasText(properties.getKdn().getApiKey());
    }

    private boolean isKd100Configured() {
        return StringUtils.hasText(properties.getKd100().getCustomer())
                && StringUtils.hasText(properties.getKd100().getKey());
    }

    private LogisticsQueryGateway resolveGateway(String provider) {
        if ("kuaidiniao".equals(provider)) {
            return kuaidiNiaoGateway;
        }
        if ("kuaidi100".equals(provider)) {
            return kuaidi100Gateway;
        }
        return mockGateway;
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "mock";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private String providerLabel(String provider) {
        if ("kuaidiniao".equals(provider)) {
            return "快递鸟";
        }
        if ("kuaidi100".equals(provider)) {
            return "快递100";
        }
        return provider;
    }

    static String maskTrackingNo(String trackingNo) {
        if (!StringUtils.hasText(trackingNo)) {
            return "***";
        }
        String trimmed = trackingNo.trim();
        if (trimmed.length() <= 5) {
            return "***";
        }
        return trimmed.substring(0, 3) + "***" + trimmed.substring(trimmed.length() - 2);
    }

    private record ConfigState(
            boolean enabled,
            boolean configured,
            LogisticsGatewayHealthStatus status,
            boolean sandboxEnabled,
            String message) {
    }
}
