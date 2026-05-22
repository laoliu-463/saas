package com.colonel.saas.job;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.service.DistributedJobLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class DouyinTokenRefreshJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final DouyinTokenService douyinTokenService;
    private final DistributedJobLockService jobLockService;
    private final boolean autoRefreshEnabled;

    public DouyinTokenRefreshJob(
            DouyinTokenService douyinTokenService,
            DistributedJobLockService jobLockService,
            @Value("${douyin.token.auto-refresh.enabled:true}") boolean autoRefreshEnabled) {
        this.douyinTokenService = douyinTokenService;
        this.jobLockService = jobLockService;
        this.autoRefreshEnabled = autoRefreshEnabled;
    }

    @Scheduled(cron = "${douyin.token.auto-refresh.cron:0 */10 * * * ?}")
    public void refreshExpiringToken() {
        if (!autoRefreshEnabled) {
            return;
        }
        if (!jobLockService.tryAcquire(JobLockKeys.DOUYIN_TOKEN_REFRESH, LOCK_TTL)) {
            log.info("DouyinTokenRefreshJob skipped, another process is running");
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
        } finally {
            jobLockService.release(JobLockKeys.DOUYIN_TOKEN_REFRESH);
        }
    }
}
