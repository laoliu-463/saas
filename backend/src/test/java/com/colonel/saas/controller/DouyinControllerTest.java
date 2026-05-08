package com.colonel.saas.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.douyin.DoudianTokenGateway;
import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.douyin.api.InstitutionApi;
import com.colonel.saas.douyin.api.OrderApi;
import com.colonel.saas.douyin.api.ProductApi;
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
    private ActivityApi activityApi;
    @Mock
    private ProductApi productApi;
    @Mock
    private OrderApi orderApi;
    @Mock
    private DouyinTokenService douyinTokenService;
    @Mock
    private InstitutionApi institutionApi;
    @Mock
    private DoudianTokenGateway doudianTokenGateway;
    @Mock
    private DouyinApiClient douyinApiClient;

    private DouyinController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private Logger controllerLogger;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        controller = new DouyinController(activityApi, productApi, orderApi, institutionApi, douyinTokenService, doudianTokenGateway, douyinApiClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        controllerLogger = (Logger) LoggerFactory.getLogger(DouyinController.class);
        originalLevel = controllerLogger.getLevel();
        controllerLogger.setLevel(Level.OFF);
    }

    @AfterEach
    void restoreLogger() {
        controllerLogger.setLevel(originalLevel);
    }

    @Test
    void huodongLiebiao_success_returnsSuccessResult() {
        when(activityApi.list("test_app")).thenReturn(Map.of("items", List.of()));

        ApiResult<Map<String, Object>> result = controller.huodongLiebiao("test_app");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("module", "M1.2 Douyin SDK");
        assertThat(result.getData()).containsEntry("endpoint", "alliance.instituteColonelActivityList");
        assertThat(result.getData()).containsEntry("appId", "test_app");
        assertThat(result.getData()).containsEntry("status", "success");
    }

    @Test
    void huodongLiebiao_failure_returnsErrorFields() {
        when(activityApi.list(any()))
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
        when(activityApi.detail("test_app", "54321")).thenReturn(Map.of("detail", Map.of()));

        ApiResult<Map<String, Object>> result = controller.huodongXiangqing("test_app", "54321");

        assertThat(result.getData()).containsEntry("endpoint", "buyin.colonelActivityDetail");
        assertThat(result.getData()).containsEntry("activityId", "54321");
        assertThat(result.getData()).containsEntry("status", "success");
    }

    @Test
    void huodongXiangqing_standardRestPath_bindsRequestParams() throws Exception {
        when(activityApi.detail("test_app", "54321")).thenReturn(Map.of("detail", Map.of()));

        mockMvc.perform(get("/douyin/activities/54321")
                        .queryParam("appId", "test_app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value("54321"))
                .andExpect(jsonPath("$.data.endpoint").value("buyin.colonelActivityDetail"));

        verify(activityApi).detail("test_app", "54321");
    }

    @Test
    void huodongShangpin_success_returnsSuccessResult() {
        when(productApi.listActivities(any(), any(), any(), any(), any(), any(), any())).thenReturn(Map.of());

        ApiResult<Map<String, Object>> result =
                controller.huodongShangpin("test_app", 1, 2L, 3L, 4L, 20L, "keyword");

        assertThat(result.getData()).containsEntry("endpoint", "alliance.instituteColonelActivityList");
        assertThat(result.getData()).containsEntry("status", "success");
    }

    @Test
    void shangpinLiebiao_success_returnsSuccessResult() {
        when(productApi.listProductsByActivity("test_app", "54321", 10, "cursor-1")).thenReturn(Map.of());

        ApiResult<Map<String, Object>> result =
                controller.shangpinLiebiao("test_app", "54321", 10, "cursor-1");

        assertThat(result.getData()).containsEntry("endpoint", "alliance.colonelActivityProduct");
        assertThat(result.getData()).containsEntry("activityId", "54321");
        assertThat(result.getData()).containsEntry("status", "success");
    }

    @Test
    void shangpinSucaiZhuangtai_success_returnsSuccessResult() {
        DouyinController.ProductMaterialStatusRequest request = new DouyinController.ProductMaterialStatusRequest();
        request.setAppId("test_app");
        request.setProducts(List.of("https://haohuo.jinritemai.com/views/product/detail?id=1"));
        when(productApi.materialsProductStatus(eq("test_app"), any())).thenReturn(Map.of());

        ApiResult<Map<String, Object>> result = controller.shangpinSucaiZhuangtai(request);

        assertThat(result.getData()).containsEntry("endpoint", "buyin.materialsProductStatus");
        assertThat(result.getData()).containsEntry("status", "success");
    }

    @Test
    void shangpinSucaiZhuangtai_failure_returnsErrorStatus() {
        DouyinController.ProductMaterialStatusRequest request = new DouyinController.ProductMaterialStatusRequest();
        request.setAppId("test_app");
        request.setProducts(List.of("https://haohuo.jinritemai.com/views/product/detail?id=1"));
        when(productApi.materialsProductStatus(eq("test_app"), any()))
                .thenThrow(new DouyinApiException(40004, "PARAM_INVALID", "isv.parameter-invalid:257", "log_1", "buyin.materialsProductStatus"));

        ApiResult<Map<String, Object>> result = controller.shangpinSucaiZhuangtai(request);

        assertThat(result.getData()).containsEntry("status", "failed");
        assertThat(result.getData()).containsEntry("errorCode", 40004);
        assertThat(result.getData()).containsEntry("subCode", "isv.parameter-invalid:257");
    }

    @Test
    void shangpinSucaiZhuangtai_standardRestPath_bindsRequestBody() throws Exception {
        when(productApi.materialsProductStatus(eq("test_app"), any())).thenReturn(Map.of("ok", true));

        mockMvc.perform(post("/douyin/product-material-status-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "products", List.of("https://haohuo.jinritemai.com/views/product/detail?id=1")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.endpoint").value("buyin.materialsProductStatus"));
    }

    @Test
    void shangpinSucaiZhuangtai_standardRestPath_rejectsEmptyProducts() throws Exception {
        mockMvc.perform(post("/douyin/product-material-status-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", "test_app",
                                "products", List.of()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("products cannot be empty"));
    }

    @Test
    void dingdanJiesuan_success_returnsQueryContext() {
        when(orderApi.listColonelMultiSettlementOrders(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("data", Map.of("cursor", "1")));

        ApiResult<Map<String, Object>> result = controller.dingdanJiesuan(
                "test_app", 20, "0", "update", "2026-04-01 00:00:00", "2026-04-02 00:00:00");

        assertThat(result.getData()).containsEntry("endpoint", "buyin.colonelMultiSettlementOrders");
        assertThat(result.getData()).containsEntry("status", "success");
        assertThat(result.getData()).containsKey("query");
    }

    @Test
    void dingdanJiesuan_failure_returnsErrorStatus() {
        when(orderApi.listColonelMultiSettlementOrders(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DouyinApiException(
                        40004,
                        "PARAM_INVALID",
                        "isv.parameter-invalid:1033",
                        "log_order_1",
                        "buyin.colonelMultiSettlementOrders"));

        ApiResult<Map<String, Object>> result = controller.dingdanJiesuan(null, 20, "0", "update", null, null);

        assertThat(result.getData()).containsEntry("status", "failed");
        assertThat(result.getData()).containsEntry("errorCode", 40004);
        assertThat(result.getData()).containsEntry("subCode", "isv.parameter-invalid:1033");
    }

    @Test
    void dingdanJiesuan_standardRestPath_bindsQueryParams() throws Exception {
        when(orderApi.listColonelMultiSettlementOrders(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("data", Map.of("cursor", "1")));

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
    void dingdanTongbuYuanshi_standardRestPath_callsInstituteOrderColonel() throws Exception {
        when(douyinApiClient.post(eq("buyin.instituteOrderColonel"), any()))
                .thenReturn(Map.of("data", Map.of("order_list", List.of())));

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

        verify(douyinApiClient).post(eq("buyin.instituteOrderColonel"), any());
    }

    @Test
    void quxiaoHuodongShangpin_success_returnsPayload() {
        DouyinController.ActivityProductCancelRequest request = new DouyinController.ActivityProductCancelRequest();
        request.setAppId("test_app");
        request.setActivityId(12345L);
        when(activityApi.cancelActivityProduct(eq("test_app"), any())).thenReturn(Map.of());

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
        when(activityApi.cancelActivityProduct(eq("test_app"), any()))
                .thenThrow(new DouyinApiException(99999, "ACTIVITY_NOT_FOUND", null, "log_xyz", "/cancel"));

        ApiResult<Map<String, Object>> result = controller.quxiaoHuodongShangpin(request);

        assertThat(result.getData()).containsEntry("status", "failed");
        assertThat(result.getData()).containsEntry("message", "ACTIVITY_NOT_FOUND");
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
        when(activityApi.createOrUpdate(any(ActivityApi.ActivityCreateOrUpdateCommand.class)))
                .thenReturn(Map.of("activity_id", 12345L));

        ApiResult<Map<String, Object>> result = controller.chuangjianHuodong(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("activity_id", 12345L);
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
        when(institutionApi.info("test_app")).thenReturn(Map.of("data", Map.of("institution_id", "by_1")));

        ApiResult<Map<String, Object>> result = controller.jigouShenfen("test_app");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("endpoint", "buyin.institutionInfo");
        assertThat(result.getData()).containsEntry("appId", "test_app");
        assertThat(result.getData()).containsEntry("status", "success");
        assertThat(result.getData()).containsKey("remoteResponse");
    }

    @Test
    void jigouShenfen_standardRestPath_returnsInstitutionInfo() throws Exception {
        when(institutionApi.info("test_app")).thenReturn(Map.of("data", Map.of("institution_id", "by_1")));

        mockMvc.perform(MockMvcRequestBuilders.get("/douyin/institution-info")
                        .queryParam("appId", "test_app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.endpoint").value("buyin.institutionInfo"))
                .andExpect(jsonPath("$.data.remoteResponse.data.institution_id").value("by_1"));

        verify(institutionApi).info("test_app");
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
        DoudianTokenGateway.TokenCreateProbeResult probeResult = new DoudianTokenGateway.TokenCreateProbeResult(
                "authorization_code",
                "present",
                null,
                null,
                true,
                "Colonel",
                null,
                new DoudianTokenGateway.TokenCreateResponseView(
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
        when(doudianTokenGateway.probeCreateToken(any())).thenReturn(probeResult);

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

        verify(doudianTokenGateway).probeCreateToken(any());
    }

    @Test
    void promotionLinkRawProbe_returnsRemoteResponse() throws Exception {
        when(douyinApiClient.post(eq("buyin.instPickSourceConvert"), any()))
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

        verify(douyinApiClient).post(eq("buyin.instPickSourceConvert"), any());
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
}
