package com.colonel.saas.service.talent;

import com.colonel.saas.config.TalentCollectEnvironmentStatus;
import com.colonel.saas.config.TalentCollectProperties;
import com.colonel.saas.douyin.DouyinTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class TalentCollectEnvironmentReporter {

    private final TalentCollectProperties collectProperties;
    private final DouyinTokenService douyinTokenService;
    private final Environment environment;

    public TalentCollectEnvironmentStatus resolveStatus() {
        if (isTestEnrichProfile()) {
            return TalentCollectEnvironmentStatus.MOCK_ONLY;
        }
        if (collectProperties.isMockOnly()) {
            return TalentCollectEnvironmentStatus.MOCK_ONLY;
        }
        if (!collectProperties.isApiAllowed() || !collectProperties.getApi().isEnabled()) {
            return collectProperties.isCrawlerAllowed()
                    ? TalentCollectEnvironmentStatus.CRAWLER_FALLBACK
                    : TalentCollectEnvironmentStatus.NOT_CONFIGURED;
        }
        DouyinTokenService.TokenStatus tokenStatus = douyinTokenService.getTokenStatus(null);
        if (!tokenStatus.isHasAccessToken() && !tokenStatus.isHasRefreshToken()) {
            return TalentCollectEnvironmentStatus.NOT_CONFIGURED;
        }
        if (tokenStatus.isReauthorizeRequired()) {
            return TalentCollectEnvironmentStatus.NOT_AUTHORIZED;
        }
        // 抖店开放接口当前无达人主页资料拉取能力（TalentApi 仅转链等），不得宣称 REAL_CONNECTED
        return TalentCollectEnvironmentStatus.UNSUPPORTED;
    }

    public String resolveStatusLabel() {
        return resolveStatus().name();
    }

    private boolean isTestEnrichProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("test".equalsIgnoreCase(profile) || "local-mock".equalsIgnoreCase(profile)) {
                String enrichMode = environment.getProperty("talent.enrich.mode", "");
                if ("test".equalsIgnoreCase(enrichMode)) {
                    return true;
                }
            }
        }
        String enrichMode = environment.getProperty("talent.enrich.mode", "");
        return StringUtils.hasText(enrichMode) && "test".equalsIgnoreCase(enrichMode.trim());
    }
}
