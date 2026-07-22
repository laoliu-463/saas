package com.colonel.saas.domain.order.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 订单域 pick_source 映射读取适配器（DDD-ORDER-004）。
 */
@Component
public class OrderPickSourceMappingAdapter {

    private static final String SOURCE_TYPE_NATIVE = "NATIVE";

    private final PickSourceMappingMapper pickSourceMappingMapper;

    public OrderPickSourceMappingAdapter(PickSourceMappingMapper pickSourceMappingMapper) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
    }

    public PickSourceMapping findByPickSourceOrExtra(String pickSource, String pickExtra) {
        if (StringUtils.hasText(pickSource)) {
            PickSourceMapping byPickSource = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getPickSource, pickSource.trim())
                    .eq(PickSourceMapping::getStatus, 1)
                    .last("limit 1"));
            if (byPickSource != null) {
                return byPickSource;
            }
        }
        if (!StringUtils.hasText(pickExtra)) {
            return null;
        }
        PickSourceMapping byPickExtra = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getPickExtra, pickExtra.trim())
                .eq(PickSourceMapping::getStatus, 1)
                .last("limit 1"));
        if (byPickExtra != null) {
            return byPickExtra;
        }
        String normalized = pickExtra.length() > 20 ? pickExtra.substring(pickExtra.length() - 20) : pickExtra;
        return pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getShortId, normalized)
                .eq(PickSourceMapping::getStatus, 1)
                .last("limit 1"));
    }

    /**
     * Find the native colonel mapping used by real orders that do not carry pick_source.
     *
     * <p>The lookup order mirrors the legacy attribution contract: exact native key,
     * activity/product fallback, then buyin-only fallback when the order has no second
     * activity. Ambiguous results are deliberately not auto-selected.</p>
     */
    public NativeMappingLookup findByNativeOrder(
            String colonelBuyinId,
            String activityId,
            String productId,
            boolean allowGenericFallback) {
        if (!StringUtils.hasText(colonelBuyinId)) {
            return NativeMappingLookup.none();
        }

        if (StringUtils.hasText(activityId) && StringUtils.hasText(productId)) {
            NativeMappingLookup exact = single(pickSourceMappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getColonelBuyinId, colonelBuyinId.trim())
                    .eq(PickSourceMapping::getActivityId, activityId.trim())
                    .eq(PickSourceMapping::getProductId, productId.trim())
                    .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                    .eq(PickSourceMapping::getStatus, 1)
                    .orderByDesc(PickSourceMapping::getUpdateTime)));
            if (exact.mapping() != null || exact.ambiguous()) {
                return exact;
            }

            NativeMappingLookup activityProduct = single(pickSourceMappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getActivityId, activityId.trim())
                    .eq(PickSourceMapping::getProductId, productId.trim())
                    .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                    .eq(PickSourceMapping::getStatus, 1)
                    .orderByDesc(PickSourceMapping::getUpdateTime)));
            if (activityProduct.mapping() != null || activityProduct.ambiguous()) {
                return activityProduct;
            }
        }

        if (!allowGenericFallback) {
            return NativeMappingLookup.none();
        }
        return single(pickSourceMappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getColonelBuyinId, colonelBuyinId.trim())
                .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                .eq(PickSourceMapping::getStatus, 1)
                .orderByDesc(PickSourceMapping::getUpdateTime)));
    }

    private NativeMappingLookup single(List<PickSourceMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return NativeMappingLookup.none();
        }
        if (mappings.size() > 1) {
            return new NativeMappingLookup(null, true);
        }
        return new NativeMappingLookup(mappings.get(0), false);
    }

    public record NativeMappingLookup(PickSourceMapping mapping, boolean ambiguous) {
        public static NativeMappingLookup none() {
            return new NativeMappingLookup(null, false);
        }
    }
}
