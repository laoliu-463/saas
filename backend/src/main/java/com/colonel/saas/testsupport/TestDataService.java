package com.colonel.saas.testsupport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.constant.ProductDisplayStatus;
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
import com.colonel.saas.domain.order.infrastructure.OrderSyncPersistenceService;
import com.colonel.saas.domain.order.application.OrderSyncService;
import com.colonel.saas.service.PickSourceMappingService;
import com.colonel.saas.service.SampleStatusLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
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

/**
 * 测试数据播种服务，负责在测试环境中准备完整的演示数据集。
 * <p>
 * 该服务仅在配置 {@code app.test.enabled=true} 时激活（{@link ConditionalOnProperty}），
 * 实现 {@link ApplicationRunner} 接口，应用启动后自动执行数据初始化。
 * </p>
 * <p>
 * 核心职责：
 * <ul>
 *   <li>确保测试所需数据库表结构和索引存在（{@link #ensureSchema}）</li>
 *   <li>播种商品（Product）、达人（Talent）、寄样申请（SampleRequest）、
 *       选品映射（PickSourceMapping）、订单（ColonelsettlementOrder）等全链路测试数据</li>
 *   <li>支持重置全部测试数据（{@link #resetAll}）和动态生成各场景订单（{@code generate*Order}）</li>
 *   <li>模拟寄样发货和签收流程（{@link #shipSample}、{@link #signSample}）</li>
 * </ul>
 * </p>
 * <p>
 * 覆盖的测试场景包括：正常归因出单、推广映射缺失、未带推广参数、多候选歧义映射、
 * 历史不可回填、活动商品未覆盖、达人认领/过期释放、寄样全生命周期（待发货/运输中/待审核/已完成/已拒绝/已关闭）等。
 * </p>
 *
 * @see com.colonel.saas.entity.Product 商品实体
 * @see com.colonel.saas.entity.Talent 达人实体
 * @see com.colonel.saas.entity.SampleRequest 寄样申请实体
 * @see com.colonel.saas.entity.ColonelsettlementOrder 订单实体
 * @see com.colonel.saas.service.AttributionService 归因服务
 */
@Service
@Profile("test")
@ConditionalOnProperty(prefix = "app.test", name = "enabled", havingValue = "true")
public class TestDataService implements ApplicationRunner {

    /** 渠道负责人用户名，用于测试数据中的渠道归属角色 */
    private static final String CHANNEL_USERNAME = "channel_leader";
    /** 渠道员工用户名，用于测试数据中的渠道从属角色 */
    private static final String CHANNEL_STAFF_USERNAME = "channel_staff";
    /** 商务负责人用户名，用于测试数据中的商务归属角色 */
    private static final String BIZ_USERNAME = "biz_leader";
    /** 商务员工用户名 */
    private static final String BIZ_STAFF_USERNAME = "biz_staff";
    /** 运营员工用户名 */
    private static final String OPS_USERNAME = "ops_staff";
    /** 商务部门固定 UUID，用于测试数据中的部门归属 */
    private static final UUID BIZ_DEPT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    /** 渠道部门固定 UUID */
    private static final UUID CHANNEL_DEPT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    /** 运营部门固定 UUID */
    private static final UUID OPS_DEPT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    /** 测试活动 ID，所有测试数据共享的活动标识 */
    private static final String ACTIVITY_ID = "TEST_ACTIVITY_A";
    /** 主演示商品 ID（已转链可出单） */
    private static final String PRODUCT_ID = "10901825";
    /** 第二商品 ID（推广映射缺失场景） */
    private static final String SECOND_PRODUCT_ID = "10901826";
    /** 第三商品 ID（未带推广参数场景） */
    private static final String THIRD_PRODUCT_ID = "10901827";
    /** 第四商品 ID（审核通过待分配场景） */
    private static final String FOURTH_PRODUCT_ID = "10901828";
    /** 主链路测试 pick_source 编码，对应主演示商品和达人 A/D 的映射 */
    private static final String MAPPING_PICK_SOURCE = "TESTPS01";
    /** 主链路测试 pick_source 短 ID */
    private static final String MAPPING_SHORT_ID = "TESTPS01";
    /** 达人 A 的抖音 UID（寄样待交作业场景） */
    private static final String TALENT_UID_A = "talent_test_a";
    /** 达人 B 的抖音 UID（映射缺失订单场景） */
    private static final String TALENT_UID_B = "talent_test_b";
    /** 达人 C 的抖音 UID（他人已认领场景） */
    private static final String TALENT_UID_C = "talent_test_c";
    /** 达人 D 的抖音 UID（已有订单产出场景） */
    private static final String TALENT_UID_D = "talent_test_d";
    /** 达人 E 的抖音 UID（保护期到期回公海场景） */
    private static final String TALENT_UID_E = "talent_test_e";
    /** 达人 F 的抖音 UID（寄样已拒绝/待审核场景） */
    private static final String TALENT_UID_F = "talent_test_f";
    /** 达人 G 的抖音 UID（寄样已关闭场景） */
    private static final String TALENT_UID_G = "talent_test_g";
    /** 达人 D 专用 pick_source 编码，用于出单演示 */
    private static final String MAPPING_PICK_SOURCE_D = "TESTPS04";
    /** 达人 D 专用 pick_source 短 ID */
    private static final String MAPPING_SHORT_ID_D = "TESTPS04";
    /** 多候选歧义映射场景使用的原生百应 ID */
    private static final String AMBIGUOUS_NATIVE_BUYIN_ID = "900000000001";
    /** 历史不可回填场景使用的原生百应 ID */
    private static final String HISTORY_UNSAFE_BUYIN_ID = "900000000002";
    private static final List<String> SEED_REQUIRED_TABLES = List.of(
            "sys_user",
            "product",
            "product_snapshot",
            "talent",
            "sample_request",
            "pick_source_mapping",
            "colonelsettlement_order",
            "product_operation_state",
            "talent_claim",
            "crawler_talent_info"
    );

