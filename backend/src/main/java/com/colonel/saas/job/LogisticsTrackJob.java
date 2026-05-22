package com.colonel.saas.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.LogisticsTrackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class LogisticsTrackJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final int STATUS_SHIPPING = 3;

    private final LogisticsTrackService logisticsTrackService;
    private final SampleRequestMapper sampleRequestMapper;
    private final DistributedJobLockService jobLockService;
    private final boolean jobEnabled;

    public LogisticsTrackJob(
            LogisticsTrackService logisticsTrackService,
            SampleRequestMapper sampleRequestMapper,
            DistributedJobLockService jobLockService,
            @Value("${app.logistics.refresh-job-enabled:false}") boolean jobEnabled) {
        this.logisticsTrackService = logisticsTrackService;
        this.sampleRequestMapper = sampleRequestMapper;
        this.jobLockService = jobLockService;
        this.jobEnabled = jobEnabled;
    }

    @Scheduled(cron = "0 0 */4 * * ?")
    public void refreshShippingSamples() {
        if (!jobEnabled) {
            return;
        }
        if (!jobLockService.tryAcquire(JobLockKeys.LOGISTICS_TRACK, LOCK_TTL)) {
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
            jobLockService.release(JobLockKeys.LOGISTICS_TRACK);
        }
    }
}
