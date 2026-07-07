package com.colonel.saas.controller;

import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.domain.shared.application.DouyinActivityDiagnosticService;
import com.colonel.saas.domain.shared.application.DouyinOrderDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.DouyinPromotionDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.DouyinProductDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.DouyinTokenDiagnosticQueryService;
import com.colonel.saas.domain.shared.application.dto.DouyinActivityListProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinActivityMutateProbeCommand;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.service.DouyinWebhookEventService;
import com.colonel.saas.service.settlement.SettlementOrderGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinControllerActivityDiagnosticTest {

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
    void activityList_shouldKeepLegacyResponseShapeAndDelegateToApplicationService() {
        Map<String, Object> remoteResponse = Map.of("total", 1L, "activityList", List.of(Map.of("activityId", 1001L)));
        DouyinActivityListProbeQuery expectedQuery =
                new DouyinActivityListProbeQuery("app-1", 0, 0L, 1L, 1L, 20L, null);
        when(activityDiagnosticService.listActivities(expectedQuery)).thenReturn(remoteResponse);

        ApiResult<Map<String, Object>> response = controller().huodongLiebiao("app-1");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("module", "M1.2 Douyin SDK")
                .containsEntry("endpoint", "alliance.instituteColonelActivityList")
                .containsEntry("appId", "app-1")
                .containsEntry("status", "success")
                .containsEntry("remoteResponse", remoteResponse);
        verify(activityDiagnosticService).listActivities(expectedQuery);
    }

    @Test
    void activityDetail_shouldKeepLegacyResponseShapeAndDelegateToApplicationService() {
        Map<String, Object> remoteResponse = Map.of("activity_id", 1001L);
        when(activityDiagnosticService.activityDetail("app-1", "1001")).thenReturn(remoteResponse);

        ApiResult<Map<String, Object>> response = controller().huodongXiangqing("app-1", "1001");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("module", "M1.2 Douyin SDK")
                .containsEntry("endpoint", "buyin.colonelActivityDetail")
                .containsEntry("appId", "app-1")
                .containsEntry("activityId", "1001")
                .containsEntry("status", "success")
                .containsEntry("remoteResponse", remoteResponse);
        verify(activityDiagnosticService).activityDetail("app-1", "1001");
    }

    @Test
    void activityProducts_shouldKeepLegacyResponseShapeAndDelegateToApplicationService() {
        Map<String, Object> remoteResponse = Map.of("total", 1L);
        DouyinActivityListProbeQuery expectedQuery =
                new DouyinActivityListProbeQuery("app-1", 2, 3L, 4L, 5L, 6L, "keyword");
        when(activityDiagnosticService.listActivities(expectedQuery)).thenReturn(remoteResponse);

        ApiResult<Map<String, Object>> response =
                controller().huodongShangpin("app-1", 2, 3L, 4L, 5L, 6L, "keyword");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("module", "M1.2 Douyin SDK")
                .containsEntry("endpoint", "alliance.instituteColonelActivityList")
                .containsEntry("appId", "app-1")
                .containsEntry("status", "success")
                .containsEntry("remoteResponse", remoteResponse);
        verify(activityDiagnosticService).listActivities(expectedQuery);
    }

    @Test
    void cancelActivityProduct_shouldKeepPayloadAndDelegateToApplicationService() {
        DouyinController.ActivityProductCancelRequest request = new DouyinController.ActivityProductCancelRequest();
        request.setAppId("app-1");
        request.setActivityId(1001L);
        request.setProductIds(List.of("P-1"));
        request.setReason("  stop  ");
        Map<String, Object> remoteResponse = Map.of("success", true);
        Map<String, Object> expectedPayload = Map.of(
                "activity_id", 1001L,
                "product_ids", List.of("P-1"),
                "products", List.of(Map.of("product_id", "P-1")),
                "reason", "stop");
        when(activityDiagnosticService.cancelActivityProduct("app-1", expectedPayload)).thenReturn(remoteResponse);

        ApiResult<Map<String, Object>> response = controller().quxiaoHuodongShangpin(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("module", "M1.2 Douyin SDK")
                .containsEntry("endpoint", "alliance.colonelActivityProductCancel")
                .containsEntry("appId", "app-1")
                .containsEntry("payload", expectedPayload)
                .containsEntry("status", "success")
                .containsEntry("remoteResponse", remoteResponse);
        verify(activityDiagnosticService).cancelActivityProduct("app-1", expectedPayload);
    }

    @Test
    void createActivity_shouldMapRequestToApplicationCommandAndKeepResponseShape() {
        DouyinController.ActivityCreateOrUpdateRequest request = activityRequest();
        Map<String, Object> remoteResponse = Map.of("code", 0);
        DouyinActivityMutateProbeCommand expectedCommand = activityCommand(null);
        when(activityDiagnosticService.createOrUpdateActivity(expectedCommand)).thenReturn(remoteResponse);

        ApiResult<Map<String, Object>> response = controller().chuangjianHuodong(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(remoteResponse);
        verify(activityDiagnosticService).createOrUpdateActivity(expectedCommand);
    }

    @Test
    void updateActivity_shouldUsePathActivityIdAndKeepResponseShape() {
        DouyinController.ActivityCreateOrUpdateRequest request = activityRequest();
        request.setActivityId(999L);
        Map<String, Object> remoteResponse = Map.of("code", 0);
        DouyinActivityMutateProbeCommand expectedCommand = activityCommand(1001L);
        when(activityDiagnosticService.createOrUpdateActivity(expectedCommand)).thenReturn(remoteResponse);

        ApiResult<Map<String, Object>> response = controller().gengxinHuodong(1001L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(remoteResponse);
        verify(activityDiagnosticService).createOrUpdateActivity(expectedCommand);
    }

    private DouyinController.ActivityCreateOrUpdateRequest activityRequest() {
        DouyinController.ActivityCreateOrUpdateRequest request = new DouyinController.ActivityCreateOrUpdateRequest();
        request.setAppId("app-1");
        request.setApplicationLimited(false);
        request.setIsNewShop(true);
        request.setShopType("normal");
        request.setActivityName("activity");
        request.setActivityDesc("desc");
        request.setApplyStartTime("2026-07-01 00:00:00");
        request.setApplyEndTime("2026-07-02 00:00:00");
        request.setCommissionRate("10");
        request.setServiceRate("5");
        request.setWechatId("wechat");
        request.setPhoneNum("13800000000");
        request.setEstimatedSingleSale("1000");
        request.setActivityType(1);
        request.setSpecifiedShopIds("S1");
        request.setOnline(true);
        request.setCategories("cat");
        request.setShopScore(90);
        request.setMinPromotionDays(7);
        request.setThresholdCrossBorder(0);
        request.setMinExclusionDuration(0);
        request.setAdCommissionRate("0");
        request.setAdServiceRate("0");
        request.setCosLimitType(0);
        return request;
    }

    private DouyinActivityMutateProbeCommand activityCommand(Long activityId) {
        return new DouyinActivityMutateProbeCommand(
                "app-1",
                activityId,
                false,
                true,
                "normal",
                "activity",
                "desc",
                "2026-07-01 00:00:00",
                "2026-07-02 00:00:00",
                "10",
                "5",
                "wechat",
                "13800000000",
                "1000",
                1,
                "S1",
                true,
                "cat",
                90,
                7,
                0,
                0,
                "0",
                "0",
                0);
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
