package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.OrderApi;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealDouyinOrderGatewayTest {

    @Test
    void listSettlement_mapsOrderListFromInstituteOrderColonelResponse() {
        OrderApi orderApi = mock(OrderApi.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinOrderGateway gateway = new RealDouyinOrderGateway(
                orderApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("order_id", "4933609365066313446");
        order.put("product_id", "3810562766247428542");
        order.put("shop_id", "56591058");
        order.put("shop_name", "测试店铺");
        order.put("talent_uid", "7351155267604218149");
        order.put("pick_source", "ps_real_001");
        order.put("order_amount", 9900);
        order.put("settle_colonel_commission", 990);
        order.put("order_status", 1);
        order.put("create_time", 1711900800L);
        order.put("settle_time", 1711987200L);
        order.put("encrypt_post_receiver_mobile", "#ML3B#cipher#1##");
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(orderApi.listSettlement(1711900800L, 1711987200L, 20, "0"))
                .thenReturn(Map.of(
                        "code", 10000,
                        "data", Map.of(
                                "order_list", List.of(order),
                                "has_more", false,
                                "next_cursor", "0"
                        )
                ));

        DouyinOrderGateway.OrderListResult result = gateway.listSettlement(
                new DouyinOrderGateway.DouyinOrderQueryRequest(1711900800L, 1711987200L, 20, "0")
        );

        assertThat(result.orders()).hasSize(1);
        DouyinOrderGateway.DouyinOrderItem item = result.orders().get(0);
        assertThat(item.externalOrderId()).isEqualTo("4933609365066313446");
        assertThat(item.productId()).isEqualTo("3810562766247428542");
        assertThat(item.merchantId()).isEqualTo("56591058");
        assertThat(item.pickSource()).isEqualTo("ps_real_001");
        assertThat(item.orderAmount()).isEqualTo(9900L);
        assertThat(item.serviceFee()).isEqualTo(990L);
        assertThat(item.orderStatus()).isEqualTo(1);
        assertThat(item.rawPayload()).containsEntry("encrypt_post_receiver_mobile", "#ML3B#cipher#1##");
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextCursor()).isEqualTo("0");
    }

    @Test
    void listSettlement_mapsNestedOrdersArray() {
        OrderApi orderApi = mock(OrderApi.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinOrderGateway gateway = new RealDouyinOrderGateway(
                orderApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(orderApi.listSettlement(1L, 2L, 20, "0"))
                .thenReturn(Map.of(
                        "code", 10000,
                        "data", Map.of(
                                "data", Map.of(
                                        "orders", List.of(Map.of(
                                                "orderId", "ORDER_NESTED",
                                                "productId", "PRODUCT_NESTED",
                                                "createTime", 1711900800000L
                                        )),
                                        "cursor", "12"
                                )
                        )
                ));

        DouyinOrderGateway.OrderListResult result = gateway.listSettlement(
                new DouyinOrderGateway.DouyinOrderQueryRequest(1L, 2L, 20, "0")
        );

        assertThat(result.orders()).hasSize(1);
        assertThat(result.orders().get(0).externalOrderId()).isEqualTo("ORDER_NESTED");
        assertThat(result.orders().get(0).productId()).isEqualTo("PRODUCT_NESTED");
        assertThat(result.orders().get(0).createTime()).isEqualTo(1711900800L);
        assertThat(result.nextCursor()).isEqualTo("12");
    }

    @Test
    void listSettlement_mapsRealAuthorFieldsAsTalentIdentity() {
        OrderApi orderApi = mock(OrderApi.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinOrderGateway gateway = new RealDouyinOrderGateway(
                orderApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("order_id", "6952647330859784065");
        order.put("product_id", "3745254399715443024");
        order.put("shop_id", 22955671);
        order.put("shop_name", "双汇恒发专卖店");
        order.put("author_buyin_id", "7137334329718292775");
        order.put("author_account", "哆咪哆零食");
        order.put("pay_goods_amount", 1990);
        order.put("pay_success_time", "2026-05-07 12:06:34");
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(orderApi.listSettlement(1778155464L, 1778157264L, 20, "0"))
                .thenReturn(Map.of(
                        "code", 10000,
                        "data", Map.of(
                                "orders", List.of(order),
                                "cursor", "0"
                        )
                ));

        DouyinOrderGateway.OrderListResult result = gateway.listSettlement(
                new DouyinOrderGateway.DouyinOrderQueryRequest(1778155464L, 1778157264L, 20, "0")
        );

        assertThat(result.orders()).hasSize(1);
        DouyinOrderGateway.DouyinOrderItem item = result.orders().get(0);
        assertThat(item.talentId()).isEqualTo("7137334329718292775");
        assertThat(item.talentName()).isEqualTo("哆咪哆零食");
        assertThat(item.pickSource()).isNull();
        assertThat(item.rawPayload()).containsEntry("author_buyin_id", "7137334329718292775");
    }
}
