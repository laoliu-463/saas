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

/**
 * 物流轨迹刷新定时任务。
 * <p>
 * 每 4 小时执行一次，扫描所有状态为"发货中"且已填写快递单号的寄样记录，
 * 调用物流查询接口刷新最新的物流轨迹信息，并根据物流状态自动推进寄样流程。
 * </p>
 * <p>
 * 处理逻辑：
 * <ol>
 *   <li>查询所有状态为"发货中"（status=3）且快递单号和快递公司编码均非空的记录</li>
 *   <li>逐条调用 {@link LogisticsTrackService#refreshAndProgress(SampleRequest)} 刷新轨迹</li>
 *   <li>若物流显示已签收，服务内部会自动将寄样状态推进到下一阶段</li>
 * </ol>
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>Cron：{@code 0 0 0/4 * * ?}（每 4 小时整点执行）</li>
 *   <li>默认禁用（{@code app.logistics.refresh-job-enabled=false}），需显式开启</li>
 *   <li>分布式锁 TTL：30 分钟，覆盖大批量快递查询耗时</li>
 *   <li>单条记录异常不阻塞整体流程</li>
 * </ul>
 * </p>
 *
 * @see LogisticsTrackService#refreshAndProgress(SampleRequest)
 * @see JobLockKeys#LOGISTICS_TRACK
 */
@Slf4j
@Component
public class LogisticsTrackJob {

    /** 分布式锁 TTL */
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    /** 寄样状态：发货中 */
    private static final int STATUS_SHIPPING = 3;

    /** 物流轨迹刷新服务 */
    private final LogisticsTrackService logisticsTrackService;
    /** 寄样申请 Mapper */
    private final SampleRequestMapper sampleRequestMapper;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;
    /** 是否启用此定时任务，默认禁用 */
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

    /**
     * 刷新所有发货中样品的物流轨迹。
     * <p>
     * 查询条件：状态为"发货中" + 快递单号非空 + 快递公司编码非空。
     * 逐条刷新，单条失败不影响其他记录的处理。
     * </p>
     */
    @Scheduled(cron = "0 0 */4 * * ?")
    public void refreshShippingSamples() {
        if (!jobEnabled) {
            return;
        }
        // 获取分布式锁
        if (!jobLockService.tryAcquire(JobLockKeys.LOGISTICS_TRACK, LOCK_TTL)) {
            log.info("LogisticsTrackJob skipped, another process is running");
            return;
        }
        try {
            // 查询所有发货中且有快递信息的寄样记录
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
                    // 刷新单条记录的物流轨迹，可能自动推进状态
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
