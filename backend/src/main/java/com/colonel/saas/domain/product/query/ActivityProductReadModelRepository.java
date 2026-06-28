package com.colonel.saas.domain.product.query;

import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;

import java.util.List;
import java.util.Set;

/**
 * 活动商品读模型持久化端口。
 */
public interface ActivityProductReadModelRepository {

    List<ProductOperationState> findStates(String activityId, Set<String> productIds);

    List<ProductOperationLog> findDecisionLogs(String activityId, Set<String> productIds);

    ProductOperationLog findLatestDecisionLog(String activityId, String productId);

    List<Merchant> findMerchants(Set<Long> shopIds);

    Merchant findMerchant(Long shopId);

    String findActivityName(String activityId);
}
