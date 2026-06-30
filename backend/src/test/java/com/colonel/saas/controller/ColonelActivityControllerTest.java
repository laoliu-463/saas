package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.domain.product.application.ProductActivitySyncApplicationService;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.service.activity.ActivityAccessService;
import com.colonel.saas.service.ColonelsettlementActivityService;
import com.colonel.saas.service.ProductActivityManualSyncService;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.ShortTtlCacheService;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private ProductService productService;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private ColonelsettlementActivityService colonelActivityService;
    @Mock
    private ProductActivityManualSyncService productActivityManualSyncService;
    @Mock
    private ProductActivitySyncApplicationService productActivitySyncApplicationService;
    @Mock
    private UserDomainFacade userDomainFacade;
    @Mock
    private ColonelsettlementActivityMapper colonelActivityMapper;

    private ActivityAccessService activityAccessService;
    private ColonelActivityController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        activityAccessService = new ActivityAccessService(colonelActivityMapper, new CurrentUserPermissionPolicy());
        controller = new ColonelActivityController(
                douyinActivityGateway,
                productService,
                new ShortTtlCacheService(),
                sysUserService,
                colonelActivityService,
                productActivityManualSyncService,
                productActivitySyncApplicationService,
                userDomainFacade,
                activityAccessService,
                new ProductDisplayPolicy()
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
                productService,
                cacheService,
                sysUserService,
                colonelActivityService,
                productActivityManualSyncService,
                productActivitySyncApplicationService,
                userDomainFacade,
                activityAccessService,
                new ProductDisplayPolicy());
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
    void list_shouldQueryLocalDbEvenForAdminAllFilterWithoutCallingUpstream() throws Exception {
        // 改造后：即使是 admin + all filter，也走 DB，不调抖音（504 根因修复）
        Map<String, Object> dbPayload = new LinkedHashMap<>();
        dbPayload.put("total", 21L);
        dbPayload.put("activityList", List.of(Map.of(
                "activityId", "3916506",
                "activityName", "星链达客-zy",
                "status", 3,
                "activityStatus", 3,
                "activityStartTime", "2026-05-06",
                "startTime", "2026-05-06",
                "activityEndTime", "2026-08-03",
                "endTime", "2026-08-03",
                "statusText", "报名中"
        )));
        when(colonelActivityService.buildAssignmentListPage(
                eq(1L),
                eq(20L),
                eq(0),
                eq("all"),
                eq(null),
                eq(null),
                any())).thenReturn(dbPayload);

        mockMvc.perform(get("/colonel/activities")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.activityList[0].activityId").value("3916506"))
                .andExpect(jsonPath("$.data.activityList[0].status").value(3))
                .andExpect(jsonPath("$.data.activityList[0].statusText").value("报名中"));

        // 关键断言：admin+all filter 也不调抖音
        verify(douyinActivityGateway, never()).listActivities(any());
    }

    @Test
    void list_shouldReturnNeedSyncHintWhenDbEmpty() throws Exception {
        // 改造后：DB 空（total=0）时返回 needSync=true + DATA_NOT_READY 提示，永不调抖音
        Map<String, Object> emptyPayload = new LinkedHashMap<>();
        emptyPayload.put("total", 0L);
        emptyPayload.put("activityList", List.of());
        when(colonelActivityService.buildAssignmentListPage(
                any(Long.class), any(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(emptyPayload);

        mockMvc.perform(get("/colonel/activities")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.needSync").value(true))
                .andExpect(jsonPath("$.data.errorCode").value("DATA_NOT_READY"))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("同步活动")));

        verify(douyinActivityGateway, never()).listActivities(any());
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
    void list_shouldPassDisplayNameResolverWithoutFullUserDto() throws Exception {
        UUID recruiterId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(userDomainFacade.loadUserDisplayNamesByIds(any()))
                .thenReturn(Map.of(recruiterId, "招商负责人"));
        when(colonelActivityService.buildAssignmentListPage(
                eq(1L),
                eq(20L),
                eq(0),
                eq("assigned"),
                eq(null),
                eq(null),
                any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Function<UUID, String> resolver =
                            invocation.getArgument(6, java.util.function.Function.class);
                    String assigneeName = resolver.apply(recruiterId);
                    return Map.of(
                            "total", 1L,
                            "activityList", List.of(Map.of(
                                    "activityId", 3916506L,
                                    "assigneeName", assigneeName
                            ))
                    );
                });

        mockMvc.perform(get("/colonel/activities")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("assignmentFilter", "assigned")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityList[0].assigneeName").value("招商负责人"));

        verify(userDomainFacade).loadUserDisplayNamesByIds(any());
        verify(userDomainFacade, never()).getUserById(any());
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
    void listProducts_shouldExposeBizStatusFieldsFromLocalSnapshot() throws Exception {
        // 改造后：DB 有快照时走 DB，永远不调抖音
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

        when(productActivitySyncApplicationService.loadActivityProductList(any())).thenReturn(listView);

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

        ArgumentCaptor<ProductActivitySyncApplicationService.ActivityProductListQueryCommand> queryCaptor =
                ArgumentCaptor.forClass(ProductActivitySyncApplicationService.ActivityProductListQueryCommand.class);
        verify(productActivitySyncApplicationService).loadActivityProductList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().activityId()).isEqualTo("100018");
        assertThat(queryCaptor.getValue().count()).isEqualTo(20);
        assertThat(queryCaptor.getValue().productInfo()).isNull();
        assertThat(queryCaptor.getValue().bizStatus()).isNull();
        assertThat(queryCaptor.getValue().status()).isNull();
        // 关键断言：DB 有快照时绝不触发刷新入口
        verify(productActivitySyncApplicationService, never()).refreshActivityProductList(any());
    }

    @Test
    void listProducts_shouldNeverCallUpstreamByDefaultAndReturnNeedSyncWhenNoSnapshots() throws Exception {
        // 改造后（504 根因修复）：refresh=false 且 DB 无快照时，由 product application 返回 needSync 提示
        Map<String, Object> hintPayload = new LinkedHashMap<>();
        hintPayload.put("items", List.of());
        hintPayload.put("total", 0L);
        hintPayload.put("activityId", "100018");
        hintPayload.put("needSync", Boolean.TRUE);
        hintPayload.put("errorCode", "DATA_NOT_READY");
        hintPayload.put("message", "该活动尚未同步商品，请先点击「同步商品」");
        hintPayload.put("lastSyncAt", null);
        when(productActivitySyncApplicationService.loadActivityProductList(any())).thenReturn(hintPayload);

        mockMvc.perform(get("/colonel/activities/{activityId}/products", "100018")
                        .param("searchType", "4")
                        .param("sortType", "1")
                        .param("count", "20")
                        .param("retrieveMode", "1")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value("100018"))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.needSync").value(true))
                .andExpect(jsonPath("$.data.errorCode").value("DATA_NOT_READY"))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("同步商品")))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());

        // 关键断言：默认 refresh=false 时绝不触发刷新入口
        verify(productActivitySyncApplicationService).loadActivityProductList(any());
        verify(productActivitySyncApplicationService, never()).refreshActivityProductList(any());
        verify(productService, never()).upsertSnapshots(any(), any());
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

        when(productActivitySyncApplicationService.loadActivityProductList(any())).thenReturn(listView);

        mockMvc.perform(get("/colonel/activities/{activityId}/products", "100018")
                        .param("count", "10")
                        .param("cursor", "cursor-1")
                        .param("productInfo", "本地")
                        .param("bizStatus", "PENDING_AUDIT")
                        .param("status", "1")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("本地快照商品"));

        ArgumentCaptor<ProductActivitySyncApplicationService.ActivityProductListQueryCommand> queryCaptor =
                ArgumentCaptor.forClass(ProductActivitySyncApplicationService.ActivityProductListQueryCommand.class);
        verify(productActivitySyncApplicationService).loadActivityProductList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().activityId()).isEqualTo("100018");
        assertThat(queryCaptor.getValue().count()).isEqualTo(10);
        assertThat(queryCaptor.getValue().cursor()).isEqualTo("cursor-1");
        assertThat(queryCaptor.getValue().productInfo()).isEqualTo("本地");
        assertThat(queryCaptor.getValue().bizStatus()).isEqualTo("PENDING_AUDIT");
        assertThat(queryCaptor.getValue().status()).isEqualTo(1);
        verify(productActivitySyncApplicationService, never()).refreshActivityProductList(any());
        verify(productService, never()).upsertSnapshots(eq("100018"), any());
    }

    @Test
    void listProducts_shouldRejectUnsupportedActivityProductStatus() throws Exception {
        mockMvc.perform(get("/colonel/activities/{activityId}/products", "100018")
                        .param("status", "4")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("商品状态仅支持 0=待审核、1=推广中、2=申请未通过、3=合作已终止、6=合作已到期"));

        verify(productActivitySyncApplicationService, never()).loadActivityProductList(any());
        verify(productActivitySyncApplicationService, never()).refreshActivityProductList(any());
    }

    @Test
    void listProducts_refreshTrueShouldBypassExistingSnapshotsAndRefreshFromGateway() throws Exception {
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
        listView.put("syncStats", Map.of(
                "libraryEntryCount", 1,
                "autoLibraryEligible", true));

        when(productActivitySyncApplicationService.refreshActivityProductList(any())).thenReturn(listView);

        mockMvc.perform(get("/colonel/activities/{activityId}/products", "100018")
                        .param("count", "20")
                        .param("refresh", "true")
                        .param("appId", "APP-1")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value(100018))
                .andExpect(jsonPath("$.data.items[0].productId").value(9002))
                .andExpect(jsonPath("$.data.nextCursor").value("fresh-cursor"))
                .andExpect(jsonPath("$.data.syncStats.libraryEntryCount").value(1))
                .andExpect(jsonPath("$.data.syncStats.autoLibraryEligible").value(true));

        verify(productService, never()).hasActivitySnapshots("100018");
        verify(colonelActivityService).syncActivitySummaryFromUpstream(eq("100018"), eq("APP-1"));
        ArgumentCaptor<ProductActivitySyncApplicationService.ActivityProductListRefreshCommand> commandCaptor =
                ArgumentCaptor.forClass(ProductActivitySyncApplicationService.ActivityProductListRefreshCommand.class);
        verify(productActivitySyncApplicationService).refreshActivityProductList(commandCaptor.capture());
        assertThat(commandCaptor.getValue().activityId()).isEqualTo("100018");
        assertThat(commandCaptor.getValue().searchType()).isEqualTo(4L);
        assertThat(commandCaptor.getValue().sortType()).isEqualTo(1L);
        assertThat(commandCaptor.getValue().count()).isEqualTo(20);
        assertThat(commandCaptor.getValue().cooperationType()).isEqualTo(0);
        assertThat(commandCaptor.getValue().retrieveMode()).isEqualTo(1L);
        assertThat(commandCaptor.getValue().appId()).isEqualTo("APP-1");
        verify(productService, never()).upsertSnapshots(eq("100018"), any());
        verify(productService, never()).buildActivityProductListViewFromDb(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void syncProducts_shouldTriggerBackgroundSyncAndReturnAcceptedImmediately() throws Exception {
        when(productActivityManualSyncService.trigger("100018", null)).thenReturn(
                new ProductActivityManualSyncService.SyncTriggerResult("100018", "ACCEPTED"));

        mockMvc.perform(post("/colonel/activities/{activityId}/products/sync", "100018")
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value("100018"))
                .andExpect(jsonPath("$.data.syncStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.message").value("商品同步已转入后台执行"));

        verify(productActivityManualSyncService).trigger("100018", null);
        verify(productService, never()).refreshActivitySnapshots(any());
    }

    @Test
    void listProducts_shouldMapProductGatewayErrorsToBusinessMessagesOnRefresh() {
        // 改造后：refresh=true 仍调抖音（用户主动触发），错误映射测试保留
        // 默认 refresh=false 已不再调抖音（见 listProducts_shouldNeverCallUpstreamByDefaultAndReturnNeedSyncWhenNoSnapshots）
        record ErrorCase(DouyinApiException exception, String messageContains) {
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
            ProductService localProductService = mock(ProductService.class);
            ProductActivitySyncApplicationService localProductActivitySyncApplicationService =
                    mock(ProductActivitySyncApplicationService.class);
            // refresh=true 路径由 product application 触发上游刷新并向 Controller 透出 DouyinApiException
            when(localProductActivitySyncApplicationService.refreshActivityProductList(any()))
                    .thenThrow(item.exception());
            ColonelActivityController errorController = new ColonelActivityController(
                    douyinActivityGateway,
                    localProductService,
                    new ShortTtlCacheService(),
                    sysUserService,
                    colonelActivityService,
                    productActivityManualSyncService,
                    localProductActivitySyncApplicationService,
                    userDomainFacade,
                    activityAccessService,
                    new ProductDisplayPolicy());

            // refresh=true：触发 syncActivitySummaryFromUpstream + refreshActivityProductList
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
                    true, // refresh=true 触发上游调用
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(RoleCodes.ADMIN)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(item.messageContains());
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
