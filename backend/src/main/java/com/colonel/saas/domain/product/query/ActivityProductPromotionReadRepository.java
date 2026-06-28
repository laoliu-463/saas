package com.colonel.saas.domain.product.query;

import com.colonel.saas.entity.PromotionLink;

import java.util.List;
import java.util.Set;

/**
 * 活动商品推广链接读侧端口。
 */
public interface ActivityProductPromotionReadRepository {

    List<PromotionLink> findPromotionLinks(String activityId, Set<String> productIds);

    List<PromotionLink> findPromotionLinks(String activityId, String productId);
}
