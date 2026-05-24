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
 * 抖店/联盟 Token 驱动的官方 API 采集适配层。当前 SDK 无达人主页资料接口，返回明确错误码供 fallback。
 */
@Slf4j
@Component
public class DouyinApiTalentProfileProvider implements TalentProfileProvider {

    private final TalentCollectProperties collectProperties;
    private final DouyinTokenService douyinTokenService;

    public DouyinApiTalentProfileProvider(
            TalentCollectProperties collectProperties,
            DouyinTokenService douyinTokenService) {
        this.collectProperties = collectProperties;
        this.douyinTokenService = douyinTokenService;
    }

    @Override
    public String providerCode() {
        return "API";
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public boolean supports(TalentProfileQuery query) {
        return collectProperties.isApiAllowed()
                && collectProperties.getApi().isEnabled()
                && query != null
                && StringUtils.hasText(query.getInput())
                && !query.isManualFill();
    }

    @Override
    public TalentProfileResult fetch(TalentProfileQuery query) {
        String requestId = UUID.randomUUID().toString();
        long started = System.currentTimeMillis();
        String talentRef = query.getTalentId() == null ? query.getInput() : query.getTalentId().toString();
        try {
            DouyinTokenService.TokenStatus tokenStatus = douyinTokenService.getTokenStatus(null);
            if (!tokenStatus.isHasAccessToken() && !tokenStatus.isHasRefreshToken()) {
                return logAndFail(requestId, talentRef, started, "NOT_CONFIGURED", "抖店 Token 未配置，请先完成授权");
            }
            if (tokenStatus.isReauthorizeRequired()) {
                return logAndFail(requestId, talentRef, started, "NOT_AUTHORIZED", "抖店 Token 需重新授权");
            }
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
