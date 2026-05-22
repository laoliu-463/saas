package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.ProductApi;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealDouyinProductGatewayTest {

    @Test
    void queryActivityProducts_delegatesToContractFixtureWhenContractModeEnabled() {
        ProductApi productApi = mock(ProductApi.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinProductGateway gateway = new RealDouyinProductGateway(
                productApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
        DouyinProductGateway.ActivityProductQueryRequest request =
                new DouyinProductGateway.ActivityProductQueryRequest("app", "10001", null, null, 20, null,
                        null, null, null, 1L, null, null);
        DouyinProductGateway.ActivityProductListResult contractResult =
                new DouyinProductGateway.ActivityProductListResult(true, 10001L, 111111L, 0L, "", List.of());
        when(upstreamModeSupport.isContract()).thenReturn(true);
        when(contractFixtureProvider.buildProductListResult(request)).thenReturn(contractResult);

        assertThat(gateway.queryActivityProducts(request)).isSameAs(contractResult);
    }

    @Test
    void queryActivityProducts_mapsLiveListRowsAndNormalizesFields() {
        ProductApi productApi = mock(ProductApi.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinProductGateway gateway = new RealDouyinProductGateway(
                productApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("product_id", "9001");
        row.put("title", "Live Product");
        row.put("cover", "https://img.example/product.jpg");
        row.put("price", "1990");
        row.put("cos_ratio", 10);
        row.put("cos_fee", "199");
        row.put("activity_cos_ratio", 2500);
        row.put("cos_type", 1);
        row.put("ad_service_ratio", "8%");
        row.put("activity_ad_cos_ratio", "800");
        row.put("has_douin_goods_tag", "yes");
        row.put("in_stock", 1);
        row.put("sales", "30");
        row.put("shop_id", "56591058");
        row.put("shop_name", "Live Shop");
        row.put("shop_score", "4.90");
        row.put("status", 6);
        row.put("category_name", "Food");
        row.put("product_stock", "99");
        row.put("colonel_coupon_info", "coupon");
        row.put("activity_start_time", "2026-05-01");
        row.put("activity_end_time", "2026-05-31");
        row.put("promotion_start_time", "2026-05-02");
        row.put("promotion_end_time", "2026-05-30");
        row.put("detail_url", "https://detail.example");
        row.put("origin_colonel_buyin_id", "7351155267604218149");
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(productApi.listProductsByActivity("app", "10001", 1L, 0L, 20, "coop", 1,
                "Live", 6, 1L, "cursor", 2L))
                .thenReturn(Map.of("data", Map.of(
                        "institution_id", "111111",
                        "total", "1",
                        "next_cursor", "next-1",
                        "data", List.of(row)
                )));

        DouyinProductGateway.ActivityProductListResult result = gateway.queryActivityProducts(
                new DouyinProductGateway.ActivityProductQueryRequest("app", "10001", 1L, 0L, 20, "coop", 1,
                        "Live", 6, 1L, "cursor", 2L)
        );

        assertThat(result.test()).isFalse();
        assertThat(result.activityId()).isEqualTo(10001L);
        assertThat(result.institutionId()).isEqualTo(111111L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.nextCursor()).isEqualTo("next-1");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(9001L);
            assertThat(item.title()).isEqualTo("Live Product");
            assertThat(item.priceText()).isEqualTo("19.90");
            assertThat(item.activityCosRatioText()).isEqualTo("25.00%");
            assertThat(item.cosTypeText()).isEqualTo("双佣金");
            assertThat(item.hasDouinGoodsTag()).isTrue();
            assertThat(item.inStock()).isTrue();
            assertThat(item.statusText()).isEqualTo("合作已到期");
            assertThat(item.originColonelBuyinId()).isEqualTo("7351155267604218149");
            assertThat(item.rawPayload()).containsEntry("product_id", "9001");
        });
    }

    @Test
    void queryActivityProducts_handlesMissingDataAndInvalidNumbers() {
        ProductApi productApi = mock(ProductApi.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinProductGateway gateway = new RealDouyinProductGateway(
                productApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(productApi.listProductsByActivity(null, "bad", null, null, null, null, null,
                null, null, null, null, null))
                .thenReturn(Map.of("data", Map.of("list", List.of(Map.of(
                        "productId", "not-number",
                        "price", "bad",
                        "activity_cos_ratio", "bad",
                        "cosType", 0,
                        "status", 99,
                        "has_douin_goods_tag", "false",
                        "in_stock", "no"
                )))));

        DouyinProductGateway.ActivityProductListResult result = gateway.queryActivityProducts(
                new DouyinProductGateway.ActivityProductQueryRequest(null, "bad", null, null, null, null, null,
                        null, null, null, null, null)
        );

        assertThat(result.activityId()).isZero();
        assertThat(result.institutionId()).isZero();
        assertThat(result.total()).isNull();
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isZero();
            assertThat(item.price()).isZero();
            assertThat(item.activityCosRatio()).isZero();
            assertThat(item.cosTypeText()).isEqualTo("固定佣金");
            assertThat(item.statusText()).isEqualTo("未知状态");
            assertThat(item.hasDouinGoodsTag()).isFalse();
            assertThat(item.inStock()).isFalse();
        });
    }

    @Test
    void queryProductSkus_delegatesToContractFixtureWhenContractModeEnabled() {
        ProductApi productApi = mock(ProductApi.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinProductGateway gateway = new RealDouyinProductGateway(
                productApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
        List<DouyinProductGateway.ProductSkuResult> contractSkus =
                List.of(new DouyinProductGateway.ProductSkuResult("SKU1", "Default", 100L, 1, null));
        when(upstreamModeSupport.isContract()).thenReturn(true);
        when(contractFixtureProvider.buildProductSkus("P1")).thenReturn(contractSkus);

        assertThat(gateway.queryProductSkus("P1")).isSameAs(contractSkus);
    }

    @Test
    void queryProductSkus_mapsBuyinProductSkusV2Response() {
        ProductApi productApi = mock(ProductApi.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinProductGateway gateway = new RealDouyinProductGateway(
                productApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(productApi.getProductSkusV2("3810562766247428542"))
                .thenReturn(Map.of("data", Map.of(
                        "skus", Map.of(
                                "1791844509315072", Map.of(
                                        "sku_id", "1791844509315072",
                                        "price", 1990,
                                        "stock_num", 12,
                                        "spec_detail_name1", "原味",
                                        "spec_detail_name2", "一箱",
                                        "picture_url", "https://img.example/sku.jpg"
                                )
                        )
                )));

        List<DouyinProductGateway.ProductSkuResult> skus = gateway.queryProductSkus("3810562766247428542");

        assertThat(skus).hasSize(1);
        DouyinProductGateway.ProductSkuResult sku = skus.get(0);
        assertThat(sku.skuId()).isEqualTo("1791844509315072");
        assertThat(sku.skuName()).isEqualTo("原味 / 一箱");
        assertThat(sku.price()).isEqualTo(1990L);
        assertThat(sku.stock()).isEqualTo(12);
        assertThat(sku.cover()).isEqualTo("https://img.example/sku.jpg");
    }

    @Test
    void queryProductSkus_mapsListRowsAndFallbackValues() {
        ProductApi productApi = mock(ProductApi.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinProductGateway gateway = new RealDouyinProductGateway(
                productApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(productApi.getProductSkusV2("P1"))
                .thenReturn(Map.of("data", Map.of(
                        "sku_list", List.of(
                                Map.of("id", "SKU-A", "skuName", "Named", "skuPrice", "2500", "stock", "bad"),
                                Map.of("skuId", "SKU-B", "specName1", "Red", "specName2", "XL", "stock_count", 3)
                        )
                )));

        List<DouyinProductGateway.ProductSkuResult> skus = gateway.queryProductSkus("P1");

        assertThat(skus).hasSize(2);
        assertThat(skus.get(0).skuId()).isEqualTo("SKU-A");
        assertThat(skus.get(0).skuName()).isEqualTo("Named");
        assertThat(skus.get(0).price()).isEqualTo(2500L);
        assertThat(skus.get(0).stock()).isZero();
        assertThat(skus.get(1).skuName()).isEqualTo("Red / XL");
        assertThat(skus.get(1).price()).isZero();
        assertThat(skus.get(1).stock()).isEqualTo(3);
    }

    @Test
    void queryProductSkus_returnsEmptyWhenSkuPayloadIsMissing() {
        ProductApi productApi = mock(ProductApi.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinProductGateway gateway = new RealDouyinProductGateway(
                productApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(productApi.getProductSkusV2("P1")).thenReturn(Map.of("data", Map.of()));

        assertThat(gateway.queryProductSkus("P1")).isEmpty();
    }
}
