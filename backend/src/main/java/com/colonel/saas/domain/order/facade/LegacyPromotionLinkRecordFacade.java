package com.colonel.saas.domain.order.facade;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.mapper.PromotionLinkMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

/**
 * {@link PromotionLinkRecordFacade} 遗留实现：委派现有 {@link PromotionLinkMapper}。
 */
@Service
public class LegacyPromotionLinkRecordFacade implements PromotionLinkRecordFacade {

    private final PromotionLinkMapper promotionLinkMapper;

    public LegacyPromotionLinkRecordFacade(PromotionLinkMapper promotionLinkMapper) {
        this.promotionLinkMapper = promotionLinkMapper;
    }

    @Override
    public List<PromotionLink> findByProductId(String productId) {
        if (!StringUtils.hasText(productId)) {
            return List.of();
        }
        return promotionLinkMapper.selectList(new QueryWrapper<PromotionLink>()
                .eq("product_id", productId)
                .orderByDesc("created_at"));
    }

    @Override
    public List<PromotionLink> findByActivityAndProductIds(String activityId, Collection<String> productIds) {
        if (!StringUtils.hasText(activityId) || productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<String> normalizedProductIds = productIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedProductIds.isEmpty()) {
            return List.of();
        }
        return promotionLinkMapper.selectList(new QueryWrapper<PromotionLink>()
                .eq("activity_id", activityId)
                .in("product_id", normalizedProductIds)
                .orderByDesc("created_at"));
    }

    @Override
    public List<PromotionLink> findByActivityAndProductId(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return List.of();
        }
        return promotionLinkMapper.selectList(new QueryWrapper<PromotionLink>()
                .eq("activity_id", activityId)
                .eq("product_id", productId)
                .orderByDesc("created_at"));
    }

    @Override
    public void save(PromotionLink link) {
        if (link == null) {
            return;
        }
        promotionLinkMapper.insert(link);
    }
}
