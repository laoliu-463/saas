package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.api.PromotionApi;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RealDouyinPromotionGatewayTest {

    @Test
    void generateLink_shouldRejectLivePromotionWriteWhenNotEnabled() {
        PromotionApi promotionApi = mock(PromotionApi.class);
        DouyinApiClient douyinApiClient = mock(DouyinApiClient.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinPromotionGateway gateway = new RealDouyinPromotionGateway(
                promotionApi,
                douyinApiClient,
                upstreamModeSupport,
                contractFixtureProvider,
                false
        );
        when(upstreamModeSupport.isContract()).thenReturn(false);

        assertThatThrownBy(() -> gateway.generateLink(command()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("真实抖店推广写操作未开启");

        verifyNoInteractions(promotionApi);
    }

    @Test
    void generateLink_shouldCallPromotionApiWhenWriteEnabled() {
        PromotionApi promotionApi = mock(PromotionApi.class);
        DouyinApiClient douyinApiClient = mock(DouyinApiClient.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinPromotionGateway gateway = new RealDouyinPromotionGateway(
                promotionApi,
                douyinApiClient,
                upstreamModeSupport,
                contractFixtureProvider,
                true
        );
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(promotionApi.generateLink(eq("ext-1"), eq(4), eq(List.of("product-1")), eq(true), any()))
                .thenReturn(new PromotionApi.PromotionLinkResult(
                        "ps_001",
                        "channel_001",
                        "SHORT001",
                        "https://short.example/SHORT001",
                        "https://haohuo.example/item?pick_source=ps_001",
                        "uuid-seed"
                ));

        DouyinPromotionGateway.PromotionLinkResult result = gateway.generateLink(command());

        assertThat(result.pickSource()).isEqualTo("ps_001");
        assertThat(result.pickExtra()).isEqualTo("channel_001");
        verify(promotionApi).generateLink(eq("ext-1"), eq(4), eq(List.of("product-1")), eq(true), any());
    }

    @Test
    void rawUpstreamPost_shouldRejectInstPickSourceConvertWhenWriteDisabled() {
        PromotionApi promotionApi = mock(PromotionApi.class);
        DouyinApiClient douyinApiClient = mock(DouyinApiClient.class);
        DouyinUpstreamModeSupport upstreamModeSupport = mock(DouyinUpstreamModeSupport.class);
        DouyinContractFixtureProvider contractFixtureProvider = mock(DouyinContractFixtureProvider.class);
        RealDouyinPromotionGateway gateway = new RealDouyinPromotionGateway(
                promotionApi,
                douyinApiClient,
                upstreamModeSupport,
                contractFixtureProvider,
                false
        );
        when(upstreamModeSupport.isContract()).thenReturn(false);

        assertThatThrownBy(() -> gateway.rawUpstreamPost(
                "app-1",
                "buyin.instPickSourceConvert",
                Map.of("product_url", "https://example/item")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("真实抖店推广写操作未开启");

        verifyNoInteractions(douyinApiClient);
    }

    private DouyinPromotionGateway.PromotionLinkCommand command() {
        return new DouyinPromotionGateway.PromotionLinkCommand(
                "ext-1",
                4,
                List.of("product-1"),
                true,
                new DouyinPromotionGateway.PromotionContext(
                        null,
                        null,
                        "product-1",
                        "activity-1",
                        "https://haohuo.example/item",
                        "PRODUCT_LIBRARY",
                        null,
                        "channel_001"
                )
        );
    }
}
