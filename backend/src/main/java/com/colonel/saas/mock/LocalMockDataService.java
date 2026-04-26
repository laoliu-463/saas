package com.colonel.saas.mock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ColonelsettlementOrder;
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
import com.colonel.saas.service.OrderSyncService;
import com.colonel.saas.service.OrderSyncPersistenceService;
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
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "app.mock", name = "enabled", havingValue = "true")
public class LocalMockDataService implements ApplicationRunner {

    private static final String CHANNEL_USERNAME = "channel_leader";
    private static final String BIZ_USERNAME = "biz_leader";
    private static final String ACTIVITY_ID = "MOCK_ACTIVITY_A";
    private static final String PRODUCT_ID = "10901825";
    private static final String SECOND_PRODUCT_ID = "10901826";
    private static final String THIRD_PRODUCT_ID = "10901827";
    private static final String MAPPING_PICK_SOURCE = "MOCKPS01";
    private static final String MAPPING_SHORT_ID = "MOCKPS01";
    private static final String TALENT_UID_A = "talent_mock_a";
    private static final String TALENT_UID_B = "talent_mock_b";
    private static final String TALENT_UID_C = "talent_mock_c";
    private static final String TALENT_UID_D = "talent_mock_d";

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

    public LocalMockDataService(
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
            @Value("${app.mock.seed-on-startup:false}") boolean seedOnStartup) {
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
        if (seedOnStartup) {
            seedAll(false);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> seedAll(boolean syncOrders) {
        UUID channelUserId = requireUserId(CHANNEL_USERNAME);
        UUID channelDeptId = findDeptId(channelUserId);
        UUID bizUserId = requireUserId(BIZ_USERNAME);

        Product mainProduct = upsertProduct(PRODUCT_ID, "Mock归因订单商品", 9900L, 1, 1);
        Product secondProduct = upsertProduct(SECOND_PRODUCT_ID, "Mock未归因订单商品", 5900L, 1, 1);
        Product thirdProduct = upsertProduct(THIRD_PRODUCT_ID, "Mock无归因码订单商品", 12900L, 1, 1);

        Talent talentA = upsertTalent(TALENT_UID_A, "Mock达人A", 186_000L, "四川成都", "符合寄样条件");
        Talent talentB = upsertTalent(TALENT_UID_B, "Mock达人B", 96_000L, "浙江杭州", "不符合寄样条件");
        Talent talentC = upsertTalent(TALENT_UID_C, "Mock达人C", 320_000L, "广东深圳", "已认领达人");
        Talent talentD = upsertTalent(TALENT_UID_D, "Mock达人D", 42_000L, "江苏南京", "公海达人");

        upsertProductSnapshot(mainProduct, 1, "ON_SHELF", 32560L, "189", "https://mock.local/product/" + mainProduct.getProductId());
        upsertProductSnapshot(secondProduct, 0, "PENDING_AUDIT", 8420L, "64", "https://mock.local/product/" + secondProduct.getProductId());
        upsertProductSnapshot(thirdProduct, 2, "OFFLINE", 1280L, "12", "https://mock.local/product/" + thirdProduct.getProductId());

        upsertOperationState(mainProduct.getProductId(), bizUserId, "ASSIGNED", 2, "local-mock assigned");
        upsertOperationState(secondProduct.getProductId(), null, "PENDING_AUDIT", 1, "local-mock pending audit");
        upsertOperationState(thirdProduct.getProductId(), null, "REJECTED", 3, "local-mock rejected");

        pickSourceMappingService.saveOrUpdate(
                channelUserId,
                "渠道组长测试",
                channelDeptId,
                TALENT_UID_A,
                talentA.getNickname(),
                MAPPING_SHORT_ID,
                UUID.nameUUIDFromBytes(MAPPING_PICK_SOURCE.getBytes()),
                MAPPING_PICK_SOURCE,
                mainProduct.getProductId(),
                ACTIVITY_ID,
                "https://mock.source.local/product/" + mainProduct.getProductId(),
                "https://mock.promote.link/activity/" + ACTIVITY_ID + "/product/" + mainProduct.getProductId() + "?pick_source=" + MAPPING_PICK_SOURCE,
                null,
                "PRODUCT_LIBRARY"
        );

        SampleRequest sampleRequest = upsertPendingHomeworkSample(
                mainProduct,
                talentA,
                bizUserId,
                channelUserId,
                channelDeptId
        );
        SampleRequest shippingSample = upsertPendingShipSample(
                secondProduct,
                talentB,
                bizUserId,
                channelUserId,
                channelDeptId
        );

        // Seed Crawler Talent Info for Sample Apply candidates
        upsertCrawlerTalent("talent_mock_a", "Mock达人A", 186_000L, 4.9, "四川成都", "美妆个护");
        upsertCrawlerTalent("talent_mock_b", "Mock达人B", 96_000L, 4.2, "浙江杭州", "服饰穿搭");
        upsertCrawlerTalent("talent_mock_c", "Mock达人C", 320_000L, 4.8, "广东深圳", "母婴用品");
        upsertCrawlerTalent("talent_mock_d", "Mock达人D", 42_000L, 4.5, "江苏南京", "数码家电");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("products", List.of(mainProduct.getProductId(), secondProduct.getProductId(), thirdProduct.getProductId()));
        result.put("talents", List.of(talentA.getDouyinUid(), talentB.getDouyinUid(), talentC.getDouyinUid(), talentD.getDouyinUid()));
        result.put("pickSource", MAPPING_PICK_SOURCE);
        result.put("sampleRequestNo", sampleRequest.getRequestNo());
        result.put("shippingSampleRequestNo", shippingSample.getRequestNo());

        if (syncOrders) {
            long now = System.currentTimeMillis() / 1000;
            result.put("orderSync", orderSyncService.syncByTimeRange(now - 7200, now + 60));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resetAll() {
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
        return result;
    }

    public OrderSyncService.SyncResult syncMockOrders() {
        long now = System.currentTimeMillis() / 1000;
        return orderSyncService.syncByTimeRange(now - 7200, now + 60);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateAttributedOrder() {
        Product product = requireProduct(PRODUCT_ID);
        PickSourceMapping mapping = requireMapping(MAPPING_PICK_SOURCE);
        ColonelsettlementOrder order = buildMockOrder(
                "MOCK_GEN_ATTR_" + System.currentTimeMillis(),
                product,
                "Mock归因订单商品",
                19900L,
                2600L,
                mapping.getPickSource(),
                mapping.getTalentId(),
                mapping.getActivityId(),
                "M_ATTR",
                "Mock归因商家"
        );
        Map<String, Object> source = new LinkedHashMap<>(order.getExtraData());
        AttributionService.AttributionResult attribution = attributionService.resolveAttribution(order, source);
        applyAttribution(order, attribution);
        boolean inserted = orderSyncPersistenceService.persistOrder(order);
        SampleRequest sample = findSampleByRequestNo("MOCK-SAMPLE-001");
        return orderResult("attributed", order, inserted, sample);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateNoPickSourceOrder() {
        Product product = requireProduct(THIRD_PRODUCT_ID);
        ColonelsettlementOrder order = buildMockOrder(
                "MOCK_GEN_NOPICK_" + System.currentTimeMillis(),
                product,
                "Mock无归因码订单商品",
                12900L,
                2000L,
                null,
                TALENT_UID_C,
                ACTIVITY_ID,
                "M_NOPICK",
                "Mock无归因码商家"
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
        ColonelsettlementOrder order = buildMockOrder(
                "MOCK_GEN_NOMAP_" + System.currentTimeMillis(),
                product,
                "Mock映射缺失订单商品",
                9900L,
                1200L,
                "UPS" + (System.currentTimeMillis() % 10_000),
                TALENT_UID_B,
                ACTIVITY_ID,
                "M_NOMAP",
                "Mock映射缺失商家"
        );
        Map<String, Object> source = new LinkedHashMap<>(order.getExtraData());
        AttributionService.AttributionResult attribution = attributionService.resolveAttribution(order, source);
        applyAttribution(order, attribution);
        boolean inserted = orderSyncPersistenceService.persistOrder(order);
        return orderResult("missing-mapping", order, inserted, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> shipSample(UUID sampleRequestId) {
        SampleRequest sample = requireSample(sampleRequestId);
        ensureSampleStatus(sample, 2);
        Product product = productMapper.selectById(sample.getProductId());
        LogisticsGateway.LogisticsResult shipment = logisticsGateway.createShipment(new LogisticsGateway.LogisticsCommand(
                sample.getId(),
                product == null ? null : product.getProductId(),
                "Mock收件人",
                "13800000000",
                "Mock地址-成都市高新区全链路演示仓"
        ));
        int fromStatus = sample.getStatus();
        sample.setStatus(3);
        sample.setTrackingNo(shipment.trackingNo());
        sample.setShipTime(shipment.shipTime());
        sampleRequestMapper.updateById(sample);
        sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), sample.getUserId(), "mock logistics ship");
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
        sampleRequestMapper.updateById(sample);
        sampleStatusLogService.log(sample.getId(), fromStatus, 4, sample.getUserId(), "mock logistics delivered");
        sampleStatusLogService.log(sample.getId(), 4, sample.getStatus(), sample.getUserId(), "mock logistics sign -> pending homework");
        return sampleResult(sample, logisticsStatus.status().toLowerCase(Locale.ROOT), logisticsStatus.trackingNo());
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
        product.setCategory("全链路Mock");
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
        talent.setDouyinNo("douyin_" + douyinUid);
        talent.setNickname(nickname);
        talent.setFans(fans);
        talent.setLevel(fans > 100_000 ? "A" : "B");
        talent.setLikesCount(fans * 8);
        talent.setFollowingCount(321L);
        talent.setWorksCount(87L);
        talent.setIpLocation(ipLocation);
        talent.setAvatarUrl("https://mock.local/avatar/" + douyinUid + ".png");
        talent.setStatus(1);
        talent.setCrawlStatus(1);
        talent.setCrawlMessage("mock data ready: " + sourceTag);
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
        UUID id = jdbcTemplate.query(
                """
                INSERT INTO product_operation_state (
                    id, activity_id, product_id, assignee_id, biz_status,
                    audit_status, audit_remark, last_operation_at, deleted,
                    create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (activity_id, product_id) DO UPDATE SET
                    assignee_id = EXCLUDED.assignee_id,
                    biz_status = EXCLUDED.biz_status,
                    audit_status = EXCLUDED.audit_status,
                    audit_remark = EXCLUDED.audit_remark,
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
        snapshot.setCover("https://mock.local/product/" + product.getProductId() + ".png");
        snapshot.setPrice(product.getPrice());
        snapshot.setPriceText(formatPriceText(product.getPrice()));
        snapshot.setShopId(1000L + Long.parseLong(product.getProductId()));
        snapshot.setShopName("MockShop-" + product.getProductId());
        snapshot.setStatus(status);
        snapshot.setStatusText(statusText);
        snapshot.setCategoryName("LOCAL_MOCK");
        snapshot.setProductStock(stock);
        snapshot.setSales(sales);
        snapshot.setDetailUrl(detailUrl);
        snapshot.setPromotionStartTime(LocalDateTime.now().minusDays(7).toString());
        snapshot.setPromotionEndTime(LocalDateTime.now().plusDays(7).toString());
        snapshot.setActivityCosRatio(2500L);
        snapshot.setActivityCosRatioText("25%");
        snapshot.setCosType(1);
        snapshot.setCosTypeText("RATIO");
        snapshot.setAdServiceRatio("8%");
        snapshot.setActivityAdCosRatio(800L);
        snapshot.setHasDouinGoodsTag(Boolean.TRUE);
        snapshot.setRawPayload("{\"mock\":true,\"activityId\":\"" + ACTIVITY_ID + "\",\"productId\":\"" + product.getProductId() + "\"}");
        snapshot.setSyncTime(LocalDateTime.now());
        if (snapshot.getCreateTime() == null) {
            productSnapshotMapper.insert(snapshot);
        } else {
            productSnapshotMapper.updateById(snapshot);
        }
        return snapshot;
    }

    private SampleRequest upsertPendingHomeworkSample(
            Product product,
            Talent talent,
            UUID userId,
            UUID channelUserId,
            UUID channelDeptId) {
        String requestNo = "MOCK-SAMPLE-001";
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
        sample.setTrackingNo("MOCK-TRACK-001");
        sample.setStatus(5);
        sample.setAuditTime(LocalDateTime.now().minusDays(3));
        sample.setShipTime(LocalDateTime.now().minusDays(2));
        sample.setDeliverTime(LocalDateTime.now().minusDays(1));
        sample.setRemark("local-mock auto seeded");
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
                    "Mock收件人",
                    "13800000000",
                    "Mock地址-成都市高新区全链路演示仓",
                    sample.getExpectedSampleNum(),
                    sample.getActualSampleNum(),
                    "MockExpress",
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
        String requestNo = "MOCK-SAMPLE-SHIP-001";
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
        sample.setRemark("local-mock pending ship");
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
                "Mock收件人",
                "13800000001",
                "Mock地址-杭州市余杭区物流演示仓",
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

    private UUID requireUserId(String username) {
        UUID userId = jdbcTemplate.query(
                "SELECT id FROM sys_user WHERE username = ? AND deleted = 0 LIMIT 1",
                rs -> rs.next() ? UUID.fromString(rs.getString(1)) : null,
                username
        );
        if (userId == null) {
            throw new IllegalStateException("mock seed user not found: " + username);
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

    private Product requireProduct(String productId) {
        Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductId, productId)
                .last("limit 1"));
        if (product == null) {
            throw new IllegalStateException("mock product not found: " + productId);
        }
        return product;
    }

    private PickSourceMapping requireMapping(String pickSource) {
        PickSourceMapping mapping = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getPickSource, pickSource)
                .eq(PickSourceMapping::getStatus, 1)
                .last("limit 1"));
        if (mapping == null) {
            throw new IllegalStateException("mock pick_source_mapping not found: " + pickSource);
        }
        return mapping;
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

    private ColonelsettlementOrder buildMockOrder(
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
        result.put("status", latest == null ? null : latest.getStatus());
        result.put("requestNo", latest == null ? null : latest.getRequestNo());
        return result;
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
                    avatar_url, crawl_status, last_crawl_time, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (talent_id) DO UPDATE SET
                    nickname = EXCLUDED.nickname,
                    fans_count = EXCLUDED.fans_count,
                    credit_score = EXCLUDED.credit_score,
                    region = EXCLUDED.region,
                    main_category = EXCLUDED.main_category,
                    avatar_url = EXCLUDED.avatar_url,
                    crawl_status = EXCLUDED.crawl_status,
                    last_crawl_time = EXCLUDED.last_crawl_time,
                    updated_at = CURRENT_TIMESTAMP
                """,
                talentId, nickname, fans, BigDecimal.valueOf(score), region, category,
                "https://mock.local/avatar/" + talentId + ".png"
        );
    }
}
