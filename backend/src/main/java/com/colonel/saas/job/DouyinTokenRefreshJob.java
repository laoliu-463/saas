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

/**
 * 抖音 OAuth Token 自动刷新定时任务。
 * <p>
 * 每 10 分钟（可配置）检查抖音 OAuth access_token 的有效期，
 * 在 token 即将过期时自动使用 refresh_token 进行续期，
 * 确保系统持续具备调用抖音电商开放平台 API 的授权。
 * </p>
 * <p>
 * 刷新决策逻辑：
 * <ol>
 *   <li>若 refresh_token 不存在 → 跳过（需人工重新授权）</li>
 *   <li>若需要重新授权（refresh_token 也过期）→ 记录警告并跳过</li>
 *   <li>若 access_token 不存在或即将过期 → 执行刷新</li>
 *   <li>若 token 仍有效 → 跳过</li>
 * </ol>
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：通过 {@code douyin.token.auto-refresh.cron} 配置，默认每 10 分钟</li>
 *   <li>分布式锁 TTL：5 分钟，防止多实例并发刷新</li>
 *   <li>通过 {@code douyin.token.auto-refresh.enabled} 控制开关，默认启用</li>
 * </ul>
 * </p>
 *
 * @see DouyinTokenService
 * @see JobLockKeys#DOUYIN_TOKEN_REFRESH
 */
@Slf4j
@Component
public class DouyinTokenRefreshJob {

    /** 分布式锁 TTL，Token 刷新通常很快完成，5 分钟足够 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    /** 抖音 Token 管理服务 */
    private final DouyinTokenService douyinTokenService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;
    /** 是否启用自动刷新 */
    private final boolean autoRefreshEnabled;

    public DouyinTokenRefreshJob(
            DouyinTokenService douyinTokenService,
            DistributedJobLockService jobLockService,
            @Value("${douyin.token.auto-refresh.enabled:true}") boolean autoRefreshEnabled) {
        this.douyinTokenService = douyinTokenService;
        this.jobLockService = jobLockService;
        this.autoRefreshEnabled = autoRefreshEnabled;
    }

    /**
     * 检查并刷新即将过期的抖音 OAuth Token。
     * <p>
     * 通过分布式锁确保多实例环境下只有一个节点执行刷新操作，
     * 避免并发刷新导致 token 失效冲突。
     * </p>
     */
    @Scheduled(cron = "${douyin.token.auto-refresh.cron:0 */10 * * * ?}")
    public void refreshExpiringToken() {
        if (!autoRefreshEnabled) {
            return;
        }
        // 获取分布式锁，防止多实例同时刷新 Token
        if (!jobLockService.tryAcquire(JobLockKeys.DOUYIN_TOKEN_REFRESH, LOCK_TTL)) {
            log.info("DouyinTokenRefreshJob skipped, another process is running");
            return;
        }
        try {
            // 查询当前 Token 状态
            DouyinTokenService.TokenStatus status = douyinTokenService.getTokenStatus(null);
            // refresh_token 不存在，无法自动刷新
            if (!status.isHasRefreshToken()) {
                log.debug("DouyinTokenRefreshJob skipped: refresh_token missing, appId={}", status.getAppId());
                return;
            }
            // refresh_token 已过期，需要人工重新授权
            if (status.isReauthorizeRequired()) {
                log.warn("DouyinTokenRefreshJob skipped: reauthorize required, appId={}", status.getAppId());
                return;
            }
            // access_token 不存在或即将过期，执行刷新
            if (!status.isHasAccessToken() || status.isTokenExpiringSoon()) {
                douyinTokenService.refreshToken(null);
                log.info("DouyinTokenRefreshJob refreshed token, appId={}", status.getAppId());
                return;
            }
            // Token 仍有效，无需刷新
            log.debug("DouyinTokenRefreshJob skipped: token still valid, appId={}", status.getAppId());
        } catch (DouyinApiException | BusinessException e) {
            log.warn("DouyinTokenRefreshJob failed with business error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("DouyinTokenRefreshJob failed unexpectedly", e);
        } finally {
            // 无论成功失败，都必须释放分布式锁
            jobLockService.release(JobLockKeys.DOUYIN_TOKEN_REFRESH);
        }
    }
}
