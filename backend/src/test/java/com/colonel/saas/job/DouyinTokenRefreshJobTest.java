package com.colonel.saas.job;

import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.service.DistributedJobLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DouyinTokenRefreshJobTest {

    @Mock
    private DouyinTokenService douyinTokenService;
    @Mock
    private DistributedJobLockService jobLockService;

    @BeforeEach
    void grantLock() {
        lenient().when(jobLockService.tryAcquire(eq(JobLockKeys.DOUYIN_TOKEN_REFRESH), any(Duration.class))).thenReturn(true);
    }

    @Test
    void refreshExpiringToken_shouldSkipWhenDisabled() {
        DouyinTokenRefreshJob job = new DouyinTokenRefreshJob(douyinTokenService, jobLockService, false);

        job.refreshExpiringToken();

        verify(douyinTokenService, never()).getTokenStatus(null);
        verify(douyinTokenService, never()).refreshToken(null);
        verify(jobLockService, never()).tryAcquire(any(), any());
    }

    @Test
    void refreshExpiringToken_shouldSkipWhenLockNotAcquired() {
        DouyinTokenRefreshJob job = new DouyinTokenRefreshJob(douyinTokenService, jobLockService, true);
        when(jobLockService.tryAcquire(eq(JobLockKeys.DOUYIN_TOKEN_REFRESH), any(Duration.class))).thenReturn(false);

        job.refreshExpiringToken();

        verify(douyinTokenService, never()).getTokenStatus(null);
    }

    @Test
    void refreshExpiringToken_shouldSkipWhenNoRefreshToken() {
        DouyinTokenRefreshJob job = new DouyinTokenRefreshJob(douyinTokenService, jobLockService, true);
        when(douyinTokenService.getTokenStatus(null)).thenReturn(
                new DouyinTokenService.TokenStatus("app1", true, "a", false, "", 0, true, false)
        );

        job.refreshExpiringToken();

        verify(douyinTokenService, never()).refreshToken(null);
        verify(jobLockService).release(JobLockKeys.DOUYIN_TOKEN_REFRESH);
    }

    @Test
    void refreshExpiringToken_shouldSkipWhenReauthorizeRequired() {
        DouyinTokenRefreshJob job = new DouyinTokenRefreshJob(douyinTokenService, jobLockService, true);
        when(douyinTokenService.getTokenStatus(null)).thenReturn(
                new DouyinTokenService.TokenStatus("app1", true, "a", true, "r", 0, true, true)
        );

        job.refreshExpiringToken();

        verify(douyinTokenService, never()).refreshToken(null);
    }

    @Test
    void refreshExpiringToken_shouldRefreshWhenTokenExpiringSoon() {
        DouyinTokenRefreshJob job = new DouyinTokenRefreshJob(douyinTokenService, jobLockService, true);
        when(douyinTokenService.getTokenStatus(null)).thenReturn(
                new DouyinTokenService.TokenStatus("app1", true, "a", true, "r", 0, true, false)
        );

        job.refreshExpiringToken();

        verify(douyinTokenService).refreshToken(null);
    }

    @Test
    void refreshExpiringToken_shouldSkipWhenTokenStillValid() {
        DouyinTokenRefreshJob job = new DouyinTokenRefreshJob(douyinTokenService, jobLockService, true);
        when(douyinTokenService.getTokenStatus(null)).thenReturn(
                new DouyinTokenService.TokenStatus("app1", true, "a", true, "r", 0, false, false)
        );

        job.refreshExpiringToken();

        verify(douyinTokenService, never()).refreshToken(null);
    }
}
