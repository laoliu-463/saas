package com.colonel.saas.gateway.douyin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.colonel.saas.gateway.logistics.LogisticsGateway;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖 Gateway 接口内嵌 Record 的构造、字段访问和 toMap() 方法。
 */
class GatewayRecordTest {

    @Nested
    @DisplayName("DouyinAuthGateway Records")
    class AuthGatewayRecords {

        @Test
        void tokenCreateCommand_fields() {
            var cmd = new DouyinAuthGateway.TokenCreateCommand(
                    "auth_code_123", "authorization_code",
                    "testShop", "shop_001", "auth_001", "merchant");

            assertThat(cmd.authorizationCode()).isEqualTo("auth_code_123");
            assertThat(cmd.grantType()).isEqualTo("authorization_code");
            assertThat(cmd.testShop()).isEqualTo("testShop");
            assertThat(cmd.shopId()).isEqualTo("shop_001");
            assertThat(cmd.authId()).isEqualTo("auth_001");
            assertThat(cmd.authSubjectType()).isEqualTo("merchant");
        }

        @Test
        void tokenPayload_fields() {
            var payload = new DouyinAuthGateway.TokenPayload(
                    "access_token_abc", "refresh_token_xyz",
                    7200L, "auth_001", "merchant", 1L);

            assertThat(payload.accessToken()).isEqualTo("access_token_abc");
            assertThat(payload.refreshToken()).isEqualTo("refresh_token_xyz");
            assertThat(payload.expiresIn()).isEqualTo(7200L);
            assertThat(payload.authorityId()).isEqualTo("auth_001");
            assertThat(payload.authSubjectType()).isEqualTo("merchant");
            assertThat(payload.tokenType()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("DouyinOrderGateway Records")
    class OrderGatewayRecords {

        @Test
        void queryRequest_fields() {
            var req = new DouyinOrderGateway.DouyinOrderQueryRequest(
                    1000L, 2000L, 50, "cursor_abc");

            assertThat(req.startTime()).isEqualTo(1000L);
            assertThat(req.endTime()).isEqualTo(2000L);
            assertThat(req.count()).isEqualTo(50);
            assertThat(req.cursor()).isEqualTo("cursor_abc");
        }

        @Test
        void orderItem_fields() {
            var item = new DouyinOrderGateway.DouyinOrderItem(
                    "EXT001", "EXT_P001", "P001", "M001", "商家",
                    "T001", "达人", "pick_source",
                    10000L, 1500L, 1, 1700000000L, 1700003600L,
                    Map.of("raw", true));

            assertThat(item.externalOrderId()).isEqualTo("EXT001");
            assertThat(item.productId()).isEqualTo("P001");
            assertThat(item.orderAmount()).isEqualTo(10000L);
            assertThat(item.serviceFee()).isEqualTo(1500L);
            assertThat(item.rawPayload()).containsEntry("raw", true);
        }

        @Test
        void orderListResult_fields() {
            var result = new DouyinOrderGateway.OrderListResult(
                    List.of(), true, "next_cursor", Map.of("total", 100));

            assertThat(result.orders()).isEmpty();
            assertThat(result.hasMore()).isTrue();
            assertThat(result.nextCursor()).isEqualTo("next_cursor");
            assertThat(result.rawResponse()).containsEntry("total", 100);
        }
    }

    @Nested
    @DisplayName("DouyinProductGateway Records")
    class ProductGatewayRecords {

        @Test
        void activityProductQueryRequest_fields() {
            var req = new DouyinProductGateway.ActivityProductQueryRequest(
                    "app001", "act001", 1L, 2L, 20,
                    "info", 1, "product_name", 1, 0L, "cursor", 1L);

            assertThat(req.appId()).isEqualTo("app001");
            assertThat(req.activityId()).isEqualTo("act001");
            assertThat(req.count()).isEqualTo(20);
            assertThat(req.cursor()).isEqualTo("cursor");
        }

        @Test
        void activityProductItem_toMap() {
            var item = new DouyinProductGateway.ActivityProductItem(
                    12345L, "测试商品", "cover.jpg",
                    9900L, "99.00", 1500L, 1500L,
                    2000L, "20%", 1, "CPS", "10%", null,
                    false, true, 5000L, 100L, "测试店铺",
                    "4.9", 1, "上架", "美妆", "100",
                    "优惠券信息", "2024-01-01", "2024-12-31",
                    "2024-01-01", "2024-12-31", "http://detail",
                    "7293293346398011698", Map.of("origin_colonel_buyin_id", "7293293346398011698"));

            Map<String, Object> map = item.toMap();

            assertThat(map).containsEntry("productId", 12345L);
            assertThat(map).containsEntry("title", "测试商品");
            assertThat(map).containsEntry("price", 9900L);
            assertThat(map).containsEntry("priceText", "99.00");
            assertThat(map).containsEntry("shopName", "测试店铺");
            assertThat(map).containsEntry("inStock", true);
            assertThat(map).containsEntry("status", 1);
            assertThat(map).containsEntry("categoryName", "美妆");
            assertThat(map).containsEntry("detailUrl", "http://detail");
            assertThat(map).containsEntry("origin_colonel_buyin_id", "7293293346398011698");
            assertThat(map).containsEntry("originColonelBuyinId", "7293293346398011698");
            assertThat(map).hasSize(31);
        }

        @Test
        void activityProductListResult_toMap() {
            var item = new DouyinProductGateway.ActivityProductItem(
                    1L, "商品", "c.jpg", 100L, "1.00",
                    100L, 100L, 100L, "1%", 0, "CPS",
                    "5%", null, false, true, 10L, 1L, "店铺",
                    "4.8", 1, "上架", "类目", "10",
                    "", "", "", "", "", "http://d", null, Map.of());

            var result = new DouyinProductGateway.ActivityProductListResult(
                    true, 100L, 200L, 50L, "next", List.of(item));

            Map<String, Object> map = result.toMap();

            assertThat(map).containsEntry("test", true);
            assertThat(map).containsEntry("activityId", 100L);
            assertThat(map).containsEntry("institutionId", 200L);
            assertThat(map).containsEntry("total", 50L);
            assertThat(map).containsEntry("nextCursor", "next");
            assertThat(map.get("items")).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) map.get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0)).containsEntry("productId", 1L);
        }

