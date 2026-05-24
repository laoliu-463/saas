package com.colonel.saas.service;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.dto.logistics.LogisticsGatewayHealthResponse;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestRequest;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestResponse;
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

@Slf4j
@Service
public class LogisticsGatewayHealthService {

    private final LogisticsProperties properties;
    private final MockLogisticsQueryGateway mockGateway;
    private final KuaidiNiaoLogisticsQueryGateway kuaidiNiaoGateway;
    private final Kuaidi100LogisticsQueryGateway kuaidi100Gateway;
    private final boolean testEnabled;

    private final Map<String, LogisticsGatewayHealthStatus> lastTestStatus = new ConcurrentHashMap<>();

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

    public LogisticsGatewayHealthResponse diagnoseCurrentProvider() {
        String provider = normalizeProvider(properties.getProvider());
        return buildHealthResponse(provider, resolveConfigured(provider));
    }

    public LogisticsGatewayHealthResponse diagnoseProvider(String providerName) {
        String provider = normalizeProvider(providerName);
        return buildHealthResponse(provider, resolveConfigured(provider));
    }

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

        LogisticsQueryResult result = gateway.query(request.getLogisticsCompany(), request.getTrackingNo());
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
