package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionPolicy;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionPolicy.UnattributedBucket;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.AttributionService.AttributionResult;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 订单同步归因路由（DDD-SLIM-ORDER-002）：委派 {@link AttributionService} 解析，
 * 由 {@link OrderDefaultAttributionPolicy} 写入订单字段。
 */
@Service
public class OrderAttributionRouter {

    private final DddRefactorProperties dddRefactorProperties;
    private final AttributionService attributionService;

    public OrderAttributionRouter(
            DddRefactorProperties dddRefactorProperties,
            AttributionService attributionService) {
        this.dddRefactorProperties = dddRefactorProperties;
        this.attributionService = attributionService;
    }

    public boolean isPolicyEnabled() {
        return dddRefactorProperties.isEnabled()
                && dddRefactorProperties.getOrderAttribution().isEnabled();
    }

    public void applyInitialUnattributedStatus(ColonelsettlementOrder order) {
        OrderDefaultAttributionPolicy.applyInitialUnattributedStatus(order);
    }

    public AttributionResult resolveAndApply(
            ColonelsettlementOrder order,
            Map<String, Object> rawPayload,
            String talentName) {
        AttributionResult result = attributionService.resolveAttribution(order, rawPayload);
        OrderDefaultAttributionPolicy.applyAttributionResult(
                order, result, order.getActivityId(), talentName);
        return result;
    }

    public boolean isAttributed(String attributionStatus) {
        return OrderDefaultAttributionPolicy.isAttributed(attributionStatus);
    }

    public UnattributedBucket classifyUnattributedRemark(String remark) {
        return OrderDefaultAttributionPolicy.classifyUnattributedRemark(remark);
    }
}