    /** 商品 Mapper，用于插入/更新测试商品数据 */
    private final ProductMapper productMapper;
    /** 商品快照 Mapper，用于维护商品历史快照记录 */
    private final ProductSnapshotMapper productSnapshotMapper;
    /** 达人 Mapper，用于插入/更新测试达人数据 */
    private final TalentMapper talentMapper;
    /** 寄样申请 Mapper，用于插入/更新寄样测试数据 */
    private final SampleRequestMapper sampleRequestMapper;
    /** 推广映射 Mapper，用于管理 pick_source 与商品/达人的映射关系 */
    private final PickSourceMappingMapper pickSourceMappingMapper;
    /** 团长结算订单 Mapper，用于插入/更新测试订单数据 */
    private final ColonelsettlementOrderMapper orderMapper;
    /** 商品操作状态 Mapper，用于维护商品审核/上架等运营状态 */
    private final ProductOperationStateMapper productOperationStateMapper;
    /** 推广映射服务，用于查找商品映射关系（归因链路核心依赖） */
    private final PickSourceMappingService pickSourceMappingService;
    /** 订单同步服务，用于从外部抖音 API 拉取订单 */
    private final OrderSyncService orderSyncService;
    /** 订单同步持久化服务，负责将同步结果写入数据库 */
    private final OrderSyncPersistenceService orderSyncPersistenceService;
    /** 归因服务，负责订单与达人/商品的归属计算 */
    private final AttributionService attributionService;
    /** 寄样状态日志服务，用于记录寄样生命周期变更日志 */
    private final SampleStatusLogService sampleStatusLogService;
    /** 物流网关，用于查询和更新物流轨迹信息 */
    private final LogisticsGateway logisticsGateway;
    /** Spring JdbcTemplate，用于执行原生 SQL（如 DDL 变更） */
    private final JdbcTemplate jdbcTemplate;
    /** 是否在应用启动时自动播种测试数据，由配置 {@code app.test.seed-on-startup} 控制 */
    private final boolean seedOnStartup;

    /**
     * 构造函数，由 Spring 自动注入所有依赖。
     *
     * @param productMapper             商品 Mapper
     * @param productSnapshotMapper     商品快照 Mapper
     * @param talentMapper              达人 Mapper
     * @param sampleRequestMapper       寄样申请 Mapper
     * @param pickSourceMappingMapper   推广映射 Mapper
     * @param orderMapper               团长结算订单 Mapper
     * @param productOperationStateMapper 商品操作状态 Mapper
     * @param pickSourceMappingService  推广映射服务
     * @param orderSyncService          订单同步服务
     * @param orderSyncPersistenceService 订单同步持久化服务
     * @param attributionService        归因服务
     * @param sampleStatusLogService    寄样状态日志服务
     * @param logisticsGateway          物流网关
     * @param jdbcTemplate              JdbcTemplate（原生 SQL 执行）
     * @param seedOnStartup             是否在启动时自动播种，由 {@code app.test.seed-on-startup} 配置
     */
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

