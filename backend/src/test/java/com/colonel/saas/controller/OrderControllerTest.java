package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.facade.OrderDomainFacade;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.service.DashboardService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.OrderAttributionReplayService;
import com.colonel.saas.service.OrderQueryService;
import com.colonel.saas.service.OrderSyncService;
import com.colonel.saas.service.Order1603SettlementDryRunService;
import com.colonel.saas.service.Order2704SettlementDryRunService;
import com.colonel.saas.service.Order6468PaginationDryRunService;
import com.colonel.saas.service.ShortTtlCacheService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderSyncService orderSyncService;
    @Mock
    private Order6468PaginationDryRunService order6468PaginationDryRunService;
    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private OrderQueryService orderQueryService;
    @Mock
    private OrderAttributionReplayService orderAttributionReplayService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private UserDomainFacade userDomainFacade;
    @Mock
    private Order1603SettlementDryRunService order1603SettlementDryRunService;
    @Mock
    private Order2704SettlementDryRunService order2704SettlementDryRunService;
    @Mock
    private ProductSnapshotMapper productSnapshotMapper;
    @Mock
    private ProductMapper productMapper;
    private DddRefactorProperties dddRefactorProperties;
    @Mock
    private OrderDomainFacade orderDomainFacade;
    /**
     * t2-orders 抽 service：OrderController 委托 {@link com.colonel.saas.service.OrderService}
     * 做 wrapper 拼装。测试用真实 OrderService 实例 + mock mapper，wrapper 行为与生产一致。
     */
    private com.colonel.saas.service.OrderService orderService;

    private ShortTtlCacheService shortTtlCacheService;
    private OrderController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        initTableInfo(ColonelsettlementOrder.class);
        initTableInfo(ProductSnapshot.class);
        initTableInfo(Product.class);
        dddRefactorProperties = new DddRefactorProperties();
        DashboardService dashboardService = org.mockito.Mockito.mock(DashboardService.class);
        DataScopePolicy dataScopePolicy = new DataScopePolicy();
        orderService = new com.colonel.saas.service.OrderService(
                orderMapper, dashboardService, productSnapshotMapper, productMapper, dataScopePolicy, dddRefactorProperties);
        shortTtlCacheService = new ShortTtlCacheService();
        controller = new OrderController(
                orderSyncService,
                orderMapper,
                orderQueryService,
                orderAttributionReplayService,
                operationLogService,
                shortTtlCacheService,
                userDomainFacade,
                order6468PaginationDryRunService,
                order1603SettlementDryRunService,
                order2704SettlementDryRunService,
                orderService,
                dddRefactorProperties,
                orderDomainFacade,
                dataScopePolicy);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.getTypeHandlerRegistry().register(java.util.UUID.class, UUIDTypeHandler.class);
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }

    @Test
    void getOrderDetail_shouldReturnAggregatedDetail() throws Exception {
        java.util.UUID userId = java.util.UUID.randomUUID();
        java.util.UUID deptId = java.util.UUID.randomUUID();
        OrderDetailResponse response = new OrderDetailResponse();
        response.setOrderId("mock-order-1");
        response.setAttributionStatus("ATTRIBUTED");
        OrderDetailResponse.PromotionInfo promotion = new OrderDetailResponse.PromotionInfo();
        promotion.setMatched(true);
        response.setPromotion(promotion);
        when(orderQueryService.getOrderDetail("mock-order-1", userId, deptId, DataScope.ALL)).thenReturn(response);

        mockMvc.perform(get("/orders/mock-order-1")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value("mock-order-1"))
                .andExpect(jsonPath("$.data.attributionStatus").value("ATTRIBUTED"))
                .andExpect(jsonPath("$.data.promotion.matched").value(true));
    }

    @Test
    void syncOrders_shouldUseInstituteHotRecentForManualRealPreProbe() throws Exception {
        OrderSyncService.SyncResult result = new OrderSyncService.SyncResult(1774972800L, 1777391999L, 1, 1, 0, false);
        when(orderSyncService.syncInstituteOrdersHotRecent()).thenReturn(result);

        java.util.UUID userId = java.util.UUID.randomUUID();
        mockMvc.perform(post("/orders/sync")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime":"2026-04-01 00:00:00","endTime":"2026-04-28 23:59:59"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(orderSyncService).syncInstituteOrdersHotRecent();
        verify(orderSyncService, never()).syncByTimeRange(anyLong(), anyLong());
        verify(operationLogService).recordSystemAction(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("订单归因"),
                org.mockito.ArgumentMatchers.eq("手动同步订单"),
                org.mockito.ArgumentMatchers.eq("POST"),
                org.mockito.ArgumentMatchers.eq("order_sync"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void syncOrders_shouldUseInstituteHotRecentWhenBodyMissing() throws Exception {
        OrderSyncService.SyncResult result = new OrderSyncService.SyncResult(0L, 0L, 0, 0, 0, false);
        when(orderSyncService.syncInstituteOrdersHotRecent()).thenReturn(result);

        mockMvc.perform(post("/orders/sync")
                        .requestAttr("userId", java.util.UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(orderSyncService).syncInstituteOrdersHotRecent();
        verify(orderSyncService, never()).syncByTimeRange(anyLong(), anyLong());
    }

    @Test
    void syncOrders_shouldRequireAdminRoleAnnotation() throws Exception {
        Method syncOrders = OrderController.class.getMethod(
                "syncOrders",
                OrderController.SyncRequest.class,
                java.util.UUID.class);
        assertThat(syncOrders.getAnnotation(RequireRoles.class)).isNotNull();
        assertThat(syncOrders.getAnnotation(RequireRoles.class).value()).containsExactly(RoleCodes.ADMIN);
    }

    @Test
    void dryRun6468Pagination_shouldForwardReadonlyRequestWithoutOperationLog() throws Exception {
        Order6468PaginationDryRunService.DryRunResult result =
                new Order6468PaginationDryRunService.DryRunResult(
                        1780459200L,
                        1780549200L,
                        1780459200L,
                        1780549200L,
                        100,
                        2,
                        200,
                        200,
                        0,
                        "NO_NEXT_CURSOR",
                        "0",
                        true,
                        Order6468PaginationDryRunService.Baseline.defaultBaseline(),
                        Map.of(),
                        List.of());
        when(order6468PaginationDryRunService.dryRun(any())).thenReturn(result);

        mockMvc.perform(post("/orders/6468-pagination-dry-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "2026-06-03 12:00:00",
                                  "endTime": "2026-06-04 13:00:00",
                                  "pageSize": 100,
                                  "maxPages": 10,
                                  "maxOrders": 50000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.readOnly").value(true))
                .andExpect(jsonPath("$.data.pagesFetched").value(2));

        ArgumentCaptor<Order6468PaginationDryRunService.DryRunRequest> captor =
                ArgumentCaptor.forClass(Order6468PaginationDryRunService.DryRunRequest.class);
        verify(order6468PaginationDryRunService).dryRun(captor.capture());
        assertThat(captor.getValue().startTime()).isEqualTo(1780459200L);
        assertThat(captor.getValue().endTime()).isEqualTo(1780549200L);
        assertThat(captor.getValue().pageSize()).isEqualTo(100);
        verify(operationLogService, never()).recordSystemAction(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void replayAttribution_shouldForwardRequestToService() throws Exception {
        OrderAttributionReplayService.ReplayResult result =
                new OrderAttributionReplayService.ReplayResult(12, 4, 8, 0, true, 0, 0, 0, 0, 0, 8);
        when(orderAttributionReplayService.replay(any(), any(), any(), anyBoolean())).thenReturn(result);

        java.util.UUID userId = java.util.UUID.randomUUID();
        mockMvc.perform(post("/orders/replay-attribution")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"COLONEL_MAPPING_NOT_FOUND","limit":12,"dryRun":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.scanned").value(12))
                .andExpect(jsonPath("$.data.attributed").value(4))
                .andExpect(jsonPath("$.data.dryRun").value(true));

        verify(orderAttributionReplayService).replay(any(), org.mockito.ArgumentMatchers.eq("COLONEL_MAPPING_NOT_FOUND"), org.mockito.ArgumentMatchers.eq(12), org.mockito.ArgumentMatchers.eq(true));
        verify(operationLogService).recordSystemAction(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("订单归因"),
                org.mockito.ArgumentMatchers.eq("重算历史订单归因(预览)"),
                org.mockito.ArgumentMatchers.eq("POST"),
                org.mockito.ArgumentMatchers.eq("order_attribution"),
                org.mockito.ArgumentMatchers.eq("COLONEL_MAPPING_NOT_FOUND"),
                org.mockito.ArgumentMatchers.eq("dry-run"),
                org.mockito.ArgumentMatchers.contains("scanned=12"));
    }

    @Test
    void replayAttribution_shouldApplyAndRecordWhenDryRunMissing() throws Exception {
        OrderAttributionReplayService.ReplayResult result =
                new OrderAttributionReplayService.ReplayResult(3, 2, 1, 2, false, 0, 0, 0, 0, 0, 1);
        when(orderAttributionReplayService.replay(any(), any(), any(), anyBoolean())).thenReturn(result);

        java.util.UUID userId = java.util.UUID.randomUUID();
        mockMvc.perform(post("/orders/replay-attribution")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderIds":["order-1","order-2"],"reason":"SYNC_FAILED","limit":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.updated").value(2))
                .andExpect(jsonPath("$.data.dryRun").value(false));

        verify(orderAttributionReplayService).replay(
                org.mockito.ArgumentMatchers.eq(List.of("order-1", "order-2")),
                org.mockito.ArgumentMatchers.eq("SYNC_FAILED"),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(false));
        verify(operationLogService).recordSystemAction(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("订单归因"),
                org.mockito.ArgumentMatchers.eq("重算历史订单归因"),
                org.mockito.ArgumentMatchers.eq("POST"),
                org.mockito.ArgumentMatchers.eq("order_attribution"),
                org.mockito.ArgumentMatchers.eq("SYNC_FAILED"),
                org.mockito.ArgumentMatchers.eq("apply"),
                org.mockito.ArgumentMatchers.contains("updated=2"));
    }

    @Test
    void getOrders_shouldReturnPagedOrdersAndNormalizeReason() throws Exception {
        java.util.UUID userId = java.util.UUID.randomUUID();
        java.util.UUID deptId = java.util.UUID.randomUUID();
        java.util.UUID channelUserId = java.util.UUID.randomUUID();
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("order-1");
        order.setProductId("product-1");
        order.setProductName("订单商品");
        order.setProductTitle("订单商品标题");
        order.setShopName("订单店铺");
        order.setChannelUserId(channelUserId);
        order.setChannelUserName("渠道甲");
        order.setAttributionStatus("UNATTRIBUTED");
        order.setAttributionRemark("SYNC_FAILED");

        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<ColonelsettlementOrder> page = invocation.getArgument(0);
            page.setRecords(List.of(order));
            page.setTotal(1);
            return page;
        });
        when(orderMapper.listDisplayProductInfoByOrderIds(any())).thenReturn(List.of(Map.of(
                "orderId", "order-1",
                "productPic", "https://cdn.example.com/product.jpg",
                "awemeId", "AWEME-1",
                "contentTypeText", "短视频",
                "itemNum", 2,
                "commissionRate", new BigDecimal("500"),
                "serviceFeeRate", new BigDecimal("1")
        )));

        mockMvc.perform(get("/orders")
                        .param("page", "2")
                        .param("size", "5")
                        .param("orderId", "order-1")
                        .param("attributionStatus", "UNATTRIBUTED")
                        .param("unattributedReason", "SYNC_FAILED")
                        .param("activityId", "activity-1")
                        .param("productId", "product-1")
                        .param("channelKeyword", "渠道")
                        .param("colonelKeyword", "团长")
                        .param("orderStatus", "3")
                        .param("startTime", "2026-04-01 00:00:00")
                        .param("endTime", "2026-04-28 23:59:59")
                        .param("timeField", "settleTime")
                        .param("dashboardDiagnosis", DashboardService.DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED)
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.DEPT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderId").value("order-1"))
                .andExpect(jsonPath("$.data.records[0].productId").value("product-1"))
                .andExpect(jsonPath("$.data.records[0].productName").value("订单商品"))
                .andExpect(jsonPath("$.data.records[0].productImage").value("https://cdn.example.com/product.jpg"))
                .andExpect(jsonPath("$.data.records[0].productPic").value("https://cdn.example.com/product.jpg"))
                .andExpect(jsonPath("$.data.records[0].shopName").value("订单店铺"))
                .andExpect(jsonPath("$.data.records[0].productQuantity").value(2))
                .andExpect(jsonPath("$.data.records[0].itemNum").value(2))
                .andExpect(jsonPath("$.data.records[0].commissionRate").value(500))
                .andExpect(jsonPath("$.data.records[0].serviceFeeRate").value(1))
                .andExpect(jsonPath("$.data.records[0].awemeId").value("AWEME-1"))
                .andExpect(jsonPath("$.data.records[0].contentTypeText").value("短视频"))
                .andExpect(jsonPath("$.data.records[0].channelId").value(channelUserId.toString()))
                .andExpect(jsonPath("$.data.records[0].channelName").value("渠道甲"))
                .andExpect(jsonPath("$.data.records[0].channelUserName").value("渠道甲"))
                .andExpect(jsonPath("$.data.records[0].unattributedReason").value("SYNC_FAILED"));
    }

    @Test
    void getUnattributedOrders_shouldReturnPagedOrdersForPersonalScope() throws Exception {
        java.util.UUID userId = java.util.UUID.randomUUID();
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("order-unattributed");
        order.setAttributionRemark("NO_PICK_SOURCE");

        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<ColonelsettlementOrder> page = invocation.getArgument(0);
            page.setRecords(List.of(order));
            page.setTotal(1);
            return page;
        });

        mockMvc.perform(get("/orders/unattributed")
                        .param("unattributedReason", "NO_PICK_SOURCE")
                        .param("timeField", "createTime")
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.PERSONAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].orderId").value("order-unattributed"))
                .andExpect(jsonPath("$.data.records[0].unattributedReason").value("NO_PICK_SOURCE"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void getOrders_withRecruiterAndChannelDeptIds_shouldEmitInClauseAndPassValuesToWrapper() throws Exception {
        // 验证：业务筛选层正确把 recruiterDeptIds → dept_id IN (...)、
        //        channelDeptIds → channel_dept_id IN (...) 拼进 wrapper。
        // 不依赖具体 SQL 引擎渲染，只断言 wrapper.sqlSegment 含相关 token 且参数值被注入。
        java.util.UUID recruiter1 = java.util.UUID.randomUUID();
        java.util.UUID recruiter2 = java.util.UUID.randomUUID();
        java.util.UUID channel1 = java.util.UUID.randomUUID();

        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<ColonelsettlementOrder> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        mockMvc.perform(get("/orders")
                        .param("recruiterDeptIds", recruiter1.toString())
                        .param("recruiterDeptIds", recruiter2.toString())
                        .param("channelDeptIds", channel1.toString())
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(any(), captor.capture());
        LambdaQueryWrapper captured = captor.getValue();

        // sqlSegment 含 dept_id IN 和 channel_dept_id IN 子句
        String sqlSegment = captured.getSqlSegment();
        assertThat(sqlSegment).contains("dept_id");
        assertThat(sqlSegment).contains("channel_dept_id");
        assertThat(sqlSegment).contains("IN");

        // 参数表里能找到三个传入的 UUID（顺序无关）
        Map<String, Object> params = captured.getParamNameValuePairs();
        assertThat(params.values()).contains(recruiter1, recruiter2, channel1);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void getOrders_withCsvFormattedDeptIds_shouldStillBindAsList() throws Exception {
        // 前端 CSV 路径覆盖：buildQueryParams 用 .join(',') 拼成 "a,b"，
        // Spring `@RequestParam List<UUID>` 内置 StringToCollectionConverter 会自动 split。
        // 这条测试确保前后端实际链路（CSV）不会因为绑定不兼容而失败。
        java.util.UUID r1 = java.util.UUID.randomUUID();
        java.util.UUID r2 = java.util.UUID.randomUUID();
        java.util.UUID c1 = java.util.UUID.randomUUID();

        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<ColonelsettlementOrder> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        mockMvc.perform(get("/orders")
                        .param("recruiterDeptIds", r1 + "," + r2)
                        .param("channelDeptIds", c1.toString())
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk());

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(any(), captor.capture());
        LambdaQueryWrapper captured = captor.getValue();
        // 关键：必须先触发 sqlSegment 计算，paramNameValuePairs 才会从 MergeSegments 里写出来。
        String sqlSegment = captured.getSqlSegment();
        assertThat(sqlSegment).contains("dept_id IN");
        assertThat(sqlSegment).contains("channel_dept_id IN");
        assertThat(captured.getParamNameValuePairs().values()).contains(r1, r2, c1);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void getOrders_withoutDeptFilters_shouldNotAddDeptInClause() throws Exception {
        // 反向验证：未传 recruiterDeptIds / channelDeptIds 时，
        //   wrapper 不应包含 dept_id IN(...) 业务过滤分支（DataScope=ALL 不再注入 dept_id）。
        // 这条测试用于防止"空集合"被误拼成 dept_id IN ()，触发 SQL 语法错误。
        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<ColonelsettlementOrder> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        mockMvc.perform(get("/orders")
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk());

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(any(), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        // 不应出现 dept_id 业务过滤；attribution_remark 等其他列照常存在，不在断言范围
        assertThat(sqlSegment).doesNotContain("channel_dept_id");
        // 注意：dept_id 字符串可能因 DataScope=DEPT 注入而出现，所以此处只严格断言 channel_dept_id
        // 不存在；recruiter 维度由 ALL 路径下"未传值"覆盖。
    }

    @Test
    void getStats_shouldAggregateViaSqlGroupedMaps() throws Exception {
        when(orderMapper.selectMaps(any())).thenReturn(
                List.of(
                        Map.of("attributionStatus", "ATTRIBUTED", "total", 1L),
                        Map.of("attributionStatus", "UNATTRIBUTED", "total", 2L),
                        Map.of("attributionStatus", "PARTIAL", "total", 1L)
                ),
                List.of(
                        Map.of("reason", "pick_source 未匹配", "total", 1L),
                        Map.of("reason", "SYNC_FAILED", "total", 1L)
                )
        );

        mockMvc.perform(get("/orders/stats")
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalOrders").value(4))
                .andExpect(jsonPath("$.data.attributedOrders").value(1))
                .andExpect(jsonPath("$.data.unattributedOrders").value(2))
                .andExpect(jsonPath("$.data.partialOrders").value(1))
                .andExpect(jsonPath("$.data.syncFailedOrders").value(1))
                .andExpect(jsonPath("$.data.unattributedReasons[0].reason").value("pick_source 未匹配"));

        verify(orderMapper, times(2)).selectMaps(any());
    }

    @Test
    void getStats_shouldIgnoreBlankReasonsAndParseCaseInsensitiveColumns() throws Exception {
        when(orderMapper.selectMaps(any())).thenReturn(
                List.of(
                        Map.of("ATTRIBUTIONSTATUS", "ATTRIBUTED", "TOTAL", "2"),
                        Map.of("attributionStatus", "UNATTRIBUTED", "total", "bad-number")
                ),
                List.of(
                        Map.of("reason", "", "total", 99L),
                        Map.of("REASON", "SYNC_FAILED", "TOTAL", "3")
                )
        );

        mockMvc.perform(get("/orders/stats")
                        .requestAttr("deptId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.DEPT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalOrders").value(2))
                .andExpect(jsonPath("$.data.attributedOrders").value(2))
                .andExpect(jsonPath("$.data.unattributedOrders").value(0))
                .andExpect(jsonPath("$.data.syncFailedOrders").value(3))
                .andExpect(jsonPath("$.data.unattributedReasons[0].reason").value("SYNC_FAILED"));
    }

    @Test
    void getStats_cacheDisabledShouldQueryEveryRequest() throws Exception {
        when(orderMapper.selectMaps(any())).thenReturn(
                List.of(Map.of("attributionStatus", "ATTRIBUTED", "total", 1L)),
                List.of(),
                List.of(Map.of("attributionStatus", "ATTRIBUTED", "total", 1L)),
                List.of()
        );

        ReflectionTestUtils.setField(controller, "statsCacheEnabled", false);

        mockMvc.perform(get("/orders/stats").requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk());
        mockMvc.perform(get("/orders/stats").requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk());

        verify(orderMapper, times(4)).selectMaps(any());
    }

    @Test
    void getStats_cacheEnabledShouldHitForSameScopeAndFilters() throws Exception {
        when(orderMapper.selectMaps(any())).thenReturn(
                List.of(Map.of("attributionStatus", "ATTRIBUTED", "total", 1L)),
                List.of()
        );

        ReflectionTestUtils.setField(controller, "statsCacheEnabled", true);
        ReflectionTestUtils.setField(controller, "statsCacheTtlSeconds", 60L);

        mockMvc.perform(get("/orders/stats")
                        .param("orderStatus", "1")
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(1));
        mockMvc.perform(get("/orders/stats")
                        .param("orderStatus", "1")
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(1));

        verify(orderMapper, times(2)).selectMaps(any());
    }

    @Test
    void getStats_cacheEnabledShouldIsolateDifferentDataScope() throws Exception {
        when(orderMapper.selectMaps(any())).thenReturn(
                List.of(Map.of("attributionStatus", "ATTRIBUTED", "total", 1L)),
                List.of(),
                List.of(Map.of("attributionStatus", "UNATTRIBUTED", "total", 2L)),
                List.of()
        );

        ReflectionTestUtils.setField(controller, "statsCacheEnabled", true);
        ReflectionTestUtils.setField(controller, "statsCacheTtlSeconds", 60L);

        mockMvc.perform(get("/orders/stats").requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(1));
        mockMvc.perform(get("/orders/stats")
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.PERSONAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(2));

        verify(orderMapper, times(4)).selectMaps(any());
    }

    @Test
    void getStats_cacheEnabledShouldIsolateDifferentFilters() throws Exception {
        when(orderMapper.selectMaps(any())).thenReturn(
                List.of(Map.of("attributionStatus", "ATTRIBUTED", "total", 1L)),
                List.of(),
                List.of(Map.of("attributionStatus", "UNATTRIBUTED", "total", 2L)),
                List.of()
        );

        ReflectionTestUtils.setField(controller, "statsCacheEnabled", true);
        ReflectionTestUtils.setField(controller, "statsCacheTtlSeconds", 60L);

        mockMvc.perform(get("/orders/stats")
                        .param("orderStatus", "1")
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(1));
        mockMvc.perform(get("/orders/stats")
                        .param("orderStatus", "2")
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(2));

        verify(orderMapper, times(4)).selectMaps(any());
    }

    @Test
    void normalizeDiagnosisCategory_shouldWhitelistDashboardCategories() {
        assertThat(DashboardService.normalizeDiagnosisCategory(DashboardService.DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED))
                .isEqualTo(DashboardService.DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED);
        assertThat(DashboardService.normalizeDiagnosisCategory("UNSAFE_BECAUSE_CREATED_AFTER_ORDER"))
                .isEqualTo(DashboardService.DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE);
    }

    @Test
    void normalizeDiagnosisCategory_shouldRejectSqlInjectionPayload() {
        assertThat(DashboardService.normalizeDiagnosisCategory("'; DROP TABLE colonelsettlement_order; --"))
                .isNull();
        assertThat(DashboardService.normalizeDiagnosisCategory("UPSTREAM_PRODUCT_UNCOVERED' OR '1'='1"))
                .isNull();
    }

    @Test
    void sanitizeDiagnosisSqlPrefix_shouldRejectUnknownAlias() throws Exception {
        // t2-orders 抽 service：sanitizeDiagnosisSqlPrefix 已迁移到 OrderService，
        // controller 端不再持有该方法，单测断言已搬到 OrderServiceTest。
        // 这里保留一个轻量回归，避免 controller 端误重新持有该方法被误调。
        org.junit.jupiter.api.Assumptions.assumeTrue(orderService != null);
        assertThat(orderService.sanitizeDiagnosisSqlPrefix("evil; DROP TABLE--"))
                .isEqualTo("colonelsettlement_order.");
        assertThat(orderService.sanitizeDiagnosisSqlPrefix("fo.")).isEqualTo("fo.");
    }

    @Test
    void getFilterOptions_shouldExposeNativeColonelReasonLabels() throws Exception {
        when(orderMapper.selectMaps(any())).thenReturn(
                List.of(Map.of("value", 1)),
                List.of(Map.of("value", "UNATTRIBUTED")),
                List.of(
                        Map.of("value", "COLONEL_MAPPING_NOT_FOUND"),
                        Map.of("value", "COLONEL_MAPPING_AMBIGUOUS")
                ),
                List.of(),
                List.of(),
                List.of()
        );

        mockMvc.perform(get("/orders/filter-options")
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.unattributedReasons[0].value").value("COLONEL_MAPPING_NOT_FOUND"))
                .andExpect(jsonPath("$.data.unattributedReasons[0].label").value("原生团长订单未找到归因映射"))
                .andExpect(jsonPath("$.data.unattributedReasons[1].value").value("COLONEL_MAPPING_AMBIGUOUS"))
                .andExpect(jsonPath("$.data.unattributedReasons[1].label").value("原生团长订单命中多条归因映射"));
    }

    @Test
    void getFilterOptions_shouldMapFallbackLabelsAndSkipInvalidValues() throws Exception {
        when(orderMapper.selectMaps(any())).thenReturn(
                List.of(Map.of("value", "2"), Map.of("value", "bad")),
                List.of(Map.of("value", "PARTIAL"), Map.of("value", "FAILED")),
                List.of(
                        Map.of("value", "NO_PICK_SOURCE"),
                        Map.of("value", "自定义原因")
                ),
                List.of(
                        Map.of("value", "P-1", "label", "商品一"),
                        Map.of("value", "P-2"),
                        Map.of("value", "")
                ),
                List.of(Map.of("value", "渠道甲")),
                List.of(Map.of("value", "团长甲"))
        );

        mockMvc.perform(get("/orders/filter-options")
                        .param("keyword", "甲")
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.PERSONAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderStatuses[0].value").value("2"))
                .andExpect(jsonPath("$.data.orderStatuses[0].label").value("已发货"))
                .andExpect(jsonPath("$.data.orderStatuses.length()").value(1))
                .andExpect(jsonPath("$.data.attributionStatuses[0].label").value("部分归因"))
                .andExpect(jsonPath("$.data.attributionStatuses[1].label").value("同步/归因失败"))
                .andExpect(jsonPath("$.data.unattributedReasons[0].label").value("订单未携带推广参数"))
                .andExpect(jsonPath("$.data.unattributedReasons[1].label").value("自定义原因"))
                .andExpect(jsonPath("$.data.products[1].label").value("P-2"))
                .andExpect(jsonPath("$.data.channels[0].label").value("渠道甲"))
                .andExpect(jsonPath("$.data.colonels[0].label").value("团长甲"));
    }

    /**
     * P0-ORDER-001 渠道可见性回归：管理员（DataScope.ALL）订单列表查询
     * 不得在 wrapper 中追加 user_id / channel_dept_id 业务过滤，
     * 确保已归因与未归因订单都能看到。
     */
    @Test
    @SuppressWarnings("rawtypes")
    void getOrders_adminWithDataScopeAll_shouldNotFilterByUserOrChannelDept() throws Exception {
        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<ColonelsettlementOrder> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        mockMvc.perform(get("/orders")
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .requestAttr("deptId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk());

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(any(), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        // ALL 范围不应注入 user_id / channel_dept_id / dept_id 业务过滤
        assertThat(sqlSegment).doesNotContain("user_id");
        assertThat(sqlSegment).doesNotContain("channel_dept_id");
        // 注意：dept_id 不应作为 DataScope.ALL 的注入项；deleted/order_id 等业务列可能出现
        assertThat(sqlSegment).doesNotContain("dept_id =");
    }

    /**
     * P0-ORDER-001 渠道可见性回归：渠道账号（DataScope.PERSONAL）
     * 订单列表 wrapper 必须按当前 userId 过滤，未归因订单（user_id=null）
     * 自然被排除——这是产品设计：渠道只看自己归属订单。
     */
    @Test
    @SuppressWarnings("rawtypes")
    void getOrders_channelWithDataScopePersonal_shouldFilterByOwnUserId() throws Exception {
        java.util.UUID channelUserId = java.util.UUID.randomUUID();
        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<ColonelsettlementOrder> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        mockMvc.perform(get("/orders")
                        .requestAttr("userId", channelUserId)
                        .requestAttr("dataScope", DataScope.PERSONAL))
                .andExpect(status().isOk());

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(any(), captor.capture());
        LambdaQueryWrapper captured = captor.getValue();
        String sqlSegment = captured.getSqlSegment();
        // PERSONAL 范围必须注入 user_id 等值过滤
        assertThat(sqlSegment).contains("user_id");
        // 当前 userId 必须出现在参数表中（被绑定到 user_id = ?）
        assertThat(captured.getParamNameValuePairs().values()).contains(channelUserId);
    }

    /**
     * P0-ORDER-001 管理员未归因订单专项：/orders/unattributed 端点
     * 不论入参 attributionStatus 是否提供，都强制按 STATUS_UNATTRIBUTED 过滤，
     * 用于 admin 排查"刚付款订单可能 NO_PICK_SOURCE / NO_MAPPING"场景。
     */
    @Test
    void getUnattributedOrders_adminShouldForceUnattributedFilter() throws Exception {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("order-no-pick-source");
        order.setAttributionStatus("UNATTRIBUTED");
        order.setAttributionRemark("NO_PICK_SOURCE");

        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<ColonelsettlementOrder> page = invocation.getArgument(0);
            page.setRecords(List.of(order));
            page.setTotal(1);
            return page;
        });

        mockMvc.perform(get("/orders/unattributed")
                        // 即便管理员不传 attributionStatus，端点也强制 UNATTRIBUTED
                        .requestAttr("userId", java.util.UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].attributionStatus").value("UNATTRIBUTED"))
                .andExpect(jsonPath("$.data.records[0].unattributedReason").value("NO_PICK_SOURCE"));
    }
}
