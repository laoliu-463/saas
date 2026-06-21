package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OrderService 单测。
 *
 * <p>t2-orders 抽 service:验证 wrapper 拼装、归因 / 时间 / 数据范围 / 诊断分类 / 行规范化
 * 行为,不依赖 MyBatis-Plus lambda cache(本测试只验证 wrapper 实例的 sqlSegment 与参数,
 * 不触发 mapper 的实际 SQL 渲染)。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private DashboardService dashboardService;
    @Mock
    private ProductSnapshotMapper productSnapshotMapper;
    @Mock
    private ProductMapper productMapper;
    private OrderService service;

    @BeforeEach
    void setUp() {
        initTableInfo(ProductSnapshot.class);
        initTableInfo(Product.class);
        initTableInfo(ColonelsettlementOrder.class);
        service = new OrderService(orderMapper, dashboardService, productSnapshotMapper, productMapper, new DataScopePolicy(), new com.colonel.saas.config.DddRefactorProperties());
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.getTypeHandlerRegistry().register(java.util.UUID.class, UUIDTypeHandler.class);
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }

    // ============================================================
    // buildWrapper — 列表 wrapper 拼装
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void buildWrapper_shouldBindOrderIdAsEqFilter() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = service.buildWrapper(
                "ORDER-1", "ATTRIBUTED", "NO_PICK_SOURCE", "ACT-1", "P-1",
                "渠道甲", "招商甲", 3,
                "2026-04-01 00:00:00", "2026-04-28 23:59:59",
                "createTime", null,
                List.of(), List.of()
        );

        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("order_id");
        assertThat(sql).contains("product_id");
        assertThat(sql).contains("attribution_status");
        assertThat(sql).contains("colonel_activity_id");
        assertThat(sql).contains("order_status");
        assertThat(sql).contains("create_time");
        // channelKeyword/colonelKeyword 走嵌套 LIKE
        assertThat(sql).contains("channel_user_name");
        assertThat(sql).contains("channel_user_id");
        assertThat(sql).contains("colonel_user_name");
        assertThat(sql).contains("colonel_user_id");
        assertThat(sql).contains("LIKE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildWrapper_fullUuidChannelKeywordShouldUseEqFastPath() {
        UUID channelUserId = UUID.randomUUID();
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = service.buildWrapper(
                null, null, null, null, null,
                channelUserId.toString(), null, null,
                null, null,
                null, null,
                List.of(), List.of()
        );

        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("channel_user_id");
        assertThat(sql).doesNotContain("LIKE");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(channelUserId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildWrapper_fullUuidColonelKeywordShouldUseEqFastPath() {
        UUID colonelUserId = UUID.randomUUID();
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = service.buildWrapper(
                null, null, null, null, null,
                null, colonelUserId.toString(), null,
                null, null,
                null, null,
                List.of(), List.of()
        );

        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("colonel_user_id");
        assertThat(sql).doesNotContain("LIKE");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(colonelUserId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildWrapper_nonUuidKeywordsShouldKeepNameLikeBehavior() {
        LambdaQueryWrapper<ColonelsettlementOrder> chineseWrapper = service.buildWrapper(
                null, null, null, null, null,
                "渠道甲", null, null,
                null, null,
                null, null,
                List.of(), List.of()
        );
        assertThat(chineseWrapper.getSqlSegment())
                .contains("channel_user_name")
                .contains("channel_user_id")
                .contains("LIKE");

        LambdaQueryWrapper<ColonelsettlementOrder> englishWrapper = service.buildWrapper(
                null, null, null, null, null,
                null, "colonel-A", null,
                null, null,
                null, null,
                List.of(), List.of()
        );
        assertThat(englishWrapper.getSqlSegment())
                .contains("colonel_user_name")
                .contains("colonel_user_id")
                .contains("LIKE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildWrapper_invalidOrPartialUuidKeywordShouldKeepLikeBehavior() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = service.buildWrapper(
                null, null, null, null, null,
                "aaaaaaaa-aaaa", null, null,
                null, null,
                null, null,
                List.of(), List.of()
        );

        assertThat(wrapper.getSqlSegment())
                .contains("channel_user_id")
                .contains("LIKE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildWrapper_shouldHandleBlankFiltersAsNoOp() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = service.buildWrapper(
                null, null, null, null, null,
                null, null, null,
                null, null,
                null, null,
                List.of(), List.of()
        );

        String sql = wrapper.getSqlSegment();
        // 没有具体字段筛选时:deleted 必有;order_id/product_id 等都不会出现
        assertThat(sql).contains("deleted");
        assertThat(sql).doesNotContain("order_id =");
        assertThat(sql).doesNotContain("product_id =");
        assertThat(sql).doesNotContain("attribution_status =");
        assertThat(sql).doesNotContain("LIKE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildWrapper_defaultWindowDisabledShouldNotAddTimeRange() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = service.buildWrapper(
                null, null, null, null, null,
                null, null, null,
                null, null,
                null, null,
                List.of(), List.of()
        );

        assertThat(wrapper.getSqlSegment()).doesNotContain("create_time");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildWrapper_defaultWindowEnabledShouldAddRecentCreateTimeWhenNoExplicitRange() {
        ReflectionTestUtils.setField(service, "defaultWindowEnabled", true);
        ReflectionTestUtils.setField(service, "defaultWindowDays", 30L);

        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = service.buildWrapper(
                null, null, null, null, null,
                null, null, null,
                null, null,
                null, null,
                List.of(), List.of()
        );

        assertThat(wrapper.getSqlSegment()).contains("create_time");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildWrapper_defaultWindowEnabledShouldNotApplyToOrderIdQuery() {
        ReflectionTestUtils.setField(service, "defaultWindowEnabled", true);
        ReflectionTestUtils.setField(service, "defaultWindowDays", 30L);

        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = service.buildWrapper(
                "ORDER-EXACT", null, null, null, null,
                null, null, null,
                null, null,
                null, null,
                List.of(), List.of()
        );

        assertThat(wrapper.getSqlSegment()).contains("order_id");
        assertThat(wrapper.getSqlSegment()).doesNotContain("create_time");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildWrapper_defaultWindowEnabledShouldNotOverrideExplicitStartTime() {
        ReflectionTestUtils.setField(service, "defaultWindowEnabled", true);
        ReflectionTestUtils.setField(service, "defaultWindowDays", 30L);

        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = service.buildWrapper(
                null, null, null, null, null,
                null, null, null,
                "2026-04-01 00:00:00", null,
                null, null,
                List.of(), List.of()
        );

        assertThat(wrapper.getSqlSegment()).contains("create_time");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(LocalDateTime.of(2026, 4, 1, 0, 0));
    }

    // ============================================================
    // applyAttributionStatusFilter — UNATTRIBUTED 特殊语义
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void applyAttributionStatusFilter_unattributedShouldUseEqOrIsNull() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();
        service.applyAttributionStatusFilter(wrapper, "UNATTRIBUTED");
        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("attribution_status");
        assertThat(sql).contains("IS NULL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyAttributionStatusFilter_attributedShouldUseEq() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();
        service.applyAttributionStatusFilter(wrapper, "ATTRIBUTED");
        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("attribution_status");
        // ATTRIBUTED 是简单 eq,不带 IS NULL 分支
        assertThat(sql).doesNotContain("IS NULL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyAttributionStatusFilter_blankValueShouldBeNoOp() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();
        service.applyAttributionStatusFilter(wrapper, " ");
        assertThat(wrapper.getSqlSegment()).isEmpty();
    }

    // ============================================================
    // applyTimeRange + resolveTimeField
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void applyTimeRange_shouldSwitchColumnByTimeField() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 28, 23, 59, 59);

        LambdaQueryWrapper<ColonelsettlementOrder> createWrapper = new LambdaQueryWrapper<>();
        service.applyTimeRange(createWrapper, "create_time", start, end);
        assertThat(createWrapper.getSqlSegment()).contains("create_time");
        assertThat(createWrapper.getSqlSegment()).doesNotContain("settle_time");

        LambdaQueryWrapper<ColonelsettlementOrder> settleWrapper = new LambdaQueryWrapper<>();
        service.applyTimeRange(settleWrapper, "settle_time", start, end);
        assertThat(settleWrapper.getSqlSegment()).contains("settle_time");
        assertThat(settleWrapper.getSqlSegment()).doesNotContain("create_time");
    }

    @Test
    void resolveTimeField_shouldMapCaseInsensitively() {
        assertThat(OrderService.resolveTimeField("settleTime")).isEqualTo("settle_time");
        assertThat(OrderService.resolveTimeField("SETTLETIME")).isEqualTo("settle_time");
        assertThat(OrderService.resolveTimeField("createTime")).isEqualTo("create_time");
        assertThat(OrderService.resolveTimeField(null)).isEqualTo("create_time");
    }

    // ============================================================
    // parseLocalDateTime — 时间解析
    // ============================================================

    @Test
    void parseLocalDateTime_shouldParseYmdHms() {
        LocalDateTime result = OrderService.parseLocalDateTime("2026-04-01 00:00:00");
        assertThat(result).isEqualTo(LocalDateTime.of(2026, 4, 1, 0, 0, 0));
    }

    @Test
    void parseLocalDateTime_shouldReturnNullForBlank() {
        assertThat(OrderService.parseLocalDateTime(null)).isNull();
        assertThat(OrderService.parseLocalDateTime("")).isNull();
        assertThat(OrderService.parseLocalDateTime("   ")).isNull();
    }

    @Test
    void parseLocalDateTime_shouldReturnNullForMalformed() {
        assertThat(OrderService.parseLocalDateTime("2026/04/01")).isNull();
        assertThat(OrderService.parseLocalDateTime("not-a-datetime")).isNull();
    }

    // ============================================================
    // parseUuidCsv — 部门 ID 列表解析
    // ============================================================

    @Test
    void parseUuidCsv_shouldParseCsvAndSkipInvalid() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        List<UUID> result = OrderService.parseUuidCsv(u1 + "," + u2 + ",garbage,,");
        assertThat(result).containsExactly(u1, u2);
    }

    @Test
    void parseUuidCsv_shouldReturnEmptyForBlank() {
        assertThat(OrderService.parseUuidCsv(null)).isEmpty();
        assertThat(OrderService.parseUuidCsv("")).isEmpty();
        assertThat(OrderService.parseUuidCsv("   ")).isEmpty();
    }

    // ============================================================
    // applyDataScope — 数据范围
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void applyDataScope_personalShouldFilterByUserId() {
        UUID userId = UUID.randomUUID();
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();
        service.applyDataScope(wrapper, userId, null, DataScope.PERSONAL);
        assertThat(wrapper.getSqlSegment()).contains("user_id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyDataScope_deptShouldFilterByDeptId() {
        UUID deptId = UUID.randomUUID();
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();
        service.applyDataScope(wrapper, null, deptId, DataScope.DEPT);
        assertThat(wrapper.getSqlSegment()).contains("dept_id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyDataScope_allShouldBeNoOp() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();
        service.applyDataScope(wrapper, UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);
        assertThat(wrapper.getSqlSegment()).isEmpty();
    }

    // ============================================================
    // selectOrderListColumns — 排除 extra_data
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void selectOrderListColumns_shouldExcludeExtraData() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();
        service.selectOrderListColumns(wrapper);
        String sqlSelect = wrapper.getSqlSelect();
        assertThat(sqlSelect).contains("order_id");
        assertThat(sqlSelect).doesNotContain("extra_data");
    }

    // ============================================================
    // applyDashboardDiagnosisFilter + sanitizeDiagnosisSqlPrefix
    // ============================================================

    @Test
    @SuppressWarnings("unchecked")
    void applyDashboardDiagnosisFilter_shouldPassThroughWhitelistedCategory() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();
        service.applyDashboardDiagnosisFilter(wrapper, null, "UPSTREAM_PRODUCT_UNCOVERED");
        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("CASE").contains("WHEN");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("UPSTREAM_PRODUCT_UNCOVERED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyDashboardDiagnosisFilter_blankValueShouldBeNoOp() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();
        service.applyDashboardDiagnosisFilter(wrapper, null, " ");
        assertThat(wrapper.getSqlSegment()).isEmpty();
    }

    @Test
    void sanitizeDiagnosisSqlPrefix_shouldWhitelistOnlyTwoValues() {
        assertThat(service.sanitizeDiagnosisSqlPrefix("colonelsettlement_order."))
                .isEqualTo("colonelsettlement_order.");
        assertThat(service.sanitizeDiagnosisSqlPrefix("fo.")).isEqualTo("fo.");
        // 任何其他值都被回退到默认前缀
        assertThat(service.sanitizeDiagnosisSqlPrefix("evil; DROP TABLE--"))
                .isEqualTo("colonelsettlement_order.");
        assertThat(service.sanitizeDiagnosisSqlPrefix(null))
                .isEqualTo("colonelsettlement_order.");
    }

    // ============================================================
    // normalizeOrderRow — 行规范化
    // ============================================================

    @Test
    void normalizeOrderRow_shouldCopyAttributionRemarkToUnattributedReason() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        UUID channelUserId = UUID.randomUUID();
        order.setAttributionRemark("MAPPING_NOT_FOUND");
        order.setProductPic("https://cdn.example.com/product.jpg");
        order.setItemNum(3);
        order.setChannelUserId(channelUserId);
        order.setChannelUserName("渠道甲");
        service.normalizeOrderRow(order);
        assertThat(order.getUnattributedReason()).isEqualTo("MAPPING_NOT_FOUND");
        assertThat(order.getProductImage()).isEqualTo("https://cdn.example.com/product.jpg");
        assertThat(order.getProductQuantity()).isEqualTo(3);
        assertThat(order.getChannelId()).isEqualTo(channelUserId.toString());
        assertThat(order.getChannelName()).isEqualTo("渠道甲");
    }

    @Test
    void normalizeOrderRow_nullOrderShouldNotThrow() {
        service.normalizeOrderRow(null);
    }

    @Test
    void enrichOrderProductInfo_shouldFillDisplayFieldsFromProjectionAndSnapshot() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORDER-1");
        order.setProductId("P-1");
        order.setActivityId("A-1");

        when(orderMapper.listDisplayProductInfoByOrderIds(any())).thenReturn(List.of(Map.of(
                "orderId", "ORDER-1",
                "productPic", "https://cdn.example.com/order-product.jpg",
                "itemNum", 2,
                "commissionRate", new BigDecimal("500"),
                "serviceFeeRate", new BigDecimal("2")
        )));

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("A-1");
        snapshot.setProductId("P-1");
        snapshot.setTitle("快照商品标题");
        snapshot.setShopName("快照店铺");
        snapshot.setCover("https://cdn.example.com/snapshot.jpg");
        snapshot.setActivityCosRatio(1400L);
        snapshot.setAdServiceRatio("1%");
        when(productSnapshotMapper.selectList(any())).thenReturn(List.of(snapshot));
        when(productMapper.selectList(any())).thenReturn(List.of());

        service.enrichOrderProductInfo(List.of(order));

        assertThat(order.getProductPic()).isEqualTo("https://cdn.example.com/order-product.jpg");
        assertThat(order.getProductImage()).isEqualTo("https://cdn.example.com/order-product.jpg");
        assertThat(order.getProductTitle()).isEqualTo("快照商品标题");
        assertThat(order.getShopName()).isEqualTo("快照店铺");
        assertThat(order.getItemNum()).isEqualTo(2);
        assertThat(order.getProductQuantity()).isEqualTo(2);
        assertThat(order.getCommissionRate()).isEqualByComparingTo("500");
        assertThat(order.getServiceFeeRate()).isEqualByComparingTo("2");
    }

    @Test
    void enrichOrderList_shouldFillListExtrasFromProjection() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORDER-EXTRA-1");
        order.setOrderType(1);

        when(orderMapper.listDisplayProductInfoByOrderIds(any())).thenReturn(List.of(Map.of(
                "orderId", "ORDER-EXTRA-1",
                "productPic", "https://cdn.example.com/order-product.jpg",
                "awemeId", "AWEME-EXTRA-1",
                "contentTypeText", "短视频"
        )));
        service.enrichOrderList(List.of(order));

        assertThat(order.getProductImage()).isEqualTo("https://cdn.example.com/order-product.jpg");
        assertThat(order.getAwemeId()).isEqualTo("AWEME-EXTRA-1");
        assertThat(order.getContentTypeText()).isEqualTo("短视频");
        assertThat(order.getOrderTypeText()).isEqualTo("推广者推广");
    }

    @Test
    void enrichOrderList_emptyListShouldNotLoadDisplayInfo() {
        service.enrichOrderList(List.of());

        verifyNoInteractions(orderMapper, productSnapshotMapper, productMapper);
    }

    @Test
    void enrichOrderList_singleOrderShouldLoadDisplayInfoOnce() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORDER-SINGLE");

        when(orderMapper.listDisplayProductInfoByOrderIds(any())).thenReturn(List.of(Map.of(
                "orderId", "ORDER-SINGLE",
                "productPic", "https://cdn.example.com/single.jpg"
        )));

        service.enrichOrderList(List.of(order));

        verify(orderMapper, times(1)).listDisplayProductInfoByOrderIds(any());
        assertThat(order.getProductImage()).isEqualTo("https://cdn.example.com/single.jpg");
    }

    @Test
    void enrichOrderList_multiOrdersShouldLoadDisplayInfoOnceForCurrentPage() {
        ColonelsettlementOrder first = new ColonelsettlementOrder();
        first.setOrderId("ORDER-1");
        ColonelsettlementOrder second = new ColonelsettlementOrder();
        second.setOrderId("ORDER-2");

        when(orderMapper.listDisplayProductInfoByOrderIds(any())).thenReturn(List.of(
                Map.of("orderId", "ORDER-1", "productPic", "https://cdn.example.com/1.jpg"),
                Map.of("orderId", "ORDER-2", "awemeId", "AWEME-2")
        ));

        service.enrichOrderList(List.of(first, second));

        verify(orderMapper, times(1)).listDisplayProductInfoByOrderIds(any());
        assertThat(first.getProductImage()).isEqualTo("https://cdn.example.com/1.jpg");
        assertThat(second.getAwemeId()).isEqualTo("AWEME-2");
    }

    @Test
    void enrichOrderList_displayInfoMissingShouldUseSnapshotWhenPresent() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORDER-SNAPSHOT");
        order.setProductId("P-SNAPSHOT");
        order.setActivityId("A-SNAPSHOT");

        when(orderMapper.listDisplayProductInfoByOrderIds(any())).thenReturn(List.of());
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("A-SNAPSHOT");
        snapshot.setProductId("P-SNAPSHOT");
        snapshot.setTitle("快照标题");
        snapshot.setCover("https://cdn.example.com/snapshot.jpg");
        when(productSnapshotMapper.selectList(any())).thenReturn(List.of(snapshot));

        service.enrichOrderList(List.of(order));

        verify(orderMapper, times(1)).listDisplayProductInfoByOrderIds(any());
        assertThat(order.getProductTitle()).isEqualTo("快照标题");
        assertThat(order.getProductImage()).isEqualTo("https://cdn.example.com/snapshot.jpg");
    }

    @Test
    void enrichOrderList_snapshotMissingShouldUseProductFallbackWhenPresent() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORDER-PRODUCT");
        order.setProductId("P-PRODUCT");

        when(orderMapper.listDisplayProductInfoByOrderIds(any())).thenReturn(List.of());
        when(productSnapshotMapper.selectList(any())).thenReturn(List.of());
        Product product = new Product();
        product.setProductId("P-PRODUCT");
        product.setName("商品标题");
        product.setCover("https://cdn.example.com/product.jpg");
        when(productMapper.selectList(any())).thenReturn(List.of(product));

        service.enrichOrderList(List.of(order));

        verify(orderMapper, times(1)).listDisplayProductInfoByOrderIds(any());
        assertThat(order.getProductTitle()).isEqualTo("商品标题");
        assertThat(order.getProductImage()).isEqualTo("https://cdn.example.com/product.jpg");
    }

    // ============================================================
    // findPage + findStats — 委托 mapper 验证
    // ============================================================

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findPage_shouldPassWrapperToMapperSelectPage() {
        when(orderMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        service.findPage(1L, 20L, "ORDER-1", null, null, null, null, null, null, null,
                null, null, null, null, List.of(), List.of(),
                UUID.randomUUID(), null, DataScope.ALL);
        verify(orderMapper).selectPage(any(), any(LambdaQueryWrapper.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findStats_shouldAggregateAttributedUnattributedPartialSeparately() {
        // Mock 两轮 selectMaps 调用:第一轮 status 分组,第二轮 reason 分组
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(
                        Map.of("attributionStatus", "ATTRIBUTED", "total", 10L),
                        Map.of("attributionStatus", "UNATTRIBUTED", "total", 5L),
                        Map.of("attributionStatus", "PARTIAL", "total", 3L)
                ))
                .thenReturn(List.of(
                        Map.of("reason", "SYNC_FAILED", "total", 2L)
                ));

        OrderService.OrderStatsResult result = service.findStats(
                null, null, null, null, null, null, null, null,
                null, null, null, null,
                List.of(), List.of(),
                UUID.randomUUID(), null, DataScope.ALL, null
        );

        assertThat(result.totalOrders()).isEqualTo(18L);
        assertThat(result.attributedOrders()).isEqualTo(10L);
        assertThat(result.unattributedOrders()).isEqualTo(5L);
        assertThat(result.partialOrders()).isEqualTo(3L);
        assertThat(result.syncFailedOrders()).isEqualTo(2L);
        assertThat(result.unattributedReasons()).hasSize(1);
        assertThat(result.unattributedReasons().get(0).reason()).isEqualTo("SYNC_FAILED");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findStats_shouldIgnoreBlankReasonsInReasonGroup() {
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(
                        Map.of("attributionStatus", "ATTRIBUTED", "total", 1L)
                ))
                .thenReturn(List.of(
                        Map.of("reason", "", "total", 99L),
                        Map.of("reason", "MAPPING_NOT_FOUND", "total", 7L)
                ));

        OrderService.OrderStatsResult result = service.findStats(
                null, null, null, null, null, null, null, null,
                null, null, null, null,
                List.of(), List.of(),
                UUID.randomUUID(), null, DataScope.ALL, null
        );

        assertThat(result.unattributedReasons()).hasSize(1);
        assertThat(result.unattributedReasons().get(0).reason()).isEqualTo("MAPPING_NOT_FOUND");
        assertThat(result.syncFailedOrders()).isZero();
    }

    // ============================================================
    // private helper reachability via reflection
    // ============================================================

    @Test
    void privateHelpers_readValueAndAsTextShouldBeCaseInsensitive() {
        // 通过反射触发 private 辅助方法,保证编译期依赖 + 运行期可达
        Map<String, Object> row = Map.of("OWNER_ID", "abc", "ORDER_COUNT", 5L);
        assertThat(ReflectionTestUtils.<Object>invokeMethod(service, "readValue", row, "owner_id"))
                .isEqualTo("abc");
        Object orderCount = ReflectionTestUtils.invokeMethod(service, "readValue", row, "ORDER_COUNT");
        assertThat(ReflectionTestUtils.<Long>invokeMethod(service, "asLong", orderCount))
                .isEqualTo(5L);
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "asText", "raw"))
                .isEqualTo("raw");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "asText", (Object) null))
                .isNull();
    }
}
