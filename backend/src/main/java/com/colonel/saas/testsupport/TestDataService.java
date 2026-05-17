package com.colonel.saas.testsupport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.DashboardService;
import com.colonel.saas.service.OrderSyncPersistenceService;
import com.colonel.saas.service.OrderSyncService;
import com.colonel.saas.service.PickSourceMappingService;
import com.colonel.saas.service.SampleStatusLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "app.test", name = "enabled", havingValue = "true")
public class TestDataService implements ApplicationRunner {

    private static final String CHANNEL_USERNAME = "channel_leader";
    private static final String CHANNEL_STAFF_USERNAME = "channel_staff";
    private static final String BIZ_USERNAME = "biz_leader";
    private static final String BIZ_STAFF_USERNAME = "biz_staff";
    private static final String OPS_USERNAME = "ops_staff";
    private static final UUID BIZ_DEPT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CHANNEL_DEPT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OPS_DEPT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String ACTIVITY_ID = "TEST_ACTIVITY_A";
    private static final String PRODUCT_ID = "10901825";
    private static final String SECOND_PRODUCT_ID = "10901826";
    private static final String THIRD_PRODUCT_ID = "10901827";
    private static final String FOURTH_PRODUCT_ID = "10901828";
    private static final String MAPPING_PICK_SOURCE = "TESTPS01";
    private static final String MAPPING_SHORT_ID = "TESTPS01";
    private static final String TALENT_UID_A = "talent_test_a";
    private static final String TALENT_UID_B = "talent_test_b";
    private static final String TALENT_UID_C = "talent_test_c";
    private static final String TALENT_UID_D = "talent_test_d";
    private static final String TALENT_UID_E = "talent_test_e";
    private static final String TALENT_UID_F = "talent_test_f";
    private static final String TALENT_UID_G = "talent_test_g";
    private static final String MAPPING_PICK_SOURCE_D = "TESTPS04";
    private static final String MAPPING_SHORT_ID_D = "TESTPS04";
    private static final String AMBIGUOUS_NATIVE_BUYIN_ID = "900000000001";
    private static final String HISTORY_UNSAFE_BUYIN_ID = "900000000002";

    private final ProductMapper productMapper;
    private final ProductSnapshotMapper productSnapshotMapper;
    private final TalentMapper talentMapper;
    private final SampleRequestMapper sampleRequestMapper;
    private final PickSourceMappingMapper pickSourceMappingMapper;
    private final ColonelsettlementOrderMapper orderMapper;
    private final ProductOperationStateMapper productOperationStateMapper;
    private final PickSourceMappingService pickSourceMappingService;
    private final OrderSyncService orderSyncService;
    private final OrderSyncPersistenceService orderSyncPersistenceService;
    private final AttributionService attributionService;
    private final SampleStatusLogService sampleStatusLogService;
    private final LogisticsGateway logisticsGateway;
    private final JdbcTemplate jdbcTemplate;
    private final boolean seedOnStartup;