    /**
     * 应用启动时自动执行，实现 {@link ApplicationRunner} 接口。
     * <p>
     * 执行流程：
     * 1. 确保数据库表结构和索引存在（{@link #ensureSchema}）
     * 2. 确保演示用户的部门 ID 已正确分配
     * 3. 若 {@link #seedOnStartup} 为 true，则自动播种全部测试数据
     * </p>
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        ensureSchema();
        ensureDemoUserDeptIds();
        if (seedOnStartup && seedRequiredTablesExist()) {
            seedAll(false);
        }
    }

    /**
     * 确保测试所需的数据库表结构和索引存在。
     * <p>
     * 通过原生 SQL 语句动态添加缺失的列（如 {@code product_operation_state.biz_status}、
     * {@code product_operation_log.before_status} 等）和索引，避免测试环境因表结构不同步而失败。
     * 所有 DDL 均使用 {@code IF NOT EXISTS} 保护，可安全重复执行。
     * </p>
     */
    private void ensureSchema() {
        if (tableExists("product_operation_state")) {
            addMissingColumn("product_operation_state", "biz_status", "VARCHAR(64)");
            addMissingColumn("product_operation_state", "selected_to_library", "BOOLEAN DEFAULT FALSE");
            addMissingColumn("product_operation_state", "selected_at", "TIMESTAMP");
            addMissingColumn("product_operation_state", "selected_by", "UUID");
        }
        if (tableExists("product_operation_log")) {
            addMissingColumn("product_operation_log", "before_status", "VARCHAR(64)");
            addMissingColumn("product_operation_log", "after_status", "VARCHAR(64)");
            addMissingColumn("product_operation_log", "success", "BOOLEAN DEFAULT TRUE");
            addMissingColumn("product_operation_log", "error_message", "TEXT");
        }
        if (tableExists("pick_source_mapping")) {
            addMissingColumn("pick_source_mapping", "colonel_buyin_id", "VARCHAR(32)");
            jdbcTemplate.execute("DROP INDEX IF EXISTS uk_psm_pick_source");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_psm_pick_source ON pick_source_mapping(pick_source)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_psm_colonel_buyin_id ON pick_source_mapping(colonel_buyin_id)");
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uk_psm_pick_source_product_activity_user
                    ON pick_source_mapping(pick_source, product_id, activity_id, user_id)
                    WHERE deleted = 0
                    """);
        }
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

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public." + tableName + "') IS NOT NULL",
                Boolean.class
        ));
    }

    private boolean seedRequiredTablesExist() {
        for (String tableName : SEED_REQUIRED_TABLES) {
            if (!tableExists(tableName)) {
                return false;
            }
        }
        return true;
    }

    private void addMissingColumn(String tableName, String columnName, String columnDefinition) {
        jdbcTemplate.execute("""
                DO $$ BEGIN
                  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema = 'public' AND table_name = '%s' AND column_name = '%s') THEN
                    ALTER TABLE %s ADD COLUMN %s %s;
                  END IF;
                END $$""".formatted(tableName, columnName, tableName, columnName, columnDefinition));
    }

    /**
     * 播种全部测试数据，覆盖商品、达人、推广映射、寄样、达人认领、订单等全链路场景。
     * <p>
     * 核心播种内容：
     * <ul>
     *   <li>4 个测试商品（已转链、映射缺失、未带推广参数、审核通过待分配）</li>
     *   <li>7 个测试达人（各覆盖不同业务场景）</li>
     *   <li>商品快照和运营状态</li>
     *   <li>2 条推广映射（主链路 + 达人 D 专用）</li>
     *   <li>7 个寄样申请（待交作业/待发货/运输中/待审核/已完成/已拒绝/已关闭）</li>
     *   <li>达人认领记录（含已过期释放场景）</li>
     *   <li>模拟订单（含已归因、商品未覆盖、退款关闭等场景）</li>
     * </ul>
     * </p>
     *
     * @param syncOrders 是否在播种完成后同步外部订单，true 时调用 {@link OrderSyncService#syncByTimeRange} 拉取近 2 小时订单
     * @return 包含各测试数据标识的汇总 Map（products、talents、pickSource、sampleRequestNo 等）
     */
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
        upsertProductSnapshot(secondProduct, 1, "PENDING_AUDIT", 8420L, "64", "https://test.local/product/" + secondProduct.getProductId());
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

        resetFixedMockOrderDedupClaims();
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

    /**
     * 重置全部测试数据，删除所有测试相关的数据库记录。
     * <p>
     * 按依赖关系逆序清除以下表的数据：达人认领、寄样状态日志、寄样申请、订单、订单去重认领、
     * 推广映射、推广链接、商品操作日志、商品操作状态、商品快照、达人、商品、爬虫达人信息。
     * </p>
     *
     * @return 包含各维度清零计数的汇总 Map（products=0、talents=0、orders=0 等）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resetAll() {
        jdbcTemplate.update("DELETE FROM talent_claim");
        jdbcTemplate.update("DELETE FROM sample_status_log");
        jdbcTemplate.update("DELETE FROM sample_request");
        jdbcTemplate.update("DELETE FROM colonelsettlement_order");
        jdbcTemplate.update("DELETE FROM order_sync_dedup_claim");
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

    /**
     * 同步测试订单，从外部抖音 API 拉取最近 2 小时内的订单数据。
     *
     * @return 同步结果，包含成功/失败数量等统计信息
     */
    public OrderSyncService.SyncResult syncTestOrders() {
        long now = System.currentTimeMillis() / 1000;
        return orderSyncService.syncByTimeRange(now - 7200, now + 60);
    }

    /**
     * 生成一条可成功归因的测试订单。
     * <p>
     * 使用主演示商品（{@link #PRODUCT_ID}）和主推广映射（{@link #MAPPING_PICK_SOURCE}），
     * 经过归因服务解析后持久化，并关联寄样申请 TEST-SAMPLE-001。
     * </p>
     *
     * @return 包含订单状态和寄样关联信息的汇总 Map
     */
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

    /**
     * 生成一条未带推广参数的测试订单（归因失败场景）。
     * <p>
     * 使用第三商品（{@link #THIRD_PRODUCT_ID}），不设置 pick_source，
     * 模拟订单缺少推广来源导致归因失败的排查场景。
     * </p>
     *
     * @return 包含订单状态的汇总 Map，归因结果为失败
     */
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

    /**
     * 生成一条推广映射缺失的测试订单（归因失败场景）。
     * <p>
     * 使用第二商品（{@link #SECOND_PRODUCT_ID}），设置一个不存在的 pick_source，
     * 模拟订单的推广来源在系统中找不到对应映射的排查场景。
     * </p>
     *
     * @return 包含订单状态的汇总 Map，归因结果为映射缺失
     */
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

    /**
     * 生成一条多候选歧义映射的测试订单（归因冲突场景）。
     * <p>
     * 为主演示商品创建两条相同 {@link #AMBIGUOUS_NATIVE_BUYIN_ID} 的原生映射，
     * 分属渠道负责人和渠道员工，模拟同一订单存在多个归属候选的排查场景。
     * </p>
     *
     * @return 包含订单状态、诊断标识（{@code AMBIGUOUS_MAPPING}）和候选归属用户的汇总 Map
     */
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

    /**
     * 生成一条历史不可回填的测试订单（归因失败场景）。
     * <p>
     * 创建一条映射时间晚于订单创建时间的原生映射（{@link #HISTORY_UNSAFE_BUYIN_ID}），
     * 模拟订单产生时映射尚不存在、导致归因结果为"历史不可回填"的排查场景。
     * </p>
     *
     * @return 包含订单状态、诊断标识（{@code MECHANISM_HIT_HISTORY_UNSAFE}）和映射创建时间的汇总 Map
     */
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

    /**
     * 生成一条活动商品未覆盖的测试订单（归因失败场景）。
     * <p>
     * 将订单的商品 ID 设置为一个不存在的活动商品 ID，模拟上游商品未在活动覆盖范围内
     * 导致归因结果为"活动商品未覆盖"的排查场景。
     * </p>
     *
     * @return 包含订单状态、诊断标识（{@code UPSTREAM_PRODUCT_UNCOVERED}）和未覆盖商品 ID 的汇总 Map
     */
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

    /**
     * 模拟寄样发货，将待发货状态的寄样申请推进到运输中状态。
     * <p>
     * 调用物流网关创建发货单，获取运单号，并更新寄样状态为 3（运输中），
     * 同时记录物流来源为 MOCK，写入状态变更日志。
     * </p>
     *
     * @param sampleRequestId 待发货的寄样申请 ID
     * @return 包含寄样状态和运单号的汇总 Map
     */
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

    /**
     * 模拟寄样签收，将运输中的寄样申请推进到待交作业状态。
     * <p>
     * 查询物流轨迹确认已送达，然后将寄样状态从 3（运输中）经 4（已送达）推进到
     * 5（待交作业/待审核），同时写入两条状态变更日志。
     * </p>
     *
     * @param sampleRequestId 运输中的寄样申请 ID
     * @return 包含寄样状态和运单号的汇总 Map
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> signSample(UUID sampleRequestId) {
        SampleRequest sample = requireSample(sampleRequestId);
        ensureSampleStatus(sample, 3);
        LogisticsGateway.LogisticsTrackResult logisticsResult = logisticsGateway.queryTrack(sample.getShipperCode(), sample.getTrackingNo());
        int fromStatus = sample.getStatus();
        sample.setStatus(5);
        sample.setDeliverTime(LocalDateTime.now());
        putSampleExtraValueIfMissing(sample, "logisticsSource", "MOCK");
        sampleRequestMapper.updateById(sample);
        sampleStatusLogService.log(sample.getId(), fromStatus, 4, sample.getUserId(), "test logistics delivered");
        sampleStatusLogService.log(sample.getId(), 4, sample.getStatus(), sample.getUserId(), "test logistics sign -> pending homework");
        return sampleResult(sample, logisticsResult.internalStatus().toLowerCase(Locale.ROOT), logisticsResult.trackingNo());
    }

    /**
     * 确保测试用的模拟审计活动记录存在。
     * <p>
     * 向 {@code colonel_activity} 表插入或更新测试活动数据，供商品审核和订单归因场景使用。
     * 使用 {@code ON CONFLICT} 保证可安全重复执行。
     * </p>
     */
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

    /**
     * 确保测试用的已停用审计样本用户存在。
     * <p>
     * 创建一个状态为停用（status=0）的演示用户 {@code disabled_audit_user}，
     * 用于覆盖停用账号相关的审计场景。使用 {@code ON CONFLICT} 保证可安全重复执行。
     * </p>
     */
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

    /**
     * 插入或更新测试商品记录。
     * <p>
     * 若商品已存在（根据 {@code productId} 查找）则更新字段，否则创建新记录。
     * </p>
     *
     * @param productId    商品 ID（抖店商品标识）
     * @param name         商品名称
     * @param price        商品价格（单位：分）
     * @param status       商品状态（1=上架，0=下架）
     * @param checkStatus  审核状态（1=待审核，2=已通过，3=已拒绝）
     * @return 创建或更新后的商品实体
     */
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

    /**
     * 插入或更新测试达人记录。
     * <p>
     * 若达人已存在（根据 {@code douyinUid} 查找）则更新字段，否则创建新记录。
     * 根据粉丝数自动分配达人等级（>10 万=A 级，否则=B 级）。
     * </p>
     *
     * @param douyinUid  达人抖音 UID
     * @param nickname   达人昵称
     * @param fans       粉丝数
     * @param ipLocation IP 属地（如"四川成都"）
     * @param sourceTag  数据来源标签，写入爬虫消息字段
     * @return 创建或更新后的达人实体
     */
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

    /**
     * 插入或更新商品运营状态记录。
     * <p>
     * 使用 {@code ON CONFLICT} 保证按 (activity_id, product_id) 唯一键可安全重复执行。
     * 若审核状态为已通过（auditStatus=2），自动设置入选状态和展示状态。
     * </p>
     *
     * @param productId    商品 ID
     * @param assigneeId   负责人用户 ID，null 表示未分配
     * @param bizStatus    业务状态标识（如 ASSIGNED、PENDING_AUDIT、REJECTED）
     * @param auditStatus  审核状态（1=待审核，2=已通过，3=已拒绝）
     * @param auditRemark  审核备注
     * @return 创建或更新后的商品运营状态实体
     */
    private ProductOperationState upsertOperationState(
            String productId,
            UUID assigneeId,
            String bizStatus,
            int auditStatus,
            String auditRemark) {
        boolean selectedToLibrary = auditStatus == 2;
        LocalDateTime selectedAt = selectedToLibrary ? LocalDateTime.now() : null;
        String displayStatus = selectedToLibrary
                ? ProductDisplayStatus.DISPLAYING.name()
                : ProductDisplayStatus.PENDING.name();
        UUID id = jdbcTemplate.query(
                """
                INSERT INTO product_operation_state (
                    id, activity_id, product_id, assignee_id, biz_status,
                    audit_status, audit_remark, selected_to_library, selected_at, selected_by,
                    display_status, last_operation_at, deleted,
                    create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (activity_id, product_id) DO UPDATE SET
                    assignee_id = EXCLUDED.assignee_id,
                    biz_status = EXCLUDED.biz_status,
                    audit_status = EXCLUDED.audit_status,
                    audit_remark = EXCLUDED.audit_remark,
                    selected_to_library = EXCLUDED.selected_to_library,
                    selected_at = EXCLUDED.selected_at,
                    selected_by = EXCLUDED.selected_by,
                    display_status = EXCLUDED.display_status,
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
                displayStatus,
                LocalDateTime.now()
        );
        return productOperationStateMapper.selectById(id);
    }