        @Test
        void productSkuResult_fields() {
            var sku = new DouyinProductGateway.ProductSkuResult(
                    "SKU001", "红色-XL", 8900L, 50, "sku.jpg");

            assertThat(sku.skuId()).isEqualTo("SKU001");
            assertThat(sku.price()).isEqualTo(8900L);
            assertThat(sku.stock()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("DouyinPromotionGateway Records")
    class PromotionGatewayRecords {

        @Test
        void promotionLinkCommand_fields() {
            var ctx = new DouyinPromotionGateway.PromotionContext(
                    UUID.randomUUID(), UUID.randomUUID(),
                    "P001", "ACT001", "http://source", "scene", "T001", "channel_user-1");
            var cmd = new DouyinPromotionGateway.PromotionLinkCommand(
                    "EXT_UID_001", 1, List.of("P001"), true, ctx);

            assertThat(cmd.externalUniqueId()).isEqualTo("EXT_UID_001");
            assertThat(cmd.promotionScene()).isEqualTo(1);
            assertThat(cmd.productIds()).containsExactly("P001");
            assertThat(cmd.needShortLink()).isTrue();
            assertThat(cmd.context().productId()).isEqualTo("P001");
            assertThat(cmd.context().talentId()).isEqualTo("T001");
            assertThat(cmd.context().pickExtra()).isEqualTo("channel_user-1");
        }

        @Test
        void promotionLinkResult_fields() {
            var result = new DouyinPromotionGateway.PromotionLinkResult(
                    "pick_source_val", "pick_extra_val", "short_123",
                    "http://short.link", "http://promote.link", "seed_001");

            assertThat(result.pickSource()).isEqualTo("pick_source_val");
            assertThat(result.shortLink()).isEqualTo("http://short.link");
            assertThat(result.promoteLink()).isEqualTo("http://promote.link");
            assertThat(result.shortId()).isEqualTo("short_123");
            assertThat(result.uuidSeed()).isEqualTo("seed_001");
        }
    }

    @Nested
    @DisplayName("LogisticsGateway Records")
    class LogisticsGatewayRecords {

        @Test
        void logisticsCommand_fields() {
            UUID sampleId = UUID.randomUUID();
            var cmd = new LogisticsGateway.LogisticsCommand(
                    sampleId, "P001", "张三", "13800138000", "北京市朝阳区");

            assertThat(cmd.sampleRequestId()).isEqualTo(sampleId);
            assertThat(cmd.productId()).isEqualTo("P001");
            assertThat(cmd.recipientName()).isEqualTo("张三");
            assertThat(cmd.recipientPhone()).isEqualTo("13800138000");
            assertThat(cmd.recipientAddress()).isEqualTo("北京市朝阳区");
        }

        @Test
        void logisticsResult_fields() {
            LocalDateTime shipTime = LocalDateTime.of(2024, 1, 15, 10, 30);
            var result = new LogisticsGateway.LogisticsResult(
                    "SF123456", "顺丰速运", "已签收", shipTime);

            assertThat(result.trackingNo()).isEqualTo("SF123456");
            assertThat(result.company()).isEqualTo("顺丰速运");
            assertThat(result.status()).isEqualTo("已签收");
            assertThat(result.shipTime()).isEqualTo(shipTime);
        }
    }
}
