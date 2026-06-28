package com.colonel.saas.domain.order.infrastructure;

import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.application.OrderAmountMappingRouter;
import com.colonel.saas.domain.order.event.OrderDomainEventPublisher;
import com.colonel.saas.domain.order.event.OrderEventPayloadMapper;
import com.colonel.saas.domain.order.event.OrderStatusChangedEvent;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.OrderSyncDedupClaimMapper;
import com.colonel.saas.service.MerchantService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.PickSourceMappingService;
import com.colonel.saas.service.SampleLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 订单同步持久化服务：负责将同步订单持久化到数据库，处理去重声明、乐观锁更新或幂等插入，
 * 并在持久化完成后依次执行归因后置步骤（补齐推广映射、沉淀商家、完成寄样作业），
 * 最终在订单事务提交后发布 {@link OrderSyncedEvent} 事件通知下游。
 */
@Service
public class OrderSyncPersistenceService {

    /** 同步来源：6468 instituteOrderColonel，主订单事实 + 预估轨 + 已结算普通单结算轨。 */
    public static final String SYNC_SOURCE_INSTITUTE = "INSTITUTE";
    /** 同步来源：1603 instituteOrderColonel 结算口径，默认结算写库主链路。 */
    public static final String SYNC_SOURCE_INSTITUTE_SETTLEMENT = "INSTITUTE_SETTLEMENT";
    /** 同步来源：2704 colonelMultiSettlementOrders，分次结算补充源（非主入库、非结算轨唯一来源）。 */
    public static final String SYNC_SOURCE_SETTLEMENT = "SETTLEMENT";

    /** 订单表 Mapper，提供按 orderId 查询、乐观锁更新和幂等插入能力 */
    private final ColonelsettlementOrderMapper orderMapper;
    /** 订单同步去重声明 Mapper，用于 claim 机制防止并发重复持久化 */
    private final OrderSyncDedupClaimMapper orderSyncDedupClaimMapper;
    /** 推广映射服务，订单持久化后补齐 pick_source 映射 */
    private final PickSourceMappingService pickSourceMappingService;
    /** 商家服务，订单持久化后确保关联商家记录存在 */
    private final MerchantService merchantService;
    /** 寄样生命周期服务，订单持久化后完成待交作业状态的寄样单 */
    private final SampleLifecycleService sampleLifecycleService;
    /** 操作日志服务，记录归因后置步骤的执行轨迹 */
    private final OperationLogService operationLogService;
    /** 用户域门面，提供用户名称查询能力（DDD-USER-002 替代 SysUserMapper） */
    private final UserDomainFacade userDomainFacade;
    /** 订单金额映射路由（DDD-ORDER-002） */
    private final OrderAmountMappingRouter orderAmountMappingRouter;
    /** 订单域事件发布器（DDD-ORDER-005 / OUTBOX-001） */
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    /** 订单事件载荷映射（DDD-ORDER-005） */
    private final OrderEventPayloadMapper orderEventPayloadMapper;
    /** DDD 重构安全开关（DDD-SAMPLE-004 寄样交作业事件驱动） */
    private final DddRefactorProperties dddRefactorProperties;

