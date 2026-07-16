package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.dto.product.QuickSampleApplyRequest;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import com.colonel.saas.testsupport.DockerAvailable;
import com.colonel.saas.testsupport.TestDataService;
import com.colonel.saas.controller.SampleController;
import com.colonel.saas.controller.DataController;
import com.colonel.saas.dto.sample.SampleBatchActionRequest;
import com.colonel.saas.common.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization Tests / Baseline Tests (DDD-BASE-002).
 * <p>
 * 冻结当前核心业务逻辑与契约行为，作为重构防护基线。
 * </p>
 */
@DockerAvailable
class CharacterizationBaselineTest extends BaseIntegrationTest {

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private UserDomainService userDomainService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductQuickSampleService productQuickSampleService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private SampleLifecycleService sampleLifecycleService;

    @Autowired
    private PerformanceCalculationService performanceCalculationService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private SampleController sampleController;

    @Autowired
    private DataController dataController;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedUsersDeptsAndRoles() {
        // 0. Ensure talent_follow_record table is present
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS talent_follow_record (
                    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    product_id       VARCHAR(64) NOT NULL,
                    activity_id      VARCHAR(64),
                    talent_id        UUID,
                    talent_name      VARCHAR(255),
                    follow_status    VARCHAR(64),
                    content          TEXT,
                    next_follow_time TIMESTAMP,
                    operator_id      UUID,
                    operator_name    VARCHAR(255),
                    user_id          UUID,
                    follow_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    unfollow_time    TIMESTAMP,
                    status           VARCHAR(16) DEFAULT 'ACTIVE',
                    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    create_by        UUID,
                    update_by        UUID,
                    deleted          SMALLINT NOT NULL DEFAULT 0
                )
                """);

        // 1. Insert departments
        jdbcTemplate.update("INSERT INTO sys_dept (id, dept_code, dept_name, status, deleted) VALUES (?, ?, ?, 1, 0) ON CONFLICT (dept_code) DO NOTHING",
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "biz", "招商部");
        jdbcTemplate.update("INSERT INTO sys_dept (id, dept_code, dept_name, status, deleted) VALUES (?, ?, ?, 1, 0) ON CONFLICT (dept_code) DO NOTHING",
                UUID.fromString("22222222-2222-2222-2222-222222222222"), "channel", "渠道部");
        jdbcTemplate.update("INSERT INTO sys_dept (id, dept_code, dept_name, status, deleted) VALUES (?, ?, ?, 1, 0) ON CONFLICT (dept_code) DO NOTHING",
                UUID.fromString("33333333-3333-3333-3333-333333333333"), "ops", "运营部");

        // 2. Insert roles
        insertRole(UUID.fromString("88888888-8888-8888-8888-888888888800"), "admin", "管理员", 3);
        insertRole(UUID.fromString("88888888-8888-8888-8888-888888888801"), "channel_leader", "渠道组长", 2);
        insertRole(UUID.fromString("88888888-8888-8888-8888-888888888802"), "channel_staff", "渠道专员", 1);
        insertRole(UUID.fromString("88888888-8888-8888-8888-888888888803"), "biz_leader", "招商组长", 2);
        insertRole(UUID.fromString("88888888-8888-8888-8888-888888888804"), "biz_staff", "招商专员", 1);
        insertRole(UUID.fromString("88888888-8888-8888-8888-888888888805"), "ops_staff", "运营", 1);

        // 3. Insert users
        UUID adminId = UUID.nameUUIDFromBytes("admin".getBytes());
        UUID channelLeaderId = UUID.nameUUIDFromBytes("channel_leader".getBytes());
        UUID channelStaffId = UUID.nameUUIDFromBytes("channel_staff".getBytes());
        UUID bizLeaderId = UUID.nameUUIDFromBytes("biz_leader".getBytes());
        UUID bizStaffId = UUID.nameUUIDFromBytes("biz_staff".getBytes());
        UUID opsStaffId = UUID.nameUUIDFromBytes("ops_staff".getBytes());

        insertUser(adminId, "admin", "管理员", "admin", null);
        insertUser(channelLeaderId, "channel_leader", "渠道组长", "ch_lead", UUID.fromString("22222222-2222-2222-2222-222222222222"));
        insertUser(channelStaffId, "channel_staff", "渠道组员", "ch_staff", UUID.fromString("22222222-2222-2222-2222-222222222222"));
        insertUser(bizLeaderId, "biz_leader", "招商组长", "biz_lead", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        insertUser(bizStaffId, "biz_staff", "招商组员", "biz_staff", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        insertUser(opsStaffId, "ops_staff", "运营组员", "ops_staff", UUID.fromString("33333333-3333-3333-3333-333333333333"));

        // 4. Map user roles
        bindUserRole(adminId, "admin");
        bindUserRole(channelLeaderId, "channel_leader");
        bindUserRole(channelStaffId, "channel_staff");
        bindUserRole(bizLeaderId, "biz_leader");
        bindUserRole(bizStaffId, "biz_staff");
        bindUserRole(opsStaffId, "ops_staff");
    }

    private void insertRole(UUID id, String roleCode, String roleName, int dataScope) {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_code, role_name, data_scope, status, permissions) VALUES (?, ?, ?, ?, 1, ?::jsonb) ON CONFLICT (role_code) DO NOTHING",
                id, roleCode, roleName, dataScope, "{\"menus\":[\"talent_crm\"]}");
    }

    private void insertUser(UUID id, String username, String realName, String channelCode, UUID deptId) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, real_name, channel_code, dept_id, status, deleted) VALUES (?, ?, ?, ?, ?, ?, 1, 0) ON CONFLICT (username) DO NOTHING",
                id, username, "password", realName, channelCode, deptId);
    }

    private void bindUserRole(UUID userId, String roleCode) {
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code = ? ON CONFLICT DO NOTHING",
                userId, roleCode);
    }

    @Test
    void test01_UserLoginAndPermissionsBaseline() {
        // Seeding database
        testDataService.seedAll(false);

        // Fetch seeded channel leader details
        UUID channelLeaderId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'channel_leader' AND deleted = 0 LIMIT 1", UUID.class);
        UUID channelDeptId = jdbcTemplate.queryForObject(
                "SELECT dept_id FROM sys_user WHERE username = 'channel_leader' AND deleted = 0 LIMIT 1", UUID.class);

        // Verify CurrentUserResponse contract
        CurrentUserResponse response = userDomainService.getCurrentUser(
                channelLeaderId,
                channelDeptId,
                DataScope.DEPT,
                List.of(RoleCodes.CHANNEL_LEADER)
        );

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(channelLeaderId);
        assertThat(response.roleCodes()).containsExactly(RoleCodes.CHANNEL_LEADER);
        assertThat(response.dataScope()).isEqualTo(2); // group scope
        assertThat(response.dataScopeName()).isEqualTo("group");
        assertThat(response.permissions()).isNotNull();
    }

    @Test
    void test02_ProductLibraryBaseline() {
        // Seeding database
        testDataService.seedAll(false);

        // Query product library
        IPage<Product> result = productService.getSelectedLibraryPage(1, 20, ProductService.SelectedLibraryFilter.empty());

        assertThat(result).isNotNull();
        assertThat(result.getRecords()).isNotEmpty();

        Product mainProduct = result.getRecords().stream()
                .filter(p -> "10901825".equals(p.getProductId()))
                .findFirst()
                .orElse(null);

        assertThat(mainProduct).isNotNull();
        assertThat(mainProduct.getName()).contains("主演示商品");
        assertThat(mainProduct.getPrice()).isEqualTo(9900L);
    }

    @Test
    void test03_LinkConversionBaseline() {
        // Seeding database
        testDataService.seedAll(false);

        // Fetch a seeded product snapshot ID
        UUID mainProductSnapshotId = jdbcTemplate.queryForObject(
                "SELECT id FROM product_snapshot WHERE product_id = '10901825' LIMIT 1", UUID.class);
        UUID channelLeaderId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'channel_leader' AND deleted = 0 LIMIT 1", UUID.class);
        UUID channelDeptId = jdbcTemplate.queryForObject(
                "SELECT dept_id FROM sys_user WHERE username = 'channel_leader' AND deleted = 0 LIMIT 1", UUID.class);

        // Call conversion
        DouyinPromotionGateway.PromotionLinkResult linkResult = productService.generatePromotionLink(
                mainProductSnapshotId,
                channelLeaderId,
                channelDeptId,
                "EXT-UNIQUE-001",
                1,
                true
        );

        assertThat(linkResult).isNotNull();
        assertThat(linkResult.pickSource()).startsWith("MOCK");
        assertThat(linkResult.shortLink()).startsWith("https://test.short.link/");
        assertThat(linkResult.promoteLink()).contains("10901825");

        Integer mappingCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM pick_source_mapping
                        WHERE pick_source = ? AND product_id = ? AND deleted = 0
                        """,
                Integer.class,
                linkResult.pickSource(),
                "10901825");
        assertThat(mappingCount).isGreaterThanOrEqualTo(1);

