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

/**
 * 寄样物流订阅服务。
 * <p>
 * 负责在寄样申请发货后，向快递100发起物流轨迹订阅，
 * 以便后续自动回调获取物流状态变更。使用 {@link ObjectProvider} 延迟加载网关，
 * 避免未配置快递100时启动报错。
 * </p>
 *
 * <ul>
 *     <li>发货后自动订阅快递100物流追踪（{@link #subscribeAfterShipment}）</li>
 *     <li>判断快递100订阅是否启用（{@link #isKuaidi100SubscribeEnabled()}）</li>
 *     <li>记录订阅状态（成功/失败/跳过）到寄样申请实体</li>
 *     <li>物流单号脱敏处理（{@link #mask(String)}）</li>
 * </ul>
 *
 * <p><b>业务域：</b>寄样域 — 物流追踪订阅</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link Kuaidi100LogisticsGateway} — 快递100 API 网关</li>
 *     <li>{@link LogisticsProperties} — 物流配置属性</li>
 *     <li>{@link SampleRequestMapper} — 寄样申请数据访问</li>
 * </ul>
 *
 * @see Kuaidi100LogisticsGateway
 * @see LogisticsProperties
 */
@Slf4j
@Service
public class SampleLogisticsSubscriptionService {

    /** 物流服务提供商标识 */
    private static final String PROVIDER = "KUAIDI100";
    /** 订阅状态：已订阅 */
    private static final String STATUS_SUBSCRIBED = "SUBSCRIBED";
    /** 订阅状态：失败 */
    private static final String STATUS_FAILED = "FAILED";
    /** 订阅状态：跳过 */
    private static final String STATUS_SKIPPED = "SKIPPED";

    /** 物流配置属性 */
    private final LogisticsProperties properties;
    /** 快递100网关延迟加载提供者 */
    private final ObjectProvider<Kuaidi100LogisticsGateway> logisticsGatewayProvider;
    /** 寄样申请数据访问 */
    private final SampleRequestMapper sampleRequestMapper;

    public SampleLogisticsSubscriptionService(
            LogisticsProperties properties,
            ObjectProvider<Kuaidi100LogisticsGateway> logisticsGatewayProvider,
            SampleRequestMapper sampleRequestMapper) {
        this.properties = properties;
        this.logisticsGatewayProvider = logisticsGatewayProvider;
        this.sampleRequestMapper = sampleRequestMapper;
    }

    /**
     * 在寄样发货后发起物流轨迹订阅。
     * <p>处理流程：</p>
     * <ol>
     *     <li>校验物流单号是否存在</li>
     *     <li>校验快递100订阅功能是否启用</li>
     *     <li>校验快递公司编码非空</li>
     *     <li>通过延迟加载获取快递100网关实例</li>
     *     <li>构建订阅指令并调用快递100订阅接口</li>
     *     <li>根据返回结果更新寄样申请的订阅状态（成功/失败）</li>
     * </ol>
     *
     * @param sample 寄样申请实体（必须已填充物流单号和快递公司编码）
     * @return 订阅尝试结果（成功/失败/跳过）
     */
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

    /**
     * 判断快递100物流订阅是否启用。
     * <p>需同时满足三个条件：provider 为 kuaidi100、快递100启用、订阅功能启用。</p>
     *
     * @return true 表示已启用，false 表示未启用
     */
    private boolean isKuaidi100SubscribeEnabled() {
        return "kuaidi100".equalsIgnoreCase(StringUtils.hasText(properties.getProvider())
                ? properties.getProvider().trim()
                : "")
                && properties.getKd100().isEnabled()
                && properties.getKd100().isSubscribeEnabled();
    }

    /**
     * 标记订阅失败并持久化。
     * <p>将物流提供者设为 KUAIDI100，订阅状态设为 FAILED，并记录异常原因和最后订阅时间。</p>
     *
     * @param sample  寄样申请实体
     * @param message 失败原因描述
     */
    private void markFailed(SampleRequest sample, String message) {
        sample.setLogisticsProvider(PROVIDER);
        sample.setLogisticsSubscribeStatus(STATUS_FAILED);
        sample.setLogisticsLastSubscribeAt(LocalDateTime.now());
        sample.setLogisticsExceptionReason(StringUtils.hasText(message) ? message : "订阅失败");
        safeUpdate(sample);
    }

    /**
     * 安全持久化寄样申请。
     * <p>捕获数据库异常并记录警告日志，避免订阅流程因持久化失败而中断。</p>
     *
     * @param sample 寄样申请实体
     */
    private void safeUpdate(SampleRequest sample) {
        try {
            sampleRequestMapper.updateById(sample);
        } catch (Exception e) {
            log.warn("Persist logistics subscribe summary failed, requestNo={}, exception={}",
                    sample.getRequestNo(), e.getClass().getSimpleName());
        }
    }

    /**
     * 物流单号脱敏处理。
     * <p>保留前3位和后3位字符，中间替换为 ***，用于日志输出时保护隐私。
     * 单号长度不超过6位时返回 ***。</p>
     *
     * @param trackingNo 原始物流单号
     * @return 脱敏后的物流单号
     */
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

    /**
     * 物流订阅尝试结果。
     *
     * @param success    是否订阅成功
     * @param skipped    是否跳过（未满足订阅条件）
     * @param returnCode 返回码（成功时为快递100返回码，跳过时为 SKIPPED）
     * @param message    结果描述信息
     */
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
