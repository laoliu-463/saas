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

@Slf4j
@Component
public class OrderSyncedEventListener {

    private static final int CLAIM_STATUS_ACTIVE = 1;

    private final DashboardPerformanceSummaryService summaryService;
    private final ShortTtlCacheService shortTtlCacheService;
    private final TalentClaimMapper talentClaimMapper;
    private final BusinessRuleConfigService businessRuleConfigService;

    public OrderSyncedEventListener(
            DashboardPerformanceSummaryService summaryService,
            ShortTtlCacheService shortTtlCacheService,
            TalentClaimMapper talentClaimMapper,
            BusinessRuleConfigService businessRuleConfigService) {
        this.summaryService = summaryService;
        this.shortTtlCacheService = shortTtlCacheService;
        this.talentClaimMapper = talentClaimMapper;
        this.businessRuleConfigService = businessRuleConfigService;
    }

    @Async
    @EventListener
    public void onOrderSynced(OrderSyncedEvent event) {
        if (event == null) {
            return;
        }
        try {
            summaryService.applyOrderSynced(event);
            shortTtlCacheService.evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_SUMMARY_PREFIX);
            shortTtlCacheService.evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_METRICS_PREFIX);

            // T-03: 有订单时自动重置保护期（仅有效订单，取消/失效不重置）
            if (isValidOrder(event)) {
                resetTalentProtectionDays(event);
            }
        } catch (Exception ex) {
            log.warn("OrderSyncedEvent handling failed, orderId={}", event.orderId(), ex);
        }
    }

    private boolean isValidOrder(OrderSyncedEvent event) {
        // 取消/失效订单不重置保护期
        // orderStatus == 4 为取消；null/0 为无效
        Integer status = event.orderStatus();
        return status != null && status != 4;
    }

    private void resetTalentProtectionDays(OrderSyncedEvent event) {
        String talentUid = resolveTalentUid(event);
        if (talentUid == null) {
            return;
        }
        int protectDays = businessRuleConfigService.getTalentProtectionDays();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newProtectedUntil = now.plusDays(protectDays);

        // 查找该达人的所有 active claim，全部重置保护期
        List<TalentClaim> activeClaims = talentClaimMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TalentClaim>()
                        .eq(TalentClaim::getTalentUid, talentUid)
                        .eq(TalentClaim::getStatus, CLAIM_STATUS_ACTIVE)
                        .eq(TalentClaim::getDeleted, 0));

        if (activeClaims.isEmpty()) {
            return;
        }

        for (TalentClaim claim : activeClaims) {
            // 仅当新的保护期晚于当前时，才更新
            if (newProtectedUntil.isAfter(claim.getProtectedUntil())) {
                claim.setProtectedUntil(newProtectedUntil);
                OptimisticLockSupport.requireUpdated(talentClaimMapper.updateById(claim));
                log.debug("Reset protection for talentUid={}, claimId={}, newProtectedUntil={}",
                        talentUid, claim.getId(), newProtectedUntil);
            }
        }
    }

    /**
     * 从事件中提取 talent_uid，匹配顺序：extraData.author_id > extraData.talent_uid > talentUid。
     * 与 TalentService.hasOutputSinceClaim() 的 matchesTalent() 逻辑保持一致。
     */
    private String resolveTalentUid(OrderSyncedEvent event) {
        if (event.extraData() != null) {
            Object authorId = event.extraData().get("author_id");
            if (authorId != null && !authorId.toString().isBlank()) {
                return authorId.toString().trim();
            }
            Object talentUid = event.extraData().get("talent_uid");
            if (talentUid != null && !talentUid.toString().isBlank()) {
                return talentUid.toString().trim();
            }
        }
        return event.talentUid();
    }
}
