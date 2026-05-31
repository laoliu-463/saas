package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.service.activity.ActivityAccessService;
import com.colonel.saas.service.ColonelsettlementActivityService;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.ShortTtlCacheService;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.colonel.saas.entity.ColonelsettlementActivity;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ColonelActivityControllerTest {

    @Mock
    private DouyinActivityGateway douyinActivityGateway;
    @Mock
    private DouyinProductGateway douyinProductGateway;
    @Mock
    private ProductService productService;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private ColonelsettlementActivityService colonelActivityService;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private ColonelsettlementActivityMapper colonelActivityMapper;

    private ActivityAccessService activityAccessService;
    private ColonelActivityController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        activityAccessService = new ActivityAccessService(colonelActivityMapper);
        controller = new ColonelActivityController(
                douyinActivityGateway,
                douyinProductGateway,
                productService,
                new ShortTtlCacheService(),
                sysUserService,
                colonelActivityService,
                sysUserMapper,
                activityAccessService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void assignActivity_shouldValidateRecruiterAndReturnAssignmentPayload() throws Exception {
        UUID assigneeId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID operatorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", "100018");
        payload.put("assigneeId", assigneeId);
        payload.put("assigneeName", "招商组长测试");
        payload.put("assignedBy", operatorId);

        when(productService.assignActivity("100018", assigneeId, operatorId)).thenReturn(payload);

        mockMvc.perform(put("/colonel/activities/{activityId}/assignee", "100018")
                        .requestAttr("userId", operatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"22222222-2222-2222-2222-222222222222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value("100018"))
                .andExpect(jsonPath("$.data.assigneeName").value("招商组长测试"));

        verify(sysUserService).assertRecruiterUser(assigneeId);
        verify(productService).assignActivity("100018", assigneeId, operatorId);
    }

    @Test
    void assignActivity_shouldEvictCachedActivityListAfterAssignment() throws Exception {
        UUID assigneeId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID operatorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ShortTtlCacheService cacheService = mock(ShortTtlCacheService.class);
        ColonelActivityController localController = new ColonelActivityController(
                douyinActivityGateway,
                douyinProductGateway,
                productService,
                cacheService,
                sysUserService,
                colonelActivityService,
                sysUserMapper,
                activityAccessService);
        MockMvc localMvc = MockMvcBuilders.standaloneSetup(localController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(productService.assignActivity("100018", assigneeId, operatorId)).thenReturn(Map.of(
                "activityId", "100018",
                "assigneeId", assigneeId
        ));

        localMvc.perform(put("/colonel/activities/{activityId}/assignee", "100018")
                        .requestAttr("userId", operatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"22222222-2222-2222-2222-222222222222\"}"))
                .andExpect(status().isOk());

        verify(cacheService).evictByPrefix("activities:list:");
    }

    @Test
    void list_shouldExposeNormalizedAndLegacyActivityFields() throws Exception {
        DouyinActivityGateway.ActivityItem item = new DouyinActivityGateway.ActivityItem(
                3916506L,
                "星链达客-zy",
                "2026-05-06",
                "2026-08-03",
                3,
                "报名中",
                "2026-05-06",
                "2035-12-31",
                Map.of("9", "美妆"),
                0L
        );
        DouyinActivityGateway.ActivityListResult result =
                new DouyinActivityGateway.ActivityListResult(false, 7351155267604201765L, 21L, List.of(item));

        when(douyinActivityGateway.listActivities(any())).thenReturn(result);
        when(colonelActivityService.findAssignmentsByActivityIds(any())).thenReturn(Map.of());

        mockMvc.perform(get("/colonel/activities")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.activityList[0].activityId").value(3916506))
                .andExpect(jsonPath("$.data.activityList[0].status").value(3))
                .andExpect(jsonPath("$.data.activityList[0].activityStatus").value(3))
                .andExpect(jsonPath("$.data.activityList[0].activityStartTime").value("2026-05-06"))
                .andExpect(jsonPath("$.data.activityList[0].startTime").value("2026-05-06"))
                .andExpect(jsonPath("$.data.activityList[0].activityEndTime").value("2026-08-03"))
                .andExpect(jsonPath("$.data.activityList[0].endTime").value("2026-08-03"))
                .andExpect(jsonPath("$.data.activityList[0].statusText").value("报名中"));
    }

    @Test
    void list_shouldFilterAssignedActivitiesWhenRequested() throws Exception {
        UUID recruiterId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", 1L);
        payload.put("activityList", List.of(Map.of(
                "activityId", 3916506L,
                "activityName", "已分配活动",
                "recruiterUserId", recruiterId
        )));

        when(colonelActivityService.buildAssignmentListPage(
                eq(1L),
                eq(20L),
                eq(0),
                eq("assigned"),
                eq(null),
                eq(null),
                any()))
                .thenReturn(payload);

        mockMvc.perform(get("/colonel/activities")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("assignmentFilter", "assigned")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.activityList[0].activityId").value(3916506));

        verify(douyinActivityGateway, never()).listActivities(any());
    }

    @Test
    void list_shouldFilterMineActivitiesForCurrentUser() throws Exception {
        UUID recruiterId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", 1L);
        payload.put("activityList", List.of(Map.of(
                "activityId", 3916506L,
                "activityName", "我的活动"
        )));

        when(colonelActivityService.buildAssignmentListPage(
                eq(1L),
                eq(20L),
                eq(0),
                eq("mine"),
                eq(recruiterId),
                eq(null),
                any()))
                .thenReturn(payload);

        mockMvc.perform(get("/colonel/activities")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("assignmentFilter", "mine")
                        .requestAttr("userId", recruiterId)
                        .requestAttr("roleCodes", List.of(RoleCodes.BIZ_LEADER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.activityList[0].activityName").value("我的活动"));
    }

    @Test
    void list_shouldForceMineForRecruiterEvenWhenAllRequested() throws Exception {
        UUID recruiterId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", 0L);
        payload.put("activityList", List.of());

        when(colonelActivityService.buildAssignmentListPage(
                eq(1L),
                eq(20L),
                eq(0),
                eq("mine"),
                eq(recruiterId),
                eq(null),
                any()))
                .thenReturn(payload);

        mockMvc.perform(get("/colonel/activities")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("assignmentFilter", "all")
                        .requestAttr("userId", recruiterId)
                        .requestAttr("roleCodes", List.of(RoleCodes.BIZ_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        verify(douyinActivityGateway, never()).listActivities(any());
    }

    @Test
    void listProducts_shouldExposeBizStatusFields() throws Exception {
        DouyinProductGateway.ActivityProductItem item = new DouyinProductGateway.ActivityProductItem(
                9001L,
                "洁面乳",
                "https://img.test/product.jpg",
                5900L,
                "59.00",
                20L,
                1180L,
                25L,
                "25%",
                1,
                "普通佣金",
                "5%",
                10L,
                true,
                true,
                128L,
                7001L,
                "示例店铺",
                "4.9",
                1,
                "推广中",
                "美妆",
                "1000",
                "满减券",
                "2026-04-25 00:00:00",
                "2026-04-30 23:59:59",
                "2026-04-25 00:00:00",
                "2026-04-30 23:59:59",
                "https://detail.test/products/9001",
                null,
                Map.of()
        );
        DouyinProductGateway.ActivityProductListResult gatewayResult =
                new DouyinProductGateway.ActivityProductListResult(
                        true,
                        100018L,
                        30001L,
                        1L,
                        "next-cursor",
                        List.of(item)
                );

        Map<String, Object> itemView = new LinkedHashMap<>();
        itemView.put("productId", 9001L);
        itemView.put("title", "洁面乳");
        itemView.put("bizStatus", "APPROVED");
        itemView.put("bizStatusLabel", "审核通过");

        Map<String, Object> listView = new LinkedHashMap<>();
        listView.put("mock", true);
        listView.put("activityId", 100018L);
        listView.put("institutionId", 30001L);
        listView.put("total", 1);
        listView.put("nextCursor", "next-cursor");
        listView.put("items", List.of(itemView));

        when(productService.hasActivitySnapshots("100018")).thenReturn(false);
        when(douyinProductGateway.queryActivityProducts(any())).thenReturn(gatewayResult);
        when(productService.buildActivityProductListViewFromDb("100018", 20, null, null, null, null, null, null, null)).thenReturn(listView);

        mockMvc.perform(get("/colonel/activities/{activityId}/products", "100018")
                        .param("searchType", "4")
                        .param("sortType", "1")
                        .param("count", "20")
                        .param("retrieveMode", "1")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value(100018))
                .andExpect(jsonPath("$.data.items[0].productId").value(9001))
                .andExpect(jsonPath("$.data.items[0].bizStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.items[0].bizStatusLabel").value("审核通过"));

        verify(productService).hasActivitySnapshots("100018");
        verify(productService).upsertSnapshots(eq("100018"), eq(gatewayResult.items()));
        verify(productService).buildActivityProductListViewFromDb("100018", 20, null, null, null, null, null, null, null);
    }

    @Test
    void listProducts_shouldUseLocalSnapshotWhenAvailable() throws Exception {
        Map<String, Object> itemView = new LinkedHashMap<>();
        itemView.put("productId", 9001L);
        itemView.put("title", "本地快照商品");
        itemView.put("bizStatus", "PENDING_AUDIT");
        Map<String, Object> listView = new LinkedHashMap<>();
        listView.put("activityId", "100018");
        listView.put("total", 1);
        listView.put("items", List.of(itemView));

        when(productService.hasActivitySnapshots("100018")).thenReturn(true);
        when(productService.buildActivityProductListViewFromDb("100018", 10, "cursor-1", "本地", "PENDING_AUDIT", 1, null, null, null))
                .thenReturn(listView);

        mockMvc.perform(get("/colonel/activities/{activityId}/products", "100018")
                        .param("count", "10")
                        .param("cursor", "cursor-1")
                        .param("productInfo", "本地")
                        .param("bizStatus", "PENDING_AUDIT")
                        .param("status", "1")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("本地快照商品"));

        verify(douyinProductGateway, never()).queryActivityProducts(any());
        verify(productService, never()).upsertSnapshots(eq("100018"), any());
    }

    @Test
    void listProducts_refreshTrueShouldBypassExistingSnapshotsAndRefreshFromGateway() throws Exception {
        DouyinProductGateway.ActivityProductItem item = new DouyinProductGateway.ActivityProductItem(
                9002L,
                "防晒霜",
                "https://img.test/product-9002.jpg",
                8900L,
                "89.00",
                30L,
                2670L,
                30L,
                "30%",
                1,
                "普通佣金",
                "6%",
                12L,
                true,
                true,
                129L,
                7002L,
                "真实店铺",
                "4.8",
                1,
                "推广中",
                "美妆",
                "1001",
                "满减券",
                "2026-05-01 00:00:00",
                "2026-05-31 23:59:59",
                "2026-05-01 00:00:00",
                "2026-05-31 23:59:59",
                "https://detail.test/products/9002",
                null,
                Map.of()
        );
        Map<String, Object> itemView = new LinkedHashMap<>();
        itemView.put("productId", 9002L);
        itemView.put("title", "防晒霜");
        itemView.put("bizStatus", "APPROVED");

        Map<String, Object> listView = new LinkedHashMap<>();
        listView.put("mock", false);
        listView.put("activityId", 100018L);
        listView.put("total", 1);
        listView.put("nextCursor", "fresh-cursor");
        listView.put("items", List.of(itemView));

        when(productService.buildActivityProductListViewFromDb("100018", 20, null, null, null, null, null, null, null)).thenReturn(listView);
        when(productService.refreshActivitySnapshots(any())).thenReturn(
                new ProductService.ActivityProductRefreshResult(1, 1, 1, 0, 0));
        when(colonelActivityService.findByActivityId("100018")).thenReturn(null);

        mockMvc.perform(get("/colonel/activities/{activityId}/products", "100018")
                        .param("count", "20")
                        .param("refresh", "true")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value(100018))
                .andExpect(jsonPath("$.data.items[0].productId").value(9002))
                .andExpect(jsonPath("$.data.nextCursor").value("fresh-cursor"));

        verify(productService, never()).hasActivitySnapshots("100018");
        verify(colonelActivityService).syncActivitySummaryFromUpstream(eq("100018"), eq(null));
        verify(productService).refreshActivitySnapshots(any());
        verify(douyinProductGateway, never()).queryActivityProducts(any());
        verify(productService, never()).upsertSnapshots(eq("100018"), any());
        verify(productService).buildActivityProductListViewFromDb("100018", 20, null, null, null, null, null, null, null);
    }

    @Test
    void list_shouldMapActivityGatewayErrorsToBusinessMessages() {
        record ErrorCase(DouyinApiException exception, String message) {
        }
        List<ErrorCase> cases = List.of(
                new ErrorCase(new DouyinApiException(50002, "UPSTREAM", "isv.business-failed:4197", "log", "activity"), "招商团长授权"),
                new ErrorCase(new DouyinApiException(50002, "UPSTREAM", "isv.business-failed:4200", "log", "activity"), "账号状态异常"),
                new ErrorCase(new DouyinApiException(40004, "UPSTREAM", "isv.parameter-invalid:257", "log", "activity"), "查询参数不合法"),
                new ErrorCase(new DouyinApiException(20000, "UPSTREAM", "isv.system-error:256", "log", "activity"), "抖店服务异常"),
                new ErrorCase(new DouyinApiException(99999, "fallback", null, "log", "activity"), "团长活动查询失败: fallback")
        );

        for (ErrorCase item : cases) {
            DouyinActivityGateway activityGateway = mock(DouyinActivityGateway.class);
            when(activityGateway.listActivities(any())).thenThrow(item.exception());
            ColonelActivityController errorController = new ColonelActivityController(
                    activityGateway,
                    douyinProductGateway,
                    productService,
                    new ShortTtlCacheService(),
                    sysUserService,
                    colonelActivityService,
                    sysUserMapper,
                    activityAccessService);

            assertThatThrownBy(() -> errorController.list(
                    0, 0L, 1L, 1L, 20L, null, null, "all", null, List.of(RoleCodes.ADMIN)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(item.message());
        }
    }

    @Test
    void listProducts_shouldMapProductGatewayErrorsToBusinessMessages() {
        record ErrorCase(DouyinApiException exception, String message) {
        }
        List<ErrorCase> cases = List.of(
                new ErrorCase(new DouyinApiException(50002, "UPSTREAM", "isv.business-failed:4097", "log", "product"), "每页最多查询 20 条商品"),
                new ErrorCase(new DouyinApiException(50002, "UPSTREAM", "isv.business-failed:8197", "log", "product"), "不允许继续翻页"),
                new ErrorCase(new DouyinApiException(50002, "UPSTREAM", "isv.business-failed:4197", "log", "product"), "招商团长授权"),
                new ErrorCase(new DouyinApiException(50002, "UPSTREAM", "isv.business-failed:4200", "log", "product"), "账号状态异常"),
                new ErrorCase(new DouyinApiException(50002, "UPSTREAM", "isv.parameter-invalid:257", "log", "product"), "查询参数不合法"),
                new ErrorCase(new DouyinApiException(20000, "UPSTREAM", "isv.system-error:256", "log", "product"), "抖店服务异常"),
                new ErrorCase(new DouyinApiException(99999, "fallback", null, "log", "product"), "活动商品查询失败: fallback")
        );

        for (ErrorCase item : cases) {
            DouyinProductGateway productGateway = mock(DouyinProductGateway.class);
            ProductService localProductService = mock(ProductService.class);
            when(localProductService.hasActivitySnapshots("100018")).thenReturn(false);
            when(productGateway.queryActivityProducts(any())).thenThrow(item.exception());
            ColonelActivityController errorController = new ColonelActivityController(
                    douyinActivityGateway,
                    productGateway,
                    localProductService,
                    new ShortTtlCacheService(),
                    sysUserService,
                    colonelActivityService,
                    sysUserMapper,
                    activityAccessService);

            assertThatThrownBy(() -> errorController.listProducts(
                    "100018",
                    4L,
                    1L,
                    20,
                    null,
                    0,
                    null,
                    null,
                    null,
                    1L,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(RoleCodes.ADMIN)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(item.message());
        }
    }

    @Test
    void listProducts_shouldRejectUnassignedActivityForRecruiter() {
        UUID recruiterId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(colonelActivityMapper.selectByActivityId("100018")).thenReturn(null);

        assertThatThrownBy(() -> controller.listProducts(
                "100018",
                4L,
                1L,
                20,
                null,
                0,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                recruiterId,
                null,
                List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该活动");
    }

    @Test
    void controller_shouldAllowBizStaffAtClassLevel() {
        RequireRoles requireRoles = ColonelActivityController.class.getAnnotation(RequireRoles.class);
        assertThat(requireRoles).isNotNull();
        assertThat(requireRoles.value()).contains(RoleCodes.BIZ_STAFF);
        assertThat(requireRoles.value()).contains(RoleCodes.BIZ_LEADER, RoleCodes.ADMIN, RoleCodes.BIZ_STAFF);
    }
}