        String linkOwnerType = jdbcTemplate.queryForObject(
                "SELECT attribution_owner_type FROM promotion_link WHERE pick_source = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                linkResult.pickSource());
        String mappingOwnerType = jdbcTemplate.queryForObject(
                "SELECT attribution_owner_type FROM pick_source_mapping WHERE pick_source = ? ORDER BY create_time DESC LIMIT 1",
                String.class,
                linkResult.pickSource());
        assertThat(linkOwnerType).isEqualTo("CHANNEL");
        assertThat(mappingOwnerType).isEqualTo("CHANNEL");
    }

    @Test
    void test03b_RecruiterLinkConversionShouldSnapshotRecruiterOwnerType() {
        testDataService.seedAll(false);
        UUID mainProductSnapshotId = jdbcTemplate.queryForObject(
                "SELECT id FROM product_snapshot WHERE product_id = '10901825' LIMIT 1", UUID.class);
        UUID recruiterId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'biz_staff' AND deleted = 0 LIMIT 1", UUID.class);
        UUID recruiterDeptId = jdbcTemplate.queryForObject(
                "SELECT dept_id FROM sys_user WHERE username = 'biz_staff' AND deleted = 0 LIMIT 1", UUID.class);

        DouyinPromotionGateway.PromotionLinkResult linkResult = productService.generatePromotionLink(
                mainProductSnapshotId,
                recruiterId,
                recruiterDeptId,
                "EXT-RECRUITER-001",
                1,
                true);

        String linkOwnerType = jdbcTemplate.queryForObject(
                "SELECT attribution_owner_type FROM promotion_link WHERE pick_source = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                linkResult.pickSource());
        String mappingOwnerType = jdbcTemplate.queryForObject(
                "SELECT attribution_owner_type FROM pick_source_mapping WHERE pick_source = ? ORDER BY create_time DESC LIMIT 1",
                String.class,
                linkResult.pickSource());
        assertThat(linkOwnerType).isEqualTo("RECRUITER");
        assertThat(mappingOwnerType).isEqualTo("RECRUITER");
    }

    @Test
    void test04_SampleLifecycleAndAttributionCompleteBaseline() {
        testDataService.seedAll(false);

        UUID shipSampleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sample_request WHERE request_no = 'TEST-SAMPLE-SHIP-001' LIMIT 1", UUID.class);

        Map<String, Object> shipRes = testDataService.shipSample(shipSampleId);
        assertThat(shipRes.get("status")).isEqualTo(3);

        Map<String, Object> signRes = testDataService.signSample(shipSampleId);
        assertThat(signRes.get("status")).isEqualTo(5);

        UUID homeworkSampleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sample_request WHERE request_no = 'TEST-SAMPLE-001' LIMIT 1", UUID.class);
        UUID channelUserId = jdbcTemplate.queryForObject(
                "SELECT channel_user_id FROM sample_request WHERE id = ? LIMIT 1", UUID.class, homeworkSampleId);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORD-CHAR-BASELINE-001");
        order.setProductId("10901825");
        order.setChannelUserId(channelUserId);
        order.setExtraData(Map.of("talent_uid", "talent_test_a"));

        int completed = sampleLifecycleService.completePendingHomeworkByOrder(order);
        assertThat(completed).isEqualTo(1);

        Integer finalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM sample_request WHERE request_no = 'TEST-SAMPLE-001' LIMIT 1",
                Integer.class);
        assertThat(finalStatus).isEqualTo(6);
    }