    /**
     * 插入或更新商品快照记录。
     * <p>
     * 基于活动 ID 和商品 ID 生成确定性快照 ID，填充商品详情、价格、佣金比例等测试数据。
     * 使用 {@link ProductSnapshotMapper#upsert} 保证可安全重复执行。
     * </p>
     *
     * @param product    商品实体
     * @param status     快照状态码
     * @param statusText 快照状态文本（如 ON_SHELF、PENDING_AUDIT）
     * @param sales      销量
     * @param stock      库存数量（字符串）
     * @param detailUrl  商品详情页链接
     * @return 创建或更新后的商品快照实体
     */
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

    /**
     * 插入或更新一条"待交作业"状态的寄样申请。
     * <p>
     * 固定使用申请编号 {@code TEST-SAMPLE-001}，代表达人已完成签收、等待提交作业的场景。
     * </p>
     *
     * @param product       关联商品实体
     * @param talent        关联达人实体
     * @param userId        操作人用户 ID
     * @param channelUserId 渠道负责人用户 ID
     * @param channelDeptId 渠道部门 ID
     * @return 创建或更新后的寄样申请实体
     */
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

    /**
     * 插入或更新一条"待发货"状态的寄样申请。
     * <p>
     * 固定使用申请编号 {@code TEST-SAMPLE-SHIP-001}，代表寄样申请已审核通过、等待发货的场景。
     * </p>
     *
     * @param product       关联商品实体
     * @param talent        关联达人实体
     * @param userId        操作人用户 ID
     * @param channelUserId 渠道负责人用户 ID
     * @param channelDeptId 渠道部门 ID
     * @return 创建或更新后的寄样申请实体
     */
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

