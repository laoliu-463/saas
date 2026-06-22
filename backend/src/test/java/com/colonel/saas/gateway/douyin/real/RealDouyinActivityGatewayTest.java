package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.douyin.api.ProductApi;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealDouyinActivityGatewayTest {

    private ActivityApi activityApi;
    private ProductApi productApi;
    private DouyinUpstreamModeSupport upstreamModeSupport;
    private DouyinContractFixtureProvider contractFixtureProvider;
    private RealDouyinActivityGateway gateway;

    @BeforeEach
    void setUp() {
        activityApi = mock(ActivityApi.class);
        productApi = mock(ProductApi.class);
        upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        when(upstreamModeSupport.value()).thenReturn("real");
        when(contractFixtureProvider.appKey()).thenReturn("7623665273727387199");
        when(contractFixtureProvider.shopId()).thenReturn("56591058");
        when(contractFixtureProvider.authId()).thenReturn("7351155267604218149");
        gateway = new RealDouyinActivityGateway(
                activityApi,
                productApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
    }

    @Test
    void listActivities_shouldDelegateToContractFixtureInContractMode() {
        var query = new DouyinActivityGateway.ActivityListQuery("app", 5, 0L, 1L, 1L, 20L, "精选");
        var expected = new DouyinActivityGateway.ActivityListResult(false, 1L, 0L, List.of());
        when(upstreamModeSupport.isContract()).thenReturn(true);
        when(contractFixtureProvider.buildActivityListResult(query)).thenReturn(expected);

        assertThat(gateway.listActivities(query)).isSameAs(expected);
    }

    @Test
    void listActivityProducts_shouldDelegateToContractFixtureInContractMode() {
        var query = new DouyinActivityGateway.ActivityProductListQuery("app", "20260428001", 0L, 1L, 20, null, null, null, null, 1L, null, null);
        var expected = new DouyinActivityGateway.ActivityProductListResult(false, 20260428001L, 1L, null, "", List.of());
        when(upstreamModeSupport.isContract()).thenReturn(true);
        when(contractFixtureProvider.buildActivityProductListResult(query)).thenReturn(expected);

        assertThat(gateway.listActivityProducts(query)).isSameAs(expected);
    }

    @Test
    void activityDetail_shouldDelegateToContractFixtureInContractMode() {
        when(upstreamModeSupport.isContract()).thenReturn(true);
        when(contractFixtureProvider.buildActivityDetailResponse("app", "20260428001"))
                .thenReturn(Map.of("data", Map.of("activity_id", 20260428001L)));

        assertThat(gateway.activityDetail("app", "20260428001"))
                .containsEntry("data", Map.of("activity_id", 20260428001L));
    }

    @Test
    void listActivities_shouldNormalizeDataActivityListRows() {
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(activityApi.listActivities("app-long-key-1234", null, null, null, null, null, null))
                .thenReturn(Map.of("data", Map.of(
                        "institution_id", "7351155267604218149",
                        "total", "8",
                        "activity_list", List.of(
                                activityRow(1, "未上线活动"),
                                activityRow(2, "报名未开始活动"),
                                activityRow(3, "报名中活动"),
                                activityRow(4, "推广未开始活动"),
                                activityRow(5, "推广中活动"),
                                activityRow(7, "报名结束活动"),
                                activityRow(99, "未知状态活动"),
                                Map.of("ignored", "not activity")
                        )
                )));

        var result = gateway.listActivities(new DouyinActivityGateway.ActivityListQuery(
                "app-long-key-1234",
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(result.institutionId()).isEqualTo(7351155267604218149L);
        assertThat(result.total()).isEqualTo(8L);
        assertThat(result.activityList()).extracting(DouyinActivityGateway.ActivityItem::statusText)
                .containsExactly("未上线", "报名未开始", "报名中", "推广未开始", "推广中", "报名结束", "任意状态", "任意状态");
        assertThat(result.activityList().get(0).categoriesLimit()).isEqualTo("美妆");
    }

    @Test
    void listActivities_shouldFallbackToDataArrayThenRemoteArrayAndListSizeTotal() {
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(activityApi.listActivities("app", 5, 1L, 0L, 1L, 20L, "keyword"))
                .thenReturn(Map.of("data", Map.of("data", List.of(activityRow(5, "data数组活动")))))
                .thenReturn(Map.of("activity_list", List.of(activityRow(4, "顶层活动"))));

        var dataResult = gateway.listActivities(new DouyinActivityGateway.ActivityListQuery("app", 5, 1L, 0L, 1L, 20L, "keyword"));
        var topLevelResult = gateway.listActivities(new DouyinActivityGateway.ActivityListQuery("app", 5, 1L, 0L, 1L, 20L, "keyword"));

        assertThat(dataResult.total()).isEqualTo(1L);
        assertThat(dataResult.activityList()).singleElement()
                .extracting(DouyinActivityGateway.ActivityItem::activityName)
                .isEqualTo("data数组活动");
        assertThat(topLevelResult.total()).isEqualTo(1L);
        assertThat(topLevelResult.activityList()).singleElement()
                .extracting(DouyinActivityGateway.ActivityItem::activityName)
                .isEqualTo("顶层活动");
    }

    @Test
    void listActivityProducts_shouldNormalizeRowsAndStatusTextVariants() {
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(productApi.listProductsByActivity(eq("app"), eq("bad-activity"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("data", Map.of(
                        "institution_id", 7351155267604218149L,
                        "next_cursor", 7,
                        "data", List.of(
                                productRow(0, 1, true, "true", 1001L),
                                productRow(1, 0, false, 1, 1002L),
                                productRow(2, 1, true, "yes", 1003L),
                                productRow(3, 0, false, 0, 1004L),
                                productRow(4, 1, true, "false", 1005L),
                                productRow(6, 0, false, null, 1006L),
                                productRow(99, 0, false, "no", 1007L)
                        )
                )));

        var result = gateway.listActivityProducts(new DouyinActivityGateway.ActivityProductListQuery(
                "app",
                "bad-activity",
                0L,
                1L,
                20,
                null,
                null,
                null,
                null,
                1L,
                null,
                null
        ));

        assertThat(result.activityId()).isZero();
        assertThat(result.institutionId()).isEqualTo(7351155267604218149L);
        assertThat(result.total()).isNull();
        assertThat(result.nextCursor()).isEqualTo("7");
        assertThat(result.items()).extracting(DouyinActivityGateway.ActivityProductItem::statusText)
                .containsExactly("待审核", "推广中", "申请未通过", "合作已终止", "合作已终止", "合作已到期", "未知状态");
        assertThat(result.items()).extracting(DouyinActivityGateway.ActivityProductItem::status)
                .containsExactly(0, 1, 2, 3, 3, 6, 99);
        assertThat(result.items()).extracting(DouyinActivityGateway.ActivityProductItem::cosTypeText)
                .contains("双佣金", "固定佣金");
        assertThat(result.items()).extracting(DouyinActivityGateway.ActivityProductItem::inStock)
                .contains(true, false);
        assertThat(result.items().get(0).priceText()).isEqualTo("12.34");
        assertThat(result.items().get(0).activityCosRatioText()).isEqualTo("20.00%");
        assertThat(result.items().get(0).toMap()).containsEntry("originColonelBuyinId", "7293293346398011698");
    }

    @Test
    void listActivityProducts_shouldReadRowsFromListFallbackAndTotal() {
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(productApi.listProductsByActivity(eq("app"), eq("20260428001"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("data", Map.of(
                        "total", "1",
                        "list", List.of(productRow(1, 1, true, true, 2001L))
                )));

        var result = gateway.listActivityProducts(new DouyinActivityGateway.ActivityProductListQuery(
                "app",
                "20260428001",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(result.activityId()).isEqualTo(20260428001L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void createAndCancel_shouldForwardToActivityApi() {
        ActivityApi.ActivityCreateOrUpdateCommand command = apiCommand("app");
        Map<String, Object> payload = Map.of("product_id", 910001L);
        when(activityApi.createOrUpdate(command)).thenReturn(Map.of("ok", true));
        when(activityApi.cancelActivityProduct("app", payload)).thenReturn(Map.of("cancelled", true));

        assertThat(gateway.createOrUpdate(command)).containsEntry("ok", true);
        assertThat(gateway.cancelActivityProduct("app", payload)).containsEntry("cancelled", true);
    }

    @Test
    void createOrUpdateActivity_shouldTranslateGatewayCommandToApiCommand() {
        var command = new DouyinActivityGateway.ActivityMutateCommand(
                "app",
                20260428001L,
                true,
                false,
                "旗舰店",
                "测试活动",
                "测试描述",
                "2026-05-01 00:00:00",
                "2026-05-02 00:00:00",
                "20",
                "10",
                "wx",
                "13800000000",
                "100",
                2,
                "56591058",
                true,
                "美妆",
                90,
                30,
                0,
                7,
                "5",
                "3",
                1
        );
        when(activityApi.createOrUpdate(any())).thenReturn(Map.of("ok", true));

        assertThat(gateway.createOrUpdateActivity(command)).containsEntry("ok", true);

        ArgumentCaptor<ActivityApi.ActivityCreateOrUpdateCommand> captor =
                ArgumentCaptor.forClass(ActivityApi.ActivityCreateOrUpdateCommand.class);
        verify(activityApi).createOrUpdate(captor.capture());
        assertThat(captor.getValue().activityName()).isEqualTo("测试活动");
        assertThat(captor.getValue().specifiedShopIds()).isEqualTo("56591058");
        assertThat(captor.getValue().cosLimitType()).isEqualTo(1);
    }

    @Test
    void activityDetail_shouldCallRealApiOutsideContractMode() {
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(activityApi.detail("app", "20260428001")).thenReturn(Map.of("data", Map.of("activity_id", 20260428001L)));

        assertThat(gateway.activityDetail("app", "20260428001"))
                .containsEntry("data", Map.of("activity_id", 20260428001L));
    }

    private Map<String, Object> activityRow(int status, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("activity_id", String.valueOf(20260428000L + status));
        row.put("activity_name", name);
        row.put("activity_start_time", "2026-05-01 00:00:00");
        row.put("activity_end_time", "2026-06-01 00:00:00");
        row.put("status", status);
        row.put("application_start_time", "2026-04-25 00:00:00");
        row.put("application_end_time", "2026-04-30 00:00:00");
        row.put("categories_limit", "美妆");
        row.put("colonel_buyin_id", "7351155267604218149");
        return row;
    }

    private Map<String, Object> productRow(int status, int cosType, boolean hasTag, Object inStock, long productId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("product_id", String.valueOf(productId));
        row.put("title", "商品-" + productId);
        row.put("cover", "https://cdn.example/" + productId + ".png");
        row.put("price", "1234");
        row.put("cos_ratio", "2000");
        row.put("cos_fee", 246L);
        row.put("activity_cos_ratio", "2000");
        row.put("cos_type", cosType);
        row.put("ad_service_ratio", "10");
        row.put("activity_ad_cos_ratio", "800");
        row.put("has_douin_goods_tag", hasTag);
        row.put("in_stock", inStock);
        row.put("sales", "99");
        row.put("shop_id", "56591058");
        row.put("shop_name", "测试店");
        row.put("shop_score", "4.9");
        row.put("status", status);
        row.put("category_name", "美妆");
        row.put("product_stock", "88");
        row.put("colonel_coupon_info", "满99减10");
        row.put("activity_start_time", "2026-05-01");
        row.put("activity_end_time", "2026-06-01");
        row.put("promotion_start_time", "2026-05-01");
        row.put("promotion_end_time", "2026-06-01");
        row.put("detail_url", "https://detail.example/" + productId);
        row.put("origin_colonel_buyin_id", "7293293346398011698");
        return row;
    }

    private ActivityApi.ActivityCreateOrUpdateCommand apiCommand(String appId) {
        return new ActivityApi.ActivityCreateOrUpdateCommand(
                appId,
                20260428001L,
                false,
                null,
                null,
                "测试活动",
                "测试描述",
                "2026-05-01 00:00:00",
                "2026-05-02 00:00:00",
                "20",
                "10",
                null,
                null,
                "100",
                1,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
