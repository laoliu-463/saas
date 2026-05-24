package com.colonel.saas.domain.event;

import com.colonel.saas.constant.ProductDomainEventTypes;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProductDomainEventOutboxRouter {

    private final ProductDomainEventPublisher productDomainEventPublisher;
    private final ObjectMapper objectMapper;

    public ProductDomainEventOutboxRouter(
            ProductDomainEventPublisher productDomainEventPublisher,
            ObjectMapper objectMapper) {
        this.productDomainEventPublisher = productDomainEventPublisher;
        this.objectMapper = objectMapper;
    }

    public boolean supports(String eventType) {
        return eventType != null && (eventType.startsWith("Product")
                || eventType.startsWith("Activity")
                || eventType.startsWith("Partner")
                || eventType.startsWith("Colonel"));
    }

    public void dispatch(DomainEventOutbox event) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new TypeReference<>() {
        });
        productDomainEventPublisher.republishSpringEvent(event.getEventType(), event.getPayload());
        if (ProductDomainEventTypes.ACTIVITY_EXTENDED.equals(event.getEventType())) {
            // 活动延期由 ProductService 同步时已触发 applyForActivity；此处仅分发事件
            payload.get("activityId");
        }
    }
}