    /**
     * 插入或更新一条"待审核"状态的寄样申请。
     * <p>
     * 固定使用申请编号 {@code TEST-SAMPLE-PENDING-REVIEW-001}，代表达人已提交作业、等待审核的场景。
     * </p>
     *
     * @param product       关联商品实体
     * @param talent        关联达人实体
     * @param userId        操作人用户 ID
     * @param channelUserId 渠道负责人用户 ID
     * @param channelDeptId 渠道部门 ID
     * @return 创建或更新后的寄样申请实体
     */
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

    /**
     * 插入或更新一条"运输中"状态的寄样申请。
     * <p>
     * 固定使用申请编号 {@code TEST-SAMPLE-SHIPPING-001}，代表寄样已发货、正在运输途中的场景。
     * </p>
     *
     * @param product       关联商品实体
     * @param talent        关联达人实体
     * @param userId        操作人用户 ID
     * @param channelUserId 渠道负责人用户 ID
     * @param channelDeptId 渠道部门 ID
     * @return 创建或更新后的寄样申请实体
     */
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

    /**
     * 插入或更新一条"已完成"状态的寄样申请。
     * <p>
     * 固定使用申请编号 {@code TEST-SAMPLE-ORDER-001}，代表达人已完成作业并产出订单的场景。
     * 状态码为 6（已完成），包含完整的发货/送达/完成时间线。
     * </p>
     *
     * @param product       关联商品实体
     * @param talent        关联达人实体
     * @param userId        操作人用户 ID
     * @param channelUserId 渠道负责人用户 ID
     * @param channelDeptId 渠道部门 ID
     * @return 创建或更新后的寄样申请实体
     */
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

