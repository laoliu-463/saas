package com.colonel.saas.service;

import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.OrderSyncDedupClaimMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单同步持久化服务：负责将同步订单持久化到数据库，处理去重声明、乐观锁更新或幂等插入，
 * 并在持久化完成后依次执行归因后置步骤（补齐推广映射、沉淀商家、完成寄样作业），
 * 最终在订单事务提交后发布 {@link OrderSyncedEvent} 事件通知下游。
 */
@Service
public class OrderSyncPersistenceService {

    /** 同步来源：6468 instituteOrderColonel，负责事实/预估轨。 */
    public static final String SYNC_SOURCE_INSTITUTE = "INSTITUTE";
    /** 同步来源：2704 colonelMultiSettlementOrders，负责结算/有效轨。 */
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
    /** 系统用户 Mapper，提供用户信息查询能力 */
    private final SysUserMapper sysUserMapper;
    /** Spring 事件发布器，用于发布 OrderSyncedEvent */
    private final ApplicationEventPublisher eventPublisher;

    public OrderSyncPersistenceService(
            ColonelsettlementOrderMapper orderMapper,
            OrderSyncDedupClaimMapper orderSyncDedupClaimMapper,
            PickSourceMappingService pickSourceMappingService,
            MerchantService merchantService,
            SampleLifecycleService sampleLifecycleService,
            OperationLogService operationLogService,
            SysUserMapper sysUserMapper,
            ApplicationEventPublisher eventPublisher) {
        this.orderMapper = orderMapper;
        this.orderSyncDedupClaimMapper = orderSyncDedupClaimMapper;
        this.pickSourceMappingService = pickSourceMappingService;
        this.merchantService = merchantService;
        this.sampleLifecycleService = sampleLifecycleService;
        this.operationLogService = operationLogService;
        this.sysUserMapper = sysUserMapper;
        this.eventPublisher = eventPublisher;
    }

    /** 根据用户 ID 查询系统用户，不存在时返回 null。 */
    public SysUser getUser(UUID id) {
        return sysUserMapper.selectById(id);
    }

    /**
     * 批量加载用户信息，返回 userId → SysUser 映射。
     * 自动过滤 null 和重复 ID，返回不可变 Map。
     *
     * @param ids 待查询的用户 ID 集合
     * @return userId 到 SysUser 的映射，输入为空或无有效 ID 时返回空 Map
     */
    public Map<UUID, SysUser> loadUsersByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(distinct).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (left, right) -> left));
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
            mergeBySource(existing, order);
            order.setId(existing.getId());
            order.setCreateTime(existing.getCreateTime());
            order.setVersion(existing.getVersion());
            OptimisticLockSupport.requireUpdated(orderMapper.updateSyncedById(order));
            runAttributionFollowUps(order);
            publishOrderSynced(order, false);
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
            mergeBySource(existing, order);
            order.setId(existing.getId());
            order.setCreateTime(existing.getCreateTime());
            order.setVersion(existing.getVersion());
            OptimisticLockSupport.requireUpdated(orderMapper.updateSyncedById(order));
            runAttributionFollowUps(order);
            publishOrderSynced(order, false);
            return false;
        }
        runAttributionFollowUps(order);
        publishOrderSynced(order, true);
        return true;
    }

    /** 根据同步来源保护对方轨道，避免 6468/2704 互相覆盖不属于自己的字段。 */
    private void mergeBySource(ColonelsettlementOrder existing, ColonelsettlementOrder incoming) {
        if (SYNC_SOURCE_INSTITUTE.equals(incoming.getSyncSource())) {
            OrderDualTrackAmountResolver.mergeSettlementSnapshot(existing, incoming);
            return;
        }
        OrderDualTrackAmountResolver.mergeEstimateSnapshot(existing, incoming);
    }

    /** 发布订单同步完成事件，将订单的金额快照和归因信息通知下游消费者。 */
    private void publishOrderSynced(ColonelsettlementOrder order, boolean newlyInserted) {
        if (order == null || eventPublisher == null) {
            return;
        }
        OrderSyncedEvent event = new OrderSyncedEvent(
                order.getOrderId(),
                order.getId(),
                newlyInserted,
                order.getAttributionStatus(),
                order.getOrderAmount() == null ? 0L : order.getOrderAmount(),
                order.getOrderAmount() == null ? 0L : order.getOrderAmount(),
                order.getSettleAmount() == null ? 0L : order.getSettleAmount(),
                order.getEstimateServiceFee() == null ? 0L : order.getEstimateServiceFee(),
                order.getEffectiveServiceFee() == null ? 0L : order.getEffectiveServiceFee(),
                order.getEstimateTechServiceFee() == null ? 0L : order.getEstimateTechServiceFee(),
                order.getEffectiveTechServiceFee() == null ? 0L : order.getEffectiveTechServiceFee(),
                order.getSettleColonelCommission() == null ? 0L : order.getSettleColonelCommission(),
                order.getSettleColonelTechServiceFee() == null ? 0L : order.getSettleColonelTechServiceFee(),
                order.getSettleSecondColonelCommission() == null ? 0L : order.getSettleSecondColonelCommission(),
                order.getOrderStatus(),
                order.getCreateTime(),
                resolveTalentUid(order.getExtraData()),
                order.getExtraData());
        publishAfterCommit(event);
    }

    private void publishAfterCommit(OrderSyncedEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(event);
            }
        });
    }

    /** 从 extraData 中按优先级尝试解析达人 UID，兼容多种上游字段命名。 */
    private String resolveTalentUid(Map<String, Object> extraData) {
        if (extraData == null || extraData.isEmpty()) {
            return null;
        }
        for (String key : List.of("author_id", "talent_uid", "talentUid", "authorId", "talent_id")) {
            Object value = extraData.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        return null;
    }

    /** 依次执行归因后置步骤：补齐推广映射、沉淀商家、完成寄样作业，每步记录操作日志。 */
    private void runAttributionFollowUps(ColonelsettlementOrder order) {
        pickSourceMappingService.ensureFromOrder(order);
        recordAttributionFollowUp(order, "补齐推广映射", "ensureFromOrder");

        merchantService.ensureMerchantFromOrder(order);
        recordAttributionFollowUp(order, "沉淀商家", "ensureMerchantFromOrder");

        sampleLifecycleService.completePendingHomeworkByOrder(order);
        recordAttributionFollowUp(order, "完成寄样作业", "completePendingHomeworkByOrder");
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
