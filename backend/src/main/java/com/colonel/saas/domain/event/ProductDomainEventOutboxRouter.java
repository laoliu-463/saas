package com.colonel.saas.domain.event;

import com.colonel.saas.constant.ProductDomainEventTypes;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 商品领域事件 Outbox 路由器。
 * <p>
 * 该组件属于领域事件层，负责将 Outbox 表中持久化的领域事件
 * 路由并分发到对应的 {@link ProductDomainEventPublisher} 进行处理。
 * 基于事件类型前缀判断是否由本路由器负责处理。
 * </p>
 * <p>
 * 支持的事件类型前缀：
 * <ul>
 *   <li>{@code Product} — 商品相关事件（上架、下架、审核等）</li>
 *   <li>{@code Activity} — 活动相关事件（延期、创建等）</li>
 *   <li>{@code Partner} — 合作伙伴相关事件</li>
 *   <li>{@code Colonel} — 团长自定义事件</li>
 * </ul>
 * </p>
 *
 * @see DomainEventOutbox 领域事件 Outbox 实体
 * @see ProductDomainEventPublisher 商品领域事件发布器
 */
@Component
public class ProductDomainEventOutboxRouter {

    /** 商品领域事件发布器，负责将事件重新发布为 Spring 事件 */
    private final ProductDomainEventPublisher productDomainEventPublisher;

    /** Jackson JSON 序列化器，用于反序列化事件负载 */
    private final ObjectMapper objectMapper;

    /**
     * 构造注入依赖。
     *
     * @param productDomainEventPublisher 商品领域事件发布器
     * @param objectMapper                Jackson JSON 序列化器
     */
    public ProductDomainEventOutboxRouter(
            ProductDomainEventPublisher productDomainEventPublisher,
            ObjectMapper objectMapper) {
        this.productDomainEventPublisher = productDomainEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 判断本路由器是否支持指定的事件类型。
     * <p>
     * 通过事件类型前缀匹配，支持 Product、Activity、Partner、Colonel 四大类事件。
     * </p>
     *
     * @param eventType 事件类型字符串
     * @return 如果事件类型以支持的前缀开头则返回 {@code true}，否则返回 {@code false}
     */
    public boolean supports(String eventType) {
        return eventType != null && (eventType.startsWith("Product")
                || eventType.startsWith("Activity")
                || eventType.startsWith("Partner")
                || eventType.startsWith("Colonel"));
    }

    /**
     * 分发 Outbox 领域事件。
     * <p>
     * 从 Outbox 记录中反序列化事件负载，并通过
     * {@link ProductDomainEventPublisher#republishSpringEvent} 重新发布为 Spring 应用事件。
     * 对于活动延期事件（{@code ACTIVITY_EXTENDED}），仅做事件分发，
     * 实际的延期逻辑已由 {@code ProductService} 在同步时触发。
     * </p>
     *
     * @param event Outbox 领域事件记录
     * @throws Exception JSON 反序列化或事件发布失败时抛出
     */
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