    /**
     * 插入或更新一条"已拒绝"状态的寄样申请。
     * <p>
     * 固定使用申请编号 {@code TEST-SAMPLE-REJECT-001}，代表寄样申请被招商拒绝的场景。
     * 状态码为 7（已拒绝），包含拒绝理由。
     * </p>
     *
     * @param product       关联商品实体
     * @param talent        关联达人实体
     * @param userId        操作人用户 ID
     * @param channelUserId 渠道负责人用户 ID
     * @param channelDeptId 渠道部门 ID
     * @return 创建或更新后的寄样申请实体
     */
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

    /**
     * 插入或更新一条"已关闭"状态的寄样申请。
     * <p>
     * 固定使用申请编号 {@code TEST-SAMPLE-CLOSED-001}，代表达人签收后 30 天未出单、系统自动关闭的场景。
     * 状态码为 8（已关闭），包含关闭原因和完整的生命周期时间线。
     * </p>
     *
     * @param product       关联商品实体
     * @param talent        关联达人实体
     * @param userId        操作人用户 ID
     * @param channelUserId 渠道负责人用户 ID
     * @param channelDeptId 渠道部门 ID
     * @return 创建或更新后的寄样申请实体
     */
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

    /**
     * 清空所有达人认领记录，为重新播种做准备。
     */
    private void resetTalentClaims() {
        jdbcTemplate.update("DELETE FROM talent_claim");
    }

    /**
     * 清空固定模拟订单的去重认领记录，为重新播种做准备。
     */
    private void resetFixedMockOrderDedupClaims() {
        jdbcTemplate.update("""
                DELETE FROM order_sync_dedup_claim
                WHERE order_id IN (
                    'MOCK_SEED_TALENT_D_ORDER',
                    'MOCK_AUDIT_PRODUCT_UNCOVERED',
                    'MOCK_AUDIT_REFUNDED_CLOSED'
                )
                """);
    }

    /**
     * 插入一条有效的达人认领记录（状态=1，保护期默认30天）。
     *
     * @param talentId   达人主键 ID
     * @param talentUid  达人抖音 UID
     * @param userId     认领人（操作用户）ID
     * @param deptId     认领人所属部门 ID
     * @param claimedAt  认领时间
     */
    private void upsertTalentClaim(UUID talentId, String talentUid, UUID userId, UUID deptId, LocalDateTime claimedAt) {
        upsertTalentClaim(talentId, talentUid, userId, deptId, claimedAt, claimedAt.plusDays(30), 1);
    }

    /**
     * 插入一条已过期的达人认领记录（状态=2）。
     *
     * @param talentId   达人主键 ID
     * @param talentUid  达人抖音 UID
     * @param userId     认领人（操作用户）ID
     * @param deptId     认领人所属部门 ID
     * @param claimedAt  认领时间
     * @param expiredAt  过期时间
     */
    private void upsertExpiredTalentClaim(
            UUID talentId,
            String talentUid,
            UUID userId,
            UUID deptId,
            LocalDateTime claimedAt,
            LocalDateTime expiredAt) {
        upsertTalentClaim(talentId, talentUid, userId, deptId, claimedAt, expiredAt, 2);
    }

    /**
     * 向 talent_claim 表插入一条认领记录的底层实现。
     * 使用 JDBC 直接执行 INSERT，不做冲突处理（测试数据保证幂等清理后才调用）。
     *
     * @param talentId        达人主键 ID
     * @param talentUid       达人抖音 UID
     * @param userId          认领人（操作用户）ID
     * @param deptId          认领人所属部门 ID
     * @param claimedAt       认领时间
     * @param protectedUntil  保护期截止时间
     * @param status          认领状态：1=有效，2=已过期
     */
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

    /**
     * 构建一条已归属测试订单并持久化。
     * 流程：构建订单实体 -> 设置达人信息 -> 调用归属服务解析 -> 写入 extraData -> 持久化。
     *
     * @param orderId     抖音订单号
     * @param product     商品实体
     * @param talent      达人实体
     * @param pickSource  精选联盟转链标识（pick_source）
     * @param merchantId  商家 ID
     * @param shopName    店铺名称
     */
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