    public OrderSyncPersistenceService(
            ColonelsettlementOrderMapper orderMapper,
            OrderSyncDedupClaimMapper orderSyncDedupClaimMapper,
            PickSourceMappingService pickSourceMappingService,
            MerchantService merchantService,
            SampleLifecycleService sampleLifecycleService,
            OperationLogService operationLogService,
            UserDomainFacade userDomainFacade,
            OrderAmountMappingRouter orderAmountMappingRouter,
            OrderDomainEventPublisher orderDomainEventPublisher,
            OrderEventPayloadMapper orderEventPayloadMapper,
            DddRefactorProperties dddRefactorProperties) {
        this.orderMapper = orderMapper;
        this.orderSyncDedupClaimMapper = orderSyncDedupClaimMapper;
        this.pickSourceMappingService = pickSourceMappingService;
        this.merchantService = merchantService;
        this.sampleLifecycleService = sampleLifecycleService;
        this.operationLogService = operationLogService;
        this.userDomainFacade = userDomainFacade;
        this.orderAmountMappingRouter = orderAmountMappingRouter;
        this.orderDomainEventPublisher = orderDomainEventPublisher;
        this.orderEventPayloadMapper = orderEventPayloadMapper;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /** 根据用户 ID 查询真实姓名，不存在时返回 null（DDD-USER-002 委派 UserDomainFacade）。 */
    public String getUserName(UUID id) {
        return userDomainFacade.getUserName(id);
    }

    /**
     * 批量加载用户名称，返回 userId → realName 映射（DDD-USER-002 委派 UserDomainFacade）。
     */
    public Map<UUID, String> loadUserNamesByIds(Collection<UUID> ids) {
        return userDomainFacade.loadUserNamesByIds(ids);
    }

    /** 返回库内最新 pay_time 的 epoch 秒，无样本时为空。 */
    public Optional<Long> findLatestPayTimeEpochSeconds() {
        Long epoch = orderMapper.selectMaxPayTimeEpochSeconds();
        if (epoch == null || epoch <= 0L) {
            return Optional.empty();
        }
        return Optional.of(epoch);
    }



    /**
     * 持久化同步订单：先尝试 claim 去重，若订单已存在则以乐观锁方式更新，
     * 否则以幂等插入方式写入；持久化成功后依次执行归因后置步骤并发布事件。
     * <p>
     * 返回值语义：{@code true} 表示本次为新插入，{@code false} 表示为更新或并发重复被忽略。
     *
     * @param order 待持久化的订单实体，orderId 必须非空
     * @return true 表示新插入成功，false 表示更新或并发去重跳过
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean persistOrder(ColonelsettlementOrder order) {
        int claimEffect = orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId());
        ColonelsettlementOrder existing = orderMapper.findByOrderId(order.getOrderId());
        if (existing != null) {
            orderSyncDedupClaimMapper.bindOrderRow(order.getOrderId(), existing.getId());
            Integer previousStatus = existing.getOrderStatus();
            mergeBySource(existing, order);
            order.setId(existing.getId());
            order.setCreateTime(existing.getCreateTime());
            order.setVersion(existing.getVersion());
            OptimisticLockSupport.requireUpdated(orderMapper.updateSyncedById(order));
            runAttributionFollowUps(order);
            publishOrderSynced(order, false, previousStatus);
            return false;
        }
        if (claimEffect <= 0) {
            return false;
        }
        int effect = orderMapper.insertIgnoreByOrderId(order);
        if (effect <= 0) {
            existing = orderMapper.findByOrderId(order.getOrderId());
            if (existing == null) {
                return false;
            }
            orderSyncDedupClaimMapper.bindOrderRow(order.getOrderId(), existing.getId());
            Integer previousStatus = existing.getOrderStatus();
            mergeBySource(existing, order);
            order.setId(existing.getId());
            order.setCreateTime(existing.getCreateTime());
            order.setVersion(existing.getVersion());
            OptimisticLockSupport.requireUpdated(orderMapper.updateSyncedById(order));
            runAttributionFollowUps(order);
            publishOrderSynced(order, false, previousStatus);
            return false;
        }
        runAttributionFollowUps(order);
        publishOrderSynced(order, true, null);
        return true;
    }

    /**
     * 根据同步来源保护对方轨道：6468 空结算字段不覆盖已有结算轨；2704 不覆盖预估轨。
     * 2704 fetched=0 或 orders=[] 时不触发本合并。
     */
    private void mergeBySource(ColonelsettlementOrder existing, ColonelsettlementOrder incoming) {
        if (SYNC_SOURCE_INSTITUTE.equals(incoming.getSyncSource())) {
            orderAmountMappingRouter.mergeSettlementSnapshot(existing, incoming);
            return;
        }
        if (SYNC_SOURCE_INSTITUTE_SETTLEMENT.equals(incoming.getSyncSource())) {
            boolean explicitSettlementTechFee = hasExplicitSettlementTechFee(incoming);
            Long incomingEffectiveTechFee = incoming.getEffectiveTechServiceFee();
            orderAmountMappingRouter.mergeEstimateSnapshot(existing, incoming);
            orderAmountMappingRouter.mergeSettlementSnapshot(existing, incoming);
            if (explicitSettlementTechFee) {
                long effectiveTechFee = incomingEffectiveTechFee == null ? 0L : Math.max(incomingEffectiveTechFee, 0L);
                incoming.setEffectiveTechServiceFee(effectiveTechFee);
                incoming.setSettleColonelTechServiceFee(effectiveTechFee > 0L ? effectiveTechFee : null);
            }
            return;
        }
        orderAmountMappingRouter.mergeEstimateSnapshot(existing, incoming);
        orderAmountMappingRouter.mergeSettlementSnapshot(existing, incoming);
    }

    private static boolean hasExplicitSettlementTechFee(ColonelsettlementOrder incoming) {
        if (incoming == null) {
            return false;
        }
        Map<String, Object> raw = incoming.getExtraData();
        return containsAny(raw, Set.of(
                "settled_tech_service_fee",
                "settledTechServiceFee",
                "real_tech_service_fee",
                "realTechServiceFee"))
                || containsAny(asObjectMap(raw == null ? null : raw.get("colonel_order_info")), Set.of(
                "settled_tech_service_fee",
                "settledTechServiceFee",
                "real_tech_service_fee",
                "realTechServiceFee"))
                || containsAny(asObjectMap(raw == null ? null : raw.get("colonelOrderInfo")), Set.of(
                "settled_tech_service_fee",
                "settledTechServiceFee",
                "real_tech_service_fee",
                "realTechServiceFee"));
    }

    private static boolean containsAny(Map<String, Object> source, Set<String> keys) {
        if (source == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> asObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    /** 发布订单同步完成事件，将订单的金额快照和归因信息通知下游消费者。 */
    private void publishOrderSynced(ColonelsettlementOrder order, boolean newlyInserted, Integer previousStatus) {
        if (order == null || orderDomainEventPublisher == null) {
            return;
        }
        OrderSyncedEvent event = orderEventPayloadMapper.toOrderSyncedEvent(order, newlyInserted);
        orderDomainEventPublisher.publishOrderSynced(event);
        if (!newlyInserted && previousStatus != null && !previousStatus.equals(order.getOrderStatus())) {
            OrderStatusChangedEvent statusEvent = orderEventPayloadMapper.toOrderStatusChangedEvent(
                    order, previousStatus, newlyInserted);
            orderDomainEventPublisher.publishOrderStatusChangedDirect(statusEvent);
        }
    }

    /** 依次执行归因后置步骤：补齐推广映射、沉淀商家、完成寄样作业，每步记录操作日志。 */
    private void runAttributionFollowUps(ColonelsettlementOrder order) {
        pickSourceMappingService.ensureFromOrder(order);
        recordAttributionFollowUp(order, "补齐推广映射", "ensureFromOrder");

        merchantService.ensureMerchantFromOrder(order);
        recordAttributionFollowUp(order, "沉淀商家", "ensureMerchantFromOrder");

        if (!isSampleHomeworkEventDriven()) {
            sampleLifecycleService.completePendingHomeworkByOrder(order);
            recordAttributionFollowUp(order, "完成寄样作业", "completePendingHomeworkByOrder");
        }
    }

    /** DDD-SAMPLE-004：寄样交作业改由 {@link OrderSyncedEvent} 异步驱动。 */
    private boolean isSampleHomeworkEventDriven() {
        return dddRefactorProperties.isEnabled()
                && dddRefactorProperties.getSampleHomeworkEvent().isEnabled();
    }

    /** 记录单次归因后置步骤的操作日志，便于问题排查和执行轨迹追踪。 */
    private void recordAttributionFollowUp(ColonelsettlementOrder order, String action, String followUpName) {
        if (order == null) {
            return;
        }
        operationLogService.recordSystemAction(
                order.getUserId() != null ? order.getUserId() : order.getChannelUserId(),
                "订单归因",
                action,
                "POST",
                "order",
                order.getOrderId(),
                resolveTargetName(order),
                "订单归因副作用: " + followUpName);
    }

    /** 解析操作日志的目标显示名称：优先商品名称，其次商品标题，兜底使用 orderId。 */
    private String resolveTargetName(ColonelsettlementOrder order) {
        if (StringUtils.hasText(order.getProductName())) {
            return order.getProductName();
        }
        if (StringUtils.hasText(order.getProductTitle())) {
            return order.getProductTitle();
        }
        return order.getOrderId();
    }
}
