package com.colonel.saas.job;

import com.colonel.saas.service.SampleLifecycleService;
import io.lettuce.core.RedisCommandExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class SampleLifecycleJob {

    private static final String JOB_LOCK_KEY = "sample:lifecycle:job:lock";

    private final SampleLifecycleService sampleLifecycleService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean testEnabled;
    private final AtomicBoolean localLock = new AtomicBoolean(false);

    public SampleLifecycleJob(
            SampleLifecycleService sampleLifecycleService,
            RedisTemplate<String, Object> redisTemplate,
            @Value("${app.test.enabled:false}") boolean testEnabled) {
        this.sampleLifecycleService = sampleLifecycleService;
        this.redisTemplate = redisTemplate;
        this.testEnabled = testEnabled;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void autoCloseTimeoutRequests() {
        if (!acquireJobLock()) {
            log.info("SampleLifecycleJob skipped, another process is running");
            return;
        }
        try {
            try {
                int closedHomework = sampleLifecycleService.autoCloseTimeoutPendingHomework();
                log.info("SampleLifecycleJob auto close homework completed, closed={}", closedHomework);
            } catch (Exception ex) {
                log.error("SampleLifecycleJob auto close homework failed", ex);
            }
            try {
                int closedShip = sampleLifecycleService.autoCloseTimeoutPendingShip();
                log.info("SampleLifecycleJob auto close ship completed, closed={}", closedShip);
            } catch (Exception ex) {
                log.error("SampleLifecycleJob auto close ship failed", ex);
            }
        } finally {
            releaseJobLock();
        }
    }

    private boolean acquireJobLock() {
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    Objects.requireNonNull(JOB_LOCK_KEY),
                    "1",
                    Objects.requireNonNull(Duration.ofMinutes(30))
            );
            return Boolean.TRUE.equals(locked);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when acquiring sample lifecycle lock, fallback to local lock: {}", ex.getMessage());
                return localLock.compareAndSet(false, true);
            }
            throw ex;
        }
    }

    private void releaseJobLock() {
        localLock.set(false);
        try {
            redisTemplate.delete(JOB_LOCK_KEY);
        } catch (RedisConnectionFailureException | RedisCommandExecutionException ex) {
            if (testEnabled) {
                log.warn("Redis unavailable in test mode when releasing sample lifecycle lock, local lock already released: {}", ex.getMessage());
                return;
            }
            throw ex;
        }
    }
}