    /**
     * 构建一条模拟审核场景的测试订单并持久化。
     * 与 {@link #seedAttributedOrder} 不同，此方法不调用归属服务，
     * 而是直接设置 attributionRemark 等字段，并通过原生 SQL 回写 create_time / settle_time。
     *
     * @param orderId             抖音订单号
     * @param product             商品实体
     * @param talent              达人实体
     * @param merchantId          商家 ID
     * @param shopName            店铺名称
     * @param pickSource          精选联盟转链标识
     * @param attributionRemark   归属备注（用于模拟各种归属失败场景）
     * @param orderStatus         订单状态
     * @param orderAmount         订单金额（分）
     * @param serviceFee          服务费（分）
     * @param createTime          订单创建时间（用于模拟历史订单）
     * @param settleTime          结算时间
     * @param mappingCreateTime   转链创建时间（映射到 extraData.mappingCreateTime）
     */
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

    /**
     * 根据用户名查询用户 ID，未找到时抛出异常。
     *
     * @param username 系统用户名
     * @return 用户主键 ID
     * @throws IllegalStateException 当用户名不存在时
     */
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

    /**
     * 根据用户 ID 查询其所属部门 ID。
     *
     * @param userId 用户主键 ID
     * @return 部门 ID，用户不存在或未分配部门时返回 null
     */
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

    /**
     * 为所有演示用户补填部门 ID。
     * 仅在用户 dept_id 为 null 时才更新，避免覆盖已有配置。
     */
    private void ensureDemoUserDeptIds() {
        if (!tableExists("sys_user")) {
            return;
        }
        assignDeptIdIfMissing(BIZ_USERNAME, BIZ_DEPT_ID);
        assignDeptIdIfMissing(BIZ_STAFF_USERNAME, BIZ_DEPT_ID);
        assignDeptIdIfMissing(CHANNEL_USERNAME, CHANNEL_DEPT_ID);
        assignDeptIdIfMissing(CHANNEL_STAFF_USERNAME, CHANNEL_DEPT_ID);
        assignDeptIdIfMissing(OPS_USERNAME, OPS_DEPT_ID);
    }

    /**
     * 若用户尚未分配部门，则补填指定的部门 ID。
     *
     * @param username  系统用户名
     * @param deptId    需要分配的部门 ID
     */
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

