package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.dto.sample.SampleFilterOptionsDTO;
import com.colonel.saas.dto.sample.SampleFilterOptionItem;
import com.colonel.saas.dto.SampleTalentQueryRequest;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.SampleStatusLog;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.SampleStatusLogMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.controller.SampleFilterOptionsController;
import com.colonel.saas.service.SampleFilterOptionsService;
import com.colonel.saas.service.SampleLogisticsImportService;
import com.colonel.saas.service.SampleLogisticsSyncService;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.SampleEligibilityService;
import com.colonel.saas.service.SampleStatusLogService;
import com.colonel.saas.vo.SampleTalentVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.mock.web.MockHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleControllerTest {

    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductOperationStateMapper productOperationStateMapper;
    @Mock
    private ProductSnapshotMapper productSnapshotMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private TalentMapper talentMapper;
    @Mock
    private TalentClaimMapper talentClaimMapper;
    @Mock
    private SampleStatusLogService sampleStatusLogService;
    @Mock
    private SampleStatusLogMapper sampleStatusLogMapper;
    @Mock
    private CrawlerTalentInfoService crawlerTalentInfoService;
    @Mock
    private BusinessRuleConfigService businessRuleConfigService;
    @Mock
    private ProductService productService;
    @Mock
    private SampleEligibilityService sampleEligibilityService;
    @Mock
    private SampleLogisticsSyncService sampleLogisticsSyncService;
    @Mock
    private SampleLogisticsImportService sampleLogisticsImportService;
    @Mock
    private SampleDomainEventPublisher sampleDomainEventPublisher;

    private SampleController sampleController;

    @BeforeEach
    void setUp() {
        sampleController = new SampleController(
                sampleRequestMapper,
                productMapper,
                productOperationStateMapper,
                productSnapshotMapper,
                sysUserMapper,
                talentMapper,
                talentClaimMapper,
                sampleStatusLogService,
                sampleStatusLogMapper,
                crawlerTalentInfoService,
                businessRuleConfigService,
                productService,
                sampleEligibilityService,
                sampleLogisticsSyncService,
                sampleLogisticsImportService,
                sampleDomainEventPublisher
        );
        lenient().when(businessRuleConfigService.isSampleRestrictEnabled()).thenReturn(true);
        lenient().when(businessRuleConfigService.getSampleRestrictDays()).thenReturn(7);
        lenient().when(sampleEligibilityService.evaluate(any(), any()))
                .thenReturn(new SampleEligibilityService.EligibilityResult(
                        true,
                        java.util.List.of(),
                        new SampleEligibilityService.SampleDefaultStandard(30000L, "LV1", java.util.Map.of()),
                        new SampleEligibilityService.TalentSnapshot(50000L, "LV2")
                ));
        lenient().when(talentClaimMapper.findActiveByTalentAndUser(any(), any()))
                .thenReturn(mock(TalentClaim.class));
        lenient().when(sampleRequestMapper.updateById(any(SampleRequest.class))).thenReturn(1);
    }

    @Test
    void createSample_shouldRejectWhenDuplicateWithinSevenDays() {
        UUID talentUuid = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_001");
        crawlerTalentInfo.setNickname("test talent");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_001");
        talent.setNickname("test talent");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_001");
        request.setProductId(productId);
        request.setQuantity(1);

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_001")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(sampleRequestMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> sampleController.createSample(
                request,
                UUID.randomUUID(),
                List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("7 days");

        verify(sampleRequestMapper, never()).insert(any(SampleRequest.class));
    }

    @Test
    void createSample_shouldPersistTalentSnapshotFromCrawler() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_002");
        crawlerTalentInfo.setNickname("crawler talent");
        crawlerTalentInfo.setFansCount(120000L);
        crawlerTalentInfo.setCreditScore(new BigDecimal("4.80"));
        crawlerTalentInfo.setMainCategory("food");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_002");
        talent.setNickname("crawler talent");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_002");
        request.setProductId(productId);
        request.setQuantity(2);

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_002")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentUuid, userId)).thenReturn(mock(com.colonel.saas.entity.TalentClaim.class));
        when(sampleRequestMapper.selectCount(any())).thenReturn(0L);

        sampleController.createSample(request, userId, List.of(RoleCodes.CHANNEL_STAFF));

        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).insert(captor.capture());
        SampleRequest saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTalentUid()).isEqualTo("talent_002");
        assertThat(saved.getTalentNickname()).isEqualTo("crawler talent");
        assertThat(saved.getTalentFansCount()).isEqualTo(120000L);
        assertThat(saved.getTalentCreditScore()).isEqualTo(new BigDecimal("4.80"));
        assertThat(saved.getTalentMainCategory()).isEqualTo("food");
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getExtraData()).isNotNull();
        assertThat(((java.util.Map<?, ?>) saved.getExtraData().get("eligibilityCheck")).get("passed")).isEqualTo(true);
        assertThat(((java.util.Map<?, ?>) saved.getExtraData().get("requirementSnapshot")).get("minLevel")).isEqualTo("LV1");
    }

    @Test
    void createSample_shouldPersistStructuredRecipientFields() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("recipient product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_recipient_001");
        crawlerTalentInfo.setNickname("recipient talent");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_recipient_001");
        talent.setNickname("recipient talent");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_recipient_001");
        request.setProductId(productId);
        request.setQuantity(1);
        request.setRecipientName("张三");
        request.setRecipientPhone("13800138000");
        request.setRecipientAddress("上海市浦东新区测试路 1 号");

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_recipient_001")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentUuid, userId)).thenReturn(mock(com.colonel.saas.entity.TalentClaim.class));
        when(sampleRequestMapper.selectCount(any())).thenReturn(0L);

        var response = sampleController.createSample(request, userId, List.of(RoleCodes.CHANNEL_STAFF));

        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).insert(captor.capture());
        SampleRequest saved = captor.getValue();
        assertThat(saved.getRecipientName()).isEqualTo("张三");
        assertThat(saved.getRecipientPhone()).isEqualTo("13800138000");
        assertThat(saved.getRecipientAddress()).isEqualTo("上海市浦东新区测试路 1 号");
        assertThat(response.getData().getRecipientName()).isEqualTo("张三");
        assertThat(response.getData().getRecipientPhone()).isEqualTo("13800138000");
        assertThat(response.getData().getRecipientAddress()).isEqualTo("上海市浦东新区测试路 1 号");
    }

    @Test
    void createSample_shouldMarkInternalQuickSampleSource() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("internal sample product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_internal_sample_001");
        crawlerTalentInfo.setNickname("internal sample talent");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_internal_sample_001");
        talent.setNickname("internal sample talent");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_internal_sample_001");
        request.setProductId(productId);
        request.setQuantity(1);
        request.setApplySource("INTERNAL_QUICK_SAMPLE");

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_internal_sample_001")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentUuid, userId)).thenReturn(mock(com.colonel.saas.entity.TalentClaim.class));
        when(sampleRequestMapper.selectCount(any())).thenReturn(0L);

        var response = sampleController.createSample(request, userId, List.of(RoleCodes.CHANNEL_STAFF));

        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).insert(captor.capture());
        SampleRequest saved = captor.getValue();
        assertThat(saved.getExtraData()).containsEntry("applySource", "INTERNAL_QUICK_SAMPLE");
        assertThat(saved.getExtraData()).containsEntry("externalApply", false);
        assertThat(response.getData().getApplySource()).isEqualTo("INTERNAL_QUICK_SAMPLE");
    }

    @Test
    void createSample_shouldRejectWhenSnapshotProductIsNotSelectedToLibrary() {
        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setName("未入库商品");
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(productId);
        snapshot.setActivityId("ACT-1");
        snapshot.setProductId("P-1");
        SampleApplyRequest request = new SampleApplyRequest();
        request.setProductId(productId);
        request.setTalentId("talent_001");
        request.setQuantity(1);

        when(productMapper.selectById(productId)).thenReturn(product);
        when(productSnapshotMapper.selectById(productId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> sampleController.createSample(
                request,
                UUID.randomUUID(),
                List.of(RoleCodes.CHANNEL_STAFF)))
                .hasMessageContaining("尚未加入商品库");

        verify(sampleRequestMapper, never()).insert(any(SampleRequest.class));
    }

    @Test
    void createSample_shouldAllowManualTalentWhenCrawlerMissing() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("manual product");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("manual_talent_001");
        talent.setNickname("manual talent");
        talent.setFans(66000L);
        talent.setCategories("beauty");
        talent.setDataSource("MANUAL");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("manual_talent_001");
        request.setProductId(productId);
        request.setQuantity(1);

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("manual_talent_001")).thenReturn(null);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentUuid, userId)).thenReturn(mock(com.colonel.saas.entity.TalentClaim.class));
        when(sampleRequestMapper.selectCount(any())).thenReturn(0L);

        sampleController.createSample(request, userId, List.of(RoleCodes.CHANNEL_STAFF));

        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).insert(captor.capture());
        SampleRequest saved = captor.getValue();
        assertThat(saved.getTalentId()).isEqualTo(talentUuid);
        assertThat(saved.getTalentUid()).isEqualTo("manual_talent_001");
        assertThat(saved.getTalentNickname()).isEqualTo("manual talent");
        assertThat(saved.getTalentFansCount()).isEqualTo(66000L);
        assertThat(saved.getTalentMainCategory()).isEqualTo("beauty");
    }

    @Test
    void createSample_shouldResolveSnapshotProductIdToCanonicalProduct() {
        UUID snapshotId = UUID.randomUUID();
        UUID canonicalProductId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(snapshotId);
        snapshot.setProductId("10901827");

        Product product = new Product();
        product.setId(canonicalProductId);
        product.setProductId("10901827");
        product.setName("snapshot product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_snapshot_001");
        crawlerTalentInfo.setNickname("snapshot talent");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_snapshot_001");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_snapshot_001");
        request.setProductId(snapshotId);
        request.setQuantity(1);

        when(productMapper.selectById(snapshotId)).thenReturn(null);
        when(productSnapshotMapper.selectById(snapshotId)).thenReturn(snapshot);
        when(productMapper.selectOne(any())).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_snapshot_001")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentUuid, userId)).thenReturn(mock(com.colonel.saas.entity.TalentClaim.class));
        when(sampleRequestMapper.selectCount(any())).thenReturn(0L);

        sampleController.createSample(request, userId, List.of(RoleCodes.CHANNEL_STAFF));

        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).insert(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(canonicalProductId);
    }

    @Test
    void createSample_shouldAllowChannelLeaderBypassSevenDaysLimit() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_leader_001");
        crawlerTalentInfo.setNickname("leader talent");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_leader_001");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_leader_001");
        request.setProductId(productId);
        request.setQuantity(1);

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_leader_001")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentUuid, userId)).thenReturn(mock(com.colonel.saas.entity.TalentClaim.class));

        sampleController.createSample(request, userId, List.of(RoleCodes.CHANNEL_LEADER));

        verify(sampleRequestMapper).insert(any(SampleRequest.class));
        verify(sampleRequestMapper, never()).selectCount(any());
    }

    @Test
    void createSample_shouldRejectChannelStaffWhenTalentNotClaimed() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_unclaimed_001");
        crawlerTalentInfo.setNickname("unclaimed talent");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_unclaimed_001");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_unclaimed_001");
        request.setProductId(productId);
        request.setQuantity(1);

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_unclaimed_001")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talentUuid, userId)).thenReturn(null);

        assertThatThrownBy(() -> sampleController.createSample(request, userId, List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("请先认领");

        verify(sampleRequestMapper, never()).insert(any(SampleRequest.class));
    }

    @Test
    void createSample_shouldRejectOpsStaff() {
        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_ops_001");
        request.setProductId(UUID.randomUUID());
        request.setQuantity(1);

        assertThatThrownBy(() -> sampleController.createSample(request, UUID.randomUUID(), List.of(RoleCodes.OPS_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅渠道角色可以发起寄样申请");
    }

    @Test
    void getSampleById_shouldRejectCrossUserAccessInPersonalScope() {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setChannelUserId(ownerId);

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);

        assertThatThrownBy(() -> sampleController.getSampleById(sampleId, viewerId, null, DataScope.PERSONAL))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void getSampleById_shouldUseStoredDeptForDeptScope() {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        UUID storedDeptId = UUID.randomUUID();
        UUID movedDeptId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setChannelUserId(ownerId);
        sample.setDeptId(storedDeptId);
        sample.setStatus(1);

        SysUser owner = new SysUser();
        owner.setId(ownerId);
        owner.setDeptId(movedDeptId);

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productMapper.selectById(null)).thenReturn(null);
        when(sysUserMapper.selectById(ownerId)).thenReturn(owner);

        var response = sampleController.getSampleById(sampleId, viewerId, storedDeptId, DataScope.DEPT);

        assertThat(response.getData().getId()).isEqualTo(sampleId);
    }

    @Test
    void getSampleById_shouldExposeEligibilitySnapshotFields() {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setChannelUserId(ownerId);
        sample.setDeptId(deptId);
        sample.setStatus(1);
        sample.setRemark("原始备注");
        sample.setExtraData(java.util.Map.of(
                "applyReason", "潜力达人，申请破格寄样",
                "eligibilityCheck", java.util.Map.of("passed", false, "failedRules", java.util.List.of("minLevel")),
                "requirementSnapshot", java.util.Map.of("minLevel", "LV1", "actualLevel", "LV0")
        ));

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productMapper.selectById(null)).thenReturn(null);
        when(sysUserMapper.selectById(ownerId)).thenReturn(new SysUser());

        var response = sampleController.getSampleById(sampleId, ownerId, deptId, DataScope.PERSONAL);

        assertThat(response.getData().getApplyReason()).isEqualTo("潜力达人，申请破格寄样");
        assertThat(response.getData().getEligibilityCheck()).containsEntry("passed", false);
        assertThat(response.getData().getRequirementSnapshot()).containsEntry("actualLevel", "LV0");
    }

    @Test
    void createSample_shouldPopulateDeptFields() {
        UUID talentUuid = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_001");
        crawlerTalentInfo.setNickname("test talent");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_001");
        talent.setNickname("test talent");

        SysUser operator = new SysUser();
        operator.setId(userId);
        operator.setDeptId(deptId);

        SampleApplyRequest request = new SampleApplyRequest();
        request.setProductId(productId);
        request.setTalentId("talent_001");
        request.setQuantity(1);

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_001")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(sampleRequestMapper.selectCount(any())).thenReturn(0L);
        when(sysUserMapper.selectById(userId)).thenReturn(operator);

        sampleController.createSample(request, userId, List.of(RoleCodes.CHANNEL_STAFF));

        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).insert(captor.capture());
        assertThat(captor.getValue().getDeptId()).isEqualTo(deptId);
        assertThat(captor.getValue().getChannelDeptId()).isEqualTo(deptId);
    }

    @Test
    void actionSample_shouldRejectChannelStaffAuditAction() {
        UUID sampleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setChannelUserId(userId);
        sample.setStatus(1);

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("PENDING_SHIP");

        assertThatThrownBy(() -> sampleController.actionSample(
                sampleId,
                request,
                userId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("招商角色");
    }

    @Test
    void actionSample_shouldRejectBizLeaderAuditAction() {
        UUID sampleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setChannelUserId(userId);
        sample.setStatus(1);

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("PENDING_SHIP");

        assertThatThrownBy(() -> sampleController.actionSample(
                sampleId,
                request,
                userId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.BIZ_LEADER)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("招商角色");
    }

    @Test
    void getSamplePage_shouldRejectOpsStaffPendingAuditStatus() {
        assertThatThrownBy(() -> sampleController.getSamplePage(
                1,
                10,
                null,
                "PENDING_AUDIT",
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("待发货及后续物流");
    }

    @Test
    void getSampleById_shouldRejectOpsStaffWhenStatusBeforePendingShip() {
        UUID sampleId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(1);

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);

        assertThatThrownBy(() -> sampleController.getSampleById(
                sampleId,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("待发货及后续物流");
    }

    @Test
    void exportSamples_shouldRejectChannelRoles() {
        assertThatThrownBy(() -> sampleController.exportSamples(
                null,
                null,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.CHANNEL_LEADER),
                new MockHttpServletResponse()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅管理员、招商或运营");
    }

    @Test
    void exportSamples_shouldAllowOpsStaff() throws Exception {
        Page<SampleRequest> emptyPage = new Page<>(1, 500, 0);
        emptyPage.setRecords(List.of());
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any())).thenReturn(emptyPage);

        MockHttpServletResponse response = new MockHttpServletResponse();

        sampleController.exportSamples(
                null,
                null,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF),
                response);

        assertThat(response.getContentType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).startsWith("\ufeff寄样单号");
    }

    @Test
    void sensitiveSampleBatchAndExportEndpoints_shouldDeclareNarrowMethodRoles() throws Exception {
        RequireRoles batchApprove = SampleController.class
                .getMethod(
                        "batchApprove",
                        SampleController.SampleBatchActionRequest.class,
                        UUID.class,
                        UUID.class,
                        DataScope.class,
                        Object.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles batchReject = SampleController.class
                .getMethod(
                        "batchReject",
                        SampleController.SampleBatchActionRequest.class,
                        UUID.class,
                        UUID.class,
                        DataScope.class,
                        Object.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles batchShip = SampleController.class
                .getMethod(
                        "batchShip",
                        SampleController.SampleBatchShipRequest.class,
                        UUID.class,
                        UUID.class,
                        DataScope.class,
                        Object.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles refreshLogistics = SampleController.class
                .getMethod(
                        "refreshLogistics",
                        UUID.class,
                        UUID.class,
                        UUID.class,
                        DataScope.class,
                        Object.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles exportSamples = SampleController.class
                .getMethod(
                        "exportSamples",
                        String.class,
                        String.class,
                        UUID.class,
                        UUID.class,
                        UUID.class,
                        UUID.class,
                        DataScope.class,
                        Object.class,
                        jakarta.servlet.http.HttpServletResponse.class)
                .getAnnotation(RequireRoles.class);

        assertThat(batchApprove).isNotNull();
        assertThat(batchApprove.value()).containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF);
        assertThat(batchReject).isNotNull();
        assertThat(batchReject.value()).containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF);
        assertThat(batchShip).isNotNull();
        assertThat(batchShip.value()).containsExactly(RoleCodes.ADMIN, RoleCodes.OPS_STAFF);
        assertThat(refreshLogistics).isNotNull();
        assertThat(refreshLogistics.value()).containsExactly(RoleCodes.ADMIN, RoleCodes.OPS_STAFF);
        assertThat(exportSamples).isNotNull();
        assertThat(exportSamples.value()).containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.OPS_STAFF);
    }

    @Test
    void getStatusTransitions_shouldExposeRoleStateAndErrorMatrix() throws Exception {
        GetMapping mapping = SampleController.class
                .getMethod("getStatusTransitions")
                .getAnnotation(GetMapping.class);

        var response = sampleController.getStatusTransitions();
        Map<String, SampleController.SampleStatusTransitionVO> transitions = response.getData().stream()
                .collect(java.util.stream.Collectors.toMap(
                        SampleController.SampleStatusTransitionVO::getAction,
                        transition -> transition));

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/status-transitions");
        assertThat(response.getData()).hasSize(7);

        SampleController.SampleStatusTransitionVO approve = transitions.get("PENDING_SHIP");
        assertThat(approve.getAliases()).contains("APPROVED");
        assertThat(approve.getFromStatuses()).containsExactly("PENDING_AUDIT");
        assertThat(approve.getToStatus()).isEqualTo("PENDING_SHIP");
        assertThat(approve.getInternalToStatus()).isEqualTo("PENDING_SHIP");
        assertThat(approve.getRoleCodes()).containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF);
        assertThat(approve.getBatchEndpoint()).isEqualTo("POST /samples/batch-approve");
        assertThat(approve.getInvalidStateMessage()).contains("expected PENDING_AUDIT");

        SampleController.SampleStatusTransitionVO reject = transitions.get("REJECTED");
        assertThat(reject.getFromStatuses()).containsExactly("PENDING_AUDIT");
        assertThat(reject.getToStatus()).isEqualTo("REJECTED");
        assertThat(reject.getRequiredFields()).containsExactly("reason");
        assertThat(reject.getMissingFieldMessage()).isEqualTo("reason is required when reject sample request");
        assertThat(reject.getRoleCodes()).containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF);

        SampleController.SampleStatusTransitionVO shipping = transitions.get("SHIPPING");
        assertThat(shipping.getAliases()).contains("SHIPPED");
        assertThat(shipping.getFromStatuses()).containsExactly("PENDING_SHIP");
        assertThat(shipping.getToStatus()).isEqualTo("SHIPPED");
        assertThat(shipping.getInternalToStatus()).isEqualTo("SHIPPING");
        assertThat(shipping.getRequiredFields()).containsExactly("trackingNo");
        assertThat(shipping.getRoleCodes()).containsExactly(RoleCodes.ADMIN, RoleCodes.OPS_STAFF);
        assertThat(shipping.getBatchEndpoint()).isEqualTo("POST /samples/batch-ship");

        SampleController.SampleStatusTransitionVO pendingHomework = transitions.get("PENDING_HOMEWORK");
        assertThat(pendingHomework.getAliases()).containsExactly("SIGNED", "PENDING_TASK");
        assertThat(pendingHomework.getFromStatuses()).containsExactly("SHIPPED", "DELIVERED");
        assertThat(pendingHomework.getToStatus()).isEqualTo("PENDING_TASK");
        assertThat(pendingHomework.getInternalToStatus()).isEqualTo("PENDING_HOMEWORK");
        assertThat(pendingHomework.getRoleCodes()).containsExactly(RoleCodes.ADMIN, RoleCodes.OPS_STAFF);

        SampleController.SampleStatusTransitionVO completed = transitions.get("COMPLETED");
        assertThat(completed.isUserCallable()).isFalse();
        assertThat(completed.getActorType()).isEqualTo("SYSTEM");
        assertThat(completed.getFromStatuses()).containsExactly("PENDING_TASK");
        assertThat(completed.getToStatus()).isEqualTo("FINISHED");
        assertThat(completed.getTrigger()).contains("订单同步");
        assertThat(completed.getForbiddenMessage()).contains("仅允许系统自动推进");

        SampleController.SampleStatusTransitionVO closed = transitions.get("CLOSED");
        assertThat(closed.isUserCallable()).isFalse();
        assertThat(closed.getActorType()).isEqualTo("SYSTEM");
        assertThat(closed.getFromStatuses()).containsExactly("PENDING_TASK");
        assertThat(closed.getToStatus()).isEqualTo("CLOSED");
        assertThat(closed.getTrigger()).contains("超时");
    }

    @Test
    void filterOptions_shouldDelegateToSampleFilterOptionsService() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        SampleFilterOptionsDTO options = new SampleFilterOptionsDTO();
        options.setStatuses(List.of(new SampleFilterOptionItem("待审核", "PENDING_AUDIT")));
        SampleFilterOptionsService filterOptionsService = org.mockito.Mockito.mock(SampleFilterOptionsService.class);
        SampleFilterOptionsController controller = new SampleFilterOptionsController(filterOptionsService);
        when(filterOptionsService.buildOptions(userId, deptId, DataScope.ALL, List.of(RoleCodes.BIZ_STAFF)))
                .thenReturn(options);

        GetMapping mapping = SampleFilterOptionsController.class
                .getMethod("filterOptions", UUID.class, UUID.class, DataScope.class, Object.class)
                .getAnnotation(GetMapping.class);
        var response = controller.filterOptions(userId, deptId, DataScope.ALL, List.of(RoleCodes.BIZ_STAFF));

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/filter-options");
        assertThat(response.getData()).isSameAs(options);
        verify(filterOptionsService).buildOptions(userId, deptId, DataScope.ALL, List.of(RoleCodes.BIZ_STAFF));
    }

    @Test
    void createSample_shouldSkipRestrictionWhenDisabled() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_disabled_001");
        crawlerTalentInfo.setNickname("disabled talent");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_disabled_001");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_disabled_001");
        request.setProductId(productId);
        request.setQuantity(1);

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_disabled_001")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(businessRuleConfigService.isSampleRestrictEnabled()).thenReturn(false);

        sampleController.createSample(request, userId, List.of(RoleCodes.CHANNEL_STAFF));

        verify(sampleRequestMapper).insert(any(SampleRequest.class));
        verify(sampleRequestMapper, never()).selectCount(any());
    }

    @Test
    void createSample_shouldRequireRemarkWhenEligibilityFailed() {
        UUID productId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_not_fit");
        crawlerTalentInfo.setNickname("talent not fit");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_not_fit");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_not_fit");
        request.setProductId(productId);
        request.setQuantity(1);
        request.setRemark("");

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_not_fit")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(sampleRequestMapper.selectCount(any())).thenReturn(0L);
        when(sampleEligibilityService.evaluate(any(), any()))
                .thenReturn(new SampleEligibilityService.EligibilityResult(
                        false,
                        java.util.List.of("达人等级未达到 LV1"),
                        new SampleEligibilityService.SampleDefaultStandard(30000L, "LV1", java.util.Map.of()),
                        new SampleEligibilityService.TalentSnapshot(1000L, "LV0")
                ));

        assertThatThrownBy(() -> sampleController.createSample(request, UUID.randomUUID(), java.util.List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("请先填写申请原因");
    }

    @Test
    void createSample_shouldPersistEligibilitySnapshotWhenReasonProvided() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_reason_ok");
        crawlerTalentInfo.setNickname("talent reason ok");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_reason_ok");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_reason_ok");
        request.setProductId(productId);
        request.setQuantity(1);
        request.setRemark("潜力达人，申请破格寄样");

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_reason_ok")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(sampleRequestMapper.selectCount(any())).thenReturn(0L);
        when(sampleEligibilityService.evaluate(any(), any()))
                .thenReturn(new SampleEligibilityService.EligibilityResult(
                        false,
                        java.util.List.of("近30天销售额未达到 30000", "达人等级未达到 LV1"),
                        new SampleEligibilityService.SampleDefaultStandard(30000L, "LV1", java.util.Map.of("other_conditions", "粉丝>=10000")),
                        new SampleEligibilityService.TalentSnapshot(1000L, "LV0")
                ));

        sampleController.createSample(request, userId, java.util.List.of(RoleCodes.CHANNEL_STAFF));

        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).insert(captor.capture());
        SampleRequest saved = captor.getValue();
        assertThat(saved.getExtraData()).isNotNull();
        assertThat(saved.getExtraData()).containsEntry("applyReason", "潜力达人，申请破格寄样");
        assertThat(((java.util.Map<?, ?>) saved.getExtraData().get("eligibilityCheck")).get("passed")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<String> failedRules = (List<String>) ((java.util.Map<?, ?>) saved.getExtraData().get("eligibilityCheck")).get("failedRules");
        assertThat(failedRules).containsExactly("min30DaySales", "minLevel");
        assertThat(((java.util.Map<?, ?>) saved.getExtraData().get("requirementSnapshot")).get("actualLevel")).isEqualTo("LV0");
    }

    @Test
    void searchTalents_shouldReturnPagedResults() {
        SampleTalentVO vo = new SampleTalentVO();
        vo.setTalentId("talent_001");
        vo.setNickname("老铁好物");
        vo.setFansCount(100000L);
        vo.setCreditScore(new BigDecimal("4.6"));
        IPage<SampleTalentVO> page = new Page<>(1, 20);
        page.setRecords(java.util.List.of(vo));
        page.setTotal(1);

        when(crawlerTalentInfoService.searchTalents(
                eq("老铁"),
                eq("浙江"),
                eq(10000L),
                eq(500000L),
                eq(new BigDecimal("4.5")),
                eq(1),
                eq(20)
        )).thenReturn(page);

        SampleTalentQueryRequest query = new SampleTalentQueryRequest();
        query.setKeyword("老铁");
        query.setRegion("浙江");
        query.setMinFans(10000L);
        query.setMaxFans(500000L);
        query.setMinScore(new BigDecimal("4.5"));
        query.setPage(1);
        query.setSize(20);

        var response = sampleController.searchTalents(query);

        assertThat(response.getData().getTotal()).isEqualTo(1L);
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getNickname()).isEqualTo("老铁好物");
    }

    @Test
    void searchProducts_shouldReturnCanonicalProductIds() {
        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setProductId("10901825");
        product.setName("主演示商品-已转链可出单");

        Page<Product> productPage = new Page<>(1, 20);
        productPage.setRecords(List.of(product));
        productPage.setTotal(1);

        when(productService.getSelectedLibraryPage(1, 20, "主演示", null)).thenReturn(productPage);

        var response = sampleController.searchProducts(1, 20, "主演示", List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.getData().getTotal()).isEqualTo(1L);
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getId()).isEqualTo(productId);
        assertThat(response.getData().getRecords().get(0).getProductName()).isEqualTo("主演示商品-已转链可出单");
        verify(productService).getSelectedLibraryPage(1, 20, "主演示", null);
    }

    @Test
    void getSamplePage_shouldPassKeywordAndStatusFilters() {
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("排查演示商品-推广映射缺失");

        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setProductId(productId);
        sample.setTalentNickname("达人B-映射缺失订单");
        sample.setTalentUid("talent_test_b");
        sample.setRequestNo("TEST-SAMPLE-SHIP-001");
        sample.setStatus(5);

        IPage<SampleRequest> page = new Page<>(1, 10);
        page.setRecords(List.of(sample));
        page.setTotal(1);

        when(productMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(product));
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product));
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        var response = sampleController.getSamplePage(
                1,
                10,
                "映射缺失",
                "PENDING_HOMEWORK",
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                null
        );

        assertThat(response.getData().getTotal()).isEqualTo(1L);
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getTalentName()).isEqualTo("达人B-映射缺失订单");
        verify(productMapper).selectList(any(QueryWrapper.class));
        verify(sampleRequestMapper).findPageWithScope(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    void getSamplePage_shouldFilterByChannelAndRecruiterUserIds() {
        UUID channelUserId = UUID.randomUUID();
        UUID recruiterUserId = UUID.randomUUID();
        Page<SampleRequest> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(List.of());

        when(sampleRequestMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class), eq(recruiterUserId)))
                .thenReturn(emptyPage);

        var response = sampleController.getSamplePage(
                1,
                10,
                null,
                "PENDING_SHIP",
                channelUserId,
                recruiterUserId,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.ADMIN)
        );

        assertThat(response.getData().getTotal()).isZero();
        ArgumentCaptor<QueryWrapper<SampleRequest>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(sampleRequestMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture(), eq(recruiterUserId));
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("sr.status");
        assertThat(sqlSegment).contains("sr.channel_user_id");
    }

    @Test
    void getSampleBoard_shouldTraverseMultiplePages() {
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("分页寄样商品");

        SampleRequest first = new SampleRequest();
        first.setId(UUID.randomUUID());
        first.setProductId(productId);
        first.setTalentNickname("达人A");
        first.setStatus(1);

        SampleRequest second = new SampleRequest();
        second.setId(UUID.randomUUID());
        second.setProductId(productId);
        second.setTalentNickname("达人B");
        second.setStatus(2);

        IPage<SampleRequest> firstPage = new Page<>(1, 2000, 2001);
        firstPage.setRecords(List.of(first));
        IPage<SampleRequest> secondPage = new Page<>(2, 2000, 2001);
        secondPage.setRecords(List.of(second));

        when(sampleRequestMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(firstPage)
                .thenReturn(secondPage);
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product));

        var response = sampleController.getSampleBoard(UUID.randomUUID(), null, DataScope.ALL);

        assertThat(response.getData().get("PENDING_AUDIT")).hasSize(1);
        assertThat(response.getData().get("PENDING_SHIP")).hasSize(1);
    }

    @Test
    void actionSample_shouldAllowApproveFromPendingAudit() {
        UUID sampleId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(1);
        sample.setProductId(productId);
        sample.setTalentId(talentId);
        sample.setTalentNickname("test talent");
        sample.setRequestNo("SM20260421000001");
        sample.setExpectedSampleNum(1);

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("APPROVED");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productMapper.selectById(productId)).thenReturn(product);

        var response = sampleController.actionSample(
                sampleId,
                request,
                userId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.BIZ_STAFF));

        assertThat(response.getData().getStatus()).isEqualTo("PENDING_SHIP");
        verify(sampleRequestMapper).updateById(sample);
        verify(sampleStatusLogService).log(sampleId, 1, 2, userId, null);
    }

    @Test
    void actionSample_shouldAllowShippingFromPendingShip() {
        UUID sampleId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(2);
        sample.setProductId(productId);
        sample.setTalentId(talentId);
        sample.setTalentNickname("test talent");
        sample.setRequestNo("SM20260421000002");
        sample.setExpectedSampleNum(1);

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("SHIPPED");
        request.setTrackingNo("YT123456");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productMapper.selectById(productId)).thenReturn(product);

        var response = sampleController.actionSample(
                sampleId,
                request,
                userId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF));

        assertThat(response.getData().getStatus()).isEqualTo("SHIPPED");
        assertThat(response.getData().getTrackingNo()).isEqualTo("YT123456");
        assertThat(response.getData().getLogisticsSource()).isEqualTo("MANUAL");
        assertThat(sample.getExtraData()).containsEntry("logisticsSource", "MANUAL");
        verify(sampleRequestMapper).updateById(sample);
        verify(sampleStatusLogService).log(sampleId, 2, 3, userId, null);
    }

    @Test
    void actionSample_shouldAllowOpsSignDirectlyToPendingHomework() {
        UUID sampleId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(3);
        sample.setProductId(productId);
        sample.setRequestNo("SM20260506000001");

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("SIGNED");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productMapper.selectById(productId)).thenReturn(product);

        var response = sampleController.actionSample(
                sampleId,
                request,
                userId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF));

        assertThat(response.getData().getStatus()).isEqualTo("PENDING_TASK");
        assertThat(response.getData().getLogisticsSource()).isEqualTo("MANUAL");
        assertThat(sample.getExtraData()).containsEntry("logisticsSource", "MANUAL");
        verify(sampleStatusLogService).log(sampleId, 3, 5, userId, null);
    }

    @Test
    void getStatusLogs_shouldExposeLegacyStatuses() {
        UUID sampleId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(5);

        SampleStatusLog log = new SampleStatusLog();
        log.setId(UUID.randomUUID());
        log.setRequestId(sampleId);
        log.setFromStatus(3);
        log.setToStatus(5);
        log.setOperatorId(operatorId);

        SysUser operator = new SysUser();
        operator.setId(operatorId);
        operator.setRealName("运营测试");
        operator.setUsername("ops_staff");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(sampleStatusLogMapper.selectList(any())).thenReturn(List.of(log));
        when(sysUserMapper.selectById(operatorId)).thenReturn(operator);

        var response = sampleController.getStatusLogs(
                sampleId,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.ADMIN));

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getFromStatus()).isEqualTo("SHIPPED");
        assertThat(response.getData().get(0).getToStatus()).isEqualTo("PENDING_TASK");
        assertThat(response.getData().get(0).getOperatorName()).isEqualTo("运营测试 (ops_staff)");
    }

    @Test
    void actionSample_shouldRejectOpsCompleteAction() {
        UUID sampleId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(5);

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("COMPLETED");

        assertThatThrownBy(() -> sampleController.actionSample(
                sampleId,
                request,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅允许系统自动推进");
    }

    @Test
    void actionSample_shouldReportExpectedAndActualStatusWhenTransitionInvalid() {
        UUID sampleId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(2);

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("REJECTED");
        request.setReason("not matched");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);

        assertThatThrownBy(() -> sampleController.actionSample(
                sampleId,
                request,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.BIZ_STAFF)))
                .hasMessageContaining("expected PENDING_AUDIT but was PENDING_SHIP");
    }

    @Test
    void actionSample_shouldAllowRejectFromPendingAudit() {
        UUID sampleId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(1);
        sample.setProductId(productId);
        sample.setRequestNo("SM20260421000003");

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("REJECTED");
        request.setReason("not matched");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productMapper.selectById(productId)).thenReturn(product);

        var response = sampleController.actionSample(
                sampleId,
                request,
                userId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.BIZ_STAFF));

        assertThat(response.getData().getStatus()).isEqualTo("REJECTED");
        assertThat(response.getData().getRejectReason()).isEqualTo("not matched");
        verify(sampleStatusLogService).log(sampleId, 1, 7, userId, "not matched");
    }

    @Test
    void actionSample_shouldRejectInvalidTransition() {
        UUID sampleId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(8);

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("COMPLETED");

        assertThatThrownBy(() -> sampleController.actionSample(
                sampleId,
                request,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("仅允许系统自动推进");

        verify(sampleRequestMapper, never()).updateById(any(SampleRequest.class));
    }

    @Test
    void batchApprove_shouldCountSuccessAndFailures() {
        UUID sampleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(1);
        sample.setRequestNo("SR-APPROVE-1");

        SampleController.SampleBatchActionRequest request = new SampleController.SampleBatchActionRequest();
        request.setRequestNos(List.of("SR-APPROVE-1", "SR-MISSING"));
        request.setRemark("批量通过");

        when(sampleRequestMapper.selectOne(any())).thenReturn(sample).thenReturn(null);

        var response = sampleController.batchApprove(
                request,
                userId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.BIZ_STAFF));

        assertThat(response.getData()).containsEntry("success", 1).containsEntry("fail", 1);
        assertThat(sample.getStatus()).isEqualTo(2);
        assertThat(sample.getAuditTime()).isNotNull();
        verify(sampleRequestMapper).updateById(sample);
        verify(sampleStatusLogService).log(sampleId, 1, 2, userId, "批量通过");
    }

    @Test
    void batchReject_shouldRequireRemark() {
        SampleController.SampleBatchActionRequest request = new SampleController.SampleBatchActionRequest();
        request.setRequestNos(List.of("SR-REJECT-1"));
        request.setRemark(" ");

        assertThatThrownBy(() -> sampleController.batchReject(
                request,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.BIZ_STAFF)))
                .hasMessageContaining("remark is required");

        verify(sampleRequestMapper, never()).selectOne(any());
    }

    @Test
    void batchReject_shouldCountSuccessAndFailures() {
        UUID sampleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(1);
        sample.setRequestNo("SR-REJECT-1");

        SampleController.SampleBatchActionRequest request = new SampleController.SampleBatchActionRequest();
        request.setRequestNos(List.of("SR-REJECT-1", "SR-MISSING"));
        request.setRemark("达人资质不符");

        when(sampleRequestMapper.selectOne(any())).thenReturn(sample).thenReturn(null);

        var response = sampleController.batchReject(
                request,
                userId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.BIZ_STAFF));

        assertThat(response.getData()).containsEntry("success", 1).containsEntry("fail", 1);
        assertThat(sample.getStatus()).isEqualTo(7);
        assertThat(sample.getRejectReason()).isEqualTo("达人资质不符");
        assertThat(sample.getAuditTime()).isNotNull();
        verify(sampleRequestMapper).updateById(sample);
        verify(sampleStatusLogService).log(sampleId, 1, 7, userId, "达人资质不符");
    }

    @Test
    void batchShip_shouldCountSuccessAndFailuresAndMarkManualLogistics() {
        UUID sampleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(2);
        sample.setRequestNo("SR-SHIP-1");

        SampleController.SampleBatchShipItem item = new SampleController.SampleBatchShipItem();
        item.setRequestNo("SR-SHIP-1");
        item.setTrackingNo("SF123456");
        item.setShipperCode("SF");
        SampleController.SampleBatchShipItem missingItem = new SampleController.SampleBatchShipItem();
        missingItem.setRequestNo("SR-MISSING");
        missingItem.setTrackingNo("YT999");
        missingItem.setShipperCode("YT");
        SampleController.SampleBatchShipRequest request = new SampleController.SampleBatchShipRequest();
        request.setItems(List.of(item, missingItem));

        when(sampleRequestMapper.selectOne(any())).thenReturn(sample).thenReturn(null);

        var response = sampleController.batchShip(
                request,
                userId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF));

        assertThat(response.getData()).containsEntry("success", 1).containsEntry("fail", 1);
        assertThat(sample.getStatus()).isEqualTo(3);
        assertThat(sample.getTrackingNo()).isEqualTo("SF123456");
        assertThat(sample.getShipperCode()).isEqualTo("SF");
        assertThat(sample.getExtraData()).containsEntry("logisticsSource", "MANUAL");
        assertThat(sample.getShipTime()).isNotNull();
        verify(sampleRequestMapper).updateById(sample);
        verify(sampleStatusLogService).log(sampleId, 2, 3, userId, "SF123456");
    }

    @Test
    void exportSamples_shouldWriteEscapedCsvAcrossPages() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID channelUserId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setName("商品\n一");

        SysUser channelUser = new SysUser();
        channelUser.setId(channelUserId);
        channelUser.setRealName("张三");
        channelUser.setUsername("zhangsan");

        SampleRequest first = new SampleRequest();
        first.setId(UUID.randomUUID());
        first.setRequestNo("SR-\"1");
        first.setTalentNickname("达人,甲");
        first.setTalentUid("talent-1");
        first.setProductId(productId);
        first.setStatus(3);
        first.setChannelUserId(channelUserId);
        first.setTrackingNo("SF,123");
        first.setRejectReason("少\"件");
        first.setRemark("备注\n换行");
        first.setCreateTime(java.time.LocalDateTime.of(2026, 5, 22, 10, 0));

        SampleRequest second = new SampleRequest();
        second.setId(UUID.randomUUID());
        second.setRequestNo("SR-2");
        second.setTalentNickname("达人乙");
        second.setTalentUid("talent-2");
        second.setProductId(productId);
        second.setStatus(3);
        second.setCreateTime(java.time.LocalDateTime.of(2026, 5, 22, 11, 0));

        Page<SampleRequest> firstPage = new Page<>(1, 500, 501);
        firstPage.setRecords(List.of(first));
        Page<SampleRequest> secondPage = new Page<>(2, 500, 501);
        secondPage.setRecords(List.of(second));
        when(productMapper.selectList(any())).thenReturn(List.of(product));
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product));
        when(sysUserMapper.selectById(channelUserId)).thenReturn(channelUser);
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any()))
                .thenReturn(firstPage)
                .thenReturn(secondPage);

        MockHttpServletResponse response = new MockHttpServletResponse();

        sampleController.exportSamples(
                "SHIPPED",
                "商品",
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.ADMIN),
                response);

        String content = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(response.getContentType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(response.getHeader("Content-Disposition")).contains("samples.csv");
        assertThat(content).startsWith("\ufeff寄样单号");
        assertThat(content).contains("\"SR-\"\"1\"");
        assertThat(content).contains("\"达人,甲\"");
        assertThat(content).contains("\"商品\n一\"");
        assertThat(content).contains("SHIPPING");
        assertThat(content).contains("张三 (zhangsan)");
        assertThat(content).contains("\"SF,123\"");
        assertThat(content).contains("\"少\"\"件\"");
        assertThat(content).contains("\"备注\n换行\"");
        assertThat(content).contains("SR-2");
    }

    @Test
    void refreshLogistics_shouldReloadProgressedSample() {
        UUID sampleId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(3);
        sample.setProductId(productId);
        sample.setTalentNickname("达人甲");

        SampleRequest progressed = new SampleRequest();
        progressed.setId(sampleId);
        progressed.setStatus(5);
        progressed.setProductId(productId);
        progressed.setTalentNickname("达人甲");

        Product product = new Product();
        product.setId(productId);
        product.setName("测试商品");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample, progressed);
        when(productMapper.selectById(productId)).thenReturn(product);

        var response = sampleController.refreshLogistics(
                sampleId,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF));

        assertThat(response.getData().getStatus()).isEqualTo("PENDING_TASK");
        assertThat(response.getData().getProductName()).isEqualTo("测试商品");
        verify(sampleLogisticsSyncService).syncOne(sampleId);
    }

    @Test
    void deleteSample_shouldDeletePendingSample() {
        UUID sampleId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(1);

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);

        var response = sampleController.deleteSample(
                sampleId,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.getCode()).isEqualTo(200);
        verify(sampleRequestMapper).deleteById(sampleId);
    }

    @Test
    void deleteSample_shouldRejectNonPendingOrRejectedSample() {
        UUID sampleId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(3);

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);

        assertThatThrownBy(() -> sampleController.deleteSample(
                sampleId,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.CHANNEL_STAFF)))
                .hasMessageContaining("Only pending/rejected sample can be deleted");

        verify(sampleRequestMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void batchRequestTypes_shouldExposeAssignedValues() {
        SampleController.SampleBatchActionRequest rejectRequest = new SampleController.SampleBatchActionRequest();
        rejectRequest.setRequestNos(List.of("SR-1", "SR-2"));
        rejectRequest.setRemark("缺货");

        SampleController.SampleBatchShipItem shipItem = new SampleController.SampleBatchShipItem();
        shipItem.setRequestNo("SR-1");
        shipItem.setTrackingNo("SF123");
        shipItem.setShipperCode("SF");
        SampleController.SampleBatchShipRequest shipRequest = new SampleController.SampleBatchShipRequest();
        shipRequest.setItems(List.of(shipItem));

        assertThat(rejectRequest.getRequestNos()).containsExactly("SR-1", "SR-2");
        assertThat(rejectRequest.getRemark()).isEqualTo("缺货");
        assertThat(shipRequest.getItems()).containsExactly(shipItem);
        assertThat(shipItem.getRequestNo()).isEqualTo("SR-1");
        assertThat(shipItem.getTrackingNo()).isEqualTo("SF123");
        assertThat(shipItem.getShipperCode()).isEqualTo("SF");
    }

    @Test
    void sampleProductAndEligibilityVos_shouldExposeAssignedValues() {
        UUID productPk = UUID.randomUUID();
        SampleController.SampleProductVO product = new SampleController.SampleProductVO(productPk, "P-1", "样品");
        product.setProductId("P-2");
        product.setProductName("更新样品");

        SampleController.SampleEligibilityCheckVO eligibility = new SampleController.SampleEligibilityCheckVO();
        eligibility.setEligible(false);
        eligibility.setNeedReason(true);
        eligibility.setReasons(List.of("近30天销售额不足"));
        eligibility.setMin30DaySales(30000L);
        eligibility.setMinLevel("LV2");
        eligibility.setCurrent30DaySales(12000L);
        eligibility.setCurrentLevel("LV1");

        assertThat(product.getId()).isEqualTo(productPk);
        assertThat(product.getProductId()).isEqualTo("P-2");
        assertThat(product.getProductName()).isEqualTo("更新样品");
        assertThat(eligibility.isEligible()).isFalse();
        assertThat(eligibility.isNeedReason()).isTrue();
        assertThat(eligibility.getReasons()).containsExactly("近30天销售额不足");
        assertThat(eligibility.getMin30DaySales()).isEqualTo(30000L);
        assertThat(eligibility.getMinLevel()).isEqualTo("LV2");
        assertThat(eligibility.getCurrent30DaySales()).isEqualTo(12000L);
        assertThat(eligibility.getCurrentLevel()).isEqualTo("LV1");
    }

    @Test
    void sampleBoardCard_shouldExposeAssignedValues() {
        UUID id = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        java.time.LocalDateTime createdAt = java.time.LocalDateTime.of(2026, 5, 22, 10, 0);
        java.time.LocalDateTime stateAt = java.time.LocalDateTime.of(2026, 5, 22, 11, 0);
        SampleController.SampleBoardCard card = new SampleController.SampleBoardCard();
        card.setId(id);
        card.setRequestNo("SR-BOARD");
        card.setTalentName("达人");
        card.setProductId(productId);
        card.setProductName("商品");
        card.setQuantity(2);
        card.setChannelUserName("渠道");
        card.setTrackingNo("SF123");
        card.setRejectReason("不合适");
        card.setRemark("备注");
        card.setStatus("PENDING_SHIP");
        card.setCreateTime(createdAt);
        card.setStateEnterTime(stateAt);

        assertThat(card.getId()).isEqualTo(id);
        assertThat(card.getRequestNo()).isEqualTo("SR-BOARD");
        assertThat(card.getTalentName()).isEqualTo("达人");
        assertThat(card.getProductId()).isEqualTo(productId);
        assertThat(card.getProductName()).isEqualTo("商品");
        assertThat(card.getQuantity()).isEqualTo(2);
        assertThat(card.getChannelUserName()).isEqualTo("渠道");
        assertThat(card.getTrackingNo()).isEqualTo("SF123");
        assertThat(card.getRejectReason()).isEqualTo("不合适");
        assertThat(card.getRemark()).isEqualTo("备注");
        assertThat(card.getStatus()).isEqualTo("PENDING_SHIP");
        assertThat(card.getCreateTime()).isEqualTo(createdAt);
        assertThat(card.getStateEnterTime()).isEqualTo(stateAt);
    }

    @Test
    void privateRoleAndStatusHelpers_shouldNormalizeAliasesAndPermissions() {
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "isExemptFromSevenDaysLimit", (Object) null))
                .isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "isExemptFromSevenDaysLimit", List.of(RoleCodes.ADMIN)))
                .isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "isExemptFromSevenDaysLimit", "[" + RoleCodes.CHANNEL_LEADER + "]"))
                .isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "isExemptFromSevenDaysLimit", " "))
                .isFalse();

        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "hasAnyRole", null, (Object) new String[]{RoleCodes.ADMIN}))
                .isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "hasAnyRole", List.of(RoleCodes.OPS_STAFF), (Object) new String[]{RoleCodes.OPS_STAFF}))
                .isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "hasAnyRole", "[" + RoleCodes.BIZ_STAFF + "]", (Object) new String[]{RoleCodes.BIZ_STAFF}))
                .isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "isOpsStaffOnly", List.of(RoleCodes.OPS_STAFF)))
                .isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "isOpsStaffOnly", List.of(RoleCodes.OPS_STAFF, RoleCodes.ADMIN)))
                .isFalse();

        assertThat(ReflectionTestUtils.<String>invokeMethod(sampleController, "normalizeAction", "APPROVED")).isEqualTo("PENDING_SHIP");
        assertThat(ReflectionTestUtils.<String>invokeMethod(sampleController, "normalizeAction", "SHIPPED")).isEqualTo("SHIPPING");
        assertThat(ReflectionTestUtils.<String>invokeMethod(sampleController, "normalizeAction", "SIGNED")).isEqualTo("PENDING_HOMEWORK");
        assertThat(ReflectionTestUtils.<String>invokeMethod(sampleController, "normalizeAction", "PENDING_TASK")).isEqualTo("PENDING_HOMEWORK");
        assertThat(ReflectionTestUtils.<String>invokeMethod(sampleController, "normalizeAction", "FINISHED")).isEqualTo("COMPLETED");
        assertThat(ReflectionTestUtils.<String>invokeMethod(sampleController, "normalizeAction", " closed ")).isEqualTo("CLOSED");
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "isOpsVisibleStatusCode", (Integer) null)).isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "isOpsVisibleStatusCode", 2)).isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(sampleController, "isOpsVisibleStatusCode", 1)).isFalse();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(sampleController, "ensureOpsVisibleStatus", "PENDING_AUDIT"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(sampleController, "parseStatus", "missing"))
                .hasMessageContaining("Invalid status");
    }

    @Test
    void privateEligibilityHelpers_shouldBuildFailureSnapshotAndReasonRequirement() {
        SampleEligibilityService.EligibilityResult ineligible = new SampleEligibilityService.EligibilityResult(
                false,
                List.of("近30天销售额不足", "等级不足", "人工规则"),
                new SampleEligibilityService.SampleDefaultStandard(30000L, "LV2", Map.of("source", "config")),
                new SampleEligibilityService.TalentSnapshot(12000L, "LV1")
        );
        SampleApplyRequest request = new SampleApplyRequest();
        request.setRemark(" 破格申请 ");

        Map<String, Object> extra = ReflectionTestUtils.invokeMethod(sampleController, "buildSampleExtraData", request, ineligible);
        assertThat(extra).containsEntry("applyReason", "破格申请").containsEntry("addressSource", "manual");
        assertThat((Map<String, Object>) extra.get("requirementSnapshot"))
                .containsEntry("min30DaySales", 30000L)
                .containsEntry("actualLevel", "LV1")
                .containsKey("rawStandard");
        assertThat((Map<String, Object>) extra.get("eligibilityCheck"))
                .containsEntry("passed", false)
                .extractingByKey("failedRules")
                .asList()
                .containsExactly("min30DaySales", "minLevel", "custom");

        SampleController.SampleEligibilityCheckVO vo = ReflectionTestUtils.invokeMethod(sampleController, "toEligibilityVO", ineligible);
        assertThat(vo.isEligible()).isFalse();
        assertThat(vo.isNeedReason()).isTrue();
        assertThat(vo.getReasons()).hasSize(3);

        when(sampleEligibilityService.evaluate(any(), any())).thenReturn(ineligible);
        SampleApplyRequest noReason = new SampleApplyRequest();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                sampleController,
                "ensureEligibilityReasonIfNeeded",
                noReason,
                new Talent(),
                new CrawlerTalentInfo()))
                .hasMessageContaining("请先填写申请原因");

        assertThat(ReflectionTestUtils.<Object>invokeMethod(
                sampleController,
                "ensureEligibilityReasonIfNeeded",
                request,
                new Talent(),
                new CrawlerTalentInfo())).isSameAs(ineligible);
    }

    @Test
    void privateTalentAndLimitHelpers_shouldMapSnapshotsAndEnforceClaims() {
        Talent manualTalent = new Talent();
        manualTalent.setNickname("手动达人");
        manualTalent.setFans(12345L);
        manualTalent.setMainCategory("");
        manualTalent.setCategories("美妆");
        manualTalent.setIpLocation("杭州");

        CrawlerTalentInfo snapshot = ReflectionTestUtils.invokeMethod(
                sampleController,
                "buildCrawlerSnapshotFromTalent",
                manualTalent,
                "selected-id");
        assertThat(snapshot.getTalentId()).isEqualTo("selected-id");
        assertThat(snapshot.getMainCategory()).isEqualTo("美妆");

        CrawlerTalentInfo info = new CrawlerTalentInfo();
        info.setTalentId("talent-new");
        info.setNickname("新达人");
        info.setFansCount(66L);
        Talent existing = new Talent();
        existing.setId(UUID.randomUUID());
        existing.setDouyinUid("talent-new");
        when(talentMapper.selectOne(any())).thenReturn(existing).thenReturn(null);

        assertThat(ReflectionTestUtils.<Talent>invokeMethod(sampleController, "findOrCreateTalentFromCrawler", info))
                .isSameAs(existing);
        Talent created = ReflectionTestUtils.invokeMethod(sampleController, "findOrCreateTalentFromCrawler", info);
        assertThat(created.getDouyinUid()).isEqualTo("talent-new");
        verify(talentMapper).insert(created);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                sampleController,
                "ensureChannelTalentClaim",
                null,
                UUID.randomUUID(),
                List.of(RoleCodes.CHANNEL_STAFF)))
                .hasMessageContaining("达人信息不完整");

        UUID userId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        when(talentClaimMapper.findActiveByTalentAndUser(talentId, userId)).thenReturn(null);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                sampleController,
                "ensureChannelTalentClaim",
                userId,
                talentId,
                List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(ForbiddenException.class);

        when(businessRuleConfigService.isSampleRestrictEnabled()).thenReturn(false);
        ReflectionTestUtils.invokeMethod(sampleController, "checkSevenDaysLimit", userId, talentId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));
        ReflectionTestUtils.invokeMethod(sampleController, "checkSevenDaysLimit", userId, talentId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_LEADER));
    }

    @Test
    void privateLoadAndBoardHelpers_shouldHandleEmptyAndMappedValues() {
        assertThat(ReflectionTestUtils.<Map<UUID, Product>>invokeMethod(sampleController, "loadProducts", (Object) null)).isEmpty();
        assertThat(ReflectionTestUtils.<Set<UUID>>invokeMethod(sampleController, "loadMatchedProductIds", " ")).isEmpty();

        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setProductId("P-1");
        product.setName("商品");
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product));
        when(productMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(product));

        assertThat(ReflectionTestUtils.<Map<UUID, Product>>invokeMethod(sampleController, "loadProducts", Set.of(productId)))
                .containsEntry(productId, product);
        assertThat(ReflectionTestUtils.<Set<UUID>>invokeMethod(sampleController, "loadMatchedProductIds", "商品"))
                .containsExactly(productId);

        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setRequestNo("SR-1");
        sample.setTalentNickname("达人");
        sample.setProductId(productId);
        sample.setExpectedSampleNum(null);
        sample.setChannelUserId(UUID.randomUUID());
        sample.setCreateTime(LocalDateTime.of(2026, 5, 1, 10, 0));
        sample.setAuditTime(LocalDateTime.of(2026, 5, 1, 11, 0));
        sample.setShipTime(LocalDateTime.of(2026, 5, 1, 12, 0));
        sample.setDeliverTime(LocalDateTime.of(2026, 5, 1, 13, 0));
        sample.setCompleteTime(LocalDateTime.of(2026, 5, 1, 14, 0));
        sample.setCloseTime(LocalDateTime.of(2026, 5, 1, 15, 0));
        Object pendingShip = ReflectionTestUtils.invokeMethod(sampleController, "parseStatus", "PENDING_SHIP");
        SampleController.SampleBoardCard card = ReflectionTestUtils.invokeMethod(sampleController, "toBoardCard", sample, product, pendingShip);
        assertThat(card.getProductName()).isEqualTo("商品");
        assertThat(card.getQuantity()).isEqualTo(1);
        assertThat(card.getStatus()).isEqualTo("PENDING_SHIP");
        assertThat(card.getStateEnterTime()).isEqualTo(sample.getAuditTime());

        Object pendingHomework = ReflectionTestUtils.invokeMethod(sampleController, "parseStatus", "PENDING_HOMEWORK");
        Object completed = ReflectionTestUtils.invokeMethod(sampleController, "parseStatus", "COMPLETED");
        Object closed = ReflectionTestUtils.invokeMethod(sampleController, "parseStatus", "CLOSED");
        try {
            java.lang.reflect.Method toLegacyStatus = SampleController.class.getDeclaredMethod("toLegacyStatus", pendingHomework.getClass());
            toLegacyStatus.setAccessible(true);
            assertThat((String) toLegacyStatus.invoke(sampleController, pendingHomework)).isEqualTo("PENDING_TASK");
            assertThat((String) toLegacyStatus.invoke(sampleController, completed)).isEqualTo("FINISHED");
            java.lang.reflect.Method resolveStateEnterTime =
                    SampleController.class.getDeclaredMethod("resolveStateEnterTime", SampleRequest.class, closed.getClass());
            resolveStateEnterTime.setAccessible(true);
            assertThat((LocalDateTime) resolveStateEnterTime.invoke(sampleController, sample, closed)).isEqualTo(sample.getCloseTime());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        assertThat(ReflectionTestUtils.<String>invokeMethod(sampleController, "generateRequestNo")).startsWith("SM");
    }
}
