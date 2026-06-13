package com.colonel.saas.domain.order.query;

import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderQueryViewTest {

    @Test
    @DisplayName("测试OrderListAssembler将实体转换为视图")
    void testOrderListAssembler() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-123");
        order.setProductName("Test Product");
        order.setChannelUserName("Channel A");

        OrderQueryView view = OrderListAssembler.toView(order);
        assertThat(view).isNotNull();
        assertThat(view.getId()).isEqualTo(order.getId());
        assertThat(view.getOrderId()).isEqualTo("ORD-123");
        assertThat(view.getProductName()).isEqualTo("Test Product");
        assertThat(view.getChannelUserName()).isEqualTo("Channel A");

        assertThat(OrderListAssembler.toView(null)).isNull();
    }

    @Test
    @DisplayName("测试OrderDetailAssembler将响应转换为视图")
    void testOrderDetailAssembler() {
        OrderDetailResponse response = new OrderDetailResponse();
        response.setOrderId("ORD-456");
        response.setOrderStatus(1);

        OrderDetailResponse.ProductInfo product = new OrderDetailResponse.ProductInfo();
        product.setProductId("PROD-1");
        response.setProduct(product);

        OrderDetailView view = OrderDetailAssembler.toView(response);
        assertThat(view).isNotNull();
        assertThat(view.getOrderId()).isEqualTo("ORD-456");
        assertThat(view.getOrderStatus()).isEqualTo(1);
        assertThat(view.getProduct()).isNotNull();
        assertThat(view.getProduct().getProductId()).isEqualTo("PROD-1");

        assertThat(OrderDetailAssembler.toView(null)).isNull();
    }
}
