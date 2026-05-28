package com.colonel.saas.service.talent.profile.provider;

import com.colonel.saas.config.TalentCollectProperties;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.service.talent.profile.TalentProfileProvider;
import com.colonel.saas.service.talent.profile.TalentProfileQuery;
import com.colonel.saas.service.talent.profile.TalentProfileResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 抖店/联盟 Token 驱动的官方 API 达人资料采集适配层。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>检查抖店 Token 状态（是否已配置、是否需要重新授权）</li>
 *   <li>当前抖音开放平台 SDK 尚未提供达人主页资料拉取接口，
 *       因此本提供者仅做 Token 状态校验，不实际发起 API 调用</li>
 *   <li>返回明确的错误码（NOT_CONFIGURED / NOT_AUTHORIZED / UNSUPPORTED），
 *       供上层策略链 fallback 到爬虫或手动补录提供者</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>作为 {@link TalentProfileProvider} 策略链中优先级最高的提供者（order=5），
 * 优先尝试官方 API 通道。若 Token 不可用或接口不支持，自动降级到下一级提供者。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域 / 抖店官方 API 通道</p>
 *
 * @see TalentProfileProvider
 * @see com.colonel.saas.douyin.DouyinTokenService
 */
@Slf4j
@Component
public class DouyinApiTalentProfileProvider implements TalentProfileProvider {

    /** 采集配置属性，控制是否允许 API 类采集 */
    private final TalentCollectProperties collectProperties;

    /** 抖店 Token 服务，用于获取和管理抖店/联盟的 OAuth Token 状态 */
    private final DouyinTokenService douyinTokenService;

    /**
     * 构造方法 —— 通过 Spring 注入依赖。
     *
     * @param collectProperties  采集配置属性
     * @param douyinTokenService 抖店 Token 服务
     */
    public DouyinApiTalentProfileProvider(
            TalentCollectProperties collectProperties,
            DouyinTokenService douyinTokenService) {
        this.collectProperties = collectProperties;
        this.douyinTokenService = douyinTokenService;
    }

    /** {@inheritDoc} 返回提供者唯一标识 "API"，表示抖音官方 API 通道 */
    @Override
    public String providerCode() {
        return "API";
    }

    /** {@inheritDoc} 返回最高优先级 5，在策略链中最先被尝试 */
    @Override
    public int order() {
        return 5;
    }

    /**
     * {@inheritDoc}
     *
     * <p>判断是否支持当前查询，需同时满足：</p>
     * <ol>
     *   <li>采集配置允许 API 类采集</li>
     *   <li>抖店 API 配置已启用</li>
     *   <li>查询对象非空且包含有效输入</li>
     *   <li>不是手动填写模式</li>
     * </ol>
     */
    @Override
    public boolean supports(TalentProfileQuery query) {
        return collectProperties.isApiAllowed()
                && collectProperties.getApi().isEnabled()
                && query != null
                && StringUtils.hasText(query.getInput())
                && !query.isManualFill();
    }

    /**
     * {@inheritDoc}
     *
     * <p>尝试通过抖店官方 API 采集达人资料。当前 SDK 不支持该能力，因此仅做 Token 校验。</p>
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：生成唯一请求 ID，记录开始时间</li>
     *   <li>第二步：获取抖店 Token 状态</li>
     *   <li>第三步：根据 Token 状态返回对应的错误码
     *     <ul>
     *       <li>Token 未配置 → NOT_CONFIGURED</li>
     *       <li>Token 需重新授权 → NOT_AUTHORIZED</li>
     *       <li>Token 正常但接口不支持 → UNSUPPORTED</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param query 达人资料查询请求
     * @return 失败的采集结果，包含具体错误码和错误信息
     */
    @Override
    public TalentProfileResult fetch(TalentProfileQuery query) {
        // 第一步：生成唯一请求 ID，用于日志追踪
        String requestId = UUID.randomUUID().toString();
        long started = System.currentTimeMillis();
        String talentRef = query.getTalentId() == null ? query.getInput() : query.getTalentId().toString();
        try {
            // 第二步：获取抖店 Token 状态
            DouyinTokenService.TokenStatus tokenStatus = douyinTokenService.getTokenStatus(null);
            // 第三步：根据 Token 状态判断失败原因
            if (!tokenStatus.isHasAccessToken() && !tokenStatus.isHasRefreshToken()) {
                return logAndFail(requestId, talentRef, started, "NOT_CONFIGURED", "抖店 Token 未配置，请先完成授权");
            }
            if (tokenStatus.isReauthorizeRequired()) {
                return logAndFail(requestId, talentRef, started, "NOT_AUTHORIZED", "抖店 Token 需重新授权");
            }
            // Token 正常但当前 SDK 无达人主页资料接口，返回 UNSUPPORTED 供 fallback
            return logAndFail(
                    requestId,
                    talentRef,
                    started,
                    "UNSUPPORTED",
                    "抖店开放接口暂未提供达人主页资料拉取能力，请使用爬虫或手动补录");
        } catch (RuntimeException ex) {
            return logAndFail(requestId, talentRef, started, "API_FAILED", ex.getMessage());
        }
    }

    /**
     * 记录采集跳过/失败日志，并构建失败的采集结果对象。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：计算耗时（毫秒）</li>
     *   <li>第二步：输出 INFO 级别日志，包含 requestId、达人引用、状态、错误码和耗时</li>
     *   <li>第三步：构建原始负载（rawPayload）记录来源和错误信息</li>
     *   <li>第四步：构建并返回失败结果</li>
     * </ol>
     *
     * @param requestId  唯一请求 ID，用于日志关联
     * @param talentRef  达人引用标识（talentId 或原始输入）
     * @param startedAt  请求开始时间戳（毫秒）
     * @param errorCode  错误码（NOT_CONFIGURED / NOT_AUTHORIZED / UNSUPPORTED / API_FAILED）
     * @param message    错误描述信息
     * @return 包含错误信息的失败采集结果
     */
    private TalentProfileResult logAndFail(
            String requestId,
            String talentRef,
            long startedAt,
            String errorCode,
            String message) {
        long elapsed = System.currentTimeMillis() - startedAt;
        log.info(
                "Talent API collect skipped, requestId={}, talentRef={}, source=API, status={}, errorCode={}, elapsedMs={}",
                requestId,
                talentRef,
                "failed",
                errorCode,
                elapsed);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("source", "API");
        raw.put("requestId", requestId);
        raw.put("errorCode", errorCode);
        return TalentProfileResult.builder()
                .success(false)
                .providerCode(providerCode())
                .syncStatus(TalentProfileResult.STATUS_FAILED)
                .errorCode(errorCode)
                .errorMessage(message)
                .unsupportedFields(TalentProfileResult.DEFAULT_UNSUPPORTED)
                .rawPayload(raw)
                .build();
    }
}
