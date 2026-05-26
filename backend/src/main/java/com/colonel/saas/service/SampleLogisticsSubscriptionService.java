package com.colonel.saas.service;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.gateway.logistics.LogisticsSubscribeCommand;
import com.colonel.saas.gateway.logistics.LogisticsSubscribeResult;
import com.colonel.saas.gateway.logistics.kuaidi100.Kuaidi100LogisticsGateway;
import com.colonel.saas.mapper.SampleRequestMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
public class SampleLogisticsSubscriptionService {

    private static final String PROVIDER = "KUAIDI100";
    private static final String STATUS_SUBSCRIBED = "SUBSCRIBED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";

    private final LogisticsProperties properties;
    private final ObjectProvider<Kuaidi100LogisticsGateway> logisticsGatewayProvider;
    private final SampleRequestMapper sampleRequestMapper;

    public SampleLogisticsSubscriptionService(
            LogisticsProperties properties,
            ObjectProvider<Kuaidi100LogisticsGateway> logisticsGatewayProvider,
            SampleRequestMapper sampleRequestMapper) {
        this.properties = properties;
        this.logisticsGatewayProvider = logisticsGatewayProvider;
        this.sampleRequestMapper = sampleRequestMapper;
    }

    public SubscribeAttemptResult subscribeAfterShipment(SampleRequest sample) {
        if (sample == null || !StringUtils.hasText(sample.getTrackingNo())) {
            return SubscribeAttemptResult.skipped("缺少物流单号");
        }
        if (!isKuaidi100SubscribeEnabled()) {
            return SubscribeAttemptResult.skipped("快递100订阅未启用");
        }
        if (!StringUtils.hasText(sample.getShipperCode())) {
            markFailed(sample, "快递公司编码不能为空");
            return SubscribeAttemptResult.failed("快递公司编码不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        sample.setLogisticsProvider(PROVIDER);
        sample.setLogisticsLastSubscribeAt(now);
        try {
            Kuaidi100LogisticsGateway logisticsGateway = logisticsGatewayProvider.getIfAvailable();
            if (logisticsGateway == null) {
                markFailed(sample, "快递100网关未配置");
                return SubscribeAttemptResult.failed("快递100网关未配置");
            }
            LogisticsSubscribeResult result = logisticsGateway.subscribeTrack(LogisticsSubscribeCommand.builder()
                    .sampleRequestId(sample.getId())
                    .companyCode(sample.getShipperCode())
                    .trackingNo(sample.getTrackingNo())
                    .phone(sample.getRecipientPhone())
                    .to(sample.getRecipientAddress())
                    .build());
            if (result != null && result.isSuccess()) {
                sample.setLogisticsSubscribeStatus(STATUS_SUBSCRIBED);
                if (sample.getLogisticsSubscribedAt() == null) {
                    sample.setLogisticsSubscribedAt(now);
                }
                sample.setLogisticsExceptionReason(null);
                safeUpdate(sample);
                return SubscribeAttemptResult.success(result.getReturnCode(), result.getMessage());
            }
            String message = result == null ? "订阅返回空响应" : result.getMessage();
            markFailed(sample, message);
            return SubscribeAttemptResult.failed(message);
        } catch (Exception e) {
            log.warn("Sample logistics subscribe failed, requestNo={}, trackingNo={}, error={}",
                    sample.getRequestNo(), mask(sample.getTrackingNo()), e.getMessage());
            markFailed(sample, e.getMessage());
            return SubscribeAttemptResult.failed(e.getMessage());
        }
    }

    private boolean isKuaidi100SubscribeEnabled() {
        return "kuaidi100".equalsIgnoreCase(StringUtils.hasText(properties.getProvider())
                ? properties.getProvider().trim()
                : "")
                && properties.getKd100().isEnabled()
                && properties.getKd100().isSubscribeEnabled();
    }

    private void markFailed(SampleRequest sample, String message) {
        sample.setLogisticsProvider(PROVIDER);
        sample.setLogisticsSubscribeStatus(STATUS_FAILED);
        sample.setLogisticsLastSubscribeAt(LocalDateTime.now());
        sample.setLogisticsExceptionReason(StringUtils.hasText(message) ? message : "订阅失败");
        safeUpdate(sample);
    }

    private void safeUpdate(SampleRequest sample) {
        try {
            sampleRequestMapper.updateById(sample);
        } catch (Exception e) {
            log.warn("Persist logistics subscribe summary failed, requestNo={}, exception={}",
                    sample.getRequestNo(), e.getClass().getSimpleName());
        }
    }

    private String mask(String trackingNo) {
        if (!StringUtils.hasText(trackingNo)) {
            return "";
        }
        String trimmed = trackingNo.trim();
        if (trimmed.length() <= 6) {
            return "***";
        }
        return trimmed.substring(0, 3).toUpperCase(Locale.ROOT) + "***" + trimmed.substring(trimmed.length() - 3);
    }

    public record SubscribeAttemptResult(boolean success, boolean skipped, String returnCode, String message) {
        static SubscribeAttemptResult success(String returnCode, String message) {
            return new SubscribeAttemptResult(true, false, returnCode, message);
        }

        static SubscribeAttemptResult failed(String message) {
            return new SubscribeAttemptResult(false, false, null, message);
        }

        static SubscribeAttemptResult skipped(String message) {
            return new SubscribeAttemptResult(false, true, STATUS_SKIPPED, message);
        }
    }
}
