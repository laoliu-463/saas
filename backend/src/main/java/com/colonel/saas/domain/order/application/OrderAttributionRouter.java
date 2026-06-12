package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionPolicy;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionPolicy.UnattributedBucket;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionResult;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.AttributionService.AttributionResult;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 订单同步归因路由（DDD-ORDER-004 / SLIM-ORDER-002）。
 *
 * <p>开关开启时委派 {@link OrderDefaultAttributionResolver} + {@link OrderDefaultAttributionPolicy}，
 * 仅计算默认归因；关闭时走 legacy {@link AttributionService}（含独家规则）。</p>
 */
@Service
public class OrderAttributionRouter {

    private final DddRefactorProperties dddRefactorProperties;
    private final AttributionService attributionService;
    private final OrderDefaultAttributionResolver defaultAttributionResolver;

    public OrderAttributionRouter(
            DddRefactorProperties dddRefactorProperties,
            AttributionService attributionService,
            OrderDefaultAttributionResolver defaultAttributionResolver) {
        this.dddRefactorProperties = dddRefactorProperties;
        this.attributionService = attributionService;
        this.defaultAttributionResolver = defaultAttributionResolver;
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
        if (isPolicyEnabled()) {
            OrderDefaultAttributionResult result = defaultAttributionResolver.resolve(order, rawPayload);
            OrderDefaultAttributionPolicy.applyToOrder(order, result, talentName);
            return OrderDefaultAttributionPolicy.toLegacyResult(result);
        }
        AttributionResult legacy = attributionService.resolveAttribution(order, rawPayload);
        OrderDefaultAttributionPolicy.applyAttributionResult(
                order, legacy, order.getActivityId(), talentName);
        return legacy;
    }

    public boolean isAttributed(String attributionStatus) {
        return OrderDefaultAttributionPolicy.isAttributed(attributionStatus);
    }

    public UnattributedBucket classifyUnattributedRemark(String remark) {
        return OrderDefaultAttributionPolicy.classifyUnattributedRemark(remark);
    }
}
