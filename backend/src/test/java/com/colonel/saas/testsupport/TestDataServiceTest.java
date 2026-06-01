package com.colonel.saas.testsupport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.colonel.saas.service.OrderSyncPersistenceService;
import com.colonel.saas.service.OrderSyncService;
import com.colonel.saas.service.PickSourceMappingService;
import com.colonel.saas.service.SampleStatusLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestDataServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID DEPT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SAMPLE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PRODUCT_ENTITY_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID TALENT_ENTITY_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Mock
    ProductMapper productMapper;
    @Mock
    ProductSnapshotMapper productSnapshotMapper;
    @Mock
    TalentMapper talentMapper;
    @Mock
    SampleRequestMapper sampleRequestMapper;
    @Mock
    PickSourceMappingMapper pickSourceMappingMapper;
    @Mock
    ColonelsettlementOrderMapper orderMapper;
    @Mock
    ProductOperationStateMapper productOperationStateMapper;
    @Mock
    PickSourceMappingService pickSourceMappingService;
    @Mock
    OrderSyncService orderSyncService;
    @Mock
    OrderSyncPersistenceService orderSyncPersistenceService;
    @Mock
    AttributionService attributionService;
    @Mock
    SampleStatusLogService sampleStatusLogService;
    @Mock
    LogisticsGateway logisticsGateway;
    @Mock
    JdbcTemplate jdbcTemplate;

    private TestDataService service;

    @BeforeEach
    void setUp() {
        service = service(false);
    }

    @Test
    void runEnsuresSchemaAndDemoDepartmentsWithoutSeedingWhenDisabled() {
        stubTestSupportTablesExist();

        service.run(mock(org.springframework.boot.ApplicationArguments.class));

        verify(jdbcTemplate, atLeast(17)).execute(anyString());
        verify(jdbcTemplate, atLeast(5)).update(anyString(), any(Object[].class));
    }

    @Test
    void runSkipsOptionalProductStateSchemaPatchWhenTableDoesNotExist() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    return !sql.contains("product_operation_state");
                });

        service.run(mock(org.springframework.boot.ApplicationArguments.class));

        verify(jdbcTemplate, never()).execute(ArgumentMatchers.contains("ALTER TABLE product_operation_state"));
        verify(jdbcTemplate).execute(ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS douyin_webhook_event"));
        verify(jdbcTemplate, atLeast(5)).update(anyString(), any(Object[].class));
    }

    @Test
    void runSkipsDemoDepartmentPatchWhenSysUserTableDoesNotExist() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    return !sql.contains("sys_user");
                });

        service.run(mock(org.springframework.boot.ApplicationArguments.class));

        verify(jdbcTemplate, never()).update(ArgumentMatchers.contains("UPDATE sys_user"), any(Object[].class));
        verify(jdbcTemplate).execute(ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS douyin_webhook_event"));
    }

    @Test
    void runSkipsSeedWhenSeedTablesDoNotExist() {
        service = service(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    return !sql.contains("sys_user");
                });

        service.run(mock(org.springframework.boot.ApplicationArguments.class));

        verify(jdbcTemplate, never()).query(
                ArgumentMatchers.contains("SELECT id FROM sys_user"),
                ArgumentMatchers.<ResultSetExtractor<Object>>any(),
                any(Object[].class)
        );
        verify(productMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void resetAllClearsDemoTablesAndReturnsZeroCounters() {
        Map<String, Object> result = service.resetAll();

        assertThat(result)
                .containsEntry("products", 0)
                .containsEntry("talents", 0)
                .containsEntry("orders", 0)
                .containsEntry("pickSourceMappings", 0)
                .containsEntry("sampleRequests", 0)
                .containsEntry("talentClaims", 0);
        verify(jdbcTemplate, atLeast(13)).update(anyString());
    }

    @Test
    void seedAllBuildsDemoProductsTalentsSamplesMappingsAndCanSyncOrders() {
        service = service(true);
        stubSeedDependencies();

        Map<String, Object> result = service.seedAll(true);

        assertThat(result)
                .containsEntry("pickSource", "TESTPS01")
                .containsKeys(
                        "products",
                        "talents",
                        "sampleRequestNo",
                        "orderSampleRequestNo",
                        "shippingSampleId",
                        "orderSync"
                );
        verify(productMapper, atLeast(4)).insert(any(Product.class));
        verify(talentMapper, atLeast(7)).insert(any(Talent.class));
        ArgumentCaptor<ProductSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ProductSnapshot.class);
        verify(productSnapshotMapper, atLeast(4)).upsert(snapshotCaptor.capture());
        List<ProductSnapshot> snapshots = snapshotCaptor.getAllValues();
        assertThat(snapshots)
                .filteredOn(snapshot -> "10901826".equals(snapshot.getProductId()))
                .singleElement()
                .satisfies(snapshot -> assertThat(snapshot.getStatus()).isEqualTo(1));
        verify(productOperationStateMapper, atLeast(4)).selectById(any(UUID.class));
        verify(pickSourceMappingService, atLeast(2)).saveOrUpdate(
                any(UUID.class),
                any(),
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyString()
        );
        verify(orderSyncService).syncByTimeRange(ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong());
    }

    @Test
    void generatedOrderScenariosPersistOrdersWithExpectedSceneMetadata() {
        stubOrderScenarioDependencies();

        assertThat(service.generateAttributedOrder())
                .containsEntry("scene", "attributed")
                .containsEntry("inserted", true)
                .containsEntry("pickSource", "TESTPS01")
                .containsEntry("sampleStatus", 5);
        assertThat(service.generateNoPickSourceOrder())
                .containsEntry("scene", "no-pick-source")
                .containsEntry("inserted", true)
                .containsEntry("pickSource", null);
        assertThat(service.generateMissingMappingOrder())
                .containsEntry("scene", "missing-mapping")
                .containsEntry("inserted", true);
        assertThat(service.generateAmbiguousMappingOrder())
                .containsEntry("scene", "ambiguous-mapping")
                .containsEntry("dashboardDiagnosis", "AMBIGUOUS_MAPPING")
                .containsEntry("colonelBuyinId", "900000000001");
        assertThat(service.generateHistoryUnsafeOrder())
                .containsEntry("scene", "history-unsafe")
                .containsEntry("dashboardDiagnosis", "MECHANISM_HIT_HISTORY_UNSAFE")
                .containsEntry("colonelBuyinId", "900000000002");
        assertThat(service.generateProductUncoveredOrder())
                .containsEntry("scene", "product-uncovered")
                .containsEntry("dashboardDiagnosis", "UPSTREAM_PRODUCT_UNCOVERED");

        verify(orderSyncPersistenceService, atLeast(6)).persistOrder(any());
        verify(pickSourceMappingService, atLeast(3)).saveOrUpdate(
                any(UUID.class),
                any(),
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void shipAndSignSampleAdvanceStatusAndReturnLogisticsMetadata() {
        Product product = product("10901826");
        SampleRequest sample = sample(SAMPLE_ID, "TEST-SAMPLE-SHIP-001", 2);
        when(sampleRequestMapper.selectById(SAMPLE_ID)).thenReturn(sample);
        when(productMapper.selectById(PRODUCT_ENTITY_ID)).thenReturn(product);
        when(logisticsGateway.createShipment(any(LogisticsGateway.LogisticsCommand.class)))
                .thenReturn(new LogisticsGateway.LogisticsResult(
                        "TRACK-1",
                        "MOCK",
                        "SHIPPING",
                        LocalDateTime.of(2026, 5, 1, 10, 0)
                ));

        Map<String, Object> shipped = service.shipSample(SAMPLE_ID);

        assertThat(shipped)
                .containsEntry("action", "shipping")
                .containsEntry("trackingNo", "TRACK-1")
                .containsEntry("status", 3)
                .containsEntry("logisticsSource", "MOCK");
        verify(sampleStatusLogService).log(SAMPLE_ID, 2, 3, USER_ID, "test logistics ship");

        when(logisticsGateway.queryTrack(any(), eq("TRACK-1")))
                .thenReturn(new LogisticsGateway.LogisticsTrackResult(
                        "MOCK",
                        "TRACK-1",
                        true,
                        null,
                        "3",
                        "SIGNED",
                        true,
                        LocalDateTime.of(2026, 5, 2, 10, 0),
                        List.of(),
                        Map.of()
                ));

        Map<String, Object> signed = service.signSample(SAMPLE_ID);

        assertThat(signed)
                .containsEntry("action", "signed")
                .containsEntry("trackingNo", "TRACK-1")
                .containsEntry("status", 5)
                .containsEntry("logisticsSource", "MOCK");
        verify(sampleStatusLogService).log(SAMPLE_ID, 3, 4, USER_ID, "test logistics delivered");
        verify(sampleStatusLogService).log(SAMPLE_ID, 4, 5, USER_ID, "test logistics sign -> pending homework");
    }

    @Test
    void syncTestOrdersDelegatesToOrderSyncService() {
        OrderSyncService.SyncResult syncResult = new OrderSyncService.SyncResult(1L, 2L, 1, 2, 3, true);
        when(orderSyncService.syncByTimeRange(ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong()))
                .thenReturn(syncResult);

        assertThat(service.syncTestOrders()).isSameAs(syncResult);
    }

    private TestDataService service(boolean seedOnStartup) {
        return new TestDataService(
                productMapper,
                productSnapshotMapper,
                talentMapper,
                sampleRequestMapper,
                pickSourceMappingMapper,
                orderMapper,
                productOperationStateMapper,
                pickSourceMappingService,
                orderSyncService,
                orderSyncPersistenceService,
                attributionService,
                sampleStatusLogService,
                logisticsGateway,
                jdbcTemplate,
                seedOnStartup
        );
    }

    private void stubSeedDependencies() {
        stubJdbcQueryResults();
        when(productMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(productSnapshotMapper.selectById(any(UUID.class))).thenReturn(null);
        when(productOperationStateMapper.selectById(ArgumentMatchers.nullable(UUID.class)))
                .thenReturn(new ProductOperationState());
        when(sampleRequestMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(sampleRequestMapper.selectById(any(UUID.class))).thenAnswer(invocation ->
                sample(invocation.getArgument(0), "TEST-SAMPLE-SELECTED", 5));
        when(orderSyncPersistenceService.persistOrder(any())).thenReturn(true);
        when(orderSyncService.syncByTimeRange(ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong()))
                .thenReturn(new OrderSyncService.SyncResult(1L, 2L, 1, 2, 3, true));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(AttributionService.AttributionResult.unattributed(
                        TALENT_ENTITY_ID,
                        "talent_test_a",
                        "TEST_ACTIVITY_A",
                        null,
                        AttributionService.REASON_MAPPING_NOT_FOUND
                ));
    }

    private void stubOrderScenarioDependencies() {
        Product product = product("10901825");
        Talent talent = talent("talent_test_b");
        PickSourceMapping mapping = mapping();
        SampleRequest sample = sample(SAMPLE_ID, "TEST-SAMPLE-001", 5);
        when(productMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(product);
        when(talentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(talent);
        when(pickSourceMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mapping);
        when(sampleRequestMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sample);
        when(sampleRequestMapper.selectById(any(UUID.class))).thenReturn(sample);
        stubJdbcQueryResults();
        when(orderSyncPersistenceService.persistOrder(any())).thenReturn(true);
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(AttributionService.AttributionResult.attributed(
                        USER_ID,
                        DEPT_ID,
                        USER_ID,
                        TALENT_ENTITY_ID,
                        "talent_test_b",
                        "TEST_ACTIVITY_A",
                        USER_ID,
                        AttributionService.REASON_ATTRIBUTED
                ));
    }

    private Product product(String productId) {
        Product product = new Product();
        product.setId(PRODUCT_ENTITY_ID);
        product.setProductId(productId);
        product.setName("Product " + productId);
        product.setPrice(12900L);
        product.setCreateTime(LocalDateTime.of(2026, 5, 1, 0, 0));
        return product;
    }

    private Talent talent(String uid) {
        Talent talent = new Talent();
        talent.setId(TALENT_ENTITY_ID);
        talent.setDouyinUid(uid);
        talent.setUid(uid);
        talent.setNickname("Talent " + uid);
        talent.setFans(100_000L);
        talent.setCreateTime(LocalDateTime.of(2026, 5, 1, 0, 0));
        return talent;
    }

    private PickSourceMapping mapping() {
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setPickSource("TESTPS01");
        mapping.setTalentId("talent_test_a");
        mapping.setTalentName("Talent A");
        mapping.setActivityId("TEST_ACTIVITY_A");
        mapping.setProductId("10901825");
        mapping.setStatus(1);
        return mapping;
    }

    private SampleRequest sample(UUID id, String requestNo, int status) {
        SampleRequest sample = new SampleRequest();
        sample.setId(id);
        sample.setRequestNo(requestNo);
        sample.setProductId(PRODUCT_ENTITY_ID);
        sample.setUserId(USER_ID);
        sample.setStatus(status);
        sample.setTrackingNo("TRACK-1");
        sample.setShipperCode("MOCK");
        sample.setExtraData(Map.of("logisticsSource", "MOCK"));
        return sample;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubJdbcQueryResults() {
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor>any(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    if (sql.contains("password")) {
                        return "encoded-password";
                    }
                    if (sql.contains("real_name")) {
                        return "Channel User";
                    }
                    if (sql.contains("dept_id")) {
                        return DEPT_ID;
                    }
                    if (sql.contains("RETURNING id")) {
                        return UUID.fromString("99999999-9999-9999-9999-999999999999");
                    }
                    return USER_ID;
                });
    }

    private void stubTestSupportTablesExist() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
    }
}
