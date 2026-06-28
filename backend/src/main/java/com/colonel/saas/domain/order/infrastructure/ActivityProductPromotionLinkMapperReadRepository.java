package com.colonel.saas.domain.order.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.product.query.ActivityProductPromotionReadRepository;
import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.mapper.PromotionLinkMapper;

import java.util.List;
import java.util.Set;

/**
 * MyBatis-backed 活动商品推广链接读侧适配器。
 */
public class ActivityProductPromotionLinkMapperReadRepository implements ActivityProductPromotionReadRepository {

    private final PromotionLinkMapper promotionLinkMapper;

    public ActivityProductPromotionLinkMapperReadRepository(PromotionLinkMapper promotionLinkMapper) {
        this.promotionLinkMapper = promotionLinkMapper;
    }

    @Override
    public List<PromotionLink> findPromotionLinks(String activityId, Set<String> productIds) {
        return promotionLinkMapper.selectList(new LambdaQueryWrapper<PromotionLink>()
                .eq(PromotionLink::getActivityId, activityId)
                .in(PromotionLink::getProductId, productIds)
                .orderByDesc(PromotionLink::getCreatedAt));
    }

    @Override
    public List<PromotionLink> findPromotionLinks(String activityId, String productId) {
        return promotionLinkMapper.selectList(new LambdaQueryWrapper<PromotionLink>()
                .eq(PromotionLink::getActivityId, activityId)
                .eq(PromotionLink::getProductId, productId)
                .orderByDesc(PromotionLink::getCreatedAt));
    }
}
