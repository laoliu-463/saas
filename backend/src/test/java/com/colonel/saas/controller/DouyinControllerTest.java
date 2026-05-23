package com.colonel.saas.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import com.colonel.saas.service.DouyinWebhookEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DouyinControllerTest {

    @Mock
    private DouyinActivityGateway douyinActivityGateway;
    @Mock
    private DouyinProductGateway douyinProductGateway;
    @Mock
    private DouyinOrderGateway douyinOrderGateway;
    @Mock
    private DouyinPromotionGateway douyinPromotionGateway;
    @Mock
    private DouyinTokenGateway douyinTokenGateway;
    @Mock
    private DouyinTokenService douyinTokenService;
    @Mock
    private DouyinWebhookEventService douyinWebhookEventService;

    private DouyinController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private Logger controllerLogger;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        controller = new DouyinController(
                douyinActivityGateway,
                douyinProductGateway,
                douyinOrderGateway,
                douyinPromotionGateway,
                douyinTokenGateway,
                douyinTokenService,
                douyinWebhookEventService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        controllerLogger = (Logger) LoggerFactory.getLogger(DouyinController.class);
        originalLevel = controllerLogger.getLevel();
        controllerLogger.setLevel(Level.OFF);
    }

    @Test
    void replayWebhookEvents_shouldReturnReplaySummary() throws Exception {
        when(douyinWebhookEventService.replayUnfinished(20))
                .thenReturn(new DouyinWebhookEventService.ReplayResult(3, 2, 1));

        mockMvc.perform(post("/douyin/webhook-events/replay")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.scanned").value(3))
                .andExpect(jsonPath("$.data.consumed").value(2))
                .andExpect(jsonPath("$.data.failed").value(1));

        verify(douyinWebhookEventService).replayUnfinished(20);
    }

    @AfterEach
    void restoreLogger() {
        controllerLogger.setLevel(originalLevel);
    }

    @Test
    void huodongLiebiao_success_returnsSuccessResult() {
        when(douyinActivityGateway.listActivities(any()))
                .thenReturn(new DouyinActivityGateway.ActivityListResult(false, 0L, 0L, List.of()));

        ApiResult<Map<String, Object>> result = controller.huodongLiebiao("test_app");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("module", "M1.2 Douyin SDK");
        assertThat(result.getData()).containsEntry("endpoint", "alliance.instituteColonelActivityList");
        assertThat(result.getData()).containsEntry("appId", "test_app");
        assertThat(result.getData()).containsEntry("status", "success");
    }

    @Test
    void huodongLiebiao_failure_returnsErrorFields() {
        when(douyinActivityGateway.listActivities(any()))
                .thenThrow(new DouyinApiException(10012, "INVALID_TOKEN", "sub_123", "log_abc", "/list"));

        ApiResult<Map<String, Object>> result = controller.huodongLiebiao(null);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("status", "failed");
        assertThat(result.getData()).containsEntry("message", "INVALID_TOKEN");
        assertThat(result.getData()).containsEntry("errorCode", 10012);
        assertThat(result.getData()).containsEntry("subCode", "sub_123");
        assertThat(result.getData()).containsEntry("logId", "log_abc");
        assertThat(result.getData()).containsEntry("failedEndpoint", "/list");
    }

    @Test
    void huodongXiangqing_success_returnsSuccessResult() {
        when(douyinActivityGateway.activityDetail("test_app", "54321")).thenReturn(Map.of("detail", Map.of()));

        ApiResult<Map<String, Object>> result = controller.huodongXiangqing("test_app", "54321");

        assertThat(result.getData()).containsEntry("endpoint", "buyin.colonelActivityDetail");
        assertThat(result.getData()).containsEntry("activityId", "54321");
        assertThat(result.getData()).containsEntry("status", "success");
    }

    @Test
    void huodongXiangqing_failure_returnsGenericErrorFields() {
        when(douyinActivityGateway.activityDetail("test_app", "54321"))
                .thenThrow(new BusinessException("activity missing"));

        ApiResult<Map<String, Object>> result = controller.huodongXiangqing("test_app", "54321");

        assertThat(result.getData()).containsEntry("status", "failed");
        assertThat(result.getData()).containsEntry("message", "activity missing");
        assertThat(result.getData()).containsEntry("errorType", "BusinessException");
    }

    @Test
    void huodongXiangqing_standardRestPath_bindsRequestParams() throws Exception {
        when(douyinActivityGateway.activityDetail("test_app", "54321")).thenReturn(Map.of("detail", Map.of()));

        mockMvc.perform(get("/douyin/activities/54321")
                        .queryParam("appId", "test_app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value("54321"))
                .andExpect(jsonPath("$.data.endpoint").value("buyin.colonelActivityDetail"));

        verify(douyinActivityGateway).activityDetail("test_app", "54321");
    }

    @Test
    void huodongShangpin_success_returnsSuccessResult() {
        when(douyinActivityGateway.listActivities(any()))
                .thenReturn(new DouyinActivityGateway.ActivityListResult(false, 0L, 0L, List.of()));

        ApiResult<Map<String, Object>> result =
                controller.huodongShangpin("test_app", 1, 2L, 3L, 4L, 20L, "keyword");

        assertThat(result.getData()).containsEntry("endpoint", "alliance.instituteColonelActivityList");
        assertThat(result.getData()).containsEntry("status", "success");
    }

    @Test
    void huodongShangpin_failure_returnsErrorStatus() {
        when(douyinActivityGateway.listActivities(any()))
                .thenThrow(new IllegalStateException("upstream not ready"));

        ApiResult<Map<String, Object>> result =
                controller.huodongShangpin("test_app", 1, 2L, 3L, 4L, 20L, "keyword");

        assertThat(result.getData()).containsEntry("status", "failed");
        assertThat(result.getData()).containsEntry("message", "upstream not ready");
        assertThat(result.getData()).containsEntry("errorType", "IllegalStateException");
    }

    @Test
    void shangpinLiebiao_success_returnsSuccessResult() {
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(false, 0L, 0L, 0L, null, List.of()));

        ApiResult<Map<String, Object>> result =
                controller.shangpinLiebiao("test_app", "54321", 10, "cursor-1");

        assertThat(result.getData()).containsEntry("endpoint", "alliance.colonelActivityProduct");
        assertThat(result.getData()).containsEntry("activityId", "54321");
        assertThat(result.getData()).containsEntry("status", "success");
    }

    @Test
    void shangpinLiebiao_failure_returnsErrorStatus() {
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenThrow(new IllegalArgumentException("activityId invalid"));

        ApiResult<Map<String, Object>> result =
                controller.shangpinLiebiao("test_app", "bad", 10, "cursor-1");

        assertThat(result.getData()).containsEntry("status", "failed");
        assertThat(result.getData()).containsEntry("message", "activityId invalid");
    }

    @Test
    void dingdanJiesuan_success_returnsQueryContext() {
        when(douyinOrderGateway.listSettlement(any()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(
                        List.of(), false, "1", Map.of("data", Map.of("cursor", "1"))));

        ApiResult<Map<String, Object>> result = controller.dingdanJiesuan(
                "test_app", 20, "0", "update", "2026-04-01 00:00:00", "2026-04-02 00:00:00", null, null);

        assertThat(result.getData()).containsEntry("endpoint", "buyin.colonelMultiSettlementOrders");
        assertThat(result.getData()).containsEntry("status", "success");
        assertThat(result.getData()).containsKey("query");
    }

    @Test
    void dingdanJiesuan_failure_returnsErrorStatus() {
        when(douyinOrderGateway.listSettlement(any()))
                .thenThrow(new DouyinApiException(
                        40004,
                        "PARAM_INVALID",
                        "isv.parameter-invalid:1033",
                        "log_order_1",
                        "buyin.colonelMultiSettlementOrders"));

        ApiResult<Map<String, Object>> result = controller.dingdanJiesuan(
                null,
                20,
                "0",
                "update",
                "2026-04-01 00:00:00",
                "2026-04-02 00:00:00",
                null,
                null);

        assertThat(result.getData()).containsEntry("status", "failed");
        assertThat(result.getData()).containsEntry("errorCode", 40004);
        assertThat(result.getData()).containsEntry("subCode", "isv.parameter-invalid:1033");
    }

    @Test
    void dingdanJiesuan_standardRestPath_bindsQueryParams() throws Exception {
        when(douyinOrderGateway.listSettlement(any()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(
                        List.of(), false, "1", Map.of("data", Map.of("cursor", "1"))));

        mockMvc.perform(get("/douyin/order-settlements")
                        .queryParam("appId", "test_app")
                        .queryParam("size", "20")
                        .queryParam("cursor", "0")
                        .queryParam("timeType", "update")
                        .queryParam("startTime", "2026-04-01 00:00:00")
                        .queryParam("endTime", "2026-04-02 00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.endpoint").value("buyin.colonelMultiSettlementOrders"))
                .andExpect(jsonPath("$.data.query.size").value(20))
                .andExpect(jsonPath("$.data.query.timeType").value("update"))
                .andExpect(jsonPath("$.data.query.startTime").value("2026-04-01 00:00:00"))
                .andExpect(jsonPath("$.data.query.endTime").value("2026-04-02 00:00:00"));
    }

    @Test
    void dingdanJiesuan_standardRestPath_supportsOrderIdsAliases() throws Exception {
        when(douyinOrderGateway.listSettlementByOrderIds(any()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(
                        List.of(), false, null, Map.of("data", Map.of("orders", List.of()))));

        mockMvc.perform(get("/douyin/order-settlements")
                        .queryParam("appId", "test_app")
                        .queryParam("order_ids", "4737996432465788974,4737996432465788973"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.query.orderIds").value("4737996432465788974,4737996432465788973"));

        verify(douyinOrderGateway).listSettlementByOrderIds(argThat(ids ->
                ids != null
                        && ids.size() == 2
                        && ids.get(0).equals("4737996432465788974")
                        && ids.get(1).equals("4737996432465788973")));
    }

    @Test
    void dingdanTongbuYuanshi_standardRestPath_callsInstituteOrderColonel() throws Exception {
        when(douyinOrderGateway.listInstituteOrders(any()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(
                        List.of(), false, null, Map.of("data", Map.of("order_list", List.of()))));

        mockMvc.perform(post("/douyin/order-sync-probes/raw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "start_time", 1711900800,
                                "end_time", 1711987200,
                                "page", 1,
                                "count", 20
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.endpoint").value("buyin.instituteOrderColonel"))
                .andExpect(jsonPath("$.data.appId").value("test_app"))
                .andExpect(jsonPath("$.data.payload.start_time").value(1711900800))
                .andExpect(jsonPath("$.data.payload.count").value(20));

        verify(douyinOrderGateway).listInstituteOrders(argThat(req ->
                req.startTime() == 1711900800L
                        && req.endTime() == 1711987200L
                        && req.count() == 20));
    }

    @Test
    void dingdanTongbuYuanshi_shouldAcceptDateTimeStringsAndDefaultCount() throws Exception {
        when(douyinOrderGateway.listInstituteOrders(any()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(
                        List.of(), false, null, Map.of("data", Map.of("order_list", List.of()))));

        mockMvc.perform(post("/douyin/order-sync-probes/raw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "start_time", "2026-04-01 00:00:00",
                                "end_time", "2026-04-02 00:00:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("success"));

        verify(douyinOrderGateway).listInstituteOrders(argThat(req -> req.count() == 20 && req.cursor() == null));
    }

    @Test
    void dingdanTongbuYuanshi_missingStartTime_returnsFailedStatus() throws Exception {
        mockMvc.perform(post("/douyin/order-sync-probes/raw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "start_time", " ",
                                "end_time", "2026-04-02 00:00:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.message").value("start_time is required"))
                .andExpect(jsonPath("$.data.errorType").value("IllegalArgumentException"));
    }

    @Test
    void quxiaoHuodongShangpin_success_returnsPayload() {
        DouyinController.ActivityProductCancelRequest request = new DouyinController.ActivityProductCancelRequest();
        request.setAppId("test_app");
        request.setActivityId(12345L);
        when(douyinActivityGateway.cancelActivityProduct(eq("test_app"), any())).thenReturn(Map.of());

        ApiResult<Map<String, Object>> result = controller.quxiaoHuodongShangpin(request);

        assertThat(result.getData()).containsEntry("endpoint", "alliance.colonelActivityProductCancel");
        assertThat(result.getData()).containsEntry("status", "success");
        assertThat(result.getData()).containsKey("payload");
    }

    @Test
    void quxiaoHuodongShangpin_failure_returnsErrorStatus() {
        DouyinController.ActivityProductCancelRequest request = new DouyinController.ActivityProductCancelRequest();
        request.setAppId("test_app");
        request.setActivityId(12345L);
        when(douyinActivityGateway.cancelActivityProduct(eq("test_app"), any()))
                .thenThrow(new DouyinApiException(99999, "ACTIVITY_NOT_FOUND", null, "log_xyz", "/cancel"));

        ApiResult<Map<String, Object>> result = controller.quxiaoHuodongShangpin(request);

        assertThat(result.getData()).containsEntry("status", "failed");
        assertThat(result.getData()).containsEntry("message", "ACTIVITY_NOT_FOUND");
    }

    @Test
    void quxiaoHuodongShangpin_withProductIds_buildsProductsPayload() {
        DouyinController.ActivityProductCancelRequest request = new DouyinController.ActivityProductCancelRequest();
        request.setAppId("test_app");
        request.setApplyIds(List.of(100L));
        request.setProductIds(List.of("1001", "1002"));
        request.setReason("  终止合作  ");
        when(douyinActivityGateway.cancelActivityProduct(eq("test_app"), any())).thenReturn(Map.of("ok", true));

        ApiResult<Map<String, Object>> result = controller.quxiaoHuodongShangpin(request);

        assertThat(result.getData()).containsEntry("status", "success");
        assertThat((Map<String, Object>) result.getData().get("payload"))
                .containsEntry("apply_ids", List.of(100L))
                .containsEntry("product_ids", List.of("1001", "1002"))
                .containsEntry("reason", "终止合作");
        verify(douyinActivityGateway).cancelActivityProduct(eq("test_app"), argThat(payload ->
                payload.containsKey("products")));
    }

    @Test
    void quxiaoHuodongShangpin_withoutIdentifiers_returnsFailedStatus() {
        DouyinController.ActivityProductCancelRequest request = new DouyinController.ActivityProductCancelRequest();
        request.setAppId("test_app");

        ApiResult<Map<String, Object>> result = controller.quxiaoHuodongShangpin(request);

        assertThat(result.getData()).containsEntry("status", "failed");
        assertThat(result.getData()).containsEntry("message", "activityId, applyIds, productIds at least one required");
    }

    @Test
    void quxiaoHuodongShangpinYuanshi_success_trimsAppIdAndPassesPayload() throws Exception {
        when(douyinActivityGateway.cancelActivityProduct(eq("test_app"), any())).thenReturn(Map.of("ok", true));

        mockMvc.perform(post("/douyin/activity-product-cancellations/raw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", " test_app ",
                                "activity_id", 12345L,
                                "reason", "终止合作"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.appId").value("test_app"))
                .andExpect(jsonPath("$.data.payload.activity_id").value(12345));

        verify(douyinActivityGateway).cancelActivityProduct(eq("test_app"), argThat(payload ->
                payload.containsKey("activity_id") && !payload.containsKey("appId")));
    }

    @Test
    void quxiaoHuodongShangpinYuanshi_emptyBody_returnsFailedStatus() throws Exception {
        mockMvc.perform(post("/douyin/activity-product-cancellations/raw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.message").value("request body is required"));
    }

    @Test
    void quxiaoHuodongShangpinYuanshi_nullExceptionMessage_returnsDefaultSdkMessage() throws Exception {
        when(douyinActivityGateway.cancelActivityProduct(eq("test_app"), any()))
                .thenThrow(new IllegalStateException());

        mockMvc.perform(post("/douyin/activity-product-cancellations/raw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "activity_id", 12345L
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.message").value("Douyin SDK call failed"))
                .andExpect(jsonPath("$.data.errorType").value("IllegalStateException"));
    }

    @Test
    void chuangjianGengxinHuodong_success_returnsApiResult() {
        DouyinController.ActivityCreateOrUpdateRequest request = new DouyinController.ActivityCreateOrUpdateRequest();
        request.setAppId("test_app");
        request.setApplicationLimited(false);
        request.setActivityName("test activity");
        request.setActivityDesc("desc");
        request.setApplyStartTime("2026-04-01 00:00:00");
        request.setApplyEndTime("2026-04-30 23:59:59");
        request.setCommissionRate("10");
        request.setServiceRate("5");
        request.setEstimatedSingleSale("100");
        request.setActivityType(1);
        request.setOnline(true);
        when(douyinActivityGateway.createOrUpdateActivity(any(DouyinActivityGateway.ActivityMutateCommand.class)))
                .thenReturn(Map.of("activity_id", 12345L));

        ApiResult<Map<String, Object>> result = controller.chuangjianHuodong(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("activity_id", 12345L);
    }

    @Test
    void gengxinHuodong_standardRestPath_passesPathActivityId() throws Exception {
        when(douyinActivityGateway.createOrUpdateActivity(any(DouyinActivityGateway.ActivityMutateCommand.class)))
                .thenReturn(Map.of("activity_id", 67890L));
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("appId", "test_app");
        request.put("applicationLimited", false);
        request.put("activityName", "test activity");
        request.put("activityDesc", "desc");
        request.put("applyStartTime", "2026-04-01 00:00:00");
        request.put("applyEndTime", "2026-04-30 23:59:59");
        request.put("commissionRate", "10");
        request.put("serviceRate", "5");
        request.put("estimatedSingleSale", "100");
        request.put("activityType", 1);
        request.put("online", true);

        mockMvc.perform(MockMvcRequestBuilders.put("/douyin/activities/67890")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activity_id").value(67890));

        verify(douyinActivityGateway).createOrUpdateActivity(argThat(command -> command.activityId().equals(67890L)));
    }

    @Test
    void tokenShuaxin_returnsTokenStatus() {
        DouyinTokenService.TokenStatus tokenStatus =
                new DouyinTokenService.TokenStatus("test_app", true, "tok***", true, "ref***", 0, false, false);
        doNothing().when(douyinTokenService).refreshToken("test_app");
        when(douyinTokenService.getTokenStatus("test_app")).thenReturn(tokenStatus);

        ApiResult<DouyinTokenService.TokenStatus> result = controller.tokenShuaxin("test_app");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getAppId()).isEqualTo("test_app");
    }

    @Test
    void tokenZhuangtai_standardRestPath_returnsTokenStatus() throws Exception {
        DouyinTokenService.TokenStatus tokenStatus =
                new DouyinTokenService.TokenStatus("test_app", true, "tok***", true, "ref***", 0, false, false);
        when(douyinTokenService.getTokenStatus("test_app")).thenReturn(tokenStatus);

        mockMvc.perform(MockMvcRequestBuilders.get("/douyin/tokens")
                        .queryParam("appId", "test_app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.appId").value("test_app"));
    }

    @Test
    void jigouShenfen_success_returnsInstitutionInfo() {
        when(douyinTokenGateway.institutionInfo("test_app")).thenReturn(Map.of("data", Map.of("institution_id", "by_1")));

        ApiResult<Map<String, Object>> result = controller.jigouShenfen("test_app");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("endpoint", "buyin.institutionInfo");
        assertThat(result.getData()).containsEntry("appId", "test_app");
        assertThat(result.getData()).containsEntry("status", "success");
        assertThat(result.getData()).containsKey("remoteResponse");
    }

    @Test
    void jigouShenfen_standardRestPath_returnsInstitutionInfo() throws Exception {
        when(douyinTokenGateway.institutionInfo("test_app")).thenReturn(Map.of("data", Map.of("institution_id", "by_1")));

        mockMvc.perform(MockMvcRequestBuilders.get("/douyin/institution-info")
                        .queryParam("appId", "test_app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.endpoint").value("buyin.institutionInfo"))
                .andExpect(jsonPath("$.data.remoteResponse.data.institution_id").value("by_1"));

        verify(douyinTokenGateway).institutionInfo("test_app");
    }

    @Test
    void tokenChuangjian_returnsTokenStatus() {
        DouyinController.TokenCreateRequest request = new DouyinController.TokenCreateRequest();
        request.setAppId("test_app");
        request.setCode("auth-code");
        request.setGrantType("authorization_code");
        DouyinTokenService.TokenStatus tokenStatus =
                new DouyinTokenService.TokenStatus("test_app", true, "tok***", true, "ref***", 0, false, false);
        doNothing().when(douyinTokenService).exchangeCodeAndBootstrap(
                eq("test_app"), eq("auth-code"), eq("authorization_code"), eq(null), eq(null), eq(null), eq(null));
        when(douyinTokenService.getTokenStatus("test_app")).thenReturn(tokenStatus);

        ApiResult<DouyinTokenService.TokenStatus> result = controller.tokenChuangjian(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getAppId()).isEqualTo("test_app");
    }

    @Test
    void tokenChuangjian_standardRestPath_returnsTokenStatus() throws Exception {
        DouyinTokenService.TokenStatus tokenStatus =
                new DouyinTokenService.TokenStatus("test_app", true, "tok***", true, "ref***", 0, false, false);
        doNothing().when(douyinTokenService).exchangeCodeAndBootstrap(
                eq("test_app"), eq("auth-code"), eq("authorization_code"), eq(null), eq(null), eq(null), eq(null));
        when(douyinTokenService.getTokenStatus("test_app")).thenReturn(tokenStatus);

        mockMvc.perform(post("/douyin/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "code", "auth-code",
                                "grantType", "authorization_code"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.appId").value("test_app"));
    }

    @Test
    void tokenChuangjian_authorizationCode_allowsSnakeCasePayload() throws Exception {
        DouyinTokenService.TokenStatus tokenStatus =
                new DouyinTokenService.TokenStatus("test_app", true, "tok***", true, "ref***", 0, false, false);
        doNothing().when(douyinTokenService).exchangeCodeAndBootstrap(
                eq("test_app"), eq("auth-code"), eq("authorization_code"), eq(null), eq(null), eq(null), eq(null));
        when(douyinTokenService.getTokenStatus("test_app")).thenReturn(tokenStatus);

        mockMvc.perform(post("/douyin/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "app_id", "test_app",
                                "authorization_code", "auth-code",
                                "grant_type", "authorization_code"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.appId").value("test_app"));

        verify(douyinTokenService).exchangeCodeAndBootstrap(
                "test_app", "auth-code", "authorization_code", null, null, null, null);
    }

    @Test
    void tokenCreateProbe_returnsSanitizedRawSdkResponse() throws Exception {
        DouyinTokenGateway.ProbeTokenCreateResult probeResult = new DouyinTokenGateway.ProbeTokenCreateResult(
                "authorization_code",
                "present",
                null,
                null,
                true,
                "Colonel",
                new DouyinTokenGateway.TokenProbeResponseView(
                        "50002",
                        "业务处理失败",
                        "isv.business-failed:4",
                        "authorization code required",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
        when(douyinTokenGateway.probeCreateToken(any())).thenReturn(probeResult);

        mockMvc.perform(post("/douyin/token-create-probes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "grantType", "authorization_code",
                                "code", "auth-code",
                                "authId", "7351155267604218149",
                                "authSubjectType", "Colonel"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.module").value("M1.3 Real SDK Probe"))
                .andExpect(jsonPath("$.data.endpoint").value("token.create"))
                .andExpect(jsonPath("$.data.requestSnapshot.grantType").value("authorization_code"))
                .andExpect(jsonPath("$.data.requestSnapshot.authIdPresent").value(true))
                .andExpect(jsonPath("$.data.response.code").value("50002"))
                .andExpect(jsonPath("$.data.response.subMsg").value("authorization code required"));

        verify(douyinTokenGateway).probeCreateToken(any());
    }

    @Test
    void promotionLinkRawProbe_returnsRemoteResponse() throws Exception {
        when(douyinPromotionGateway.rawUpstreamPost(eq("test_app"), eq("buyin.instPickSourceConvert"), any()))
                .thenReturn(Map.of("data", Map.of("pick_source", "ABC12345")));

        mockMvc.perform(post("/douyin/promotion-link-probes/raw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "method", "buyin.instPickSourceConvert",
                                "product_url", "https://haohuo.jinritemai.com/ecommerce/trade/detail/index.html?id=1",
                                "pick_extra", "channel_demo"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.module").value("M1.3 Promotion Raw Probe"))
                .andExpect(jsonPath("$.data.endpoint").value("buyin.instPickSourceConvert"))
                .andExpect(jsonPath("$.data.appId").value("test_app"))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.remoteResponse.data.pick_source").value("ABC12345"));

        verify(douyinPromotionGateway).rawUpstreamPost(eq("test_app"), eq("buyin.instPickSourceConvert"), any());
    }

    @Test
    void promotionLinkRawProbe_productSkusBranch_returnsSkuList() throws Exception {
        when(douyinProductGateway.queryProductSkus("P-1001"))
                .thenReturn(List.of(new DouyinProductGateway.ProductSkuResult("SKU-1", "规格一", 1000L, 9, "cover")));

        mockMvc.perform(post("/douyin/promotion-link-probes/raw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "method", "buyin.productSkus.v2",
                                "productId", "P-1001"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.remoteResponse.data.skus[0].skuId").value("SKU-1"));

        verify(douyinProductGateway).queryProductSkus("P-1001");
    }

    @Test
    void promotionLinkRawProbe_productSkusBranch_requiresProductId() throws Exception {
        mockMvc.perform(post("/douyin/promotion-link-probes/raw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "method", "buyin.productSkus.v2"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.message").value("product_id is required for buyin.productSkus.v2"));
    }

    @Test
    void promotionLinkRawProbe_missingMethod_returnsFailedStatus() throws Exception {
        mockMvc.perform(post("/douyin/promotion-link-probes/raw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "product_url", "https://haohuo.jinritemai.com/ecommerce/trade/detail/index.html?id=1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.module").value("M1.3 Promotion Raw Probe"))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.message").value("method is required"))
                .andExpect(jsonPath("$.data.errorType").value("IllegalArgumentException"));
    }

    @Test
    void activityCreateOrUpdateRequest_shouldExposeAllFields() {
        DouyinController.ActivityCreateOrUpdateRequest request = new DouyinController.ActivityCreateOrUpdateRequest();

        request.setAppId("test_app");
        request.setActivityId(12345L);
        request.setApplicationLimited(Boolean.FALSE);
        request.setIsNewShop(Boolean.TRUE);
        request.setShopType("normal");
        request.setActivityName("测试活动");
        request.setActivityDesc("联调用活动");
        request.setApplyStartTime("2026-04-28 10:00:00");
        request.setApplyEndTime("2026-04-29 10:00:00");
        request.setCommissionRate("10");
        request.setServiceRate("5");
        request.setWechatId("wechat-demo");
        request.setPhoneNum("13800000000");
        request.setEstimatedSingleSale("1000");
        request.setActivityType(1);
        request.setSpecifiedShopIds("1001,1002");
        request.setOnline(Boolean.TRUE);
        request.setCategories("食品");
        request.setShopScore(90);
        request.setMinPromotionDays(7);
        request.setThresholdCrossBorder(0);
        request.setMinExclusionDuration(1);
        request.setAdCommissionRate("2");
        request.setAdServiceRate("1");
        request.setCosLimitType(3);

        assertThat(request.getAppId()).isEqualTo("test_app");
        assertThat(request.getActivityId()).isEqualTo(12345L);
        assertThat(request.getApplicationLimited()).isFalse();
        assertThat(request.getIsNewShop()).isTrue();
        assertThat(request.getShopType()).isEqualTo("normal");
        assertThat(request.getActivityName()).isEqualTo("测试活动");
        assertThat(request.getActivityDesc()).isEqualTo("联调用活动");
        assertThat(request.getApplyStartTime()).isEqualTo("2026-04-28 10:00:00");
        assertThat(request.getApplyEndTime()).isEqualTo("2026-04-29 10:00:00");
        assertThat(request.getCommissionRate()).isEqualTo("10");
        assertThat(request.getServiceRate()).isEqualTo("5");
        assertThat(request.getWechatId()).isEqualTo("wechat-demo");
        assertThat(request.getPhoneNum()).isEqualTo("13800000000");
        assertThat(request.getEstimatedSingleSale()).isEqualTo("1000");
        assertThat(request.getActivityType()).isEqualTo(1);
        assertThat(request.getSpecifiedShopIds()).isEqualTo("1001,1002");
        assertThat(request.getOnline()).isTrue();
        assertThat(request.getCategories()).isEqualTo("食品");
        assertThat(request.getShopScore()).isEqualTo(90);
        assertThat(request.getMinPromotionDays()).isEqualTo(7);
        assertThat(request.getThresholdCrossBorder()).isZero();
        assertThat(request.getMinExclusionDuration()).isEqualTo(1);
        assertThat(request.getAdCommissionRate()).isEqualTo("2");
        assertThat(request.getAdServiceRate()).isEqualTo("1");
        assertThat(request.getCosLimitType()).isEqualTo(3);
    }

    @Test
    void douyinController_shouldRequireAdminRoleAnnotation() {
        RequireRoles requireRoles = DouyinController.class.getAnnotation(RequireRoles.class);
        assertThat(requireRoles).isNotNull();
        assertThat(requireRoles.value()).containsExactly(RoleCodes.ADMIN);
    }
}
