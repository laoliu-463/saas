package com.colonel.saas.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.service.LogisticsTrackService;
import io.lettuce.core.RedisCommandExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class LogisticsTrackJob {

    private static final String JOB_LOCK_KEY = "logistics:track:job:lock";
    private static final int STATUS_SHIPPING = 3;

    private final LogisticsTrackService logisticsTrackService;
    private final SampleRequestMapper sampleRequestMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean jobEnabled;
    private final boolean testEnabled;
    private final AtomicBoolean localLock = new AtomicBoolean(false);

    public LogisticsTrackJob(
            LogisticsTrackService logisticsTrackService,
            SampleRequestMapper sampleRequestMapper,
            RedisTemplate<String, Object> redisTemplate,
            @Value("${app.logistics.refresh-job-enabled:false}") boolean jobEnabled,
            @Value("${app.test.enabled:false}") boolean testEnabled) {
        this.logisticsTrackService = logisticsTrackService;
        this.sampleRequestMapper = sampleRequestMapper;
        this.redisTemplate = redisTemplate;
        this.jobEnabled = jobEnabled;
        this.testEnabled = testEnabled;
    }

    @Scheduled(cron = "0 0 */4 * * ?")
    public void refreshShippingSamples() {
        if (!jobEnabled) {
            return;
        }
        if (!acquireJobLock()) {
            log.info("LogisticsTrackJob skipped, another process is running");
            return;
        }
        try {
            LambdaQueryWrapper<SampleRequest> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SampleRequest::getStatus, STATUS_SHIPPING)
                    .isNotNull(SampleRequest::getTrackingNo)
                    .ne(SampleRequest::getTrackingNo, "")
                    .isNotNull(SampleRequest::getShipperCode)
                    .ne(SampleRequest::getShipperCode, "");
            List<SampleRequest> samples = sampleRequestMapper.selectList(wrapper);

            log.info("LogisticsTrackJob processing {} shipping samples", samples.size());
            int success = 0;
            int fail = 0;
            for (SampleRequest sample : samples) {
                try {
                    logisticsTrackService.refreshAndProgress(sample);
                    success++;
                } catch (Exception ex) {
                    fail++;
                    log.error("LogisticsTrackJob failed for sample {}", sample.getRequestNo(), ex);
                }
            }
            log.info("LogisticsTrackJob completed: success={}, fail={}", success, fail);
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
                log.warn("Redis unavailable in test mode when acquiring logistics track lock, fallback to local lock: {}", ex.getMessage());
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
                log.warn("Redis unavailable in test mode when releasing logistics track lock, local lock already released: {}", ex.getMessage());
                return;
            }
            throw ex;
        }
    }
}