    /**
     * 根据商品 ID 查询商品实体，未找到时抛出异常。
     *
     * @param productId 商品 ID（抖音侧）
     * @return 商品实体
     * @throws IllegalStateException 当商品不存在时
     */
    private Product requireProduct(String productId) {
        Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductId, productId)
                .last("limit 1"));
        if (product == null) {
            throw new IllegalStateException("test product not found: " + productId);
        }
        return product;
    }

    /**
     * 根据 pick_source 查询有效的转链映射记录，未找到时抛出异常。
     *
     * @param pickSource 精选联盟转链标识
     * @return 转链映射实体
     * @throws IllegalStateException 当映射记录不存在时
     */
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

    /**
     * 根据抖音 UID 查询达人实体，未找到时抛出异常。
     *
     * @param douyinUid 达人抖音 UID
     * @return 达人实体
     * @throws IllegalStateException 当达人不存在时
     */
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

    /**
     * 根据寄样申请 ID 查询寄样记录，未找到时抛出异常。
     *
     * @param sampleRequestId 寄样申请主键 ID
     * @return 寄样申请实体
     * @throws IllegalStateException 当寄样记录不存在时
     */
    private SampleRequest requireSample(UUID sampleRequestId) {
        SampleRequest sample = sampleRequestMapper.selectById(sampleRequestId);
        if (sample == null) {
            throw new IllegalStateException("sample request not found: " + sampleRequestId);
        }
        return sample;
    }

    /**
     * 根据寄样申请编号查询寄样记录。
     *
     * @param requestNo 寄样申请编号
     * @return 寄样申请实体，不存在时返回 null
     */
    private SampleRequest findSampleByRequestNo(String requestNo) {
        return sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("limit 1"));
    }

    /**
     * 校验寄样申请当前状态是否与预期一致，不一致时抛出异常。
     *
     * @param sample         寄样申请实体
     * @param expectedStatus 期望的状态值
     * @throws IllegalStateException 当实际状态与期望状态不匹配时
     */
    private void ensureSampleStatus(SampleRequest sample, int expectedStatus) {
        if (sample.getStatus() == null || sample.getStatus() != expectedStatus) {
            throw new IllegalStateException("sample request status mismatch, expected=" + expectedStatus + ", actual=" + sample.getStatus());
        }
    }

    /**
     * 构建一条测试用的订单实体（ColonelsettlementOrder）。
     * 设置订单基础字段、金额字段，并填充 extraData 扩展数据。
     *
     * @param orderId      抖音订单号
     * @param product      商品实体（用于获取商品 ID 等信息）
     * @param productName  商品名称（可与 product.getName() 不同，用于特殊场景）
     * @param orderAmount  订单金额（单位：分）
     * @param serviceFee   服务费（单位：分）
     * @param pickSource   精选联盟转链标识
     * @param talentUid    达人抖音 UID
     * @param activityId   团长活动 ID
     * @param merchantId   商家 ID
     * @param shopName     店铺名称
     * @return 构建好的订单实体
     */
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
        order.setSettleAmount(orderAmount);
        order.setEstimateServiceFee(serviceFee);
        order.setEffectiveServiceFee(serviceFee);
        order.setEstimateTechServiceFee(0L);
        order.setEffectiveTechServiceFee(0L);
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

    /**
     * 将归属服务的计算结果写入订单实体。
     * 包括归属用户、部门、达人、活动、归属状态等字段，
     * 并从 sys_user 补填渠道用户和团长用户的真实姓名。
     *
     * @param order       订单实体（将被就地修改）
     * @param attribution 归属服务计算结果
     */
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

    /**
     * 构建订单种子结果的摘要 Map，用于日志输出或返回给调用方。
     * 包含场景标识、是否插入、订单核心字段，以及可选的关联寄样信息。
     *
     * @param scene    场景标识（如 SYNC / ATTRIBUTED / NO_PICK_SOURCE 等）
     * @param order    订单实体
     * @param inserted 是否为新插入（true）或更新（false）
     * @param sample   关联的寄样申请（可为 null）
     * @return 包含订单关键字段的摘要 Map
     */
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

    /**
     * 构建寄样操作结果的摘要 Map，用于日志输出或返回给调用方。
     * 会重新查询最新寄样状态以反映操作后的实际状态。
     *
     * @param sample     寄样申请实体
     * @param action     操作动作标识（如 ship / sign 等）
     * @param trackingNo 快递单号（可为 null）
     * @return 包含寄样关键字段的摘要 Map
     */
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

    /**
     * 插入或更新一条原生（非爬虫来源）的转链映射记录。
     * 调用 pickSourceMappingService 完成核心写入后，再通过原生 SQL 回写 create_time / valid_from。
     *
     * @param userId         操作用户 ID
     * @param deptId         部门 ID
     * @param talentId       达人 ID
     * @param talentName     达人昵称
     * @param productId      商品 ID
     * @param activityId     活动 ID
     * @param colonelBuyinId 团长百应 ID
     * @param pickSource     精选联盟转链标识
     * @param createTime     创建时间（用于模拟历史数据）
     */
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

    /**
     * 通过原生 SQL 更新订单的 create_time、settle_time 和 colonel_buyin_id。
     * 用于在持久化后覆写时间字段，模拟历史订单场景。
     *
     * @param orderId        抖音订单号
     * @param createTime     要设置的创建时间
     * @param settleTime     要设置的结算时间
     * @param colonelBuyinId 团长百应 ID
     */
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

    /**
     * 向寄样申请的 extraData 扩展数据中写入键值对（覆盖写）。
     * 若 extraData 为空则自动创建。
     *
     * @param sample 寄样申请实体（将被就地修改）
     * @param key    扩展数据键名
     * @param value  扩展数据值
     */
    private void putSampleExtraValue(SampleRequest sample, String key, Object value) {
        Map<String, Object> extra = sample.getExtraData() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sample.getExtraData());
        extra.put(key, value);
        sample.setExtraData(extra);
    }

    /**
     * 向寄样申请的 extraData 扩展数据中写入键值对（仅在键不存在时写入）。
     * 若 extraData 为空则自动创建。
     *
     * @param sample 寄样申请实体（将被就地修改）
     * @param key    扩展数据键名
     * @param value  扩展数据值
     */
    private void putSampleExtraValueIfMissing(SampleRequest sample, String key, Object value) {
        Map<String, Object> extra = sample.getExtraData() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sample.getExtraData());
        extra.putIfAbsent(key, value);
        sample.setExtraData(extra);
    }

    /**
     * 从商家 ID 字符串中提取纯数字部分并转换为 Long。
     * 用于将 merchant_id 字符串转为 shop_id 数字字段。
     *
     * @param merchantId 商家 ID 字符串（可能包含非数字字符）
     * @return 纯数字部分转换后的 Long 值，无数字时返回 0
     */
    private Long parseShopId(String merchantId) {
        String digits = merchantId == null ? "" : merchantId.replaceAll("\\D", "");
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }

    /**
     * 将分单位的金额格式化为元单位的文本（如 25900 -> "259.00"）。
     *
     * @param priceCent 金额（单位：分），可为 null
     * @return 格式化后的元金额文本
     */
    private String formatPriceText(Long priceCent) {
        long safePriceCent = priceCent == null ? 0L : priceCent;
        return String.format(Locale.ROOT, "%.2f", safePriceCent / 100.0);
    }

    /**
     * 向 crawler_talent_info 表插入或更新一条爬虫来源的达人信息。
     * 使用 PostgreSQL 的 ON CONFLICT ... DO UPDATE 实现幂等写入。
     *
     * @param talentId  达人 ID
     * @param nickname  达人昵称
     * @param fans      粉丝数
     * @param score     信用评分
     * @param region    所在地区
     * @param category  主营类目
     */
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
