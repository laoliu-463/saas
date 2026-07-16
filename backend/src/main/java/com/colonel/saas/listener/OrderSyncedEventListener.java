package com.colonel.saas.listener;

import com.colonel.saas.config.OrderDerivedCacheKeys;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.domain.talent.application.TalentClaimApplicationService;
import com.colonel.saas.service.ShortTtlCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单同步事件监听器。
 * <p>
 * 监听订单同步完成事件（{@link OrderSyncedEvent}），异步执行以下副作用：
 * <ol>
 *   <li>清除订单事实相关的短 TTL 缓存</li>
 *   <li>对于有效订单，自动重置关联达人的保护期（T-03 需求）</li>
 * </ol>
 * </p>
 * <p>
 * 达人保护期重置逻辑：
 * <ul>
 *   <li>仅有效订单（非取消、非失效）触发重置</li>
 *   <li>从事件的 extraData 中解析达人 UID（author_id > talent_uid > event.talentUid）</li>
 *   <li>委托达人认领应用服务查找 active claim，并延长保护期</li>
 *   <li>仅当新保护期晚于当前保护期时才更新，避免缩短保护期</li>
 * </ul>
 * </p>
 *
 * @see OrderSyncedEvent
 */
@Slf4j
@Component
public class OrderSyncedEventListener {

    /** 短 TTL 缓存服务，用于清除仪表盘缓存 */
    private final ShortTtlCacheService shortTtlCacheService;
    /** 达人认领应用服务 */
    private final TalentClaimApplicationService talentClaimApplicationService;

    /**
     * 构造函数，注入依赖。
     *
     * @param summaryService 仪表盘业绩汇总服务
     * @param shortTtlCacheService 短 TTL 缓存服务
     * @param talentClaimApplicationService 达人认领应用服务
     */
    public OrderSyncedEventListener(
            ShortTtlCacheService shortTtlCacheService,
            TalentClaimApplicationService talentClaimApplicationService) {
        this.shortTtlCacheService = shortTtlCacheService;
        this.talentClaimApplicationService = talentClaimApplicationService;
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
     * 委托达人认领应用服务查找 active claim 记录并延长保护期。
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
        int updated = talentClaimApplicationService.extendActiveClaimProtectionByTalentUid(talentUid, LocalDateTime.now());
        log.debug("Reset protection for talentUid={}, updatedClaims={}", talentUid, updated);
    }

    /**
     * 从事件中提取达人 UID。
     * <p>
     * 匹配优先级：extraData.author_id > extraData.talent_uid > event.talentUid()。
     * 与达人认领应用服务中的订单达人匹配逻辑保持一致。
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
