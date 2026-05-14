package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.ProductApi;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealDouyinProductGatewayTest {

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
}
