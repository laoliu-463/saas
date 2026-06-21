package com.colonel.saas.listener;

import com.colonel.saas.config.OrderDerivedCacheKeys;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.service.DashboardPerformanceSummaryService;
import com.colonel.saas.service.ShortTtlCacheService;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 订单同步事件监听器。
 * <p>
 * 监听订单同步完成事件（{@link OrderSyncedEvent}），异步执行以下副作用：
 * <ol>
 *   <li>更新仪表盘业绩汇总数据</li>
 *   <li>清除仪表盘相关的短 TTL 缓存（汇总 + 指标）</li>
 *   <li>对于有效订单，自动重置关联达人的保护期（T-03 需求）</li>
 * </ol>
 * </p>
 * <p>
 * 达人保护期重置逻辑：
 * <ul>
 *   <li>仅有效订单（非取消、非失效）触发重置</li>
 *   <li>从事件的 extraData 中解析达人 UID（author_id > talent_uid > event.talentUid）</li>
 *   <li>查找该达人所有 active 状态的 claim，将保护期延长至当前时间 + 配置天数</li>
 *   <li>仅当新保护期晚于当前保护期时才更新，避免缩短保护期</li>
 * </ul>
 * </p>
 *
 * @see OrderSyncedEvent
 * @see DashboardPerformanceSummaryService#applyOrderSynced(OrderSyncedEvent)
 */
@Slf4j
@Component
public class OrderSyncedEventListener {

    /** 达人认领有效状态值 */
    private static final int CLAIM_STATUS_ACTIVE = 1;

    /** 仪表盘业绩汇总服务 */
    private final DashboardPerformanceSummaryService summaryService;
    /** 短 TTL 缓存服务，用于清除仪表盘缓存 */
    private final ShortTtlCacheService shortTtlCacheService;
    /** 达人认领 Mapper */
    private final TalentClaimMapper talentClaimMapper;
    /** 配置域门面，读取达人保护期天数（DDD-CONFIG-002） */
    private final com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;

    /**
     * 构造函数，注入依赖。
     *
     * @param summaryService 仪表盘业绩汇总服务
     * @param shortTtlCacheService 短 TTL 缓存服务
     * @param talentClaimMapper 达人认领 Mapper
     * @param configDomainFacade 配置域门面
     */
    public OrderSyncedEventListener(
            DashboardPerformanceSummaryService summaryService,
            ShortTtlCacheService shortTtlCacheService,
            TalentClaimMapper talentClaimMapper,
            com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade) {
        this.summaryService = summaryService;
        this.shortTtlCacheService = shortTtlCacheService;
        this.talentClaimMapper = talentClaimMapper;
        this.configDomainFacade = configDomainFacade;
    }

    /**
     * 监听订单同步完成事件，异步执行业绩汇总更新、缓存清除和达人保护期重置。
     *
     * @param event 订单同步完成事件
     */
    @Async
    @EventListener
    public void onOrderSynced(OrderSyncedEvent event) {
        if (event == null) {
            return;
        }
        try {
            // 更新仪表盘业绩汇总数据
            summaryService.applyOrderSynced(event);
            // 清除仪表盘相关的短 TTL 缓存
            shortTtlCacheService.evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_SUMMARY_PREFIX);
            shortTtlCacheService.evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_METRICS_PREFIX);
            shortTtlCacheService.evictByPrefix(OrderDerivedCacheKeys.ORDER_STATS_PREFIX);

            // T-03: 有效订单自动重置达人保护期（取消/失效订单不重置）
            if (isValidOrder(event)) {
                resetTalentProtectionDays(event);
            }
        } catch (Exception ex) {
            log.warn("OrderSyncedEvent handling failed, orderId={}", event.orderId(), ex);
        }
    }

    /**
     * 判断是否为有效订单。
     * <p>
     * 有效订单指非取消（status != 4）且非无效（status != null/0）的订单。
     * 仅有效订单才会触发达人保护期重置。
     * </p>
     *
     * @param event 订单同步事件
     * @return 是否为有效订单
     */
    private boolean isValidOrder(OrderSyncedEvent event) {
        // 取消/失效订单不重置保护期
        // orderStatus == 4 为取消；null/0 为无效
        Integer status = event.orderStatus();
        return status != null && status != 4;
    }

    /**
     * 重置关联达人的保护期。
     * <p>
     * 查找该达人所有 active 状态的 claim 记录，将保护期延长至
     * 当前时间 + 配置的保护天数。仅当新保护期晚于当前保护期时才更新，
     * 避免缩短已有保护期。
     * </p>
     *
     * @param event 订单同步事件，用于提取达人 UID
     */
    private void resetTalentProtectionDays(OrderSyncedEvent event) {
        // 从事件中解析达人 UID
        String talentUid = resolveTalentUid(event);
        if (talentUid == null) {
            return;
        }
        // 读取配置的保护期天数，计算新的保护截止时间
        int protectDays = configDomainFacade.getTalentClaimProtectDays();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newProtectedUntil = now.plusDays(protectDays);

        // 查找该达人的所有 active claim，准备重置保护期
        List<TalentClaim> activeClaims = talentClaimMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TalentClaim>()
                        .eq(TalentClaim::getTalentUid, talentUid)
                        .eq(TalentClaim::getStatus, CLAIM_STATUS_ACTIVE)
                        .eq(TalentClaim::getDeleted, 0));

        if (activeClaims.isEmpty()) {
            return;
        }

        for (TalentClaim claim : activeClaims) {
            // 仅当新的保护期晚于当前时，才更新（避免缩短保护期）
            if (newProtectedUntil.isAfter(claim.getProtectedUntil())) {
                claim.setProtectedUntil(newProtectedUntil);
                OptimisticLockSupport.requireUpdated(talentClaimMapper.updateById(claim));
                log.debug("Reset protection for talentUid={}, claimId={}, newProtectedUntil={}",
                        talentUid, claim.getId(), newProtectedUntil);
            }
        }
    }

    /**
     * 从事件中提取达人 UID。
     * <p>
     * 匹配优先级：extraData.author_id > extraData.talent_uid > event.talentUid()。
     * 与 {@code TalentService.hasOutputSinceClaim()} 的 matchesTalent() 逻辑保持一致。
     * </p>
     *
     * @param event 订单同步事件
     * @return 达人 UID，无法解析时返回 null
     */
    private String resolveTalentUid(OrderSyncedEvent event) {
        if (event.extraData() != null) {
            // 优先使用 author_id（抖音平台返回的达人 ID）
            Object authorId = event.extraData().get("author_id");
            if (authorId != null && !authorId.toString().isBlank()) {
                return authorId.toString().trim();
            }
            // 其次使用 talent_uid
            Object talentUid = event.extraData().get("talent_uid");
            if (talentUid != null && !talentUid.toString().isBlank()) {
                return talentUid.toString().trim();
            }
        }
        // 最后使用事件自身的 talentUid
        return event.talentUid();
    }
}
