package com.colonel.saas.controller;

import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.domain.shared.application.DouyinActivityDiagnosticService;
import com.colonel.saas.domain.shared.application.DouyinOrderDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.DouyinPromotionDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.DouyinProductDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.DouyinTokenDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.dto.DouyinActivityProductProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinOrderRawProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinProductSkuView;
import com.colonel.saas.domain.shared.application.dto.DouyinPromotionRawProbeCommand;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.service.DouyinWebhookEventService;
import com.colonel.saas.service.settlement.SettlementOrderGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DouyinControllerProductDiagnosticTest {

    private final DouyinActivityDiagnosticService activityDiagnosticService =
            mock(DouyinActivityDiagnosticService.class);
    private final DouyinProductDiagnosticQueryService productDiagnosticQueryService =
            mock(DouyinProductDiagnosticQueryService.class);
    private final DouyinOrderDiagnosticQueryService orderDiagnosticQueryService =
            mock(DouyinOrderDiagnosticQueryService.class);
    private final SettlementOrderGateway settlementOrderGateway = mock(SettlementOrderGateway.class);
    private final DouyinPromotionDiagnosticQueryService promotionDiagnosticQueryService =
            mock(DouyinPromotionDiagnosticQueryService.class);
    private final DouyinTokenDiagnosticQueryService tokenDiagnosticQueryService =
            mock(DouyinTokenDiagnosticQueryService.class);
    private final DouyinTokenService tokenService = mock(DouyinTokenService.class);
    private final DouyinWebhookEventService webhookEventService = mock(DouyinWebhookEventService.class);

    @Test
    void activityProductList_shouldKeepLegacyResponseShapeAndDelegateToQueryService() {
        Map<String, Object> remoteResponse = Map.of(
                "total", 1L,
                "items", List.of(Map.of("productId", 1001L)),
                "nextCursor", "next-1");
        when(productDiagnosticQueryService.activityProducts(
                new DouyinActivityProductProbeQuery("app-1", "ACT-1", 20, "cursor-1")))
                .thenReturn(remoteResponse);

        ApiResult<Map<String, Object>> response = controller()
                .shangpinLiebiao("app-1", "ACT-1", 20, "cursor-1");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("module", "M1.2 Douyin SDK")
                .containsEntry("endpoint", "alliance.colonelActivityProduct")
                .containsEntry("appId", "app-1")
                .containsEntry("activityId", "ACT-1")
                .containsEntry("status", "success")
                .containsEntry("remoteResponse", remoteResponse);
        verify(productDiagnosticQueryService)
                .activityProducts(new DouyinActivityProductProbeQuery("app-1", "ACT-1", 20, "cursor-1"));
    }

    @Test
    void rawPromotionProductSkus_shouldKeepLegacyResponseShapeAndDelegateToQueryService() {
        List<DouyinProductSkuView> skus = List.of(
                new DouyinProductSkuView("sku-1", "red", 100L, 2, "cover-url"));
        when(productDiagnosticQueryService.productSkus("P-1")).thenReturn(skus);
        Map<String, Object> request = Map.of(
                "appId", "app-1",
                "method", "buyin.productSkus.v2",
                "product_id", "P-1");

        ApiResult<Map<String, Object>> response = controller().tuiguangLianjieYuanshi(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("module", "M1.3 Promotion Raw Probe")
                .containsEntry("endpoint", "buyin.productSkus.v2")
                .containsEntry("appId", "app-1")
                .containsEntry("status", "success")
                .containsEntry("remoteResponse", Map.of("data", Map.of("skus", skus)));
        verify(productDiagnosticQueryService).productSkus("P-1");
        verifyNoInteractions(promotionDiagnosticQueryService);
    }

    @Test
    void rawPromotionProbe_shouldKeepLegacyResponseShapeAndDelegateToQueryService() {
        Map<String, Object> payload = Map.of(
                "product_url", "https://item.example/1",
                "pick_extra", "channel-demo");
        Map<String, Object> remoteResponse = Map.of("code", 0, "data", Map.of("pick_source", "PS-1"));
        when(promotionDiagnosticQueryService.rawUpstreamPost(
                new DouyinPromotionRawProbeCommand("app-1", "buyin.instPickSourceConvert", payload)))
                .thenReturn(remoteResponse);
        Map<String, Object> request = Map.of(
                "appId", "app-1",
                "method", "buyin.instPickSourceConvert",
                "product_url", "https://item.example/1",
                "pick_extra", "channel-demo");

        ApiResult<Map<String, Object>> response = controller().tuiguangLianjieYuanshi(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("module", "M1.3 Promotion Raw Probe")
                .containsEntry("endpoint", "buyin.instPickSourceConvert")
                .containsEntry("appId", "app-1")
                .containsEntry("payload", payload)
                .containsEntry("status", "success")
                .containsEntry("remoteResponse", remoteResponse);
        verify(promotionDiagnosticQueryService)
                .rawUpstreamPost(new DouyinPromotionRawProbeCommand("app-1", "buyin.instPickSourceConvert", payload));
    }

    @Test
    void rawOrderProbe_shouldKeepLegacyResponseShapeAndDelegateToQueryService() {
        Map<String, Object> request = Map.of(
                "appId", "app-1",
                "start_time", 1711900800L,
                "end_time", 1711987200L,
                "count", 50,
                "cursor", "cursor-1");
        Map<String, Object> remoteResponse = Map.of(
                "data", Map.of("orders", List.of(Map.of("order_id", "O-1"))));
        when(orderDiagnosticQueryService.instituteOrdersRawResponse(
                new DouyinOrderRawProbeQuery(1711900800L, 1711987200L, 50, "cursor-1")))
                .thenReturn(remoteResponse);

        ApiResult<Map<String, Object>> response = controller().dingdanTongbuYuanshi(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("module", "M1.4 Order Raw Probe")
                .containsEntry("endpoint", "buyin.instituteOrderColonel")
                .containsEntry("appId", "app-1")
                .containsEntry("payload", request)
                .containsEntry("status", "success")
                .containsEntry("remoteResponse", remoteResponse);
        verify(orderDiagnosticQueryService).instituteOrdersRawResponse(
                new DouyinOrderRawProbeQuery(1711900800L, 1711987200L, 50, "cursor-1"));
    }

    private DouyinController controller() {
        return new DouyinController(
                activityDiagnosticService,
                productDiagnosticQueryService,
                orderDiagnosticQueryService,
                settlementOrderGateway,
                promotionDiagnosticQueryService,
                tokenDiagnosticQueryService,
                tokenService,
                webhookEventService);
    }
}
