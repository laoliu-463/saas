package com.colonel.saas.domain.order.facade;

import com.colonel.saas.entity.PromotionLink;

import java.util.Collection;
import java.util.List;

/**
 * 推广链接事实门面。
 * <p>
 * ProductService 负责转链编排，但不直接穿透订单/归因侧 PromotionLink Mapper。
 * </p>
 */
public interface PromotionLinkRecordFacade {

    List<PromotionLink> findByProductId(String productId);

    List<PromotionLink> findByActivityAndProductIds(String activityId, Collection<String> productIds);

    List<PromotionLink> findByActivityAndProductId(String activityId, String productId);

    void save(PromotionLink link);
}
