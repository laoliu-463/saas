package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.dto.SampleTalentQueryRequest;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Product;
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
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.service.LogisticsTrackService;
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

import java.math.BigDecimal;
import java.util.List;
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
    private LogisticsTrackService logisticsTrackService;

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
                logisticsTrackService
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
    void exportSamples_shouldRejectOpsStaff() {
        assertThatThrownBy(() -> sampleController.exportSamples(
                null,
                null,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF),
                new MockHttpServletResponse()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅管理员、招商组长或渠道组长");
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
}
