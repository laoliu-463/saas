package com.colonel.saas.gateway.douyin.contract;

import com.colonel.saas.domain.product.facade.dto.PickSourceMappingReadDTO;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.service.PickSourceMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DouyinContractFixtureProviderTest {

    @Mock
    private PickSourceMappingService pickSourceMappingService;

    private DouyinContractFixtureProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DouyinContractFixtureProvider(pickSourceMappingService);
    }

    @Test
    void basicIdentifiers_shouldExposeStableContractDefaults() {
        assertThat(provider.appKey()).isEqualTo("7623665273727387199");
        assertThat(provider.shopId()).isEqualTo("56591058");
        assertThat(provider.authId()).isEqualTo("7351155267604218149");
    }

    @Test
    void buildTokenPayload_shouldTrimAppIdAndRespectRefreshTokenHint() {
        long before = Instant.now().getEpochSecond();

        var payload = provider.buildTokenPayload(" app_123456789 ", " refresh_hint ");

        assertThat(payload.accessToken()).startsWith("contract_access_456789_");
        assertThat(Long.parseLong(payload.accessToken().substring(payload.accessToken().lastIndexOf('_') + 1)))
                .isGreaterThanOrEqualTo(before);
        assertThat(payload.refreshToken()).isEqualTo("refresh_hint");
        assertThat(payload.expiresIn()).isEqualTo(7200L);
        assertThat(payload.authorityId()).isEqualTo("7351155267604218149");
        assertThat(payload.authSubjectType()).isEqualTo("institution");
        assertThat(payload.tokenType()).isEqualTo(1L);
    }

    @Test
    void buildTokenPayload_shouldFallbackForBlankAppAndRefreshHint() {
        var payload = provider.buildTokenPayload(" ", null);

        assertThat(payload.accessToken()).startsWith("contract_access_387199_");
        assertThat(payload.refreshToken()).isEqualTo("contract_refresh_387199");
    }

    @Test
    void buildInstitutionInfoResponse_shouldReturnUpstreamShapedSuccessMap() {
        Map<String, Object> response = provider.buildInstitutionInfoResponse(" custom_app ");

        assertThat(response).containsEntry("err_no", 0)
                .containsEntry("err_msg", "success")
                .containsEntry("upstream_mode", "contract");
        assertThat(response.get("log_id")).asString().contains("buyin-institutionInfo");
        assertThat(data(response)).containsEntry("app_key", "custom_app")
                .containsEntry("shop_id", "56591058")
                .containsEntry("auth_id", "7351155267604218149")
                .containsEntry("institution_name", "星链达客")
                .containsEntry("auth_subject_type", "self_use");
    }

    @Test
    void buildActivityListResult_shouldFilterSortAndPageActivities() {
        var result = provider.buildActivityListResult(new DouyinActivityGateway.ActivityListQuery(
                "app",
                5,
                null,
                null,
                1L,
                1L,
                "精选"
        ));

        assertThat(result.test()).isFalse();
        assertThat(result.institutionId()).isEqualTo(7351155267604218149L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.activityList()).singleElement()
                .satisfies(item -> {
                    assertThat(item.activityId()).isEqualTo(20260428001L);
                    assertThat(item.activityName()).contains("精选联盟");
                    assertThat(item.toMap()).containsEntry("activityStatus", 5);
                });
    }

    @Test
    void buildActivityListResult_shouldReturnEmptyPageWhenPageStartsPastFilteredRows() {
        var result = provider.buildActivityListResult(new DouyinActivityGateway.ActivityListQuery(
                null,
                0,
                1L,
                0L,
                3L,
                1L,
                null
        ));

        assertThat(result.total()).isEqualTo(2L);
        assertThat(result.activityList()).isEmpty();
    }

    @Test
    void buildActivityListResponse_shouldSerializeActivityRows() {
        Map<String, Object> response = provider.buildActivityListResponse(
                " app-x ",
                null,
                2L,
                0L,
                1L,
                20L,
                "活动"
        );

        assertThat(data(response)).containsEntry("total", 2)
                .containsEntry("app_key", "app-x")
                .containsEntry("institution_id", "7351155267604218149");
        assertThat(list(data(response).get("activity_list"))).hasSize(2);
    }

    @Test
    void buildActivityDetailResponse_shouldFallbackToFirstActivityWhenIdUnknown() {
        Map<String, Object> response = provider.buildActivityDetailResponse(null, "missing");

        assertThat(data(response)).containsEntry("activity_id", 20260428001L)
                .containsEntry("app_key", "7623665273727387199");
    }

    @Test
    void buildActivityProductListResult_shouldSupportCursorModeAndNextCursor() {
        var result = provider.buildActivityProductListResult(new DouyinActivityGateway.ActivityProductListQuery(
                null,
                "20260428001",
                null,
                null,
                1,
                null,
                null,
                "测试爆品",
                null,
                1L,
                "1",
                null
        ));

        assertThat(result.activityId()).isEqualTo(20260428001L);
        assertThat(result.total()).isNull();
        assertThat(result.nextCursor()).isEqualTo("2");
        assertThat(result.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.productId()).isEqualTo(910002L);
                    assertThat(item.toMap()).containsEntry("origin_colonel_buyin_id", "7293293346398011698");
                });
        assertThat(result.toSnapshotItems()).hasSize(1);
    }

    @Test
    void buildActivityProductListResult_shouldSupportPageModeAndStatusFilter() {
        var result = provider.buildActivityProductListResult(new DouyinActivityGateway.ActivityProductListQuery(
                null,
                "20260501002",
                null,
                null,
                20,
                null,
                null,
                null,
                3,
                0L,
                null,
                1L
        ));

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.nextCursor()).isEmpty();
        assertThat(result.items()).singleElement()
                .extracting(DouyinActivityGateway.ActivityProductItem::statusText)
                .isEqualTo("合作已终止");
    }

    @Test
    void buildProductListResult_shouldFallbackDefaultsAndClampPageSize() {
        var result = provider.buildProductListResult(new DouyinProductGateway.ActivityProductQueryRequest(
                null,
                "bad-id",
                null,
                null,
                99,
                null,
                null,
                null,
                null,
                0L,
                null,
                null
        ));

        assertThat(result.activityId()).isZero();
        assertThat(result.total()).isEqualTo(3L);
        assertThat(result.items()).hasSize(3);
        assertThat(result.toMap()).containsEntry("nextCursor", "");
    }

    @Test
    void buildProductListResponse_shouldSerializeProductRows() {
        Map<String, Object> response = provider.buildProductListResponse(
                "app-y",
                "20260501002",
                1,
                "not-a-number",
                "待开始",
                null,
                1L,
                null
        );

        assertThat(data(response)).containsEntry("next_cursor", "1")
                .containsEntry("app_key", "app-y");
        assertThat(list(data(response).get("data"))).singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("product_id", 920001L)
                .containsEntry("title", "待开始活动-可推广商品");
    }

    @Test
    void buildProductSkus_shouldCreateDeterministicSkuRows() {
        assertThat(provider.buildProductSkus("910001"))
                .hasSize(2)
                .extracting(DouyinProductGateway.ProductSkuResult::skuName)
                .containsExactly("标准装", "加量装");
    }

    @Test
    void buildPromotionLinkResult_shouldUseCommandContextAndShortLinkFlag() {
        var result = provider.buildPromotionLinkResult(new DouyinPromotionGateway.PromotionLinkCommand(
                "external",
                1,
                List.of("product-987654"),
                true,
                new DouyinPromotionGateway.PromotionContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "product-987654",
                        "activity-001",
                        "https://source",
                        "PRODUCT_LIBRARY",
                        "talent-1",
                        "pick-extra"
                )
        ));

        assertThat(result.pickSource()).isEqualTo("ps_7351155267604218149_654");
        assertThat(result.shortId()).isEqualTo("PS6547351");
        assertThat(result.shortLink()).isEqualTo("https://contract.short.link/PS6547351");
        assertThat(result.promoteLink()).contains("activity_id=activity-001")
                .contains("id=product-987654")
                .contains("pick_source=ps_7351155267604218149_654");
    }

    @Test
    void buildPromotionLinkResult_shouldFallbackProductAndOmitShortLink() {
        var result = provider.buildPromotionLinkResult(new DouyinPromotionGateway.PromotionLinkCommand(
                "external",
                1,
                List.of(),
                false,
                null
        ));

        assertThat(result.pickSource()).isEqualTo("ps_7351155267604218149_001");
        assertThat(result.shortLink()).isNull();
        assertThat(result.uuidSeed()).isNotBlank();
    }

    @Test
    void buildOrderListResult_shouldIncludeLatestMappingWhenAvailable() {
        when(pickSourceMappingService.findLatestActiveMapping()).thenReturn(activeMapping());

        DouyinOrderGateway.OrderListResult result = provider.buildOrderListResult(
                new DouyinOrderGateway.DouyinOrderQueryRequest(1000L, 2000L, 20, null)
        );

        assertThat(result.orders()).hasSize(3);
        assertThat(result.orders().get(0))
                .satisfies(order -> {
                    assertThat(order.externalOrderId()).isEqualTo("CONTRACT_ORD_ATTR_SHORT001");
                    assertThat(order.pickSource()).isEqualTo("pick_source_001");
                    assertThat(order.rawPayload()).containsEntry("colonel_activity_id", "20260428001");
                });
        assertThat(result.rawResponse()).containsEntry("order_count", 3)
                .containsEntry("upstream_mode", "contract");
    }

    @Test
    void buildOrderListResult_shouldStillReturnUnattributedOrdersWithoutMapping() {
        when(pickSourceMappingService.findLatestActiveMapping()).thenReturn(null);

        DouyinOrderGateway.OrderListResult result = provider.buildOrderListResult(
                new DouyinOrderGateway.DouyinOrderQueryRequest(0L, 0L, 20, null)
        );

        assertThat(result.orders()).hasSize(2);
        assertThat(result.orders()).extracting(DouyinOrderGateway.DouyinOrderItem::externalOrderId)
                .containsExactly("CONTRACT_ORD_UNATTR_1", "CONTRACT_ORD_UNATTR_2");
    }

    @Test
    void buildOrderSettlementResponse_shouldSerializeOrderRowsAndParseTimes() {
        when(pickSourceMappingService.findLatestActiveMapping()).thenReturn(activeMapping());

        Map<String, Object> response = provider.buildOrderSettlementResponse(
                " app-z ",
                null,
                "cursor-1",
                null,
                "2026-05-01 00:00:00",
                "bad-time",
                "ORDER-1,ORDER-2"
        );

        assertThat(data(response)).containsEntry("has_more", false)
                .containsEntry("next_cursor", "0")
                .containsEntry("time_type", "update")
                .containsEntry("order_ids", "ORDER-1,ORDER-2")
                .containsEntry("app_key", "app-z");
        assertThat(list(data(response).get("data"))).hasSize(3);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> response) {
        return (Map<String, Object>) response.get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private PickSourceMappingReadDTO activeMapping() {
        return new PickSourceMappingReadDTO(
                "SHORT001",
                "910001",
                "20260428001",
                "pick_source_001",
                null,
                "talent-contract",
                "契约达人");
    }
}
