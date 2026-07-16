package com.colonel.saas.domain.order.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.order.policy.OrderAttributionInput;
import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution;
import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution.Status;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;
import com.colonel.saas.domain.shared.attribution.AttributionSource;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 订单域推广链接映射读取适配器（DDD-ORDER-004）。
 *
 * <p>订单归属只在候选记录收敛为唯一 {@code (userId, ownerType)} 时返回拥有者；
 * 禁止按更新时间或创建时间从多个不同拥有者中任取一条。</p>
 */
@Component
public class OrderPickSourceMappingAdapter {

    private static final String SOURCE_TYPE_NATIVE = "NATIVE";

    private final PickSourceMappingMapper pickSourceMappingMapper;

    public OrderPickSourceMappingAdapter(PickSourceMappingMapper pickSourceMappingMapper) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
    }

    public OrderLinkAttributionResolution resolve(OrderAttributionInput input) {
        if (input == null) {
            return notFound("ATTRIBUTION_INPUT_MISSING");
        }

        OrderLinkAttributionResolution byPickSource = resolvePickSource(input);
        if (byPickSource != null) {
            return byPickSource;
        }

        OrderLinkAttributionResolution exactNative = resolveExactNative(input);
        if (exactNative != null) {
            return exactNative;
        }

        OrderLinkAttributionResolution activityProductNative = resolveActivityProductNative(input);
        if (activityProductNative != null) {
            return activityProductNative;
        }

        return resolveBuyinDiagnostic(input);
    }

    /**
     * 保留旧调用点的读取接口；新订单归因必须使用 {@link #resolve(OrderAttributionInput)}。
     */
    @Deprecated
    public PickSourceMapping findByPickSourceOrExtra(String pickSource, String pickExtra) {
        if (StringUtils.hasText(pickSource)) {
            PickSourceMapping byPickSource = pickSourceMappingMapper.selectOne(activeQuery()
                    .eq(PickSourceMapping::getPickSource, pickSource.trim())
                    .last("limit 1"));
            if (byPickSource != null) {
                return byPickSource;
            }
        }
        if (!StringUtils.hasText(pickExtra)) {
            return null;
        }
        PickSourceMapping byPickExtra = pickSourceMappingMapper.selectOne(activeQuery()
                .eq(PickSourceMapping::getPickExtra, pickExtra.trim())
                .last("limit 1"));
        if (byPickExtra != null) {
            return byPickExtra;
        }
        String normalized = pickExtra.length() > 20 ? pickExtra.substring(pickExtra.length() - 20) : pickExtra;
        return pickSourceMappingMapper.selectOne(activeQuery()
                .eq(PickSourceMapping::getShortId, normalized)
                .last("limit 1"));
    }

    private OrderLinkAttributionResolution resolvePickSource(OrderAttributionInput input) {
        OrderLinkAttributionResolution result = resolveIfDecisive(
                findByPickSource(input.pickSource()),
                input,
                AttributionSource.PICK_SOURCE,
                false,
                null);
        if (result != null) {
            return result;
        }
        result = resolveIfDecisive(
                findByPickExtra(input.pickExtra()),
                input,
                AttributionSource.PICK_SOURCE,
                false,
                null);
        if (result != null) {
            return result;
        }
        if (!StringUtils.hasText(input.pickExtra())) {
            return null;
        }
        String normalized = input.pickExtra().length() > 20
                ? input.pickExtra().substring(input.pickExtra().length() - 20)
                : input.pickExtra();
        return resolveIfDecisive(
                findByShortId(normalized),
                input,
                AttributionSource.PICK_SOURCE,
                false,
                null);
    }

    private OrderLinkAttributionResolution resolveExactNative(OrderAttributionInput input) {
        for (NativeKey key : nativeKeys(input)) {
            if (!key.isComplete(input.productId())) {
                continue;
            }
            OrderLinkAttributionResolution result = resolveIfDecisive(
                    pickSourceMappingMapper.selectList(activeQuery()
                            .eq(PickSourceMapping::getColonelBuyinId, key.colonelBuyinId())
                            .eq(PickSourceMapping::getActivityId, key.activityId())
                            .eq(PickSourceMapping::getProductId, input.productId())
                            .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                            .orderByAsc(PickSourceMapping::getCreateTime)),
                    input,
                    AttributionSource.NATIVE_UNIQUE_LINK_OWNER,
                    true,
                    key.colonelBuyinId());
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private OrderLinkAttributionResolution resolveActivityProductNative(OrderAttributionInput input) {
        if (!StringUtils.hasText(input.productId())) {
            return null;
        }
        for (String activityId : activityIds(input)) {
            OrderLinkAttributionResolution result = resolveIfDecisive(
                    pickSourceMappingMapper.selectList(activeQuery()
                            .eq(PickSourceMapping::getActivityId, activityId)
                            .eq(PickSourceMapping::getProductId, input.productId())
                            .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                            .orderByAsc(PickSourceMapping::getCreateTime)),
                    input,
                    AttributionSource.NATIVE_UNIQUE_LINK_OWNER,
                    true,
                    input.colonelBuyinId());
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private OrderLinkAttributionResolution resolveBuyinDiagnostic(OrderAttributionInput input) {
        boolean candidatesFound = false;
        for (NativeKey key : nativeKeys(input)) {
            if (!StringUtils.hasText(key.colonelBuyinId())) {
                continue;
            }
            List<PickSourceMapping> candidates = pickSourceMappingMapper.selectList(activeQuery()
                    .eq(PickSourceMapping::getColonelBuyinId, key.colonelBuyinId())
                    .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                    .orderByAsc(PickSourceMapping::getCreateTime));
            candidatesFound = candidatesFound || (candidates != null && !candidates.isEmpty());
        }
        return notFound(candidatesFound ? "NATIVE_BUYIN_DIAGNOSTIC_ONLY" : "MAPPING_NOT_FOUND");
    }

    private OrderLinkAttributionResolution resolveIfDecisive(
            List<PickSourceMapping> candidates,
            OrderAttributionInput input,
            String source,
            boolean nativeKeyMatched,
            String requestedBuyinId) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        OrderLinkAttributionResolution resolution = resolveCandidates(
                candidates, input.businessTime(), source, nativeKeyMatched, requestedBuyinId);
        return resolution.status() == Status.NOT_FOUND ? null : resolution;
    }

    private OrderLinkAttributionResolution resolveCandidates(
            List<PickSourceMapping> candidates,
            LocalDateTime businessTime,
            String source,
            boolean nativeKeyMatched,
            String requestedBuyinId) {
        List<PickSourceMapping> active = candidates.stream()
                .filter(this::isActive)
                .toList();
        if (active.isEmpty()) {
            return notFound("MAPPING_NOT_FOUND");
        }

        List<PickSourceMapping> valid = active.stream()
                .filter(row -> isWithinValidity(row, businessTime))
                .filter(row -> !isCreatedAfter(row, businessTime))
                .toList();
        if (valid.isEmpty()) {
            if (active.stream().anyMatch(row -> isCreatedAfter(row, businessTime)
                    || isValidFromAfter(row, businessTime))) {
                return new OrderLinkAttributionResolution(
                        Status.MAPPING_AFTER_ORDER,
                        null,
                        null,
                        null,
                        AttributionSource.UNATTRIBUTED,
                        "MAPPING_AFTER_ORDER",
                        nativeKeyMatched,
                        false,
                        earliestCreateTime(active));
            }
            return notFound("MAPPING_NOT_FOUND");
        }

        if (valid.stream().anyMatch(row -> row.getUserId() == null || ownerType(row) == null)) {
            return new OrderLinkAttributionResolution(
                    Status.OWNER_TYPE_MISSING,
                    null,
                    null,
                    null,
                    AttributionSource.UNATTRIBUTED,
                    "ATTRIBUTION_OWNER_TYPE_MISSING",
                    nativeKeyMatched,
                    false,
                    earliestCreateTime(valid));
        }

        Map<OwnerKey, List<PickSourceMapping>> byOwner = valid.stream()
                .collect(Collectors.groupingBy(
                        row -> new OwnerKey(row.getUserId(), ownerType(row)),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));
        if (byOwner.size() != 1) {
            return new OrderLinkAttributionResolution(
                    Status.AMBIGUOUS,
                    null,
                    null,
                    null,
                    AttributionSource.AMBIGUOUS,
                    "MULTIPLE_ATTRIBUTION_OWNERS",
                    nativeKeyMatched,
                    false,
                    earliestCreateTime(valid));
        }

        OwnerKey owner = byOwner.keySet().iterator().next();
        PickSourceMapping selected = byOwner.get(owner).stream()
                .min(Comparator.comparing(PickSourceMapping::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow();
        boolean buyinMismatch = StringUtils.hasText(requestedBuyinId)
                && StringUtils.hasText(selected.getColonelBuyinId())
                && !requestedBuyinId.trim().equals(selected.getColonelBuyinId().trim());
        return new OrderLinkAttributionResolution(
                Status.UNIQUE,
                owner.userId(),
                selected.getDeptId(),
                owner.ownerType(),
                source,
                "UNIQUE_LINK_OWNER",
                nativeKeyMatched,
                buyinMismatch,
                selected.getCreateTime());
    }

    private List<PickSourceMapping> findByPickSource(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return pickSourceMappingMapper.selectList(activeQuery()
                .eq(PickSourceMapping::getPickSource, value.trim())
                .orderByAsc(PickSourceMapping::getCreateTime));
    }

    private List<PickSourceMapping> findByPickExtra(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return pickSourceMappingMapper.selectList(activeQuery()
                .eq(PickSourceMapping::getPickExtra, value.trim())
                .orderByAsc(PickSourceMapping::getCreateTime));
    }

    private List<PickSourceMapping> findByShortId(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return pickSourceMappingMapper.selectList(activeQuery()
                .eq(PickSourceMapping::getShortId, value.trim())
                .orderByAsc(PickSourceMapping::getCreateTime));
    }

    private LambdaQueryWrapper<PickSourceMapping> activeQuery() {
        return new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getStatus, 1)
                .eq(PickSourceMapping::getDeleted, 0);
    }

    private boolean isActive(PickSourceMapping row) {
        return row != null && Integer.valueOf(1).equals(row.getStatus())
                && !Integer.valueOf(1).equals(row.getDeleted());
    }

    private boolean isWithinValidity(PickSourceMapping row, LocalDateTime businessTime) {
        if (businessTime == null) {
            return true;
        }
        return !isValidFromAfter(row, businessTime)
                && (row.getValidUntil() == null || !row.getValidUntil().isBefore(businessTime));
    }

    private boolean isCreatedAfter(PickSourceMapping row, LocalDateTime businessTime) {
        return businessTime != null && row.getCreateTime() != null && row.getCreateTime().isAfter(businessTime);
    }

    private boolean isValidFromAfter(PickSourceMapping row, LocalDateTime businessTime) {
        return businessTime != null && row.getValidFrom() != null && row.getValidFrom().isAfter(businessTime);
    }

    private AttributionOwnerType ownerType(PickSourceMapping row) {
        try {
            return AttributionOwnerType.parseNullable(row.getAttributionOwnerType());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private LocalDateTime earliestCreateTime(List<PickSourceMapping> rows) {
        return rows.stream()
                .map(PickSourceMapping::getCreateTime)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private List<NativeKey> nativeKeys(OrderAttributionInput input) {
        List<NativeKey> result = new ArrayList<>();
        result.add(new NativeKey(input.colonelBuyinId(), input.activityId()));
        NativeKey second = new NativeKey(input.secondColonelBuyinId(), input.secondActivityId());
        if (!second.equals(result.get(0))) {
            result.add(second);
        }
        return result;
    }

    private List<String> activityIds(OrderAttributionInput input) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (StringUtils.hasText(input.activityId())) {
            result.add(input.activityId().trim());
        }
        if (StringUtils.hasText(input.secondActivityId())) {
            result.add(input.secondActivityId().trim());
        }
        return List.copyOf(result);
    }

    private OrderLinkAttributionResolution notFound(String reason) {
        return new OrderLinkAttributionResolution(
                Status.NOT_FOUND,
                null,
                null,
                null,
                AttributionSource.UNATTRIBUTED,
                reason,
                false,
                false,
                null);
    }

    private record OwnerKey(UUID userId, AttributionOwnerType ownerType) {
    }

    private record NativeKey(String colonelBuyinId, String activityId) {
        private boolean isComplete(String productId) {
            return StringUtils.hasText(colonelBuyinId)
                    && StringUtils.hasText(activityId)
                    && StringUtils.hasText(productId);
        }
    }
}