    public TestDataService(
            ProductMapper productMapper,
            ProductSnapshotMapper productSnapshotMapper,
            TalentMapper talentMapper,
            SampleRequestMapper sampleRequestMapper,
            PickSourceMappingMapper pickSourceMappingMapper,
            ColonelsettlementOrderMapper orderMapper,
            ProductOperationStateMapper productOperationStateMapper,
            PickSourceMappingService pickSourceMappingService,
            OrderSyncService orderSyncService,
            OrderSyncPersistenceService orderSyncPersistenceService,
            AttributionService attributionService,
            SampleStatusLogService sampleStatusLogService,
            LogisticsGateway logisticsGateway,
            JdbcTemplate jdbcTemplate,
            @Value("${app.test.seed-on-startup:false}") boolean seedOnStartup) {
        this.productMapper = productMapper;
        this.productSnapshotMapper = productSnapshotMapper;
        this.talentMapper = talentMapper;
        this.sampleRequestMapper = sampleRequestMapper;
        this.pickSourceMappingMapper = pickSourceMappingMapper;
        this.orderMapper = orderMapper;
        this.productOperationStateMapper = productOperationStateMapper;
        this.pickSourceMappingService = pickSourceMappingService;
        this.orderSyncService = orderSyncService;
        this.orderSyncPersistenceService = orderSyncPersistenceService;
        this.attributionService = attributionService;
        this.sampleStatusLogService = sampleStatusLogService;
        this.logisticsGateway = logisticsGateway;
        this.jdbcTemplate = jdbcTemplate;
        this.seedOnStartup = seedOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureSchema();
        ensureDemoUserDeptIds();
        if (seedOnStartup) {
            seedAll(false);
        }
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_name = 'product_operation_state' AND column_name = 'biz_status') THEN
                    ALTER TABLE product_operation_state ADD COLUMN biz_status VARCHAR(64);
                  END IF;
                END $$""");
        jdbcTemplate.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_name = 'product_operation_state' AND column_name = 'selected_to_library') THEN
                    ALTER TABLE product_operation_state ADD COLUMN selected_to_library BOOLEAN DEFAULT FALSE;
                  END IF;
                END $$""");
        jdbcTemplate.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_name = 'product_operation_state' AND column_name = 'selected_at') THEN
                    ALTER TABLE product_operation_state ADD COLUMN selected_at TIMESTAMP;
                  END IF;
                END $$""");
        jdbcTemplate.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_name = 'product_operation_state' AND column_name = 'selected_by') THEN
                    ALTER TABLE product_operation_state ADD COLUMN selected_by UUID;
                  END IF;
                END $$""");
        jdbcTemplate.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_name = 'product_operation_log' AND column_name = 'before_status') THEN
                    ALTER TABLE product_operation_log ADD COLUMN before_status VARCHAR(64);
                  END IF;
                END $$""");
        jdbcTemplate.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_name = 'product_operation_log' AND column_name = 'after_status') THEN
                    ALTER TABLE product_operation_log ADD COLUMN after_status VARCHAR(64);
                  END IF;
                END $$""");
        jdbcTemplate.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_name = 'product_operation_log' AND column_name = 'success') THEN
                    ALTER TABLE product_operation_log ADD COLUMN success BOOLEAN DEFAULT TRUE;
                  END IF;
                END $$""");
        jdbcTemplate.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_name = 'product_operation_log' AND column_name = 'error_message') THEN
                    ALTER TABLE product_operation_log ADD COLUMN error_message TEXT;
                  END IF;
                END $$""");
        jdbcTemplate.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_name = 'pick_source_mapping' AND column_name = 'colonel_buyin_id') THEN
                    ALTER TABLE pick_source_mapping ADD COLUMN colonel_buyin_id VARCHAR(32);
                  END IF;
                END $$""");
        jdbcTemplate.execute("DROP INDEX IF EXISTS uk_psm_pick_source");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_psm_pick_source ON pick_source_mapping(pick_source)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_psm_colonel_buyin_id ON pick_source_mapping(colonel_buyin_id)");
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_psm_pick_source_product_activity_user
                ON pick_source_mapping(pick_source, product_id, activity_id, user_id)
                WHERE deleted = 0
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS douyin_webhook_event (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    event_key VARCHAR(256) NOT NULL,
                    event_type VARCHAR(128) NOT NULL,
                    payload_hash VARCHAR(64) NOT NULL,
                    body_length INTEGER DEFAULT 0,
                    raw_payload TEXT,
                    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
                    consume_result VARCHAR(256),
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    processed_at TIMESTAMP,
                    deleted INTEGER DEFAULT 0,
                    create_time TIMESTAMP DEFAULT NOW(),
                    update_time TIMESTAMP DEFAULT NOW(),
                    create_by UUID,
                    update_by UUID
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_douyin_webhook_event_key
                ON douyin_webhook_event(event_key)
                WHERE deleted = 0
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_douyin_webhook_event_status ON douyin_webhook_event(status, create_time)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_douyin_webhook_event_type ON douyin_webhook_event(event_type)");
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> seedAll(boolean syncOrders) {
        UUID channelUserId = requireUserId(CHANNEL_USERNAME);
        UUID channelDeptId = findDeptId(channelUserId);
        UUID channelStaffUserId = requireUserId(CHANNEL_STAFF_USERNAME);
        UUID channelStaffDeptId = findDeptId(channelStaffUserId);
        UUID bizUserId = requireUserId(BIZ_USERNAME);
        ensureMockAuditActivities();
        ensureDisabledDemoUser();

        Product mainProduct = upsertProduct(PRODUCT_ID, "主演示商品-已转链可出单", 9900L, 1, 1);
        Product secondProduct = upsertProduct(SECOND_PRODUCT_ID, "排查演示商品-推广映射缺失", 5900L, 1, 1);
        Product thirdProduct = upsertProduct(THIRD_PRODUCT_ID, "排查演示商品-未带推广参数", 12900L, 1, 1);
        Product approvedProduct = upsertProduct(FOURTH_PRODUCT_ID, "审计演示商品-审核通过待分配", 7900L, 1, 1);

        Talent talentA = upsertTalent(TALENT_UID_A, "达人A-寄样待交作业", 186_000L, "四川成都", "寄样闭环演示");
        Talent talentB = upsertTalent(TALENT_UID_B, "达人B-映射缺失订单", 96_000L, "浙江杭州", "订单排查演示");
        Talent talentC = upsertTalent(TALENT_UID_C, "达人C-他人已认领", 320_000L, "广东深圳", "达人归属演示");
        Talent talentD = upsertTalent(TALENT_UID_D, "达人D-已有订单产出", 42_000L, "江苏南京", "转链出单演示");
        Talent talentE = upsertTalent(TALENT_UID_E, "达人E-保护期到期回公海", 158_000L, "湖北武汉", "达人过期释放演示");
        Talent talentF = upsertTalent(TALENT_UID_F, "达人F-寄样已拒绝", 54_000L, "福建厦门", "寄样拒绝演示");
        Talent talentG = upsertTalent(TALENT_UID_G, "达人G-寄样已关闭", 133_000L, "河南郑州", "寄样关闭演示");

        upsertProductSnapshot(mainProduct, 1, "ON_SHELF", 32560L, "189", "https://test.local/product/" + mainProduct.getProductId());
        upsertProductSnapshot(secondProduct, 0, "PENDING_AUDIT", 8420L, "64", "https://test.local/product/" + secondProduct.getProductId());
        upsertProductSnapshot(thirdProduct, 2, "OFFLINE", 1280L, "12", "https://test.local/product/" + thirdProduct.getProductId());
        upsertProductSnapshot(approvedProduct, 1, "APPROVED", 9800L, "88", "https://test.local/product/" + approvedProduct.getProductId());

        upsertOperationState(mainProduct.getProductId(), bizUserId, "ASSIGNED", 2, "test assigned");
        upsertOperationState(secondProduct.getProductId(), null, "PENDING_AUDIT", 1, "test pending audit");
        upsertOperationState(thirdProduct.getProductId(), null, "REJECTED", 3, "test rejected");
        upsertOperationState(approvedProduct.getProductId(), null, "APPROVED", 2, "test approved for audit coverage");

        pickSourceMappingService.saveOrUpdate(
                channelUserId,
                "渠道负责人-主链路演示",
                channelDeptId,
                TALENT_UID_A,
                talentA.getNickname(),
                MAPPING_SHORT_ID,
                UUID.nameUUIDFromBytes(MAPPING_PICK_SOURCE.getBytes()),
                MAPPING_PICK_SOURCE,
                mainProduct.getProductId(),
                ACTIVITY_ID,
                "https://test.source.local/product/" + mainProduct.getProductId(),
                "https://test.promote.link/activity/" + ACTIVITY_ID + "/product/" + mainProduct.getProductId() + "?pick_source=" + MAPPING_PICK_SOURCE,
                null,
                "PRODUCT_LIBRARY"
        );
        pickSourceMappingService.saveOrUpdate(
                channelUserId,
                "渠道负责人-主链路演示",
                channelDeptId,
                TALENT_UID_D,
                talentD.getNickname(),
                MAPPING_SHORT_ID_D,
                UUID.nameUUIDFromBytes(MAPPING_PICK_SOURCE_D.getBytes()),
                MAPPING_PICK_SOURCE_D,
                mainProduct.getProductId(),
                ACTIVITY_ID,
                "https://test.source.local/product/" + mainProduct.getProductId(),
                "https://test.promote.link/activity/" + ACTIVITY_ID + "/product/" + mainProduct.getProductId() + "?pick_source=" + MAPPING_PICK_SOURCE_D,
                null,
                "PRODUCT_LIBRARY"
        );

        SampleRequest sampleRequest = upsertPendingHomeworkSample(mainProduct, talentA, bizUserId, channelUserId, channelDeptId);
        SampleRequest sampleForOrders = upsertFinishedSample(mainProduct, talentD, bizUserId, channelUserId, channelDeptId);
        SampleRequest rejectedSample = upsertRejectedSample(secondProduct, talentF, bizUserId, channelUserId, channelDeptId);
        SampleRequest closedSample = upsertClosedSample(thirdProduct, talentG, bizUserId, channelUserId, channelDeptId);
        SampleRequest pendingShipSample = upsertPendingShipSample(secondProduct, talentB, bizUserId, channelUserId, channelDeptId);
        SampleRequest pendingReviewSample = upsertPendingReviewSample(thirdProduct, talentF, bizUserId, channelUserId, channelDeptId);
        SampleRequest shippingSample = upsertShippingSample(secondProduct, talentB, bizUserId, channelUserId, channelDeptId);

        upsertCrawlerTalent("talent_test_a", "达人A-寄样待交作业", 186_000L, 4.9, "四川成都", "美妆个护");
        upsertCrawlerTalent("talent_test_b", "达人B-映射缺失订单", 96_000L, 4.2, "浙江杭州", "服饰穿搭");
        upsertCrawlerTalent("talent_test_c", "达人C-他人已认领", 320_000L, 4.8, "广东深圳", "母婴用品");
        upsertCrawlerTalent("talent_test_d", "达人D-已有订单产出", 42_000L, 4.5, "江苏南京", "数码家电");
        upsertCrawlerTalent("talent_test_e", "达人E-保护期到期回公海", 158_000L, 4.6, "湖北武汉", "家居日用");
        upsertCrawlerTalent("talent_test_f", "达人F-寄样已拒绝", 54_000L, 4.1, "福建厦门", "零食饮品");
        upsertCrawlerTalent("talent_test_g", "达人G-寄样已关闭", 133_000L, 4.7, "河南郑州", "个护家清");

        resetTalentClaims();
        upsertTalentClaim(talentB.getId(), talentB.getDouyinUid(), channelUserId, channelDeptId, LocalDateTime.now().minusDays(2));
        upsertTalentClaim(talentC.getId(), talentC.getDouyinUid(), channelStaffUserId, channelStaffDeptId, LocalDateTime.now().minusDays(4));
        upsertTalentClaim(talentC.getId(), talentC.getDouyinUid(), channelUserId, channelDeptId, LocalDateTime.now().minusDays(3));
        upsertExpiredTalentClaim(
                talentE.getId(),
                talentE.getDouyinUid(),
                channelUserId,
                channelDeptId,
                LocalDateTime.now().minusDays(45),
                LocalDateTime.now().minusDays(15)
        );

        seedAttributedOrder("MOCK_SEED_TALENT_D_ORDER", mainProduct, talentD, MAPPING_PICK_SOURCE_D, "M_TALENT_D", "主演示商家-达人D转化");
        seedMockAuditOrder(
                "MOCK_AUDIT_PRODUCT_UNCOVERED",
                thirdProduct,
                talentE,
                "MOCK_MISSING_PRODUCT",
                "商品未覆盖商家",
                null,
                AttributionService.REASON_PRODUCT_NOT_FOUND,
                1,
                18600L,
                0L,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().plusMonths(1),
                LocalDateTime.now().plusDays(1)
        );
        seedMockAuditOrder(
                "MOCK_AUDIT_REFUNDED_CLOSED",
                approvedProduct,
                talentB,
                "M_REFUND_CLOSED",
                "退款关闭商家",
                MAPPING_PICK_SOURCE,
                "REFUNDED_OR_CLOSED",
                4,
                4200L,
                120L,
                LocalDateTime.now(),
                null,
                LocalDateTime.now().minusDays(7)
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("products", List.of(mainProduct.getProductId(), secondProduct.getProductId(), thirdProduct.getProductId(), approvedProduct.getProductId()));
        result.put("talents", List.of(
                talentA.getDouyinUid(),
                talentB.getDouyinUid(),
                talentC.getDouyinUid(),
                talentD.getDouyinUid(),
                talentE.getDouyinUid(),
                talentF.getDouyinUid(),
                talentG.getDouyinUid()));
        result.put("pickSource", MAPPING_PICK_SOURCE);
        result.put("sampleRequestNo", sampleRequest.getRequestNo());
        result.put("orderSampleRequestNo", sampleForOrders.getRequestNo());
        result.put("rejectedSampleRequestNo", rejectedSample.getRequestNo());
        result.put("closedSampleRequestNo", closedSample.getRequestNo());
        result.put("pendingShipSampleRequestNo", pendingShipSample.getRequestNo());
        result.put("pendingReviewSampleRequestNo", pendingReviewSample.getRequestNo());
        result.put("shippingSampleRequestNo", shippingSample.getRequestNo());
        result.put("shippingSampleId", shippingSample.getId());
        result.put("expiredClaimTalentUid", talentE.getDouyinUid());

        if (syncOrders) {
            long now = System.currentTimeMillis() / 1000;
            result.put("orderSync", orderSyncService.syncByTimeRange(now - 7200, now + 60));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resetAll() {
        jdbcTemplate.update("DELETE FROM talent_claim");
        jdbcTemplate.update("DELETE FROM sample_status_log");
        jdbcTemplate.update("DELETE FROM sample_request");
        jdbcTemplate.update("DELETE FROM colonelsettlement_order");
        jdbcTemplate.update("DELETE FROM pick_source_mapping");
        jdbcTemplate.update("DELETE FROM promotion_link");
        jdbcTemplate.update("DELETE FROM product_operation_log");
        jdbcTemplate.update("DELETE FROM product_operation_state");
        jdbcTemplate.update("DELETE FROM product_snapshot");
        jdbcTemplate.update("DELETE FROM talent");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM crawler_talent_info");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("products", 0);
        result.put("talents", 0);
        result.put("orders", 0);
        result.put("pickSourceMappings", 0);
        result.put("sampleRequests", 0);
        result.put("talentClaims", 0);
        return result;
    }

    public OrderSyncService.SyncResult syncTestOrders() {
        long now = System.currentTimeMillis() / 1000;
        return orderSyncService.syncByTimeRange(now - 7200, now + 60);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateAttributedOrder() {
        Product product = requireProduct(PRODUCT_ID);
        PickSourceMapping mapping = requireMapping(MAPPING_PICK_SOURCE);
        ColonelsettlementOrder order = buildTestOrder(
                "MOCK_GEN_ATTR_" + System.currentTimeMillis(),
                product,
                "主演示商品-已转链可出单",
                19900L,
                2600L,
                mapping.getPickSource(),
                mapping.getTalentId(),
                mapping.getActivityId(),
                "M_ATTR",
                "主演示商家-归因成功"
        );
        Map<String, Object> source = new LinkedHashMap<>(order.getExtraData());
        AttributionService.AttributionResult attribution = attributionService.resolveAttribution(order, source);
        applyAttribution(order, attribution);
        boolean inserted = orderSyncPersistenceService.persistOrder(order);
        SampleRequest sample = findSampleByRequestNo("TEST-SAMPLE-001");
        return orderResult("attributed", order, inserted, sample);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateNoPickSourceOrder() {
        Product product = requireProduct(THIRD_PRODUCT_ID);
        ColonelsettlementOrder order = buildTestOrder(
                "MOCK_GEN_NOPICK_" + System.currentTimeMillis(),
                product,
                "排查演示商品-未带推广参数",
                12900L,
                2000L,
                null,
                TALENT_UID_C,
                ACTIVITY_ID,
                "M_NOPICK",
                "排查演示商家-未带推广参数"
        );
        Map<String, Object> source = new LinkedHashMap<>(order.getExtraData());
        AttributionService.AttributionResult attribution = attributionService.resolveAttribution(order, source);
        applyAttribution(order, attribution);
        boolean inserted = orderSyncPersistenceService.persistOrder(order);
        return orderResult("no-pick-source", order, inserted, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateMissingMappingOrder() {
        Product product = requireProduct(SECOND_PRODUCT_ID);
        ColonelsettlementOrder order = buildTestOrder(
                "MOCK_GEN_NOMAP_" + System.currentTimeMillis(),
                product,
                "排查演示商品-推广映射缺失",
                9900L,
                1200L,
                "UPS" + (System.currentTimeMillis() % 10_000),
                TALENT_UID_B,
                ACTIVITY_ID,
                "M_NOMAP",
                "排查演示商家-映射缺失"
        );
        Map<String, Object> source = new LinkedHashMap<>(order.getExtraData());
        AttributionService.AttributionResult attribution = attributionService.resolveAttribution(order, source);
        applyAttribution(order, attribution);
        boolean inserted = orderSyncPersistenceService.persistOrder(order);
        return orderResult("missing-mapping", order, inserted, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateAmbiguousMappingOrder() {
        Product product = requireProduct(PRODUCT_ID);
        Talent talent = requireTalentByUid(TALENT_UID_B);
        UUID channelLeaderId = requireUserId(CHANNEL_USERNAME);
        UUID channelLeaderDeptId = findDeptId(channelLeaderId);
        UUID channelStaffId = requireUserId(CHANNEL_STAFF_USERNAME);
        UUID channelStaffDeptId = findDeptId(channelStaffId);
        LocalDateTime orderCreateTime = LocalDateTime.now().minusDays(2);

        upsertNativeMapping(
                channelLeaderId,
                channelLeaderDeptId,
                talent.getDouyinUid(),
                talent.getNickname(),
                product.getProductId(),
                ACTIVITY_ID,
                AMBIGUOUS_NATIVE_BUYIN_ID,
                "AMBPSA" + (System.currentTimeMillis() % 100000),
                orderCreateTime.minusHours(6)
        );
        upsertNativeMapping(
                channelStaffId,
                channelStaffDeptId,
                talent.getDouyinUid(),
                talent.getNickname(),
                product.getProductId(),
                ACTIVITY_ID,
                AMBIGUOUS_NATIVE_BUYIN_ID,
                "AMBPSB" + (System.currentTimeMillis() % 100000),
                orderCreateTime.minusHours(5)
        );

        ColonelsettlementOrder order = buildTestOrder(
                "MOCK_GEN_AMBIG_" + System.currentTimeMillis(),
                product,
                "主演示商品-多候选歧义映射",
                12900L,
                1800L,
                null,
                talent.getDouyinUid(),
                ACTIVITY_ID,
                "M_AMBIG",
                "排查演示商家-多候选"
        );
        order.setTalentId(talent.getId());
        order.setTalentName(talent.getNickname());
        order.setCreateTime(orderCreateTime);
        order.setSettleTime(orderCreateTime.plusHours(12));
        Map<String, Object> extra = new LinkedHashMap<>(order.getExtraData());
        extra.put("colonel_buyin_id", AMBIGUOUS_NATIVE_BUYIN_ID);
        extra.put("mockScenario", "AMBIGUOUS_MAPPING");
        order.setExtraData(extra);
        AttributionService.AttributionResult attribution = attributionService.resolveAttribution(order, new LinkedHashMap<>(extra));
        applyAttribution(order, attribution);
        order.setTalentName(talent.getNickname());
        boolean inserted = orderSyncPersistenceService.persistOrder(order);
        updateOrderNativeColumns(order.getOrderId(), orderCreateTime, order.getSettleTime(), AMBIGUOUS_NATIVE_BUYIN_ID);

        Map<String, Object> result = orderResult("ambiguous-mapping", order, inserted, null);
        result.put("dashboardDiagnosis", DashboardService.DIAGNOSIS_AMBIGUOUS_MAPPING);
        result.put("colonelBuyinId", AMBIGUOUS_NATIVE_BUYIN_ID);
        result.put("nativeMappingUsers", List.of(channelLeaderId, channelStaffId));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateHistoryUnsafeOrder() {
        Product product = requireProduct(PRODUCT_ID);
        Talent talent = requireTalentByUid(TALENT_UID_D);
        UUID channelLeaderId = requireUserId(CHANNEL_USERNAME);
        UUID channelLeaderDeptId = findDeptId(channelLeaderId);
        LocalDateTime orderCreateTime = LocalDateTime.now().minusDays(5);
        LocalDateTime mappingCreateTime = orderCreateTime.plusDays(3);

        upsertNativeMapping(
                channelLeaderId,
                channelLeaderDeptId,
                talent.getDouyinUid(),
                talent.getNickname(),
                product.getProductId(),
                ACTIVITY_ID,
                HISTORY_UNSAFE_BUYIN_ID,
                "UNSAFE" + (System.currentTimeMillis() % 100000),
                mappingCreateTime
        );

        ColonelsettlementOrder order = buildTestOrder(
                "MOCK_GEN_UNSAFE_" + System.currentTimeMillis(),
                product,
                "主演示商品-历史不可回填",
                15900L,
                2100L,
                null,
                talent.getDouyinUid(),
                ACTIVITY_ID,
                "M_UNSAFE",
                "排查演示商家-历史不可回填"
        );
        order.setTalentId(talent.getId());
        order.setTalentName(talent.getNickname());
        order.setCreateTime(orderCreateTime);
        order.setSettleTime(orderCreateTime.plusHours(6));
        order.setAttributionStatus(AttributionService.STATUS_UNATTRIBUTED);
        order.setAttributionRemark(AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND);
        Map<String, Object> extra = new LinkedHashMap<>(order.getExtraData());
        extra.put("colonel_buyin_id", HISTORY_UNSAFE_BUYIN_ID);
        extra.put("mockScenario", "MECHANISM_HIT_HISTORY_UNSAFE");
        extra.put("mappingCreateTime", mappingCreateTime.toString());
        order.setExtraData(extra);
        boolean inserted = orderSyncPersistenceService.persistOrder(order);
        updateOrderNativeColumns(order.getOrderId(), orderCreateTime, order.getSettleTime(), HISTORY_UNSAFE_BUYIN_ID);

        Map<String, Object> result = orderResult("history-unsafe", order, inserted, null);
        result.put("dashboardDiagnosis", DashboardService.DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE);
        result.put("colonelBuyinId", HISTORY_UNSAFE_BUYIN_ID);
        result.put("mappingCreateTime", mappingCreateTime);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateProductUncoveredOrder() {
        Product product = requireProduct(THIRD_PRODUCT_ID);
        Talent talent = requireTalentByUid(TALENT_UID_E);
        String uncoveredProductId = "UNCOVERED_" + (System.currentTimeMillis() % 1000000);
        LocalDateTime createTime = LocalDateTime.now().minusDays(1);

        ColonelsettlementOrder order = buildTestOrder(
                "MOCK_GEN_UNCOVERED_" + System.currentTimeMillis(),
                product,
                "排查演示商品-活动商品未覆盖",
                18900L,
                0L,
                null,
                talent.getDouyinUid(),
                ACTIVITY_ID,
                "M_UNCOVERED",
                "排查演示商家-活动商品未覆盖"
        );
        order.setProductId(uncoveredProductId);
        order.setProductName("活动商品未覆盖-" + uncoveredProductId);
        order.setProductTitle(order.getProductName());
        order.setTalentId(talent.getId());
        order.setTalentName(talent.getNickname());
        order.setCreateTime(createTime);
        order.setSettleTime(createTime.plusHours(4));
        order.setAttributionStatus(AttributionService.STATUS_UNATTRIBUTED);
        order.setAttributionRemark(AttributionService.REASON_PRODUCT_NOT_FOUND);
        Map<String, Object> extra = new LinkedHashMap<>(order.getExtraData());
        extra.put("mockScenario", "UPSTREAM_PRODUCT_UNCOVERED");
        order.setExtraData(extra);
        boolean inserted = orderSyncPersistenceService.persistOrder(order);

        Map<String, Object> result = orderResult("product-uncovered", order, inserted, null);
        result.put("dashboardDiagnosis", DashboardService.DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED);
        result.put("uncoveredProductId", uncoveredProductId);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> shipSample(UUID sampleRequestId) {
        SampleRequest sample = requireSample(sampleRequestId);
        ensureSampleStatus(sample, 2);
        Product product = productMapper.selectById(sample.getProductId());
        LogisticsGateway.LogisticsResult shipment = logisticsGateway.createShipment(new LogisticsGateway.LogisticsCommand(
                sample.getId(),
                product == null ? null : product.getProductId(),
                "演示收件人-主链路",
                "13800000000",
                "成都市高新区主链路演示仓"
        ));
        int fromStatus = sample.getStatus();
        sample.setStatus(3);
        sample.setTrackingNo(shipment.trackingNo());
        putSampleExtraValue(sample, "logisticsSource", "MOCK");
        sample.setShipTime(shipment.shipTime());
        sampleRequestMapper.updateById(sample);
        sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), sample.getUserId(), "test logistics ship");
        return sampleResult(sample, "shipping", shipment.trackingNo());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> signSample(UUID sampleRequestId) {
        SampleRequest sample = requireSample(sampleRequestId);
        ensureSampleStatus(sample, 3);
        LogisticsGateway.LogisticsStatusResult logisticsStatus = logisticsGateway.queryStatus(sample.getTrackingNo());
        int fromStatus = sample.getStatus();
        sample.setStatus(5);
        sample.setDeliverTime(LocalDateTime.now());
        putSampleExtraValueIfMissing(sample, "logisticsSource", "MOCK");
        sampleRequestMapper.updateById(sample);
        sampleStatusLogService.log(sample.getId(), fromStatus, 4, sample.getUserId(), "test logistics delivered");
        sampleStatusLogService.log(sample.getId(), 4, sample.getStatus(), sample.getUserId(), "test logistics sign -> pending homework");
        return sampleResult(sample, logisticsStatus.status().toLowerCase(Locale.ROOT), logisticsStatus.trackingNo());
    }

    private void ensureMockAuditActivities() {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO colonel_activity (
                    id, activity_id, activity_name, shop_id, shop_name, colonel_buyin_id,
                    commission_rate, service_rate, start_time, end_time, status, last_sync_at,
                    deleted, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                ON CONFLICT (activity_id) DO UPDATE SET
                    activity_name = EXCLUDED.activity_name,
                    shop_id = EXCLUDED.shop_id,
                    shop_name = EXCLUDED.shop_name,
                    colonel_buyin_id = EXCLUDED.colonel_buyin_id,
                    commission_rate = EXCLUDED.commission_rate,
                    service_rate = EXCLUDED.service_rate,
                    start_time = EXCLUDED.start_time,
                    end_time = EXCLUDED.end_time,
                    status = EXCLUDED.status,
                    last_sync_at = EXCLUDED.last_sync_at,
                    deleted = 0,
                    update_time = EXCLUDED.update_time
                """,
                UUID.nameUUIDFromBytes(ACTIVITY_ID.getBytes()),
                ACTIVITY_ID,
                "TEST/mock 审计主链路活动",
                10001001L,
                "TEST/mock 审计店铺",
                90000001L,
                BigDecimal.valueOf(0.25),
                BigDecimal.valueOf(0.08),
                now.minusDays(1),
                now.plusDays(20),
                "进行中",
                now,
                now,
                now
        );
    }

    private void ensureDisabledDemoUser() {
        String password = jdbcTemplate.query(
                "SELECT password FROM sys_user WHERE username = ? AND deleted = 0 LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null,
                CHANNEL_STAFF_USERNAME
        );
        if (password == null) {
            password = "";
        }
        UUID disabledUserId = UUID.nameUUIDFromBytes("mock-audit-disabled-user".getBytes());
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, username, password, real_name, channel_code, dept_id, status, deleted,
                    create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (username) DO UPDATE SET
                    real_name = EXCLUDED.real_name,
                    channel_code = EXCLUDED.channel_code,
                    dept_id = EXCLUDED.dept_id,
                    status = 0,
                    deleted = 0,
                    update_time = CURRENT_TIMESTAMP
                """,
                disabledUserId,
                "disabled_audit_user",
                password,
                "停用账号审计样本",
                "disabled_audit",
                CHANNEL_DEPT_ID
        );
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id)
                SELECT ?, r.id
                FROM sys_role r
                WHERE r.role_code = ?
                ON CONFLICT DO NOTHING
                """,
                disabledUserId,
                "channel_staff"
        );
    }

    private Product upsertProduct(String productId, String name, long price, int status, int checkStatus) {
        Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductId, productId)
                .last("limit 1"));
        if (product == null) {
            product = new Product();
            product.setId(UUID.randomUUID());
            product.setDeleted(0);
        }
        product.setProductId(productId);
        product.setName(name);
        product.setPrice(price);
        product.setStatus(status);
        product.setCheckStatus(checkStatus);
        product.setCategory("本地演示商品");
        if (product.getCreateTime() == null) {
            productMapper.insert(product);
        } else {
            productMapper.updateById(product);
        }
        return product;
    }

    private Talent upsertTalent(String douyinUid, String nickname, long fans, String ipLocation, String sourceTag) {
        Talent talent = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, douyinUid)
                .last("limit 1"));
        if (talent == null) {
            talent = new Talent();
            talent.setId(UUID.randomUUID());
            talent.setDeleted(0);
        }
        talent.setDouyinUid(douyinUid);
        talent.setUid(douyinUid);
        talent.setDouyinNo("douyin-demo-" + douyinUid);
        talent.setNickname(nickname);
        talent.setFans(fans);
        talent.setLevel(fans > 100_000 ? "A" : "B");
        talent.setLikesCount(fans * 8);
        talent.setFollowingCount(321L);
        talent.setWorksCount(87L);
        talent.setIpLocation(ipLocation);
        talent.setAvatarUrl("https://test.local/avatar/" + douyinUid + ".png");
        talent.setStatus(1);
        talent.setCrawlStatus(1);
        talent.setCrawlMessage("演示资料已准备: " + sourceTag);
        talent.setLastCrawlAt(LocalDateTime.now());
        if (talent.getCreateTime() == null) {
            talentMapper.insert(talent);
        } else {
            talentMapper.updateById(talent);
        }
        return talent;
    }

    private ProductOperationState upsertOperationState(
            String productId,
            UUID assigneeId,
            String bizStatus,
            int auditStatus,
            String auditRemark) {
        boolean selectedToLibrary = auditStatus == 2;
        LocalDateTime selectedAt = selectedToLibrary ? LocalDateTime.now() : null;
        UUID id = jdbcTemplate.query(
                """
                INSERT INTO product_operation_state (
                    id, activity_id, product_id, assignee_id, biz_status,
                    audit_status, audit_remark, selected_to_library, selected_at, selected_by,
                    last_operation_at, deleted,
                    create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (activity_id, product_id) DO UPDATE SET
                    assignee_id = EXCLUDED.assignee_id,
                    biz_status = EXCLUDED.biz_status,
                    audit_status = EXCLUDED.audit_status,
                    audit_remark = EXCLUDED.audit_remark,
                    selected_to_library = EXCLUDED.selected_to_library,
                    selected_at = EXCLUDED.selected_at,
                    selected_by = EXCLUDED.selected_by,
                    last_operation_at = EXCLUDED.last_operation_at,
                    deleted = 0,
                    update_time = CURRENT_TIMESTAMP
                RETURNING id
                """,
                rs -> rs.next() ? UUID.fromString(rs.getString(1)) : null,
                UUID.randomUUID(),
                ACTIVITY_ID,
                productId,
                assigneeId,
                bizStatus,
                auditStatus,
                auditRemark,
                selectedToLibrary,
                selectedAt,
                assigneeId,
                LocalDateTime.now()
        );
        return productOperationStateMapper.selectById(id);
    }

    private ProductSnapshot upsertProductSnapshot(
            Product product,
            int status,
            String statusText,
            long sales,
            String stock,
            String detailUrl) {
        UUID snapshotId = UUID.nameUUIDFromBytes((ACTIVITY_ID + ":" + product.getProductId()).getBytes());
        ProductSnapshot snapshot = productSnapshotMapper.selectById(snapshotId);
        if (snapshot == null) {
            snapshot = new ProductSnapshot();
            snapshot.setId(snapshotId);
            snapshot.setDeleted(0);
            snapshot.setActivityId(ACTIVITY_ID);
            snapshot.setProductId(product.getProductId());
        }
        snapshot.setTitle(product.getName());
        snapshot.setCover("https://test.local/product/" + product.getProductId() + ".png");
        snapshot.setPrice(product.getPrice());
        snapshot.setPriceText(formatPriceText(product.getPrice()));
        snapshot.setShopId(1000L + Long.parseLong(product.getProductId()));
        snapshot.setShopName("演示店铺-" + product.getProductId());
        snapshot.setStatus(status);
        snapshot.setStatusText(statusText);
        snapshot.setCategoryName("本地演示");
        snapshot.setProductStock(stock);
        snapshot.setSales(sales);
        snapshot.setDetailUrl(detailUrl);
        snapshot.setPromotionStartTime(LocalDateTime.now().minusDays(7).toString());
        snapshot.setPromotionEndTime(LocalDateTime.now().plusDays(7).toString());
        snapshot.setActivityCosRatio(2500L);
        snapshot.setActivityCosRatioText("25%");
        snapshot.setCosType(1);
        snapshot.setCosTypeText("佣金比例");
        snapshot.setAdServiceRatio("8%");
        snapshot.setActivityAdCosRatio(800L);
        snapshot.setHasDouinGoodsTag(Boolean.TRUE);
        snapshot.setRawPayload("{\"test\":true,\"activityId\":\"" + ACTIVITY_ID + "\",\"productId\":\"" + product.getProductId() + "\"}");
        snapshot.setSyncTime(LocalDateTime.now());
        productSnapshotMapper.upsert(snapshot);
        return snapshot;
    }

    private SampleRequest upsertPendingHomeworkSample(
            Product product,
            Talent talent,
            UUID userId,
            UUID channelUserId,
            UUID channelDeptId) {
        String requestNo = "TEST-SAMPLE-001";
        SampleRequest sample = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("limit 1"));
        if (sample == null) {
            sample = new SampleRequest();
            sample.setId(UUID.randomUUID());
            sample.setDeleted(0);
        }
        sample.setRequestNo(requestNo);
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talent.getDouyinUid());
        sample.setTalentNickname(talent.getNickname());
        sample.setTalentFansCount(talent.getFans());
        sample.setTalentCreditScore(BigDecimal.valueOf(9.20));
        sample.setTalentMainCategory("美妆个护");
        sample.setProductId(product.getId());
        sample.setUserId(userId);
        sample.setChannelUserId(channelUserId);
        sample.setExpectedSampleNum(1);
        sample.setActualSampleNum(1);
        sample.setTrackingNo("TEST-TRACK-001");
        sample.setStatus(5);
        sample.setAuditTime(LocalDateTime.now().minusDays(3));
        sample.setShipTime(LocalDateTime.now().minusDays(2));
        sample.setDeliverTime(LocalDateTime.now().minusDays(1));
        sample.setRemark("本地演示数据自动准备");
        if (sample.getCreateTime() == null) {
            jdbcTemplate.update("""
                    INSERT INTO sample_request (
                        id, request_no, talent_id, talent_uid, talent_nickname, product_id, user_id, dept_id,
                        channel_user_id, channel_dept_id, recipient_name, recipient_phone, recipient_address,
                        expected_sample_num, actual_sample_num, logistics_company, tracking_no, status,
                        sample_fee, audit_time, ship_time, deliver_time, deleted, create_time, update_time, create_by, update_by, remark
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?)
                    ON CONFLICT (request_no) DO UPDATE SET
                        talent_id = EXCLUDED.talent_id,
                        talent_uid = EXCLUDED.talent_uid,
                        talent_nickname = EXCLUDED.talent_nickname,
                        product_id = EXCLUDED.product_id,
                        user_id = EXCLUDED.user_id,
                        dept_id = EXCLUDED.dept_id,
                        channel_user_id = EXCLUDED.channel_user_id,
                        channel_dept_id = EXCLUDED.channel_dept_id,
                        recipient_name = EXCLUDED.recipient_name,
                        recipient_phone = EXCLUDED.recipient_phone,
                        recipient_address = EXCLUDED.recipient_address,
                        expected_sample_num = EXCLUDED.expected_sample_num,
                        actual_sample_num = EXCLUDED.actual_sample_num,
                        logistics_company = EXCLUDED.logistics_company,
                        tracking_no = EXCLUDED.tracking_no,
                        status = EXCLUDED.status,
                        sample_fee = EXCLUDED.sample_fee,
                        audit_time = EXCLUDED.audit_time,
                        ship_time = EXCLUDED.ship_time,
                        deliver_time = EXCLUDED.deliver_time,
                        deleted = EXCLUDED.deleted,
                        update_time = CURRENT_TIMESTAMP,
                        update_by = EXCLUDED.update_by,
                        remark = EXCLUDED.remark
                    """,
                    sample.getId(),
                    requestNo,
                    talent.getId(),
                    sample.getTalentUid(),
                    sample.getTalentNickname(),
                    product.getId(),
                    userId,
                    channelDeptId,
                    channelUserId,
                    channelDeptId,
                    "演示收件人-主链路",
                    "13800000000",
                    "成都市高新区主链路演示仓",
                    sample.getExpectedSampleNum(),
                    sample.getActualSampleNum(),
                    "演示物流-顺丰模拟",
                    sample.getTrackingNo(),
                    sample.getStatus(),
                    0L,
                    sample.getAuditTime(),
                    sample.getShipTime(),
                    sample.getDeliverTime(),
                    0,
                    userId,
                    userId,
                    sample.getRemark()
            );
            sample = sampleRequestMapper.selectById(sample.getId());
        } else {
            sampleRequestMapper.updateById(sample);
        }
        return sample;
    }

    private SampleRequest upsertPendingShipSample(
            Product product,
            Talent talent,
            UUID userId,
            UUID channelUserId,
            UUID channelDeptId) {
        String requestNo = "TEST-SAMPLE-SHIP-001";
        SampleRequest sample = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("limit 1"));
        if (sample == null) {
            sample = new SampleRequest();
            sample.setId(UUID.randomUUID());
            sample.setDeleted(0);
        }
        sample.setRequestNo(requestNo);
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talent.getDouyinUid());
        sample.setTalentNickname(talent.getNickname());
        sample.setTalentFansCount(talent.getFans());
        sample.setTalentCreditScore(BigDecimal.valueOf(8.00));
        sample.setTalentMainCategory("服饰穿搭");
        sample.setProductId(product.getId());
        sample.setUserId(userId);
        sample.setChannelUserId(channelUserId);
        sample.setExpectedSampleNum(1);
        sample.setActualSampleNum(0);
        sample.setStatus(2);
        sample.setAuditTime(LocalDateTime.now().minusHours(6));
        sample.setRemark("test pending ship");
        jdbcTemplate.update("""
                INSERT INTO sample_request (
                    id, request_no, talent_id, talent_uid, talent_nickname, product_id, user_id, dept_id,
                    channel_user_id, channel_dept_id, recipient_name, recipient_phone, recipient_address,
                    expected_sample_num, actual_sample_num, status, sample_fee, audit_time, deleted,
                    create_time, update_time, create_by, update_by, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?)
                ON CONFLICT (request_no) DO UPDATE SET
                    talent_id = EXCLUDED.talent_id,
                    talent_uid = EXCLUDED.talent_uid,
                    talent_nickname = EXCLUDED.talent_nickname,
                    product_id = EXCLUDED.product_id,
                    user_id = EXCLUDED.user_id,
                    dept_id = EXCLUDED.dept_id,
                    channel_user_id = EXCLUDED.channel_user_id,
                    channel_dept_id = EXCLUDED.channel_dept_id,
                    recipient_name = EXCLUDED.recipient_name,
                    recipient_phone = EXCLUDED.recipient_phone,
                    recipient_address = EXCLUDED.recipient_address,
                    expected_sample_num = EXCLUDED.expected_sample_num,
                    actual_sample_num = EXCLUDED.actual_sample_num,
                    status = EXCLUDED.status,
                    sample_fee = EXCLUDED.sample_fee,
                    audit_time = EXCLUDED.audit_time,
                    deleted = EXCLUDED.deleted,
                    update_time = CURRENT_TIMESTAMP,
                    update_by = EXCLUDED.update_by,
                    remark = EXCLUDED.remark
                """,
                sample.getId(),
                requestNo,
                talent.getId(),
                sample.getTalentUid(),
                sample.getTalentNickname(),
                product.getId(),
                userId,
                channelDeptId,
                channelUserId,
                channelDeptId,
                "演示收件人-订单排查",
                "13800000001",
                "杭州市余杭区物流演示仓",
                sample.getExpectedSampleNum(),
                sample.getActualSampleNum(),
                sample.getStatus(),
                0L,
                sample.getAuditTime(),
                0,
                userId,
                userId,
                sample.getRemark()
        );
        return sampleRequestMapper.selectById(sample.getId());
    }

    private SampleRequest upsertPendingReviewSample(
            Product product,
            Talent talent,
            UUID userId,
            UUID channelUserId,
            UUID channelDeptId) {
        String requestNo = "TEST-SAMPLE-PENDING-REVIEW-001";
        SampleRequest sample = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("limit 1"));
        if (sample == null) {
            sample = new SampleRequest();
            sample.setId(UUID.randomUUID());
            sample.setDeleted(0);
        }
        sample.setRequestNo(requestNo);
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talent.getDouyinUid());
        sample.setTalentNickname(talent.getNickname());
        sample.setTalentFansCount(talent.getFans());
        sample.setTalentCreditScore(BigDecimal.valueOf(7.50));
        sample.setTalentMainCategory("零食饮品");
        sample.setProductId(product.getId());
        sample.setUserId(userId);
        sample.setChannelUserId(channelUserId);
        sample.setExpectedSampleNum(1);
        sample.setActualSampleNum(0);
        sample.setStatus(1);
        sample.setRemark("审计样例：达人不符合默认资格但已填写申请原因");
        jdbcTemplate.update("""
                INSERT INTO sample_request (
                    id, request_no, talent_id, talent_uid, talent_nickname, product_id, user_id, dept_id,
                    channel_user_id, channel_dept_id, recipient_name, recipient_phone, recipient_address,
                    expected_sample_num, actual_sample_num, status, sample_fee, deleted,
                    create_time, update_time, create_by, update_by, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?)
                ON CONFLICT (request_no) DO UPDATE SET
                    talent_id = EXCLUDED.talent_id,
                    talent_uid = EXCLUDED.talent_uid,
                    talent_nickname = EXCLUDED.talent_nickname,
                    product_id = EXCLUDED.product_id,
                    user_id = EXCLUDED.user_id,
                    dept_id = EXCLUDED.dept_id,
                    channel_user_id = EXCLUDED.channel_user_id,
                    channel_dept_id = EXCLUDED.channel_dept_id,
                    recipient_name = EXCLUDED.recipient_name,
                    recipient_phone = EXCLUDED.recipient_phone,
                    recipient_address = EXCLUDED.recipient_address,
                    expected_sample_num = EXCLUDED.expected_sample_num,
                    actual_sample_num = EXCLUDED.actual_sample_num,
                    status = EXCLUDED.status,
                    sample_fee = EXCLUDED.sample_fee,
                    deleted = EXCLUDED.deleted,
                    update_time = CURRENT_TIMESTAMP,
                    update_by = EXCLUDED.update_by,
                    remark = EXCLUDED.remark
                """,
                sample.getId(),
                requestNo,
                talent.getId(),
                sample.getTalentUid(),
                sample.getTalentNickname(),
                product.getId(),
                userId,
                channelDeptId,
                channelUserId,
                channelDeptId,
                "演示收件人-待审核",
                "13800000007",
                "福州市仓山区待审核演示点",
                sample.getExpectedSampleNum(),
                sample.getActualSampleNum(),
                sample.getStatus(),
                0L,
                0,
                userId,
                userId,
                sample.getRemark()
        );
        return sampleRequestMapper.selectById(sample.getId());
    }

    private SampleRequest upsertShippingSample(
            Product product,
            Talent talent,
            UUID userId,
            UUID channelUserId,
            UUID channelDeptId) {
        String requestNo = "TEST-SAMPLE-SHIPPING-001";
        SampleRequest sample = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("limit 1"));
        if (sample == null) {
            sample = new SampleRequest();
            sample.setId(UUID.randomUUID());
            sample.setDeleted(0);
        }
        sample.setRequestNo(requestNo);
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talent.getDouyinUid());
        sample.setTalentNickname(talent.getNickname());
        sample.setTalentFansCount(talent.getFans());
        sample.setTalentCreditScore(BigDecimal.valueOf(8.10));
        sample.setTalentMainCategory("服饰穿搭");
        sample.setProductId(product.getId());
        sample.setUserId(userId);
        sample.setChannelUserId(channelUserId);
        sample.setExpectedSampleNum(1);
        sample.setActualSampleNum(1);
        sample.setTrackingNo("TEST-TRACK-SHIPPING-001");
        sample.setStatus(3);
        sample.setAuditTime(LocalDateTime.now().minusDays(1));
        sample.setShipTime(LocalDateTime.now().minusHours(6));
        sample.setRemark("审计样例：快递中寄样单");
        jdbcTemplate.update("""
                INSERT INTO sample_request (
                    id, request_no, talent_id, talent_uid, talent_nickname, product_id, user_id, dept_id,
                    channel_user_id, channel_dept_id, recipient_name, recipient_phone, recipient_address,
                    expected_sample_num, actual_sample_num, logistics_company, tracking_no, status,
                    sample_fee, audit_time, ship_time, deleted,
                    create_time, update_time, create_by, update_by, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?)
                ON CONFLICT (request_no) DO UPDATE SET
                    talent_id = EXCLUDED.talent_id,
                    talent_uid = EXCLUDED.talent_uid,
                    talent_nickname = EXCLUDED.talent_nickname,
                    product_id = EXCLUDED.product_id,
                    user_id = EXCLUDED.user_id,
                    dept_id = EXCLUDED.dept_id,
                    channel_user_id = EXCLUDED.channel_user_id,
                    channel_dept_id = EXCLUDED.channel_dept_id,
                    recipient_name = EXCLUDED.recipient_name,
                    recipient_phone = EXCLUDED.recipient_phone,
                    recipient_address = EXCLUDED.recipient_address,
                    expected_sample_num = EXCLUDED.expected_sample_num,
                    actual_sample_num = EXCLUDED.actual_sample_num,
                    logistics_company = EXCLUDED.logistics_company,
                    tracking_no = EXCLUDED.tracking_no,
                    status = EXCLUDED.status,
                    sample_fee = EXCLUDED.sample_fee,
                    audit_time = EXCLUDED.audit_time,
                    ship_time = EXCLUDED.ship_time,
                    deleted = EXCLUDED.deleted,
                    update_time = CURRENT_TIMESTAMP,
                    update_by = EXCLUDED.update_by,
                    remark = EXCLUDED.remark
                """,
                sample.getId(),
                requestNo,
                talent.getId(),
                sample.getTalentUid(),
                sample.getTalentNickname(),
                product.getId(),
                userId,
                channelDeptId,
                channelUserId,
                channelDeptId,
                "演示收件人-快递中",
                "13800000008",
                "杭州市拱墅区快递中演示点",
                sample.getExpectedSampleNum(),
                sample.getActualSampleNum(),
                "演示物流-中通模拟",
                sample.getTrackingNo(),
                sample.getStatus(),
                0L,
                sample.getAuditTime(),
                sample.getShipTime(),
                0,
                userId,
                userId,
                sample.getRemark()
        );
        return sampleRequestMapper.selectById(sample.getId());
    }

    private SampleRequest upsertFinishedSample(
            Product product,
            Talent talent,
            UUID userId,
            UUID channelUserId,
            UUID channelDeptId) {
        String requestNo = "TEST-SAMPLE-ORDER-001";
        SampleRequest sample = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("limit 1"));
        if (sample == null) {
            sample = new SampleRequest();
            sample.setId(UUID.randomUUID());
            sample.setDeleted(0);
        }
        sample.setRequestNo(requestNo);
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talent.getDouyinUid());
        sample.setTalentNickname(talent.getNickname());
        sample.setTalentFansCount(talent.getFans());
        sample.setTalentCreditScore(BigDecimal.valueOf(8.60));
        sample.setTalentMainCategory("数码家电");
        sample.setProductId(product.getId());
        sample.setUserId(userId);
        sample.setChannelUserId(channelUserId);
        sample.setExpectedSampleNum(1);
        sample.setActualSampleNum(1);
        sample.setTrackingNo("TEST-TRACK-ORDER-001");
        sample.setStatus(6);
        sample.setAuditTime(LocalDateTime.now().minusDays(8));
        sample.setShipTime(LocalDateTime.now().minusDays(7));
        sample.setDeliverTime(LocalDateTime.now().minusDays(6));
        sample.setCompleteTime(LocalDateTime.now().minusDays(2));
        sample.setRemark("test finished sample with order output");
        jdbcTemplate.update("""
                INSERT INTO sample_request (
                    id, request_no, talent_id, talent_uid, talent_nickname, product_id, user_id, dept_id,
                    channel_user_id, channel_dept_id, recipient_name, recipient_phone, recipient_address,
                    expected_sample_num, actual_sample_num, logistics_company, tracking_no, status,
                    sample_fee, audit_time, ship_time, deliver_time, complete_time, deleted,
                    create_time, update_time, create_by, update_by, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?)
                ON CONFLICT (request_no) DO UPDATE SET
                    talent_id = EXCLUDED.talent_id,
                    talent_uid = EXCLUDED.talent_uid,
                    talent_nickname = EXCLUDED.talent_nickname,
                    product_id = EXCLUDED.product_id,
                    user_id = EXCLUDED.user_id,
                    dept_id = EXCLUDED.dept_id,
                    channel_user_id = EXCLUDED.channel_user_id,
                    channel_dept_id = EXCLUDED.channel_dept_id,
                    recipient_name = EXCLUDED.recipient_name,
                    recipient_phone = EXCLUDED.recipient_phone,
                    recipient_address = EXCLUDED.recipient_address,
                    expected_sample_num = EXCLUDED.expected_sample_num,
                    actual_sample_num = EXCLUDED.actual_sample_num,
                    logistics_company = EXCLUDED.logistics_company,
                    tracking_no = EXCLUDED.tracking_no,
                    status = EXCLUDED.status,
                    sample_fee = EXCLUDED.sample_fee,
                    audit_time = EXCLUDED.audit_time,
                    ship_time = EXCLUDED.ship_time,
                    deliver_time = EXCLUDED.deliver_time,
                    complete_time = EXCLUDED.complete_time,
                    deleted = EXCLUDED.deleted,
                    update_time = CURRENT_TIMESTAMP,
                    update_by = EXCLUDED.update_by,
                    remark = EXCLUDED.remark
                """,
                sample.getId(),
                requestNo,
                talent.getId(),
                sample.getTalentUid(),
                sample.getTalentNickname(),
                product.getId(),
                userId,
                channelDeptId,
                channelUserId,
                channelDeptId,
                "演示收件人-达人转化",
                "13800000004",
                "南京市秦淮区转化演示点",
                sample.getExpectedSampleNum(),
                sample.getActualSampleNum(),
                "SF",
                sample.getTrackingNo(),
                sample.getStatus(),
                0L,
                sample.getAuditTime(),
                sample.getShipTime(),
                sample.getDeliverTime(),
                sample.getCompleteTime(),
                0,
                userId,
                userId,
                sample.getRemark()
        );
        return sampleRequestMapper.selectById(sample.getId());
    }

    private SampleRequest upsertRejectedSample(
            Product product,
            Talent talent,
            UUID userId,
            UUID channelUserId,
            UUID channelDeptId) {
        String requestNo = "TEST-SAMPLE-REJECT-001";
        SampleRequest sample = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("limit 1"));
        if (sample == null) {
            sample = new SampleRequest();
            sample.setId(UUID.randomUUID());
            sample.setDeleted(0);
        }
        sample.setRequestNo(requestNo);
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talent.getDouyinUid());
        sample.setTalentNickname(talent.getNickname());
        sample.setTalentFansCount(talent.getFans());
        sample.setTalentCreditScore(BigDecimal.valueOf(7.80));
        sample.setTalentMainCategory("零食饮品");
        sample.setProductId(product.getId());
        sample.setUserId(userId);
        sample.setChannelUserId(channelUserId);
        sample.setExpectedSampleNum(1);
        sample.setActualSampleNum(0);
        sample.setStatus(7);
        sample.setRejectReason("达人近期档期已满，暂不接受新寄样");
        sample.setAuditTime(LocalDateTime.now().minusDays(1));
        sample.setRemark("演示样例：招商已拒绝寄样申请");
        jdbcTemplate.update("""
                INSERT INTO sample_request (
                    id, request_no, talent_id, talent_uid, talent_nickname, product_id, user_id, dept_id,
                    channel_user_id, channel_dept_id, recipient_name, recipient_phone, recipient_address,
                    expected_sample_num, actual_sample_num, status, reject_reason, sample_fee, audit_time, deleted,
                    create_time, update_time, create_by, update_by, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?)
                ON CONFLICT (request_no) DO UPDATE SET
                    talent_id = EXCLUDED.talent_id,
                    talent_uid = EXCLUDED.talent_uid,
                    talent_nickname = EXCLUDED.talent_nickname,
                    product_id = EXCLUDED.product_id,
                    user_id = EXCLUDED.user_id,
                    dept_id = EXCLUDED.dept_id,
                    channel_user_id = EXCLUDED.channel_user_id,
                    channel_dept_id = EXCLUDED.channel_dept_id,
                    recipient_name = EXCLUDED.recipient_name,
                    recipient_phone = EXCLUDED.recipient_phone,
                    recipient_address = EXCLUDED.recipient_address,
                    expected_sample_num = EXCLUDED.expected_sample_num,
                    actual_sample_num = EXCLUDED.actual_sample_num,
                    status = EXCLUDED.status,
                    reject_reason = EXCLUDED.reject_reason,
                    sample_fee = EXCLUDED.sample_fee,
                    audit_time = EXCLUDED.audit_time,
                    deleted = EXCLUDED.deleted,
                    update_time = CURRENT_TIMESTAMP,
                    update_by = EXCLUDED.update_by,
                    remark = EXCLUDED.remark
                """,
                sample.getId(),
                requestNo,
                talent.getId(),
                sample.getTalentUid(),
                sample.getTalentNickname(),
                product.getId(),
                userId,
                channelDeptId,
                channelUserId,
                channelDeptId,
                "演示收件人-拒绝样例",
                "13800000005",
                "厦门市思明区寄样拒绝演示点",
                sample.getExpectedSampleNum(),
                sample.getActualSampleNum(),
                sample.getStatus(),
                sample.getRejectReason(),
                0L,
                sample.getAuditTime(),
                0,
                userId,
                userId,
                sample.getRemark()
        );
        return sampleRequestMapper.selectById(sample.getId());
    }

    private SampleRequest upsertClosedSample(
            Product product,
            Talent talent,
            UUID userId,
            UUID channelUserId,
            UUID channelDeptId) {
        String requestNo = "TEST-SAMPLE-CLOSED-001";
        SampleRequest sample = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("limit 1"));
        if (sample == null) {
            sample = new SampleRequest();
            sample.setId(UUID.randomUUID());
            sample.setDeleted(0);
        }
        sample.setRequestNo(requestNo);
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talent.getDouyinUid());
        sample.setTalentNickname(talent.getNickname());
        sample.setTalentFansCount(talent.getFans());
        sample.setTalentCreditScore(BigDecimal.valueOf(8.30));
        sample.setTalentMainCategory("个护家清");
        sample.setProductId(product.getId());
        sample.setUserId(userId);
        sample.setChannelUserId(channelUserId);
        sample.setExpectedSampleNum(1);
        sample.setActualSampleNum(1);
        sample.setTrackingNo("TEST-TRACK-CLOSED-001");
        sample.setStatus(8);
        sample.setAuditTime(LocalDateTime.now().minusDays(40));
        sample.setShipTime(LocalDateTime.now().minusDays(39));
        sample.setDeliverTime(LocalDateTime.now().minusDays(37));
        sample.setCloseTime(LocalDateTime.now().minusDays(5));
        sample.setCloseReason("样品签收后 30 天未出单，系统自动关闭");
        sample.setRemark("演示样例：待交作业超时自动关闭");
        jdbcTemplate.update("""
                INSERT INTO sample_request (
                    id, request_no, talent_id, talent_uid, talent_nickname, product_id, user_id, dept_id,
                    channel_user_id, channel_dept_id, recipient_name, recipient_phone, recipient_address,
                    expected_sample_num, actual_sample_num, logistics_company, tracking_no, status,
                    sample_fee, audit_time, ship_time, deliver_time, close_time, close_reason, deleted,
                    create_time, update_time, create_by, update_by, remark
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?)
                ON CONFLICT (request_no) DO UPDATE SET
                    talent_id = EXCLUDED.talent_id,
                    talent_uid = EXCLUDED.talent_uid,
                    talent_nickname = EXCLUDED.talent_nickname,
                    product_id = EXCLUDED.product_id,
                    user_id = EXCLUDED.user_id,
                    dept_id = EXCLUDED.dept_id,
                    channel_user_id = EXCLUDED.channel_user_id,
                    channel_dept_id = EXCLUDED.channel_dept_id,
                    recipient_name = EXCLUDED.recipient_name,
                    recipient_phone = EXCLUDED.recipient_phone,
                    recipient_address = EXCLUDED.recipient_address,
                    expected_sample_num = EXCLUDED.expected_sample_num,
                    actual_sample_num = EXCLUDED.actual_sample_num,
                    logistics_company = EXCLUDED.logistics_company,
                    tracking_no = EXCLUDED.tracking_no,
                    status = EXCLUDED.status,
                    sample_fee = EXCLUDED.sample_fee,
                    audit_time = EXCLUDED.audit_time,
                    ship_time = EXCLUDED.ship_time,
                    deliver_time = EXCLUDED.deliver_time,
                    close_time = EXCLUDED.close_time,
                    close_reason = EXCLUDED.close_reason,
                    deleted = EXCLUDED.deleted,
                    update_time = CURRENT_TIMESTAMP,
                    update_by = EXCLUDED.update_by,
                    remark = EXCLUDED.remark
                """,
                sample.getId(),
                requestNo,
                talent.getId(),
                sample.getTalentUid(),
                sample.getTalentNickname(),
                product.getId(),
                userId,
                channelDeptId,
                channelUserId,
                channelDeptId,
                "演示收件人-关闭样例",
                "13800000006",
                "郑州市高新区寄样关闭演示点",
                sample.getExpectedSampleNum(),
                sample.getActualSampleNum(),
                "演示物流-顺丰模拟",
                sample.getTrackingNo(),
                sample.getStatus(),
                0L,
                sample.getAuditTime(),
                sample.getShipTime(),
                sample.getDeliverTime(),
                sample.getCloseTime(),
                sample.getCloseReason(),
                0,
                userId,
                userId,
                sample.getRemark()
        );
        return sampleRequestMapper.selectById(sample.getId());
    }

    private void resetTalentClaims() {
        jdbcTemplate.update("DELETE FROM talent_claim");
    }

    private void upsertTalentClaim(UUID talentId, String talentUid, UUID userId, UUID deptId, LocalDateTime claimedAt) {
        upsertTalentClaim(talentId, talentUid, userId, deptId, claimedAt, claimedAt.plusDays(30), 1);
    }

    private void upsertExpiredTalentClaim(
            UUID talentId,
            String talentUid,
            UUID userId,
            UUID deptId,
            LocalDateTime claimedAt,
            LocalDateTime expiredAt) {
        upsertTalentClaim(talentId, talentUid, userId, deptId, claimedAt, expiredAt, 2);
    }

    private void upsertTalentClaim(
            UUID talentId,
            String talentUid,
            UUID userId,
            UUID deptId,
            LocalDateTime claimedAt,
            LocalDateTime protectedUntil,
            int status) {
        jdbcTemplate.update("""
                INSERT INTO talent_claim (
                    id, talent_id, talent_uid, user_id, dept_id, claim_type, status, apply_time, confirm_time, expire_time,
                    deleted, create_time, update_time, create_by, update_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)
                """,
                UUID.randomUUID(),
                talentId,
                talentUid,
                userId,
                deptId,
                1,
                status,
                claimedAt,
                claimedAt,
                protectedUntil,
                userId,
                userId
        );
    }

    private void seedAttributedOrder(
            String orderId,
            Product product,
            Talent talent,
            String pickSource,
            String merchantId,
            String shopName) {
        ColonelsettlementOrder order = buildTestOrder(
                orderId,
                product,
                product.getName(),
                25900L,
                3600L,
                pickSource,
                talent.getDouyinUid(),
                ACTIVITY_ID,
                merchantId,
                shopName
        );
        order.setTalentId(talent.getId());
        order.setTalentName(talent.getNickname());
        Map<String, Object> extra = new LinkedHashMap<>(order.getExtraData());
        extra.put("talentName", talent.getNickname());
        extra.put("mappingCreateTime", LocalDateTime.now().minusDays(7).toString());
        order.setExtraData(extra);
        AttributionService.AttributionResult attribution = attributionService.resolveAttribution(order, new LinkedHashMap<>(order.getExtraData()));
        applyAttribution(order, attribution);
        orderSyncPersistenceService.persistOrder(order);
    }

    private void seedMockAuditOrder(
            String orderId,
            Product product,
            Talent talent,
            String merchantId,
            String shopName,
            String pickSource,
            String attributionRemark,
            int orderStatus,
            long orderAmount,
            long serviceFee,
            LocalDateTime createTime,
            LocalDateTime settleTime,
            LocalDateTime mappingCreateTime) {
        ColonelsettlementOrder order = buildTestOrder(
                orderId,
                product,
                product.getName(),
                orderAmount,
                serviceFee,
                pickSource,
                talent.getDouyinUid(),
                ACTIVITY_ID,
                merchantId,
                shopName
        );
        order.setTalentId(talent.getId());
        order.setTalentName(talent.getNickname());
        order.setOrderStatus(orderStatus);
        order.setCreateTime(createTime);
        order.setUpdateTime(LocalDateTime.now());
        order.setSettleTime(settleTime);
        order.setAttributionStatus(AttributionService.STATUS_UNATTRIBUTED);
        order.setAttributionRemark(attributionRemark);
        Map<String, Object> extra = new LinkedHashMap<>(order.getExtraData());
        extra.put("talentName", talent.getNickname());
        extra.put("talentId", talent.getId().toString());
        extra.put("activityId", ACTIVITY_ID);
        extra.put("attributionRemark", attributionRemark);
        extra.put("mockScenario", orderId);
        if (mappingCreateTime != null) {
            extra.put("mappingCreateTime", mappingCreateTime.toString());
        }
        order.setExtraData(extra);
        orderSyncPersistenceService.persistOrder(order);
        jdbcTemplate.update(
                "UPDATE colonelsettlement_order SET create_time = ?, update_time = ?, settle_time = ? WHERE order_id = ?",
                createTime,
                LocalDateTime.now(),
                settleTime,
                orderId
        );
    }

    private UUID requireUserId(String username) {
        UUID userId = jdbcTemplate.query(
                "SELECT id FROM sys_user WHERE username = ? AND deleted = 0 LIMIT 1",
                rs -> rs.next() ? UUID.fromString(rs.getString(1)) : null,
                username
        );
        if (userId == null) {
            throw new IllegalStateException("test seed user not found: " + username);
        }
        return userId;
    }

    private UUID findDeptId(UUID userId) {
        return jdbcTemplate.query(
                "SELECT dept_id FROM sys_user WHERE id = ? AND deleted = 0 LIMIT 1",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    String raw = rs.getString(1);
                    return raw == null ? null : UUID.fromString(raw);
                },
                userId
        );
    }

    private void ensureDemoUserDeptIds() {
        assignDeptIdIfMissing(BIZ_USERNAME, BIZ_DEPT_ID);
        assignDeptIdIfMissing(BIZ_STAFF_USERNAME, BIZ_DEPT_ID);
        assignDeptIdIfMissing(CHANNEL_USERNAME, CHANNEL_DEPT_ID);
        assignDeptIdIfMissing(CHANNEL_STAFF_USERNAME, CHANNEL_DEPT_ID);
        assignDeptIdIfMissing(OPS_USERNAME, OPS_DEPT_ID);
    }

    private void assignDeptIdIfMissing(String username, UUID deptId) {
        jdbcTemplate.update("""
                UPDATE sys_user
                SET dept_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE username = ? AND deleted = 0 AND dept_id IS NULL
                """,
                deptId,
                username
        );
    }

    private Product requireProduct(String productId) {
        Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductId, productId)
                .last("limit 1"));
        if (product == null) {
            throw new IllegalStateException("test product not found: " + productId);
        }
        return product;
    }

    private PickSourceMapping requireMapping(String pickSource) {
        PickSourceMapping mapping = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getPickSource, pickSource)
                .eq(PickSourceMapping::getStatus, 1)
                .last("limit 1"));
        if (mapping == null) {
            throw new IllegalStateException("test pick_source_mapping not found: " + pickSource);
        }
        return mapping;
    }

    private Talent requireTalentByUid(String douyinUid) {
        Talent talent = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, douyinUid)
                .eq(Talent::getDeleted, 0)
                .last("limit 1"));
        if (talent == null) {
            throw new IllegalStateException("test talent not found: " + douyinUid);
        }
        return talent;
    }

    private SampleRequest requireSample(UUID sampleRequestId) {
        SampleRequest sample = sampleRequestMapper.selectById(sampleRequestId);
        if (sample == null) {
            throw new IllegalStateException("sample request not found: " + sampleRequestId);
        }
        return sample;
    }

    private SampleRequest findSampleByRequestNo(String requestNo) {
        return sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("limit 1"));
    }

    private void ensureSampleStatus(SampleRequest sample, int expectedStatus) {
        if (sample.getStatus() == null || sample.getStatus() != expectedStatus) {
            throw new IllegalStateException("sample request status mismatch, expected=" + expectedStatus + ", actual=" + sample.getStatus());
        }
    }

    private ColonelsettlementOrder buildTestOrder(
            String orderId,
            Product product,
            String productName,
            long orderAmount,
            long serviceFee,
            String pickSource,
            String talentUid,
            String activityId,
            String merchantId,
            String shopName) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId(orderId);
        order.setProductId(product.getProductId());
        order.setProductName(productName);
        order.setProductTitle(productName);
        order.setShopId(parseShopId(merchantId));
        order.setShopName(shopName);
        order.setOrderAmount(orderAmount);
        order.setActualAmount(orderAmount);
        order.setSettleColonelCommission(serviceFee);
        order.setSettleColonelTechServiceFee(0L);
        order.setSettleSecondColonelCommission(serviceFee);
        order.setOrderStatus(1);
        order.setPickSource(pickSource);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setSettleTime(LocalDateTime.now());
        order.setDeleted(0);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("product_name", productName);
        extra.put("merchant_id", merchantId);
        extra.put("colonel_activity_id", activityId);
        extra.put("talent_uid", talentUid);
        extra.put("author_id", talentUid);
        if (pickSource != null) {
            extra.put("pick_source", pickSource);
            extra.put("pick_extra", pickSource);
        }
        order.setExtraData(extra);
        return order;
    }

    private void applyAttribution(ColonelsettlementOrder order, AttributionService.AttributionResult attribution) {
        order.setChannelUserId(attribution.channelUserId());
        order.setChannelDeptId(attribution.deptId());
        order.setUserId(attribution.userId());
        order.setDeptId(attribution.deptId());
        order.setColonelUserId(attribution.colonelUserId());
        order.setTalentId(attribution.talentId());
        order.setActivityId(attribution.activityId());
        order.setAttributionStatus(attribution.attributionStatus());
        order.setAttributionRemark(attribution.attributionRemark());
        order.setTalentName(attribution.talentUid());
        if (order.getChannelUserId() != null) {
            String channelName = jdbcTemplate.query(
                    "SELECT real_name FROM sys_user WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null,
                    order.getChannelUserId()
            );
            order.setChannelUserName(channelName);
        }
        if (order.getColonelUserId() != null) {
            String colonelName = jdbcTemplate.query(
                    "SELECT real_name FROM sys_user WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null,
                    order.getColonelUserId()
            );
            order.setColonelUserName(colonelName);
        }
    }

    private Map<String, Object> orderResult(String scene, ColonelsettlementOrder order, boolean inserted, SampleRequest sample) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scene", scene);
        result.put("inserted", inserted);
        result.put("orderId", order.getOrderId());
        result.put("productId", order.getProductId());
        result.put("pickSource", order.getPickSource());
        result.put("attributionStatus", order.getAttributionStatus());
        result.put("attributionRemark", order.getAttributionRemark());
        result.put("channelUserId", order.getChannelUserId());
        result.put("talentUid", order.getTalentName());
        if (sample != null) {
            SampleRequest latest = sampleRequestMapper.selectById(sample.getId());
            result.put("sampleRequestId", latest == null ? null : latest.getId());
            result.put("sampleStatus", latest == null ? null : latest.getStatus());
        }
        return result;
    }

    private Map<String, Object> sampleResult(SampleRequest sample, String action, String trackingNo) {
        SampleRequest latest = sampleRequestMapper.selectById(sample.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("sampleRequestId", sample.getId());
        result.put("trackingNo", trackingNo);
        result.put("logisticsSource", latest == null || latest.getExtraData() == null
                ? null
                : latest.getExtraData().get("logisticsSource"));
        result.put("status", latest == null ? null : latest.getStatus());
        result.put("requestNo", latest == null ? null : latest.getRequestNo());
        return result;
    }

    private void upsertNativeMapping(
            UUID userId,
            UUID deptId,
            String talentId,
            String talentName,
            String productId,
            String activityId,
            String colonelBuyinId,
            String pickSource,
            LocalDateTime createTime) {
        String normalizedPickSource = pickSource.toUpperCase(Locale.ROOT);
        String shortId = normalizedPickSource.substring(0, Math.min(normalizedPickSource.length(), 10));
        pickSourceMappingService.saveOrUpdate(
                userId,
                null,
                deptId,
                talentId,
                talentName,
                shortId,
                UUID.nameUUIDFromBytes(normalizedPickSource.getBytes()),
                normalizedPickSource,
                productId,
                activityId,
                "https://test.source.local/native/" + activityId + "/" + productId,
                "https://test.promote.link/activity/" + activityId + "/product/" + productId + "?pick_source=" + normalizedPickSource,
                null,
                "PRODUCT_LIBRARY",
                normalizedPickSource,
                colonelBuyinId,
                PickSourceMappingService.SOURCE_TYPE_NATIVE
        );
        jdbcTemplate.update("""
                UPDATE pick_source_mapping
                SET create_time = ?, update_time = ?, valid_from = ?
                WHERE pick_source = ?
                  AND product_id = ?
                  AND activity_id = ?
                  AND user_id = ?
                  AND source_type = ?
                """,
                createTime,
                createTime,
                createTime,
                normalizedPickSource,
                productId,
                activityId,
                userId,
                PickSourceMappingService.SOURCE_TYPE_NATIVE
        );
    }

    private void updateOrderNativeColumns(String orderId, LocalDateTime createTime, LocalDateTime settleTime, String colonelBuyinId) {
        jdbcTemplate.update("""
                UPDATE colonelsettlement_order
                SET create_time = ?,
                    update_time = CURRENT_TIMESTAMP,
                    settle_time = ?,
                    colonel_buyin_id = CAST(? AS BIGINT)
                WHERE order_id = ?
                """,
                createTime,
                settleTime,
                colonelBuyinId,
                orderId
        );
    }

    private void putSampleExtraValue(SampleRequest sample, String key, Object value) {
        Map<String, Object> extra = sample.getExtraData() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sample.getExtraData());
        extra.put(key, value);
        sample.setExtraData(extra);
    }

    private void putSampleExtraValueIfMissing(SampleRequest sample, String key, Object value) {
        Map<String, Object> extra = sample.getExtraData() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sample.getExtraData());
        extra.putIfAbsent(key, value);
        sample.setExtraData(extra);
    }

    private Long parseShopId(String merchantId) {
        String digits = merchantId == null ? "" : merchantId.replaceAll("\\D", "");
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }

    private String formatPriceText(Long priceCent) {
        long safePriceCent = priceCent == null ? 0L : priceCent;
        return String.format(Locale.ROOT, "%.2f", safePriceCent / 100.0);
    }

    private void upsertCrawlerTalent(String talentId, String nickname, long fans, double score, String region, String category) {
        jdbcTemplate.update("""
                INSERT INTO crawler_talent_info (
                    talent_id, nickname, fans_count, credit_score, region, main_category,
                    avatar_url, last_crawl_time, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (talent_id) DO UPDATE SET
                    nickname = EXCLUDED.nickname,
                    fans_count = EXCLUDED.fans_count,
                    credit_score = EXCLUDED.credit_score,
                    region = EXCLUDED.region,
                    main_category = EXCLUDED.main_category,
                    avatar_url = EXCLUDED.avatar_url,
                    last_crawl_time = EXCLUDED.last_crawl_time,
                    updated_at = CURRENT_TIMESTAMP
                """,
                talentId, nickname, fans, BigDecimal.valueOf(score), region, category,
                "https://test.local/avatar/" + talentId + ".png"
        );
    }
}
