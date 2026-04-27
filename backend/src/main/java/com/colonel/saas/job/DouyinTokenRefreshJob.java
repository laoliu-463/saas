package com.colonel.saas.job;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.douyin.DouyinTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DouyinTokenRefreshJob {

    private final DouyinTokenService douyinTokenService;
    private final boolean autoRefreshEnabled;

    public DouyinTokenRefreshJob(
            DouyinTokenService douyinTokenService,
            @Value("${douyin.token.auto-refresh.enabled:true}") boolean autoRefreshEnabled) {
        this.douyinTokenService = douyinTokenService;
        this.autoRefreshEnabled = autoRefreshEnabled;
    }

    @Scheduled(cron = "${douyin.token.auto-refresh.cron:0 */10 * * * ?}")
    public void refreshExpiringToken() {
        if (!autoRefreshEnabled) {
            return;
        }
        try {
            DouyinTokenService.TokenStatus status = douyinTokenService.getTokenStatus(null);
            if (!status.isHasRefreshToken()) {
                log.debug("DouyinTokenRefreshJob skipped: refresh_token missing, appId={}", status.getAppId());
                return;
            }
            if (status.isReauthorizeRequired()) {
                log.warn("DouyinTokenRefreshJob skipped: reauthorize required, appId={}", status.getAppId());
                return;
            }
            if (!status.isHasAccessToken() || status.isTokenExpiringSoon()) {
                douyinTokenService.refreshToken(null);
                log.info("DouyinTokenRefreshJob refreshed token, appId={}", status.getAppId());
                return;
            }
            log.debug("DouyinTokenRefreshJob skipped: token still valid, appId={}", status.getAppId());
        } catch (DouyinApiException | BusinessException e) {
            log.warn("DouyinTokenRefreshJob failed with business error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("DouyinTokenRefreshJob failed unexpectedly", e);
        }
    }
}