    @Test
    void test05_OrderAttributionAndCalculationsBaseline() {
        // Seeding database
        testDataService.seedAll(false);

        // Verify seeded attributed order details
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                "SELECT order_id, order_amount, settle_amount, estimate_service_fee, effective_service_fee FROM colonelsettlement_order WHERE order_id = 'MOCK_SEED_TALENT_D_ORDER'");
        assertThat(orders).hasSize(1);
        Map<String, Object> orderMap = orders.get(0);
        assertThat(orderMap.get("order_id")).isEqualTo("MOCK_SEED_TALENT_D_ORDER");

        // Calculate performance from order using baseline formula
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("ORD-CALC-BASELINE");
        order.setActivityId("TEST_ACTIVITY_A");
        order.setOrderAmount(10000L);
        order.setSettleAmount(10000L);
        order.setEstimateServiceFee(1000L);
        order.setEffectiveServiceFee(1000L);
        order.setEstimateTechServiceFee(100L);
        order.setEffectiveTechServiceFee(100L);
        order.setOrderStatus(1);

        PerformanceRecord record = performanceCalculationService.upsertFromOrder(order);

        assertThat(record).isNotNull();
        // 当前行为基线：服务费收益 = 服务费收入 - 技术服务费
        assertThat(record.getEstimateServiceProfit()).isEqualTo(900L);
        assertThat(record.getEffectiveServiceProfit()).isEqualTo(1000L);
        // 提成 + 毛利必须与服务费收益守恒（无配置时默认比例由 CommissionService 解析）
        long estimateProfit = record.getEstimateServiceProfit();
        assertThat(record.getEstimateRecruiterCommission()
                + record.getEstimateChannelCommission()
                + record.getEstimateGrossProfit()).isEqualTo(estimateProfit);
        // 冻结当前默认比例下的绝对值（无 system_config 时双轨均为 15%）
        assertThat(record.getEstimateRecruiterCommission()).isEqualTo(135L);
        assertThat(record.getEstimateChannelCommission()).isEqualTo(135L);
        assertThat(record.getEstimateGrossProfit()).isEqualTo(630L);
    }

    @Test
    void test07_QuickSampleApplyCharacterizationBaseline() {
        testDataService.seedAll(false);

        UUID mainProductRelationId = jdbcTemplate.queryForObject(
                "SELECT id FROM product_snapshot WHERE product_id = '10901825' AND activity_id = 'TEST_ACTIVITY_A' LIMIT 1",
                UUID.class);
        UUID channelLeaderId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'channel_leader' AND deleted = 0 LIMIT 1", UUID.class);
        UUID channelDeptId = jdbcTemplate.queryForObject(
                "SELECT dept_id FROM sys_user WHERE username = 'channel_leader' AND deleted = 0 LIMIT 1", UUID.class);

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("talent_test_b"));
        request.setQuantity(1);
        request.setSpecification("基线规格");
        request.setRecipientAddress("成都市高新区");

        var response = productQuickSampleService.applyQuickSample(
                mainProductRelationId,
                request,
                channelLeaderId,
                channelDeptId,
                List.of(RoleCodes.CHANNEL_LEADER));

        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).isSuccess()).isTrue();
        assertThat(response.getItems().get(0).getSampleRequestId()).isNotNull();
    }

    @Test
    void test08_OrderListAndActivityProductCharacterizationBaseline() {
        testDataService.seedAll(false);

        IPage<ColonelsettlementOrder> orders = orderService.findPage(
                1, 20,
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null,
                null, null, DataScope.ALL);

        assertThat(orders.getRecords()).isNotEmpty();
        ColonelsettlementOrder seeded = orders.getRecords().stream()
                .filter(o -> "MOCK_SEED_TALENT_D_ORDER".equals(o.getOrderId()))
                .findFirst()
                .orElse(null);
        assertThat(seeded).isNotNull();
        assertThat(seeded.getEstimateServiceFee()).isNotNull();
        assertThat(seeded.getSettleAmount()).isNotNull();

        List<Map<String, Object>> activityProducts = jdbcTemplate.queryForList(
                """
                        SELECT ps.product_id, ps.activity_id, pos.display_status
                        FROM product_snapshot ps
                        JOIN product_operation_state pos
                          ON pos.activity_id = ps.activity_id AND pos.product_id = ps.product_id
                        WHERE ps.product_id = '10901825'
                        LIMIT 5
                        """);
        assertThat(activityProducts).isNotEmpty();
        assertThat(activityProducts.get(0).get("activity_id")).isEqualTo("TEST_ACTIVITY_A");
    }

    @Test
    void test06_DashboardSummaryBaseline() {
        // Seeding database
        testDataService.seedAll(false);

        // Verify summary logic behaves as expected
        DashboardService.Summary summary = dashboardService.getSummary(null, null, null, null, DataScope.ALL);

        assertThat(summary).isNotNull();
        assertThat(summary.getOrderCount()).isGreaterThan(0);
        assertThat(summary.getOrderAmount()).isGreaterThan(0);
        assertThat(summary.getServiceFee()).isGreaterThanOrEqualTo(0);
        assertThat(summary.getAttributedOrderCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void test09_OrderDetailCharacterizationBaseline() {
        // Freeze OrderQueryService.getOrderDetail() public contract.
        testDataService.seedAll(false);

        UUID channelLeaderId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'channel_leader' AND deleted = 0 LIMIT 1", UUID.class);
        UUID channelDeptId = jdbcTemplate.queryForObject(
                "SELECT dept_id FROM sys_user WHERE username = 'channel_leader' AND deleted = 0 LIMIT 1", UUID.class);

        // MOCK_SEED_TALENT_D_ORDER is attributed in seedAll; ALL scope so DataScope check is bypassed.
        OrderDetailResponse detail = orderQueryService.getOrderDetail(
                "MOCK_SEED_TALENT_D_ORDER",
                channelLeaderId,
                channelDeptId,
                DataScope.ALL);

        assertThat(detail).isNotNull();
        assertThat(detail.getOrderId()).isEqualTo("MOCK_SEED_TALENT_D_ORDER");

        // AmountInfo: must use effective_service_fee (not settle_colonel_commission).
        assertThat(detail.getAmount()).isNotNull();
        assertThat(detail.getAmount().getEffectiveServiceFee()).isNotNull();
        assertThat(detail.getAmount().getEffectiveServiceFee()).isPositive();
        assertThat(detail.getAmount().getEstimateServiceFee()).isNotNull();
        assertThat(detail.getAmount().getSettleAmount()).isNotNull();

        // ProductInfo + ChannelInfo must be populated for the seeded order.
        assertThat(detail.getProduct()).isNotNull();
        assertThat(detail.getProduct().getProductId()).isEqualTo("10901825");
        assertThat(detail.getProduct().getActivityId()).isEqualTo("TEST_ACTIVITY_A");
        assertThat(detail.getChannel()).isNotNull();
        assertThat(detail.getChannel().getChannelUserId()).isNotNull();

        // Promotion matched=true on a seeded pick_source_mapping.
        assertThat(detail.getPromotion()).isNotNull();
        assertThat(detail.getPromotion().isMatched()).isTrue();
        assertThat(detail.getPromotion().getMappingId()).isNotNull();
        assertThat(detail.getPickSource()).isNotNull();

        // DiagnosisInfo exists as an empty object for an attributed order
        // (OrderQueryService always constructs one; reasonCode is populated only when unattributed).
        assertThat(detail.getDiagnosis()).isNotNull();
        assertThat(detail.getDiagnosis().getReasonCode()).isNull();
        assertThat(detail.getDiagnosis().getReasonText()).isNull();
        assertThat(detail.getDiagnosis().getSuggestion()).isNull();
    }

    @Test
    void test10_PerformanceFormulaEdgeCasesCharacterizationBaseline() {
        // Freeze PerformanceCalculationService.upsertFromOrder() edge cases.
        testDataService.seedAll(false);

        // Case 1: zero service fee → profit = -techFee, double-track commissions are 0.
        ColonelsettlementOrder zeroFeeOrder = new ColonelsettlementOrder();
        zeroFeeOrder.setId(UUID.randomUUID());
        zeroFeeOrder.setOrderId("ORD-CALC-ZERO-FEE");
        zeroFeeOrder.setActivityId("TEST_ACTIVITY_A");
        zeroFeeOrder.setOrderAmount(10000L);
        zeroFeeOrder.setSettleAmount(10000L);
        zeroFeeOrder.setEstimateServiceFee(0L);
        zeroFeeOrder.setEffectiveServiceFee(0L);
        zeroFeeOrder.setEstimateTechServiceFee(0L);
        zeroFeeOrder.setEffectiveTechServiceFee(0L);
        zeroFeeOrder.setOrderStatus(1);

        PerformanceRecord zeroRecord = performanceCalculationService.upsertFromOrder(zeroFeeOrder);
        assertThat(zeroRecord).isNotNull();
        assertThat(zeroRecord.getEstimateServiceProfit()).isEqualTo(0L);
        assertThat(zeroRecord.getEffectiveServiceProfit()).isEqualTo(0L);
        assertThat(zeroRecord.getEstimateRecruiterCommission()
                + zeroRecord.getEstimateChannelCommission()
                + zeroRecord.getEstimateGrossProfit()).isEqualTo(0L);

        // Case 2: large amount 1,000,000 with 1,000 service fee / 100 tech fee.
        // Without system_config: profit = 900, split 135/135/630.
        ColonelsettlementOrder largeOrder = new ColonelsettlementOrder();
        largeOrder.setId(UUID.randomUUID());
        largeOrder.setOrderId("ORD-CALC-LARGE");
        largeOrder.setActivityId("TEST_ACTIVITY_A");
        largeOrder.setOrderAmount(1_000_000L);
        largeOrder.setSettleAmount(1_000_000L);
        largeOrder.setEstimateServiceFee(1_000L);
        largeOrder.setEffectiveServiceFee(1_000L);
        largeOrder.setEstimateTechServiceFee(100L);
        largeOrder.setEffectiveTechServiceFee(100L);
        largeOrder.setOrderStatus(1);

        PerformanceRecord largeRecord = performanceCalculationService.upsertFromOrder(largeOrder);
        assertThat(largeRecord).isNotNull();
        assertThat(largeRecord.getEstimateServiceProfit()).isEqualTo(900L);
        assertThat(largeRecord.getEstimateRecruiterCommission()).isEqualTo(135L);
        assertThat(largeRecord.getEstimateChannelCommission()).isEqualTo(135L);
        assertThat(largeRecord.getEstimateGrossProfit()).isEqualTo(630L);

        // Case 3: Asymmetric double-track inputs.
        // estimate path: estimateServiceFee=1000, estimateTech=100, expense=0, talent=0
        //   → profit = 1000 - 100 - 0 = 900, split 135/135/630
        // effective path: effectiveServiceFee=2000, effectiveTech=200 (IGNORED — 0L is passed
        //   as the tech-fee substitute for the settlement track), expense=0, talent=0
        //   → profit = 2000 - 0 - 0 = 2000, split 300/300/1400
        // This freezes the asymmetry: the effective track does NOT re-subtract
        // effectiveTechServiceFee (only estimateTechServiceFee feeds the estimate track).
        ColonelsettlementOrder effOrder = new ColonelsettlementOrder();
        effOrder.setId(UUID.randomUUID());
        effOrder.setOrderId("ORD-CALC-EFF");
        effOrder.setActivityId("TEST_ACTIVITY_A");
        effOrder.setOrderAmount(1_000_000L);
        effOrder.setSettleAmount(1_000_000L);
        effOrder.setEstimateServiceFee(1_000L);
        effOrder.setEffectiveServiceFee(2_000L);
        effOrder.setEstimateTechServiceFee(100L);
        effOrder.setEffectiveTechServiceFee(200L);
        effOrder.setOrderStatus(1);

        PerformanceRecord effRecord = performanceCalculationService.upsertFromOrder(effOrder);
        assertThat(effRecord).isNotNull();
        // estimate track: profit = 1000 - 100 - 0 = 900
        assertThat(effRecord.getEstimateServiceProfit()).isEqualTo(900L);
        assertThat(effRecord.getEstimateRecruiterCommission()).isEqualTo(135L);
        assertThat(effRecord.getEstimateChannelCommission()).isEqualTo(135L);
        assertThat(effRecord.getEstimateGrossProfit()).isEqualTo(630L);
        // effective track: tech-fee substitute is 0L (NOT effectiveTechServiceFee=200)
        //   profit = 2000 - 0 - 0 = 2000, split 300/300/1400
        assertThat(effRecord.getEffectiveServiceProfit()).isEqualTo(2_000L);
        assertThat(effRecord.getEffectiveRecruiterCommission()).isEqualTo(300L);
        assertThat(effRecord.getEffectiveChannelCommission()).isEqualTo(300L);
        assertThat(effRecord.getEffectiveGrossProfit()).isEqualTo(1_400L);
        // asymmetry check: estimate != effective in this case
        assertThat(effRecord.getEstimateServiceProfit())
                .isNotEqualTo(effRecord.getEffectiveServiceProfit());
    }

    @Test
    void test11_UserDataScopeResolutionCharacterizationBaseline() {
        testDataService.seedAll(false);

        // Retrieve seeded user IDs and department IDs
        UUID adminId = UUID.nameUUIDFromBytes("admin".getBytes());
        UUID channelLeaderId = UUID.nameUUIDFromBytes("channel_leader".getBytes());
        UUID channelStaffId = UUID.nameUUIDFromBytes("channel_staff".getBytes());
        UUID bizLeaderId = UUID.nameUUIDFromBytes("biz_leader".getBytes());
        UUID bizStaffId = UUID.nameUUIDFromBytes("biz_staff".getBytes());
        UUID opsStaffId = UUID.nameUUIDFromBytes("ops_staff".getBytes());

        UUID channelDeptId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID bizDeptId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID opsDeptId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        // 1. Admin (ALL scope, code=3)
        CurrentUserResponse adminResp = userDomainService.getCurrentUser(
                adminId, null, DataScope.ALL, List.of(RoleCodes.ADMIN));
        assertThat(adminResp.dataScope()).isEqualTo(3);
        assertThat(adminResp.dataScopeName()).isEqualTo("all");
        var adminScope = userDomainService.getUserDataScope(adminId, null, DataScope.ALL);
        assertThat(adminScope.scope()).isEqualTo("all");
        assertThat(adminScope.userIds()).isEmpty();

        // 2. Channel Leader (DEPT scope, code=2)
        CurrentUserResponse chLeaderResp = userDomainService.getCurrentUser(
                channelLeaderId, channelDeptId, DataScope.DEPT, List.of(RoleCodes.CHANNEL_LEADER));
        assertThat(chLeaderResp.dataScope()).isEqualTo(2);
        assertThat(chLeaderResp.dataScopeName()).isEqualTo("group");
        var chLeaderScope = userDomainService.getUserDataScope(channelLeaderId, channelDeptId, DataScope.DEPT);
        assertThat(chLeaderScope.scope()).isEqualTo("group");
        assertThat(chLeaderScope.userIds()).contains(channelLeaderId, channelStaffId);

        // 3. Channel Staff (PERSONAL scope, code=1)
        CurrentUserResponse chStaffResp = userDomainService.getCurrentUser(
                channelStaffId, channelDeptId, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF));
        assertThat(chStaffResp.dataScope()).isEqualTo(1);
        assertThat(chStaffResp.dataScopeName()).isEqualTo("self");
        var chStaffScope = userDomainService.getUserDataScope(channelStaffId, channelDeptId, DataScope.PERSONAL);
        assertThat(chStaffScope.scope()).isEqualTo("self");
        assertThat(chStaffScope.userIds()).containsExactly(channelStaffId);

        // 4. Biz Leader (DEPT scope, code=2)
        CurrentUserResponse bizLeaderResp = userDomainService.getCurrentUser(
                bizLeaderId, bizDeptId, DataScope.DEPT, List.of(RoleCodes.BIZ_LEADER));
        assertThat(bizLeaderResp.dataScope()).isEqualTo(2);
        assertThat(bizLeaderResp.dataScopeName()).isEqualTo("group");
        var bizLeaderScope = userDomainService.getUserDataScope(bizLeaderId, bizDeptId, DataScope.DEPT);
        assertThat(bizLeaderScope.scope()).isEqualTo("group");
        assertThat(bizLeaderScope.userIds()).contains(bizLeaderId, bizStaffId);

        // 5. Biz Staff (PERSONAL scope, code=1)
        CurrentUserResponse bizStaffResp = userDomainService.getCurrentUser(
                bizStaffId, bizDeptId, DataScope.PERSONAL, List.of(RoleCodes.BIZ_STAFF));
        assertThat(bizStaffResp.dataScope()).isEqualTo(1);
        assertThat(bizStaffResp.dataScopeName()).isEqualTo("self");
        var bizStaffScope = userDomainService.getUserDataScope(bizStaffId, bizDeptId, DataScope.PERSONAL);
        assertThat(bizStaffScope.scope()).isEqualTo("self");
        assertThat(bizStaffScope.userIds()).containsExactly(bizStaffId);

        // 6. Ops Staff (ALL scope, code=3 - because ops_staff maps to ALL)
        CurrentUserResponse opsStaffResp = userDomainService.getCurrentUser(
                opsStaffId, opsDeptId, DataScope.PERSONAL, List.of(RoleCodes.OPS_STAFF));
        assertThat(opsStaffResp.dataScope()).isEqualTo(3);
        assertThat(opsStaffResp.dataScopeName()).isEqualTo("all");
    }

    @Test
    void test12_ProductDetailsCharacterizationBaseline() {
        testDataService.seedAll(false);

        Map<String, Object> detail = productService.getActivityProductDetail("TEST_ACTIVITY_A", "10901825");
        assertThat(detail).isNotNull();
        assertThat(detail.get("productId")).isEqualTo("10901825");
        assertThat(detail.get("activityId")).isEqualTo("TEST_ACTIVITY_A");
        assertThat(detail.get("title")).isNotNull();
        assertThat(detail.get("price")).isNotNull();
        assertThat(detail.get("followRecords")).isNotNull();
        assertThat(detail.get("auditSupplement")).isNotNull();
        assertThat(detail.get("promotionLinks")).isNotNull();
        assertThat(detail.get("promotionMaterialPack")).isNotNull();
    }

    @Test
    void test13_SampleStatesRefinedCharacterizationBaseline() {
        testDataService.seedAll(false);

        UUID bizStaffId = UUID.nameUUIDFromBytes("biz_staff".getBytes());
        UUID bizDeptId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        // Bind mock HttpServletRequest for RoleGuardAspect
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("roleCodes", List.of(RoleCodes.BIZ_STAFF));
        request.setAttribute("userId", bizStaffId);
        request.setAttribute("deptId", bizDeptId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            SampleBatchActionRequest batchReq = new SampleBatchActionRequest();
            batchReq.setRequestNos(List.of("TEST-SAMPLE-PENDING-REVIEW-001"));
            batchReq.setRemark("拒绝测试");

            var result = sampleController.batchReject(batchReq, bizStaffId, bizDeptId, DataScope.ALL, List.of(RoleCodes.BIZ_STAFF));
            assertThat(result).isNotNull();
            assertThat(result.getData()).isNotNull();
            assertThat(result.getData().get("success")).isEqualTo(1);
            assertThat(result.getData().get("fail")).isEqualTo(0);

            // Assert DB status changed to REJECTED (status code = 7)
            Integer status = jdbcTemplate.queryForObject(
                    "SELECT status FROM sample_request WHERE request_no = 'TEST-SAMPLE-PENDING-REVIEW-001'", Integer.class);
            assertThat(status).isEqualTo(7); // 7 represents REJECTED status
            String rejectReason = jdbcTemplate.queryForObject(
                    "SELECT reject_reason FROM sample_request WHERE request_no = 'TEST-SAMPLE-PENDING-REVIEW-001'", String.class);
            assertThat(rejectReason).isEqualTo("拒绝测试");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void test14_DataExportPermissionsCharacterizationBaseline() {
        testDataService.seedAll(false);

        UUID channelLeaderId = UUID.nameUUIDFromBytes("channel_leader".getBytes());
        UUID channelStaffId = UUID.nameUUIDFromBytes("channel_staff".getBytes());
        UUID channelDeptId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        // 1. Channel Staff: Should fail with ForbiddenException (无权限访问该接口)
        MockHttpServletRequest requestStaff = new MockHttpServletRequest();
        requestStaff.setAttribute("roleCodes", List.of(RoleCodes.CHANNEL_STAFF));
        requestStaff.setAttribute("userId", channelStaffId);
        requestStaff.setAttribute("deptId", channelDeptId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requestStaff));

        try {
            MockHttpServletResponse response = new MockHttpServletResponse();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
                dataController.exportOrders(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, channelStaffId, channelDeptId, DataScope.PERSONAL, response);
            }).isInstanceOf(ForbiddenException.class);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        // 2. Channel Leader: Should pass role check and proceed (which might succeed or throw other DB-related errors, but not ForbiddenException)
        MockHttpServletRequest requestLeader = new MockHttpServletRequest();
        requestLeader.setAttribute("roleCodes", List.of(RoleCodes.CHANNEL_LEADER));
        requestLeader.setAttribute("userId", channelLeaderId);
        requestLeader.setAttribute("deptId", channelDeptId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requestLeader));

        try {
            MockHttpServletResponse response = new MockHttpServletResponse();
            dataController.exportOrders(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, channelLeaderId, channelDeptId, DataScope.DEPT, response);
            assertThat(response.getStatus()).isEqualTo(200);
        } catch (Exception e) {
            assertThat(e).isNotInstanceOf(ForbiddenException.class);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
