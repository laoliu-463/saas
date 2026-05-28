package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ExclusiveMerchantService;
import com.colonel.saas.service.ExclusiveTalentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 独家达人/商家资格月度评估定时任务。
 * <p>
 * 每月 1 日凌晨执行，基于上月的业务数据（如合作金额、订单量等）
 * 评估达人和商家是否符合独家合作资格，并将评估结果应用到当月。
 * </p>
 * <p>
 * 包含两个独立的子任务：
 * <ol>
 *   <li><b>独家达人评估</b>（每月 1 日 03:00）：评估达人的独家合作资格</li>
 *   <li><b>独家商家评估</b>（每月 1 日 03:30）：评估商家的独家合作资格</li>
 * </ol>
 * 两个任务错开 30 分钟执行，避免同时进行时的资源竞争。
 * </p>
 * <p>
 * 调度策略：
 * <ul>
 *   <li>默认禁用（{@code exclusive.enabled=false}），V1 阶段为预留功能</li>
 *   <li>分布式锁 TTL：1 小时，月度全量评估可能耗时较长</li>
 *   <li>无参构造函数默认 exclusiveEnabled=false，便于测试</li>
 * </ul>
 * </p>
 *
 * @see ExclusiveTalentService#evaluatePreviousMonthAndApplyCurrentMonth()
 * @see ExclusiveMerchantService#evaluatePreviousMonthAndApplyCurrentMonth()
 * @see JobLockKeys#EXCLUSIVE_TALENT_EVALUATE
 * @see JobLockKeys#EXCLUSIVE_MERCHANT_EVALUATE
 */
@Slf4j
@Component
public class ExclusiveEvaluateJob {

    /** 分布式锁 TTL，月度评估可能耗时较长，设置为 1 小时 */
    private static final Duration LOCK_TTL = Duration.ofHours(1);

    /** 独家达人服务 */
    private final ExclusiveTalentService exclusiveTalentService;
    /** 独家商家服务 */
    private final ExclusiveMerchantService exclusiveMerchantService;
    /** 分布式锁服务 */
    private final DistributedJobLockService jobLockService;
    /** 是否启用独家评估功能 */
    private final boolean exclusiveEnabled;

    /**
     * Spring 注入构造函数。
     *
     * @param exclusiveTalentService 独家达人服务
     * @param exclusiveMerchantService 独家商家服务
     * @param jobLockService 分布式锁服务
     * @param exclusiveEnabled 是否启用独家评估，默认 false
     */
    @Autowired
    public ExclusiveEvaluateJob(
            ExclusiveTalentService exclusiveTalentService,
            ExclusiveMerchantService exclusiveMerchantService,
            DistributedJobLockService jobLockService,
            @Value("${exclusive.enabled:false}") boolean exclusiveEnabled) {
        this.exclusiveTalentService = exclusiveTalentService;
        this.exclusiveMerchantService = exclusiveMerchantService;
        this.jobLockService = jobLockService;
        this.exclusiveEnabled = exclusiveEnabled;
    }

    /**
     * 便捷构造函数，默认禁用独家评估，便于单元测试使用。
     */
    public ExclusiveEvaluateJob(
            ExclusiveTalentService exclusiveTalentService,
            ExclusiveMerchantService exclusiveMerchantService,
            DistributedJobLockService jobLockService) {
        this(exclusiveTalentService, exclusiveMerchantService, jobLockService, false);
    }

    /**
     * 月度独家达人资格评估。
     * <p>
     * 基于上月业务数据评估达人是否符合独家合作标准，
     * 评估结果应用到当月的达人合作策略中。
     * </p>
     */
    @Scheduled(cron = "0 0 3 1 * ?")
    public void evaluateTalentMonthly() {
        if (!exclusiveEnabled) {
            log.info("Exclusive talent evaluate job skipped, exclusive.enabled=false");
            return;
        }
        if (!jobLockService.tryAcquire(JobLockKeys.EXCLUSIVE_TALENT_EVALUATE, LOCK_TTL)) {
            log.info("Exclusive talent evaluate job skipped, another process is running");
            return;
        }
        try {
            int upserted = exclusiveTalentService.evaluatePreviousMonthAndApplyCurrentMonth();
            log.info("Exclusive talent evaluate completed, upserted={}", upserted);
        } catch (Exception ex) {
            log.error("Exclusive talent evaluate failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.EXCLUSIVE_TALENT_EVALUATE);
        }
    }

    /**
     * 月度独家商家资格评估。
     * <p>
     * 基于上月业务数据评估商家是否符合独家合作标准，
     * 评估结果应用到当月的商家合作策略中。
     * </p>
     */
    @Scheduled(cron = "0 30 3 1 * ?")
    public void evaluateMerchantMonthly() {
        if (!exclusiveEnabled) {
            log.info("Exclusive merchant evaluate job skipped, exclusive.enabled=false");
            return;
        }
        if (!jobLockService.tryAcquire(JobLockKeys.EXCLUSIVE_MERCHANT_EVALUATE, LOCK_TTL)) {
            log.info("Exclusive merchant evaluate job skipped, another process is running");
            return;
        }
        try {
            int upserted = exclusiveMerchantService.evaluatePreviousMonthAndApplyCurrentMonth();
            log.info("Exclusive merchant evaluate completed, upserted={}", upserted);
        } catch (Exception ex) {
            log.error("Exclusive merchant evaluate failed", ex);
        } finally {
            jobLockService.release(JobLockKeys.EXCLUSIVE_MERCHANT_EVALUATE);
        }
    }
}
