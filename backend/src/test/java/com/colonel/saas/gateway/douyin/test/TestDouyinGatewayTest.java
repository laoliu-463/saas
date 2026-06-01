package com.colonel.saas.gateway.douyin.test;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDouyinGatewayTest {

    @Test
    void activityGatewayFiltersSortsPagesAndBuildsActivityMutations() {
        TestDouyinActivityGateway gateway = new TestDouyinActivityGateway();

        DouyinActivityGateway.ActivityListResult filtered = gateway.listActivities(
                new DouyinActivityGateway.ActivityListQuery("app", 1, 1L, 0L, 1L, 2L, "100001")
        );

        assertThat(filtered.test()).isTrue();
        assertThat(filtered.institutionId()).isEqualTo(11111111L);
        assertThat(filtered.total()).isEqualTo(1);
        assertThat(filtered.activityList())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.activityId()).isEqualTo(100001L);
                    assertThat(item.status()).isEqualTo(1);
                    assertThat(item.toMap())
                            .containsEntry("activityId", 100001L)
                            .containsEntry("activityStatus", 1)
                            .containsKeys("startTime", "endTime", "colonelBuyinId");
                });

        DouyinActivityGateway.ActivityListResult emptyPage = gateway.listActivities(
                new DouyinActivityGateway.ActivityListQuery(null, 0, null, null, 99L, 20L, "missing")
        );

        assertThat(emptyPage.activityList()).isEmpty();

        Map<String, Object> created = gateway.createOrUpdate(activityCreateCommand(null, "ASCII Activity"));
        assertThat(created)
                .containsEntry("code", 10000)
                .extractingByKey("data")
                .isInstanceOfSatisfying(Map.class, data -> {
                    Map<String, Object> payload = stringMap(data);
                    assertThat(payload.get("activity_id")).isEqualTo(900001L);
                    assertThat(payload.get("activity_name")).isEqualTo("ASCII Activity");
                });

        Map<String, Object> updated = gateway.createOrUpdate(activityCreateCommand(321L, null));
        assertThat(updated)
                .extractingByKey("data")
                .isInstanceOfSatisfying(Map.class, data -> assertThat(stringMap(data).get("activity_id")).isEqualTo(321L));

        Map<String, Object> cancel = gateway.cancelActivityProduct(null, null);
        assertThat(cancel)
                .extractingByKey("data")
                .isInstanceOfSatisfying(Map.class, data -> {
                    Map<String, Object> payload = stringMap(data);
                    assertThat(payload.get("app_id")).isEqualTo("test-app");
                    assertThat(payload.get("payload")).isEqualTo(Map.of());
                });

        Map<String, Object> customCancel = gateway.cancelActivityProduct("real-app", Map.of("product_id", 123L));
        assertThat(customCancel)
                .extractingByKey("data")
                .isInstanceOfSatisfying(Map.class, data -> {
                    Map<String, Object> payload = stringMap(data);
                    assertThat(payload.get("app_id")).isEqualTo("real-app");
                    assertThat(payload.get("payload")).isEqualTo(Map.of("product_id", 123L));
                });

        Map<String, Object> detail = gateway.activityDetail(null, "not-numeric");
        assertThat(detail)
                .extractingByKey("data")
                .isInstanceOfSatisfying(Map.class, wrapper -> {
                    Object data = stringMap(wrapper).get("data");
                    assertThat(data).isInstanceOfSatisfying(Map.class, payload ->
                            assertThat(stringMap(payload).keySet()
                                    .containsAll(List.of("colonel_buyin_id", "colonelBuyinId", "activity_name", "status_text"))).isTrue());
                });

        Map<String, Object> mutate = gateway.createOrUpdateActivity(activityMutateCommand(null));
        assertThat(mutate)
                .extractingByKey("data")
                .isInstanceOfSatisfying(Map.class, data -> assertThat(stringMap(data).get("activity_id")).isEqualTo(12345L));
    }

    @Test
    void activityGatewayProductsSupportCursorAndPageModes() {
        TestDouyinActivityGateway gateway = new TestDouyinActivityGateway();

        DouyinActivityGateway.ActivityProductListResult cursorResult = gateway.listActivityProducts(
                new DouyinActivityGateway.ActivityProductListQuery(
                        "app",
                        "10001",
                        null,
                        null,
                        3,
                        null,
                        null,
                        null,
                        1,
                        1L,
                        "bad-cursor",
                        null
                )
        );

        assertThat(cursorResult.test()).isTrue();
        assertThat(cursorResult.activityId()).isEqualTo(10001L);
        assertThat(cursorResult.total()).isNull();
        assertThat(cursorResult.nextCursor()).isNotBlank();
        assertThat(cursorResult.items())
                .hasSize(3)
                .allSatisfy(item -> {
                    assertThat(item.status()).isEqualTo(1);
                    assertThat(item.toMap())
                            .containsEntry("status", 1)
                            .containsKeys("productId", "origin_colonel_buyin_id", "originColonelBuyinId");
                });
        assertThat(cursorResult.toSnapshotItems()).hasSize(3);

        DouyinActivityGateway.ActivityProductListResult pageResult = gateway.listActivityProducts(
                new DouyinActivityGateway.ActivityProductListQuery(
                        null,
                        "10001",
                        null,
                        null,
                        100,
                        null,
                        null,
                        "1900118",
                        0,
                        0L,
                        null,
                        1L
                )
        );

        assertThat(pageResult.total()).isEqualTo(1L);
        assertThat(pageResult.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.productId()).isEqualTo(1900118L);
                    assertThat(item.status()).isEqualTo(0);
                    assertThat(item.statusText()).isNotBlank();
                });

        DouyinActivityGateway.ActivityProductListResult emptyPage = gateway.listActivityProducts(
                new DouyinActivityGateway.ActivityProductListQuery(null, "10001", null, null, 2, null, null,
                        "missing", null, 0L, null, 99L)
        );

        assertThat(emptyPage.total()).isZero();
        assertThat(emptyPage.items()).isEmpty();
    }

    @Test
    void productGatewayFiltersPagesAndBuildsSkus() {
        TestDouyinProductGateway gateway = new TestDouyinProductGateway();

        DouyinProductGateway.ActivityProductListResult cursorResult = gateway.queryActivityProducts(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        "app",
                        "10001",
                        null,
                        null,
                        3,
                        null,
                        null,
                        null,
                        1,
                        1L,
                        "not-a-number",
                        null
                )
        );

        assertThat(cursorResult.test()).isTrue();
        assertThat(cursorResult.activityId()).isEqualTo(10001L);
        assertThat(cursorResult.total()).isNull();
        assertThat(cursorResult.items())
                .hasSize(3)
                .allSatisfy(item -> {
                    assertThat(item.status()).isEqualTo(1);
                    assertThat(item.toMap())
                            .containsEntry("status", 1)
                            .containsKeys("productId", "origin_colonel_buyin_id", "originColonelBuyinId");
                });

        DouyinProductGateway.ActivityProductListResult pageResult = gateway.queryActivityProducts(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null,
                        "10001",
                        null,
                        null,
                        100,
                        null,
                        null,
                        "1900118",
                        0,
                        0L,
                        null,
                        1L
                )
        );

        assertThat(pageResult.total()).isEqualTo(1L);
        assertThat(pageResult.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(1900118L);
            assertThat(item.status()).isEqualTo(0);
            assertThat(item.statusText()).isNotBlank();
        });

        DouyinProductGateway.ActivityProductListResult emptyPage = gateway.queryActivityProducts(
                new DouyinProductGateway.ActivityProductQueryRequest(null, "10001", null, null, 2, null, null,
                        "missing", null, 0L, null, 99L)
        );
        assertThat(emptyPage.total()).isZero();
        assertThat(emptyPage.items()).isEmpty();

        assertThat(gateway.queryProductSkus("1900112"))
                .hasSize(2)
                .extracting(DouyinProductGateway.ProductSkuResult::skuId)
                .containsExactly("1900112-SKU1", "1900112-SKU2");
        assertThat(gateway.queryProductSkus("not-numeric").get(0).price()).isEqualTo(9900L);
    }

    @Test
    void promotionGatewayBuildsLinksAndRawProbePayloads() {
        TestDouyinPromotionGateway gateway = new TestDouyinPromotionGateway();
        DouyinPromotionGateway.PromotionContext context = new DouyinPromotionGateway.PromotionContext(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "P1",
                "A1",
                "https://source.example/item",
                "manual",
                "T1",
                "channel-extra"
        );

        DouyinPromotionGateway.PromotionLinkResult link = gateway.generateLink(
                new DouyinPromotionGateway.PromotionLinkCommand("external-1", 1, List.of("P1"), true, context)
        );

        assertThat(link.pickSource()).startsWith("MOCK");
        assertThat(link.pickExtra()).isEqualTo("channel-extra");
        assertThat(link.shortLink()).startsWith("https://test.short.link/");
        assertThat(link.promoteLink())
                .contains("/activity/A1/product/P1")
                .contains("pick_source=" + link.pickSource());
        assertThat(link.uuidSeed()).isNotBlank();

        DouyinPromotionGateway.PromotionLinkResult fallback = gateway.generateLink(
                new DouyinPromotionGateway.PromotionLinkCommand(null, 0, List.of(), false, null)
        );

        assertThat(fallback.pickExtra()).isEqualTo(fallback.shortId());
        assertThat(fallback.promoteLink()).contains("unknown-activity").contains("unknown-product");

        Map<String, Object> raw = gateway.rawUpstreamPost(" app ", null, Map.of("x", 1));
        assertThat(raw)
                .containsEntry("code", 10000)
                .containsEntry("msg", "success")
                .extractingByKey("data")
                .isInstanceOfSatisfying(Map.class, data -> {
                    Map<String, Object> payload = stringMap(data);
                    assertThat(payload.get("x")).isEqualTo(1);
                    assertThat(payload.get("method")).isEqualTo("");
                    assertThat(payload.get("appId")).isEqualTo("app");
                });

        Map<String, Object> blankApp = gateway.rawUpstreamPost(" ", "method.name", null);
        assertThat(blankApp)
                .extractingByKey("data")
                .isInstanceOfSatisfying(Map.class, data -> {
                    Map<String, Object> payload = stringMap(data);
                    assertThat(payload.get("method")).isEqualTo("method.name");
                    assertThat(payload.containsKey("appId")).isFalse();
                });
    }

    @Test
    void tokenGatewayBuildsPayloadsInstitutionInfoAndProbeViews() {
        TestDouyinTokenGateway gateway = new TestDouyinTokenGateway();

        DouyinTokenGateway.TokenPayload ensured = gateway.ensureToken(" app ");
        assertThat(ensured.accessToken()).isEqualTo("test_access_token_app");
        assertThat(ensured.refreshToken()).isEqualTo("test_refresh_token_app");
        assertThat(ensured.expiresIn()).isEqualTo(2592000L);
        assertThat(ensured.authorityId()).isEqualTo("test-auth-app");
        assertThat(ensured.authSubjectType()).isNull();
        assertThat(ensured.tokenType()).isEqualTo(1L);

        DouyinTokenGateway.TokenPayload refreshed = gateway.refreshToken(null, "ignored");
        assertThat(refreshed.accessToken()).isEqualTo("test_access_token_test-app");

        DouyinTokenGateway.TokenPayload created = gateway.createToken(
                new DouyinTokenGateway.TokenCreateCommand("code", "refresh_token", "shop", "S1", "AUTH1", " merchant ")
        );
        assertThat(created.authorityId()).isEqualTo("test-auth-test-app");
        assertThat(created.authSubjectType()).isEqualTo("merchant");

        DouyinTokenGateway.TokenPayload defaultCreated = gateway.createToken(
                new DouyinTokenGateway.TokenCreateCommand(null, null, null, null, null, null)
        );
        assertThat(defaultCreated.authSubjectType()).isEqualTo("COLONEL");

        Map<String, Object> info = gateway.institutionInfo(" app ");
        assertThat(info)
                .extractingByKey("data")
                .isInstanceOfSatisfying(Map.class, data -> {
                    Map<String, Object> payload = stringMap(data);
                    assertThat(payload.get("app_id")).isEqualTo("app");
                    assertThat(payload.get("institution_id")).isEqualTo(11111111L);
                });

        DouyinTokenGateway.ProbeTokenCreateResult defaultProbe = gateway.probeCreateToken(
                new DouyinTokenGateway.TokenCreateCommand(" ", " ", "test-shop", "S1", " ", null)
        );
        assertThat(defaultProbe.grantType()).isEqualTo("authorization_code");
        assertThat(defaultProbe.codeState()).isEqualTo("absent");
        assertThat(defaultProbe.testShop()).isEqualTo("test-shop");
        assertThat(defaultProbe.shopId()).isEqualTo("S1");
        assertThat(defaultProbe.authIdPresent()).isFalse();
        assertThat(defaultProbe.response().maskedAccessToken()).isEqualTo("****");
        assertThat(defaultProbe.response().maskedRefreshToken()).isEqualTo("****");

        DouyinTokenGateway.ProbeTokenCreateResult presentProbe = gateway.probeCreateToken(
                new DouyinTokenGateway.TokenCreateCommand("code", "client_credentials", null, null, "AUTH", "MERCHANT")
        );
        assertThat(presentProbe.grantType()).isEqualTo("client_credentials");
        assertThat(presentProbe.codeState()).isEqualTo("present");
        assertThat(presentProbe.authIdPresent()).isTrue();
        assertThat(presentProbe.authSubjectType()).isEqualTo("MERCHANT");
    }

    @Test
    void orderGatewayBuildsMockSettlementWindowsAndWebhookOrders() {
        PickSourceMappingMapper mapper = mock(PickSourceMappingMapper.class);
        PickSourceMapping latest = pickSourceMapping();
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(latest);
        TestDouyinOrderGateway gateway = new TestDouyinOrderGateway(mapper);

        DouyinOrderGateway.OrderListResult settlement = gateway.listSettlement(
                new DouyinOrderGateway.DouyinOrderQueryRequest(1000L, 2000L, 100, null)
        );

        assertThat(settlement.hasMore()).isFalse();
        assertThat(settlement.nextCursor()).isEqualTo("0");
        assertThat(settlement.rawResponse())
                .containsEntry("test", true)
                .containsEntry("order_count", 3);
        assertThat(settlement.orders()).hasSize(3);
        assertThat(settlement.orders().get(0))
                .satisfies(order -> {
                    assertThat(order.externalOrderId()).isEqualTo("MOCK_ORD_ATTR_SHORT1");
                    assertThat(order.externalProductId()).isEqualTo("P1");
                    assertThat(order.productId()).isEqualTo("P1");
                    assertThat(order.talentId()).isEqualTo("T1");
                    assertThat(order.talentName()).isEqualTo("Talent One");
                    assertThat(order.pickSource()).isEqualTo("PS1");
                    assertThat(order.rawPayload())
                            .containsEntry("colonel_activity_id", "A1")
                            .containsEntry("pick_extra", "PE1");
                });
        assertThat(settlement.orders().get(2).pickSource()).isNull();

        DouyinOrderGateway.OrderListResult window = gateway.listSettlementWindow("cursor", 2);
        assertThat(window.orders()).hasSize(3);

        DouyinOrderGateway.OrderListResult byIds = gateway.listSettlementByOrderIds(
                List.of(" ORD1 ", "ORD1", "", "ORD2")
        );
        assertThat(byIds.orders())
                .hasSize(2)
                .extracting(DouyinOrderGateway.DouyinOrderItem::externalOrderId)
                .containsExactly("ORD1", "ORD2");
        assertThat(byIds.rawResponse()).containsEntry("order_ids", List.of("ORD1", "ORD2"));

        DouyinOrderGateway.OrderListResult empty = gateway.listSettlementByOrderIds(null);
        assertThat(empty.orders()).isEmpty();
        assertThat(empty.rawResponse()).containsEntry("order_ids", List.of());
    }

    @Test
    void orderGatewayFallsBackWhenNoLatestPickSourceMappingExists() {
        PickSourceMappingMapper mapper = mock(PickSourceMappingMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        TestDouyinOrderGateway gateway = new TestDouyinOrderGateway(mapper);

        DouyinOrderGateway.OrderListResult settlement = gateway.listSettlement(
                new DouyinOrderGateway.DouyinOrderQueryRequest(0L, 0L, 100, null)
        );

        assertThat(settlement.orders()).hasSize(2);
        assertThat(settlement.orders().get(0).productId()).isEqualTo("1002");
        assertThat(settlement.orders().get(1).productId()).isEqualTo("1003");

        DouyinOrderGateway.OrderListResult byIds = gateway.listSettlementByOrderIds(List.of("ORD1"));
        assertThat(byIds.orders())
                .singleElement()
                .satisfies(order -> {
                    assertThat(order.externalProductId()).isEqualTo("EXT_WEBHOOK_0");
                    assertThat(order.productId()).isEqualTo("WEBHOOK_PRODUCT_0");
                    assertThat(order.pickSource()).isNull();
                    assertThat(order.rawPayload()).containsEntry("order_id", "ORD1");
                });
    }

    private ActivityApi.ActivityCreateOrUpdateCommand activityCreateCommand(Long activityId, String activityName) {
        return new ActivityApi.ActivityCreateOrUpdateCommand(
                "app",
                activityId,
                true,
                false,
                "SHOP",
                activityName,
                "desc",
                "2026-05-01",
                "2026-05-31",
                "10",
                "5",
                "wechat",
                "13800000000",
                "100",
                1,
                "shop-1",
                true,
                "category",
                80,
                7,
                0,
                0,
                "1",
                "1",
                0
        );
    }

    private DouyinActivityGateway.ActivityMutateCommand activityMutateCommand(Long activityId) {
        return new DouyinActivityGateway.ActivityMutateCommand(
                "app",
                activityId,
                true,
                false,
                "SHOP",
                "mutate",
                "desc",
                "2026-05-01",
                "2026-05-31",
                "10",
                "5",
                "wechat",
                "13800000000",
                "100",
                1,
                "shop-1",
                true,
                "category",
                80,
                7,
                0,
                0,
                "1",
                "1",
                0
        );
    }

    private PickSourceMapping pickSourceMapping() {
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setShortId("SHORT1");
        mapping.setProductId("P1");
        mapping.setActivityId("A1");
        mapping.setPickSource("PS1");
        mapping.setPickExtra("PE1");
        mapping.setTalentId("T1");
        mapping.setTalentName("Talent One");
        mapping.setStatus(1);
        return mapping;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stringMap(Object value) {
        return (Map<String, Object>) value;
    }
}
