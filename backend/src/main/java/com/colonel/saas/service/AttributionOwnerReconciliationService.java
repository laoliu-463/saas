package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.product.policy.PromotionAttributionOwnerPolicy;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import com.colonel.saas.mapper.PromotionLinkMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 分类历史推广来源映射的归属维度。
 *
 * <p>该工具仅处理尚未固化归属维度的映射，默认 dry-run；实际写入必须同时传入
 * {@code dryRun=false} 与 {@code confirm=true}。订单数量只用于输出影响范围，
 * 不会在这里改写订单归属或业绩。</p>
 */
@Service
public class AttributionOwnerReconciliationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final PickSourceMappingMapper mappingMapper;
    private final PromotionLinkMapper promotionLinkMapper;
    private final ColonelsettlementOrderMapper orderMapper;
    private final UserDomainFacade userDomainFacade;
    private final PromotionAttributionOwnerPolicy ownerPolicy = new PromotionAttributionOwnerPolicy();

    public AttributionOwnerReconciliationService(
            PickSourceMappingMapper mappingMapper,
            PromotionLinkMapper promotionLinkMapper,
            ColonelsettlementOrderMapper orderMapper,
            UserDomainFacade userDomainFacade) {
        this.mappingMapper = mappingMapper;
        this.promotionLinkMapper = promotionLinkMapper;
        this.orderMapper = orderMapper;
        this.userDomainFacade = userDomainFacade;
    }

    /**
     * 预览或固化历史链接的归属维度。
     *
     * <p>在实际执行时，同一条映射及其关联推广链接由同一个事务写入，任一更新冲突会回滚。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ReconcileResult reconcile(ReconcileRequest request) {
        ReconcileRequest safeRequest = request == null
                ? new ReconcileRequest(null, null, null, null)
                : request;
        boolean dryRun = !Boolean.FALSE.equals(safeRequest.dryRun());
        if (!dryRun && !Boolean.TRUE.equals(safeRequest.confirm())) {
            throw BusinessException.param("实际分类必须显式 confirm=true");
        }

        List<UUID> userIds = normalizeUserIds(safeRequest.userIds());
        List<UUID> mappingIds = normalizeMappingIds(safeRequest.mappingIds());
        List<PickSourceMapping> mappings = mappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                .isNull(PickSourceMapping::getAttributionOwnerType)
                .eq(PickSourceMapping::getDeleted, 0)
                .in(!userIds.isEmpty(), PickSourceMapping::getUserId, userIds)
                .in(!mappingIds.isEmpty(), PickSourceMapping::getId, mappingIds)
                .orderByAsc(PickSourceMapping::getCreateTime)
                .last("LIMIT " + normalizeLimit(safeRequest.limit())));
        List<PickSourceMapping> candidates = mappings == null
                ? List.of()
                : mappings.stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(mapping -> mappingIds.isEmpty() || mappingIds.contains(mapping.getId()))
                        .limit(MAX_LIMIT)
                        .toList();

        List<UUID> candidateUserIds = normalizeUserIds(candidates.stream()
                .map(PickSourceMapping::getUserId)
                .toList());
        Map<UUID, Set<String>> roleCodesByUserId = candidateUserIds.isEmpty()
                ? Map.of()
                : userDomainFacade.loadActiveRoleCodesByUserIds(candidateUserIds);

        List<ReconcileItem> items = new ArrayList<>();
        int classifiable = 0;
        int conflicts = 0;
        int updated = 0;
        for (PickSourceMapping mapping : candidates) {
            long potentialOrderCount = countPotentialOrders(mapping);
            OwnerResolution resolution = resolveOwner(mapping.getUserId(), roleCodesByUserId);
            if (resolution.conflict()) {
                conflicts++;
                items.add(item(mapping, null, potentialOrderCount, "CONFLICT", resolution.reason()));
                continue;
            }
            if (resolution.ownerType() == null) {
                items.add(item(mapping, null, potentialOrderCount, "UNCLASSIFIABLE", resolution.reason()));
                continue;
            }

            classifiable++;
            PromotionLink promotionLink = null;
            if (mapping.getPromotionLinkId() != null) {
                promotionLink = promotionLinkMapper.selectById(mapping.getPromotionLinkId());
                String linkConflict = validateLinkedPromotionOwner(promotionLink, resolution.ownerType());
                if (linkConflict != null) {
                    conflicts++;
                    items.add(item(mapping, resolution.ownerType(), potentialOrderCount, "CONFLICT", linkConflict));
                    continue;
                }
            }

            if (dryRun) {
                items.add(item(mapping, resolution.ownerType(), potentialOrderCount, "DRY_RUN", "仅预览，未写入"));
                continue;
            }

            mapping.setAttributionOwnerType(resolution.ownerType().name());
            if (mappingMapper.updateById(mapping) != 1) {
                throw BusinessException.conflict("推广来源映射已被并发修改，请重新预览后执行");
            }
            if (promotionLink != null && !StringUtils.hasText(promotionLink.getAttributionOwnerType())) {
                promotionLink.setAttributionOwnerType(resolution.ownerType().name());
                if (promotionLinkMapper.updateById(promotionLink) != 1) {
                    throw BusinessException.conflict("推广链接已被并发修改，请重新预览后执行");
                }
            }
            updated++;
            items.add(item(mapping, resolution.ownerType(), potentialOrderCount, "UPDATED", "已固化归属维度"));
        }
        return new ReconcileResult(candidates.size(), classifiable, conflicts, updated, dryRun, List.copyOf(items));
    }

    private OwnerResolution resolveOwner(UUID userId, Map<UUID, Set<String>> roleCodesByUserId) {
        if (userId == null) {
            return OwnerResolution.unclassifiable("映射未记录创建链接的用户");
        }
        try {
            AttributionOwnerType ownerType = ownerPolicy.resolve(roleCodesByUserId == null
                            ? Set.of()
                            : roleCodesByUserId.getOrDefault(userId, Set.of()))
                    .orElse(null);
            return ownerType == null
                    ? OwnerResolution.unclassifiable("用户没有有效的渠道或招商角色")
                    : OwnerResolution.resolved(ownerType);
        } catch (BusinessException exception) {
            return OwnerResolution.conflict(exception.getMessage());
        }
    }

    private String validateLinkedPromotionOwner(PromotionLink promotionLink, AttributionOwnerType proposedOwnerType) {
        if (promotionLink == null || Integer.valueOf(1).equals(promotionLink.getDeleted())) {
            return "关联推广链接不存在或已删除";
        }
        try {
            AttributionOwnerType existingOwnerType = AttributionOwnerType.parseNullable(
                    promotionLink.getAttributionOwnerType());
            if (existingOwnerType != null && existingOwnerType != proposedOwnerType) {
                return "映射与关联推广链接的归属维度冲突";
            }
            return null;
        } catch (IllegalArgumentException exception) {
            return "关联推广链接的归属维度值不合法";
        }
    }

    private long countPotentialOrders(PickSourceMapping mapping) {
        if (mapping == null || !StringUtils.hasText(mapping.getActivityId()) || !StringUtils.hasText(mapping.getProductId())) {
            return 0L;
        }
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0)
                .eq(ColonelsettlementOrder::getActivityId, mapping.getActivityId())
                .eq(ColonelsettlementOrder::getProductId, mapping.getProductId());
        appendBusinessTimeRange(wrapper, mapping.getValidFrom(), mapping.getValidUntil());
        Long count = orderMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private void appendBusinessTimeRange(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            LocalDateTime validFrom,
            LocalDateTime validUntil) {
        if (validFrom != null) {
            wrapper.apply("COALESCE(pay_time, order_create_time, create_time) >= {0}", validFrom);
        }
        if (validUntil != null) {
            wrapper.apply("COALESCE(pay_time, order_create_time, create_time) < {0}", validUntil);
        }
    }

    private ReconcileItem item(
            PickSourceMapping mapping,
            AttributionOwnerType proposedOwnerType,
            long potentialOrderCount,
            String status,
            String reason) {
        return new ReconcileItem(
                mapping.getId(),
                mapping.getPromotionLinkId(),
                mapping.getUserId(),
                proposedOwnerType == null ? null : proposedOwnerType.name(),
                potentialOrderCount,
                status,
                reason);
    }

    private List<UUID> normalizeUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(userIds.stream().filter(java.util.Objects::nonNull).toList()));
    }

    private List<UUID> normalizeMappingIds(Collection<UUID> mappingIds) {
        if (mappingIds == null || mappingIds.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(mappingIds.stream().filter(java.util.Objects::nonNull).toList()));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    public record ReconcileRequest(
            List<UUID> userIds,
            List<UUID> mappingIds,
            Integer limit,
            Boolean dryRun,
            Boolean confirm) {
        public ReconcileRequest(List<UUID> userIds, Integer limit, Boolean dryRun, Boolean confirm) {
            this(userIds, null, limit, dryRun, confirm);
        }
    }

    public record ReconcileItem(
            UUID mappingId,
            UUID promotionLinkId,
            UUID userId,
            String proposedOwnerType,
            long potentialOrderCount,
            String status,
            String reason) {
    }

    public record ReconcileResult(
            int scanned,
            int classifiable,
            int conflicts,
            int updated,
            boolean dryRun,
            List<ReconcileItem> items) {
    }

    private record OwnerResolution(AttributionOwnerType ownerType, boolean conflict, String reason) {
        private static OwnerResolution resolved(AttributionOwnerType ownerType) {
            return new OwnerResolution(ownerType, false, null);
        }

        private static OwnerResolution unclassifiable(String reason) {
            return new OwnerResolution(null, false, reason);
        }

        private static OwnerResolution conflict(String reason) {
            return new OwnerResolution(null, true, reason);
        }
    }
}
