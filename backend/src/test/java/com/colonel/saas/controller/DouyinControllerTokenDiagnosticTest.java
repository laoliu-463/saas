package com.colonel.saas.controller;

import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.domain.shared.application.DouyinActivityDiagnosticService;
import com.colonel.saas.domain.shared.application.DouyinOrderDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.DouyinPromotionDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.DouyinProductDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.DouyinTokenDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.dto.DouyinTokenCreateProbeCommand;
import com.colonel.saas.domain.shared.application.dto.DouyinTokenCreateProbeResult;
import com.colonel.saas.domain.shared.application.dto.DouyinTokenProbeResponseView;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.service.DouyinWebhookEventService;
import com.colonel.saas.service.settlement.SettlementOrderGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinControllerTokenDiagnosticTest {

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
    void institutionInfo_shouldKeepLegacyResponseShapeAndDelegateToQueryService() {
        Map<String, Object> remoteResponse = Map.of("code", 0, "data", Map.of("institutionId", "I-1"));
        when(tokenDiagnosticQueryService.institutionInfo("app-1")).thenReturn(remoteResponse);

        ApiResult<Map<String, Object>> response = controller().jigouShenfen("app-1");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("module", "M1.2 Douyin SDK")
                .containsEntry("endpoint", "buyin.institutionInfo")
                .containsEntry("appId", "app-1")
                .containsEntry("status", "success")
                .containsEntry("remoteResponse", remoteResponse);
        verify(tokenDiagnosticQueryService).institutionInfo("app-1");
    }

    @Test
    void tokenCreateProbe_shouldKeepLegacyResponseShapeAndDelegateToQueryService() {
        DouyinTokenProbeResponseView probeResponse = new DouyinTokenProbeResponseView(
                "0", "ok", null, null, "access***", "refresh***", 7200L, "AUTH-1", "MERCHANT", 0L);
        when(tokenDiagnosticQueryService.probeCreateToken(new DouyinTokenCreateProbeCommand(
                "code-1", "authorization_code", "shop", "S1", "AUTH-1", "MERCHANT")))
                .thenReturn(new DouyinTokenCreateProbeResult(
                        "authorization_code", "present", "shop", "S1", true, "MERCHANT", probeResponse));
        DouyinController.TokenCreateRequest request = new DouyinController.TokenCreateRequest();
        request.setAppId("app-1");
        request.setCode("code-1");
        request.setGrantType("authorization_code");
        request.setTestShop("shop");
        request.setShopId("S1");
        request.setAuthId("AUTH-1");
        request.setAuthSubjectType("MERCHANT");

        ApiResult<Map<String, Object>> response = controller().tokenCreateProbe(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("module", "M1.3 Real SDK Probe")
                .containsEntry("endpoint", "token.create")
                .containsEntry("appId", "app-1")
                .containsEntry("status", "completed")
                .containsEntry("response", probeResponse);
        assertThat(response.getData().get("requestSnapshot"))
                .isEqualTo(Map.of(
                        "grantType", "authorization_code",
                        "codeState", "present",
                        "testShop", "shop",
                        "shopId", "S1",
                        "authIdPresent", true,
                        "authSubjectType", "MERCHANT"));
        verify(tokenDiagnosticQueryService).probeCreateToken(new DouyinTokenCreateProbeCommand(
                "code-1", "authorization_code", "shop", "S1", "AUTH-1", "MERCHANT"));
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
