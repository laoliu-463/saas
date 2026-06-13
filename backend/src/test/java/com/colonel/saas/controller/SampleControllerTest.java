package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.dto.sample.SampleFilterOptionsDTO;
import com.colonel.saas.dto.sample.SampleFilterOptionItem;
import com.colonel.saas.dto.sample.LogisticsImportResult;
import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.dto.sample.SampleActionRequest;
import com.colonel.saas.dto.sample.SampleBatchActionRequest;
import com.colonel.saas.dto.sample.SampleBatchShipItem;
import com.colonel.saas.dto.sample.SampleBatchShipRequest;
import com.colonel.saas.dto.SampleTalentQueryRequest;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.SampleLogisticsTrace;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.SampleStatusLog;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryResult;
import com.colonel.saas.gateway.logistics.query.LogisticsStatusCode;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.SampleStatusLogMapper;
import com.colonel.saas.domain.product.facade.ProductDomainFacade;
import com.colonel.saas.domain.product.facade.dto.ProductReadDTO;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotReadDTO;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.controller.SampleFilterOptionsController;
import com.colonel.saas.service.SampleFilterOptionsService;
import com.colonel.saas.service.SampleLogisticsImportService;
import com.colonel.saas.service.SampleLogisticsSubscriptionService;
import com.colonel.saas.service.SampleLogisticsSyncService;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.SampleEligibilityService;
import com.colonel.saas.service.SampleStatusLogService;
import com.colonel.saas.service.SampleWriteTransactionService;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.sample.application.SampleCommandApplicationService;
import com.colonel.saas.domain.sample.application.SampleQueryApplicationService;
import com.colonel.saas.domain.sample.facade.LegacySampleDomainFacade;
import com.colonel.saas.domain.sample.policy.SampleStateMachine;
import com.colonel.saas.service.sample.LegacySampleCommandService;
import com.colonel.saas.service.sample.LegacySampleQueryService;
import com.colonel.saas.service.sample.SampleApplicationService;
import com.colonel.saas.vo.SampleTalentVO;
import com.colonel.saas.vo.sample.SampleBoardCard;
import com.colonel.saas.vo.sample.SampleEligibilityCheckVO;
import com.colonel.saas.vo.sample.SampleProductVO;
import com.colonel.saas.vo.sample.SampleStatusTransitionVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SampleControllerTest {

    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private ProductDomainFacade productDomainFacade;
    @Mock
    private UserDomainFacade userDomainFacade;
    @Mock
    private TalentDomainFacade talentDomainFacade;
    @Mock
    private SampleStatusLogService sampleStatusLogService;
    @Mock
    private SampleStatusLogMapper sampleStatusLogMapper;
    @Mock
    private CrawlerTalentInfoService crawlerTalentInfoService;
    @Mock
    private com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;
    @Mock
    private ProductService productService;
    @Mock
    private SampleEligibilityService sampleEligibilityService;
    @Mock
    private SampleLogisticsSyncService sampleLogisticsSyncService;
    @Mock
    private SampleLogisticsImportService sampleLogisticsImportService;
    @Mock
    private SampleLogisticsSubscriptionService sampleLogisticsSubscriptionService;
    @Mock
    private SampleDomainEventPublisher sampleDomainEventPublisher;

    private SampleController sampleController;
    private SampleApplicationService applicationDelegate;

    @BeforeEach
    void setUp() {
        applicationDelegate = new SampleApplicationService(
                sampleRequestMapper,
                productDomainFacade,
                userDomainFacade,
                talentDomainFacade,
                sampleStatusLogService,
                sampleStatusLogMapper,
                crawlerTalentInfoService,
                configDomainFacade,
                productService,
                sampleEligibilityService,
                sampleLogisticsSyncService,
                sampleLogisticsImportService,
                sampleLogisticsSubscriptionService,
                sampleDomainEventPublisher,
                new SampleWriteTransactionService());
        LegacySampleQueryService legacyQuery = new LegacySampleQueryService(applicationDelegate);
        LegacySampleCommandService legacyCommand = new LegacySampleCommandService(applicationDelegate);
        DddRefactorProperties dddProps = new DddRefactorProperties();
        LegacySampleDomainFacade sampleDomainFacade = new LegacySampleDomainFacade(sampleRequestMapper);
        com.colonel.saas.domain.sample.application.SampleQueryApplicationService queryApplicationService =
                new SampleQueryApplicationService(legacyQuery, sampleDomainFacade, dddProps);
        com.colonel.saas.domain.sample.application.SampleCommandApplicationService commandApplicationService =
                new SampleCommandApplicationService(legacyCommand, sampleDomainFacade, dddProps);
        sampleController = new SampleController(
                new com.colonel.saas.domain.sample.application.SampleApplicationService(
                        queryApplicationService,
                        commandApplicationService));
        lenient().when(configDomainFacade.isSampleLimitEnabled()).thenReturn(true);
        lenient().when(configDomainFacade.getSampleLimitDays()).thenReturn(7);
        lenient().when(sampleEligibilityService.evaluate(any(), any()))
                .thenReturn(new SampleEligibilityService.EligibilityResult(
                        true,
                        java.util.List.of(),
                        new SampleEligibilityService.SampleDefaultStandard(30000L, "LV1", java.util.Map.of()),
                        new SampleEligibilityService.TalentSnapshot(50000L, "LV2")
                ));
        lenient().when(sampleEligibilityService.classifyFailureRules(any()))
                .thenAnswer(invocation -> new com.colonel.saas.domain.sample.policy.SampleEligibilityPolicy()
                        .classifyFailureRules(invocation.getArgument(0)));
        lenient().when(talentDomainFacade.hasActiveClaim(any(), any())).thenReturn(true);
        lenient().when(productDomainFacade.isSelectedToLibraryForSample(any())).thenReturn(true);
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

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("talent_001")).thenReturn(crawlerTalentInfo);
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
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

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("talent_002")).thenReturn(crawlerTalentInfo);
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
        when(talentDomainFacade.hasActiveClaim(talentUuid, userId)).thenReturn(true);
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

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("talent_recipient_001")).thenReturn(crawlerTalentInfo);
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
        when(talentDomainFacade.hasActiveClaim(talentUuid, userId)).thenReturn(true);
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

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("talent_internal_sample_001")).thenReturn(crawlerTalentInfo);
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
        when(talentDomainFacade.hasActiveClaim(talentUuid, userId)).thenReturn(true);
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

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findSnapshotById(productId)).thenReturn(toSnapshotRead(snapshot));
        when(productDomainFacade.isSelectedToLibraryForSample(productId)).thenReturn(false);

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

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("manual_talent_001")).thenReturn(null);
        when(talentDomainFacade.findByDouyinUid("manual_talent_001")).thenReturn(toTalentRead(talent));
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
        when(talentDomainFacade.hasActiveClaim(talentUuid, userId)).thenReturn(true);
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

        when(productDomainFacade.findProductById(snapshotId)).thenReturn(null);
        when(productDomainFacade.findOrMaterializeSampleProduct(snapshotId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("talent_snapshot_001")).thenReturn(crawlerTalentInfo);
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
        when(talentDomainFacade.hasActiveClaim(talentUuid, userId)).thenReturn(true);
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

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("talent_leader_001")).thenReturn(crawlerTalentInfo);
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
        when(talentDomainFacade.hasActiveClaim(talentUuid, userId)).thenReturn(true);

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

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("talent_unclaimed_001")).thenReturn(crawlerTalentInfo);
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
        doReturn(false).when(talentDomainFacade).hasActiveClaim(talentUuid, userId);

        assertThatThrownBy(() -> sampleController.createSample(request, userId, List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("私海");

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

        assertThatThrownBy(() -> sampleController.getSampleById(sampleId, viewerId, null, DataScope.PERSONAL, null))
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

        UserOptionResponse owner = new UserOptionResponse(ownerId, null, null, movedDeptId, List.of(), null);

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productDomainFacade.findProductById(null)).thenReturn(null);
        when(productDomainFacade.findOrMaterializeSampleProduct(null)).thenReturn(null);
        when(userDomainFacade.getUserById(ownerId)).thenReturn(owner);

        var response = sampleController.getSampleById(sampleId, viewerId, storedDeptId, DataScope.DEPT, null);

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
        when(productDomainFacade.findProductById(null)).thenReturn(null);
        when(productDomainFacade.findOrMaterializeSampleProduct(null)).thenReturn(null);
        when(userDomainFacade.getUserById(ownerId))
                .thenReturn(new UserOptionResponse(ownerId, null, null, deptId, List.of(), null));

        var response = sampleController.getSampleById(sampleId, ownerId, deptId, DataScope.PERSONAL, null);

        assertThat(response.getData().getApplyReason()).isEqualTo("潜力达人，申请破格寄样");
        assertThat(response.getData().getEligibilityCheck()).containsEntry("passed", false);
        assertThat(response.getData().getRequirementSnapshot()).containsEntry("actualLevel", "LV0");
    }

    @Test
    void getSampleById_shouldAllowPersonalBizStaffWhenProductAssignedToUser() {
        UUID sampleId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setProductId(productId);
        sample.setChannelUserId(UUID.randomUUID());
        sample.setStatus(1);
        sample.setTalentNickname("达人甲");

        Product product = new Product();
        product.setId(productId);
        product.setProductId("source-product-1");
        product.setName("负责商品");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product), toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.isSampleProductAssignedToUser(any(), any())).thenReturn(true);

        var response = sampleController.getSampleById(
                sampleId,
                viewerId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.BIZ_STAFF));

        assertThat(response.getData().getProductName()).isEqualTo("负责商品");
        verify(productDomainFacade).isSampleProductAssignedToUser(eq(productId), eq(viewerId));
    }

    @Test
    void getSampleById_shouldAllowPersonalBizStaffWhenSnapshotProductAssignedToUser() {
        UUID sampleId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setProductId(productId);
        sample.setChannelUserId(UUID.randomUUID());
        sample.setStatus(1);

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(productId);
        snapshot.setProductId("snapshot-source-1");
        Product product = new Product();
        product.setId(productId);
        product.setName("快照负责商品");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findSnapshotById(productId)).thenReturn(toSnapshotRead(snapshot));
        when(productDomainFacade.isSampleProductAssignedToUser(productId, viewerId)).thenReturn(true);

        var response = sampleController.getSampleById(
                sampleId,
                viewerId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.BIZ_STAFF));

        assertThat(response.getData().getProductName()).isEqualTo("快照负责商品");
        verify(productDomainFacade).findSnapshotById(productId);
    }

    @Test
    void getSampleById_shouldRejectPersonalBizStaffWhenProductHasNoSourceMapping() {
        UUID sampleId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setProductId(productId);
        sample.setChannelUserId(UUID.randomUUID());
        sample.setStatus(1);

        Product product = new Product();
        product.setId(productId);
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(productId);

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findSnapshotById(productId)).thenReturn(toSnapshotRead(snapshot));

        assertThatThrownBy(() -> sampleController.getSampleById(
                sampleId,
                viewerId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权访问该寄样单");

        verify(productDomainFacade).isSampleProductAssignedToUser(productId, viewerId);
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

        UserOptionResponse operator = new UserOptionResponse(userId, null, null, deptId, List.of(), null);

        SampleApplyRequest request = new SampleApplyRequest();
        request.setProductId(productId);
        request.setTalentId("talent_001");
        request.setQuantity(1);

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("talent_001")).thenReturn(crawlerTalentInfo);
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
        when(sampleRequestMapper.selectCount(any())).thenReturn(0L);
        when(userDomainFacade.getUserById(userId)).thenReturn(operator);

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

        SampleActionRequest request = new SampleActionRequest();
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

        SampleActionRequest request = new SampleActionRequest();
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
        assertThatThrownBy(() -> getSamplePageBasic(
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
    void exportSamples_shouldRejectChannelStaff() {
        assertThatThrownBy(() -> exportSamplesBasic(
                null,
                null,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.CHANNEL_STAFF),
                new MockHttpServletResponse()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅管理员、招商、运营或渠道组长");
    }

    @Test
    void exportSamples_shouldAllowChannelLeader() throws Exception {
        Page<SampleRequest> emptyPage = new Page<>(1, 500, 0);
        emptyPage.setRecords(List.of());
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any())).thenReturn(emptyPage);

        MockHttpServletResponse response = new MockHttpServletResponse();

        exportSamplesBasic(
                null,
                null,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.CHANNEL_LEADER),
                response);

        assertThat(response.getContentType()).isEqualTo("text/csv; charset=UTF-8");
    }

    @Test
    void exportSamples_shouldAllowOpsStaff() throws Exception {
        Page<SampleRequest> emptyPage = new Page<>(1, 500, 0);
        emptyPage.setRecords(List.of());
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any())).thenReturn(emptyPage);

        MockHttpServletResponse response = new MockHttpServletResponse();

        exportSamplesBasic(
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
                .getDeclaredMethod(
                        "batchApprove",
                        SampleBatchActionRequest.class,
                        UUID.class,
                        UUID.class,
                        DataScope.class,
                        Object.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles batchReject = SampleController.class
                .getDeclaredMethod(
                        "batchReject",
                        SampleBatchActionRequest.class,
                        UUID.class,
                        UUID.class,
                        DataScope.class,
                        Object.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles batchShip = SampleController.class
                .getDeclaredMethod(
                        "batchShip",
                        SampleBatchShipRequest.class,
                        UUID.class,
                        UUID.class,
                        DataScope.class,
                        Object.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles refreshLogistics = SampleController.class
                .getDeclaredMethod(
                        "refreshLogistics",
                        UUID.class,
                        UUID.class,
                        UUID.class,
                        DataScope.class,
                        Object.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles exportSamples = SampleController.class
                .getDeclaredMethod(
                        "exportSamples",
                        String.class,
                        String.class,
                        List.class,
                        UUID.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        java.time.LocalDateTime.class,
                        java.time.LocalDateTime.class,
                        java.time.LocalDateTime.class,
                        java.time.LocalDateTime.class,
                        String.class,
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
        assertThat(exportSamples.value()).containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.OPS_STAFF, RoleCodes.CHANNEL_LEADER);
    }

    @Test
    void getStatusTransitions_shouldExposeRoleStateAndErrorMatrix() throws Exception {
        GetMapping mapping = SampleController.class
                .getDeclaredMethod("getStatusTransitions")
                .getAnnotation(GetMapping.class);

        var response = sampleController.getStatusTransitions();
        Map<String, SampleStatusTransitionVO> transitions = response.getData().stream()
                .collect(java.util.stream.Collectors.toMap(
                        SampleStatusTransitionVO::getAction,
                        transition -> transition));

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/status-transitions");
        assertThat(response.getData()).hasSize(7);

        SampleStatusTransitionVO approve = transitions.get("PENDING_SHIP");
        assertThat(approve.getAliases()).contains("APPROVED");
        assertThat(approve.getFromStatuses()).containsExactly("PENDING_AUDIT");
        assertThat(approve.getToStatus()).isEqualTo("PENDING_SHIP");
        assertThat(approve.getInternalToStatus()).isEqualTo("PENDING_SHIP");
        assertThat(approve.getRoleCodes()).containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF);
        assertThat(approve.getBatchEndpoint()).isEqualTo("POST /samples/batch-approve");
        assertThat(approve.getInvalidStateMessage()).contains("expected PENDING_AUDIT");

        SampleStatusTransitionVO reject = transitions.get("REJECTED");
        assertThat(reject.getFromStatuses()).containsExactly("PENDING_AUDIT");
        assertThat(reject.getToStatus()).isEqualTo("REJECTED");
        assertThat(reject.getRequiredFields()).containsExactly("reason");
        assertThat(reject.getMissingFieldMessage()).isEqualTo("reason is required when reject sample request");
        assertThat(reject.getRoleCodes()).containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF);

        SampleStatusTransitionVO shipping = transitions.get("SHIPPING");
        assertThat(shipping.getAliases()).contains("SHIPPED");
        assertThat(shipping.getFromStatuses()).containsExactly("PENDING_SHIP");
        assertThat(shipping.getToStatus()).isEqualTo("SHIPPED");
        assertThat(shipping.getInternalToStatus()).isEqualTo("SHIPPING");
        assertThat(shipping.getRequiredFields()).containsExactly("trackingNo");
        assertThat(shipping.getRoleCodes()).containsExactly(RoleCodes.ADMIN, RoleCodes.OPS_STAFF);
        assertThat(shipping.getBatchEndpoint()).isEqualTo("POST /samples/batch-ship");

        SampleStatusTransitionVO pendingHomework = transitions.get("PENDING_HOMEWORK");
        assertThat(pendingHomework.getAliases()).containsExactly("SIGNED", "PENDING_TASK");
        assertThat(pendingHomework.getFromStatuses()).containsExactly("SHIPPED", "DELIVERED");
        assertThat(pendingHomework.getToStatus()).isEqualTo("PENDING_TASK");
        assertThat(pendingHomework.getInternalToStatus()).isEqualTo("PENDING_HOMEWORK");
        assertThat(pendingHomework.getRoleCodes()).containsExactly(RoleCodes.ADMIN, RoleCodes.OPS_STAFF);

        SampleStatusTransitionVO completed = transitions.get("COMPLETED");
        assertThat(completed.isUserCallable()).isFalse();
        assertThat(completed.getActorType()).isEqualTo("SYSTEM");
        assertThat(completed.getFromStatuses()).containsExactly("PENDING_TASK");
        assertThat(completed.getToStatus()).isEqualTo("FINISHED");
        assertThat(completed.getTrigger()).contains("订单同步");
        assertThat(completed.getForbiddenMessage()).contains("仅允许系统自动推进");

        SampleStatusTransitionVO closed = transitions.get("CLOSED");
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

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("talent_disabled_001")).thenReturn(crawlerTalentInfo);
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
        when(configDomainFacade.isSampleLimitEnabled()).thenReturn(false);

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

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("talent_not_fit")).thenReturn(crawlerTalentInfo);
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
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

        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(crawlerTalentInfoService.findByTalentId("talent_reason_ok")).thenReturn(crawlerTalentInfo);
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any())).thenReturn(toTalentRead(talent));
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
        product.setProductId("10901825");
        product.setName("排查演示商品-推广映射缺失");
        product.setCover("https://example.test/product.png");
        product.setPrice(2190L);

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(productId);
        snapshot.setProductId("3650575210828268564");
        snapshot.setCover("https://example.test/snapshot.png");
        snapshot.setPriceText("¥21.9");
        snapshot.setShopId(123456L);
        snapshot.setShopName("演示店铺");

        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setProductId(productId);
        sample.setUserId(UUID.randomUUID());
        sample.setTalentNickname("达人B-映射缺失订单");
        sample.setTalentUid("talent_test_b");
        sample.setTalentFansCount(8056733L);
        sample.setTalentCreditScore(new BigDecimal("4.90"));
        sample.setTalentMainCategory("食品饮料");
        sample.setRequestNo("TEST-SAMPLE-SHIP-001");
        sample.setTrackingNo("SF123456789");
        sample.setShipperCode("SF");
        sample.setRecipientName("张三");
        sample.setRecipientPhone("13800138000");
        sample.setRecipientAddress("上海市测试路 1 号");
        sample.setShipTime(LocalDateTime.of(2026, 5, 1, 10, 0));
        sample.setDeliverTime(LocalDateTime.of(2026, 5, 2, 10, 0));
        sample.setExtraData(Map.of(
                "applySource", "INTERNAL_QUICK_SAMPLE",
                "cooperationType", "FREE_SAMPLE",
                "sampleOwnerType", "MERCHANT",
                "homeworkType", "HAS_ORDER"
        ));
        sample.setStatus(5);

        IPage<SampleRequest> page = new Page<>(1, 10);
        page.setRecords(List.of(sample));
        page.setTotal(1);

        when(productDomainFacade.findProductIdsByKeyword(any(), anyLong())).thenReturn(java.util.Set.of(productId));
        when(productDomainFacade.loadProductsByIds(any())).thenReturn(java.util.Map.of(productId, toProductRead(product)));
        when(productDomainFacade.findSnapshotById(productId)).thenReturn(toSnapshotRead(snapshot));
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        var response = getSamplePageBasic(
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
        var record = response.getData().getRecords().get(0);
        assertThat(record.getTalentName()).isEqualTo("达人B-映射缺失订单");
        assertThat(record.getTalentUid()).isEqualTo("talent_test_b");
        assertThat(record.getTalentFansCount()).isEqualTo(8056733L);
        assertThat(record.getTalentCreditScore()).isEqualTo("4.90");
        assertThat(record.getTalentMainCategory()).isEqualTo("食品饮料");
        assertThat(record.getProductExternalId()).isEqualTo("10901825");
        assertThat(record.getProductCover()).isEqualTo("https://example.test/product.png");
        assertThat(record.getProductPriceText()).isEqualTo("¥21.9");
        assertThat(record.getShopId()).isEqualTo("123456");
        assertThat(record.getShopName()).isEqualTo("演示店铺");
        assertThat(record.getApplicantUserId()).isEqualTo(sample.getUserId());
        assertThat(record.getLogisticsCompany()).isEqualTo("SF");
        assertThat(record.getRecipientName()).isEqualTo("张三");
        assertThat(record.getApplySourceLabel()).isEqualTo("内部寄样");
        assertThat(record.getCooperationTypeLabel()).isEqualTo("免费寄样");
        assertThat(record.getSampleOwnerTypeLabel()).isEqualTo("商家");
        assertThat(record.getHomeworkTypeLabel()).isEqualTo("有订单");
        assertThat(record.getShipTime()).isEqualTo(LocalDateTime.of(2026, 5, 1, 10, 0));
        assertThat(record.getDeliverTime()).isEqualTo(LocalDateTime.of(2026, 5, 2, 10, 0));
        verify(productDomainFacade).findProductIdsByKeyword(any(), anyLong());
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
                List.of(channelUserId),
                recruiterUserId,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
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
    void getSamplePage_shouldUseAuditorQueryForPersonalBizStaffDefaultPendingAudit() {
        UUID userId = UUID.randomUUID();
        Page<SampleRequest> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(List.of());
        when(sampleRequestMapper.findPageForAuditor(any(Page.class), eq(userId), any(QueryWrapper.class)))
                .thenReturn(emptyPage);

        var response = getSamplePageBasic(
                1,
                10,
                null,
                null,
                userId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.BIZ_STAFF));

        assertThat(response.getData().getTotal()).isZero();
        ArgumentCaptor<QueryWrapper<SampleRequest>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(sampleRequestMapper).findPageForAuditor(any(Page.class), eq(userId), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("sr.status");
    }

    @Test
    void getSamplePage_shouldUseAuditorQueryWithRecruiterForPersonalBizStaff() {
        UUID userId = UUID.randomUUID();
        UUID recruiterUserId = UUID.randomUUID();
        Page<SampleRequest> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(List.of());
        when(sampleRequestMapper.findPageForAuditor(any(Page.class), eq(userId), any(QueryWrapper.class), eq(recruiterUserId)))
                .thenReturn(emptyPage);

        var response = sampleController.getSamplePage(
                1,
                10,
                null,
                "PENDING_AUDIT",
                null,
                recruiterUserId,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                userId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.BIZ_STAFF));

        assertThat(response.getData().getTotal()).isZero();
        verify(sampleRequestMapper).findPageForAuditor(any(Page.class), eq(userId), any(QueryWrapper.class), eq(recruiterUserId));
    }

    @Test
    void getSamplePage_shouldPassCooperationWorkbenchFilters() {
        UUID channelUserId = UUID.randomUUID();
        UUID recruiterUserId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setName("合作单筛选商品");
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(productId);
        snapshot.setShopId(998877L);
        snapshot.setShopName("合作单店铺");
        Page<SampleRequest> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(List.of());

        when(productDomainFacade.findProductIdsByKeyword(any(), anyLong())).thenReturn(java.util.Set.of(productId));
        when(productDomainFacade.findProductSnapshotIdsByShopKeyword(any(), anyLong())).thenReturn(java.util.Set.of(productId));
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class), eq(recruiterUserId)))
                .thenReturn(emptyPage);

        var response = sampleController.getSamplePage(
                1,
                10,
                null,
                "PENDING_SHIP",
                List.of(channelUserId),
                recruiterUserId,
                "合作单筛选商品",
                "合作单店铺",
                "SF123",
                "TEST-SAMPLE-001",
                "达人A",
                "FREE_SAMPLE",
                "MERCHANT",
                "HAS_ORDER",
                "张三",
                "13800138000",
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 5, 2, 0, 0),
                LocalDateTime.of(2026, 5, 3, 0, 0),
                LocalDateTime.of(2026, 5, 4, 0, 0),
                "SF",
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.ADMIN)
        );

        assertThat(response.getData().getTotal()).isZero();
        ArgumentCaptor<QueryWrapper<SampleRequest>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(sampleRequestMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture(), eq(recruiterUserId));
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sqlSegment)
                .contains("sr.status")
                .contains("sr.channel_user_id")
                .contains("sr.product_id")
                .contains("sr.tracking_no")
                .contains("sr.request_no")
                .contains("sr.talent_nickname")
                .contains("sr.talent_uid")
                .contains("cooperationType")
                .contains("sampleOwnerType")
                .contains("sr.recipient_name")
                .contains("sr.recipient_phone")
                .contains("sr.create_time")
                .contains("sr.complete_time")
                .contains("sr.signed_at")
                .contains("sr.shipper_code");
    }

    @Test
    void getSamplePage_shouldApplyNoOrderAndCustomHomeworkFilters() {
        Page<SampleRequest> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(List.of());
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage)
                .thenReturn(emptyPage);

        sampleController.getSamplePage(
                1,
                10,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "NO_ORDER",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.ADMIN));
        sampleController.getSamplePage(
                1,
                10,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "CUSTOM_HOMEWORK",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.ADMIN));

        ArgumentCaptor<QueryWrapper<SampleRequest>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(sampleRequestMapper, times(2)).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues().get(0).getSqlSegment()).contains("sr.status");
        assertThat(wrapperCaptor.getAllValues().get(1).getSqlSegment()).contains("homeworkType");
    }

    @Test
    void getSamplePage_shouldUseExactMatchForTrackingAndRequestNo() {
        Page<SampleRequest> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(List.of());
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage);

        sampleController.getSamplePage(
                1, 10, null, "PENDING_SHIP", null, null, null, null,
                "SF123456", "TEST-SAMPLE-EXACT-001", null, null, null, null, null, null, null, null, null, null, null,
                UUID.randomUUID(), null, DataScope.ALL, List.of(RoleCodes.ADMIN));

        ArgumentCaptor<QueryWrapper<SampleRequest>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(sampleRequestMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        // 精确匹配：必须是 = 而不是 LIKE（不再包含 %）
        assertThat(sqlSegment)
                .contains("sr.tracking_no =")
                .contains("sr.request_no =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("SF123456", "TEST-SAMPLE-EXACT-001");
        assertThat(sqlSegment)
                .doesNotContain("sr.tracking_no like")
                .doesNotContain("sr.request_no like")
                .doesNotContain("%SF123456%")
                .doesNotContain("%TEST-SAMPLE-EXACT-001%");
    }

    @Test
    void getSamplePage_shouldApplyMultiSelectChannelUsers() {
        UUID channel1 = UUID.randomUUID();
        UUID channel2 = UUID.randomUUID();
        UUID channel3 = UUID.randomUUID();
        Page<SampleRequest> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(List.of());
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage);

        // 多选渠道（3 个 ID，含 null 噪声与重复）
        List<UUID> channelIds = new java.util.ArrayList<>();
        channelIds.add(channel1);
        channelIds.add(channel2);
        channelIds.add(null);
        channelIds.add(channel3);
        channelIds.add(channel1); // 重复也应被去重

        sampleController.getSamplePage(
                1, 10, null, "PENDING_SHIP", channelIds, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                UUID.randomUUID(), null, DataScope.ALL, List.of(RoleCodes.ADMIN));

        ArgumentCaptor<QueryWrapper<SampleRequest>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(sampleRequestMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        // 包含 IN 多选过滤
        assertThat(sqlSegment).contains("sr.channel_user_id").contains("IN");
    }

    @Test
    void getSamplePage_shouldHandleEmptyChannelListAsNoFilter() {
        Page<SampleRequest> emptyPage = new Page<>(1, 10, 0);
        emptyPage.setRecords(List.of());
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage);

        // 空列表等同于不过滤
        sampleController.getSamplePage(
                1, 10, null, "PENDING_SHIP", List.of(), null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                UUID.randomUUID(), null, DataScope.ALL, List.of(RoleCodes.ADMIN));

        ArgumentCaptor<QueryWrapper<SampleRequest>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(sampleRequestMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        // 空列表：不过滤 channel_user_id
        assertThat(sqlSegment).doesNotContain("sr.channel_user_id");
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
        when(productDomainFacade.loadProductsByIds(any())).thenReturn(java.util.Map.of(productId, toProductRead(product)));

        var response = sampleController.getSampleBoard(UUID.randomUUID(), null, DataScope.ALL, null);

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

        SampleActionRequest request = new SampleActionRequest();
        request.setAction("APPROVED");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));

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

        SampleActionRequest request = new SampleActionRequest();
        request.setAction("SHIPPED");
        request.setTrackingNo("YT123456");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));

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
        verify(sampleLogisticsSubscriptionService).subscribeAfterShipment(sample);
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

        SampleActionRequest request = new SampleActionRequest();
        request.setAction("SIGNED");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));

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
    void actionSample_shouldAllowOpsMoveDeliveredToPendingHomeworkWithoutOverwritingSource() {
        UUID sampleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(4);
        sample.setRequestNo("SR-DELIVERED");
        sample.setExtraData(Map.of("logisticsSource", "CALLBACK"));

        SampleActionRequest request = new SampleActionRequest();
        request.setAction("PENDING_HOMEWORK");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);

        var response = sampleController.actionSample(
                sampleId,
                request,
                userId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF));

        assertThat(response.getData().getStatus()).isEqualTo("PENDING_TASK");
        assertThat(sample.getStatus()).isEqualTo(5);
        assertThat(sample.getExtraData()).containsEntry("logisticsSource", "CALLBACK");
        assertThat(sample.getDeliverTime()).isNull();
        verify(sampleStatusLogService).log(sampleId, 4, 5, userId, null);
        verify(sampleDomainEventPublisher).publishSampleSigned(eq(sample), any());
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

        UserOptionResponse operator = new UserOptionResponse(operatorId, "ops_staff", "运营测试", null, List.of(), null);

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(sampleStatusLogMapper.selectList(any())).thenReturn(List.of(log));
        when(userDomainFacade.getUserById(operatorId)).thenReturn(operator);

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

        SampleActionRequest request = new SampleActionRequest();
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

        SampleActionRequest request = new SampleActionRequest();
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

        SampleActionRequest request = new SampleActionRequest();
        request.setAction("REJECTED");
        request.setReason("not matched");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));

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

        SampleActionRequest request = new SampleActionRequest();
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

        SampleBatchActionRequest request = new SampleBatchActionRequest();
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
        SampleBatchActionRequest request = new SampleBatchActionRequest();
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

        SampleBatchActionRequest request = new SampleBatchActionRequest();
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

        SampleBatchShipItem item = new SampleBatchShipItem();
        item.setRequestNo("SR-SHIP-1");
        item.setTrackingNo("SF123456");
        item.setShipperCode("SF");
        SampleBatchShipItem missingItem = new SampleBatchShipItem();
        missingItem.setRequestNo("SR-MISSING");
        missingItem.setTrackingNo("YT999");
        missingItem.setShipperCode("YT");
        SampleBatchShipRequest request = new SampleBatchShipRequest();
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
        verify(sampleLogisticsSubscriptionService).subscribeAfterShipment(sample);
    }

    @Test
    void exportSamples_shouldWriteEscapedCsvAcrossPages() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID channelUserId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setName("商品\n一");

        UserOptionResponse channelUser = new UserOptionResponse(channelUserId, "zhangsan", "张三", null, List.of(), null);

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
        when(productDomainFacade.findProductIdsByKeyword(any(), anyLong())).thenReturn(java.util.Set.of(productId));
        when(productDomainFacade.loadProductsByIds(any())).thenReturn(java.util.Map.of(productId, toProductRead(product)));
        when(userDomainFacade.getUserById(channelUserId)).thenReturn(channelUser);
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any()))
                .thenReturn(firstPage)
                .thenReturn(secondPage);

        MockHttpServletResponse response = new MockHttpServletResponse();

        exportSamplesBasic(
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
        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));

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
    void syncLogistics_shouldReturnQueryMetadataAndTraceList() {
        UUID sampleId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        SampleRequest before = new SampleRequest();
        before.setId(sampleId);
        before.setStatus(3);
        before.setProductId(productId);
        before.setRequestNo("SR-LOG-1");
        before.setTrackingNo("SF123");
        before.setShipperCode("shunfeng");
        before.setTalentNickname("达人甲");

        SampleRequest after = new SampleRequest();
        after.setId(sampleId);
        after.setStatus(3);
        after.setProductId(productId);
        after.setRequestNo("SR-LOG-1");
        after.setTrackingNo("SF123");
        after.setShipperCode("shunfeng");
        after.setLogisticsStatus("IN_TRANSIT");
        after.setLogisticsStatusName("运输中");
        after.setLogisticsLastError(null);
        after.setTalentNickname("达人甲");

        SampleLogisticsTrace trace = new SampleLogisticsTrace();
        trace.setTraceTime(LocalDateTime.of(2026, 5, 25, 10, 0));
        trace.setTraceContent("已揽收");
        trace.setStatusCode("IN_TRANSIT");
        trace.setStatusName("运输中");

        LogisticsQueryResult queryResult = LogisticsQueryResult.builder()
                .success(true)
                .provider("kuaidi100")
                .trackingNo("SF123")
                .logisticsCompany("shunfeng")
                .statusCode(LogisticsStatusCode.IN_TRANSIT)
                .statusName("运输中")
                .signed(false)
                .traces(List.of())
                .queriedAt(LocalDateTime.now())
                .build();

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(before, after);
        when(sampleLogisticsSyncService.syncOne(sampleId)).thenReturn(queryResult);
        when(sampleLogisticsSyncService.listTraces(sampleId)).thenReturn(List.of(trace));

        var response = sampleController.syncLogistics(
                sampleId,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF));

        assertThat(response.getData().getQuerySuccess()).isTrue();
        assertThat(response.getData().getProvider()).isEqualTo("kuaidi100");
        assertThat(response.getData().getTrackingNo()).isEqualTo("SF123");
        assertThat(response.getData().getTraces()).hasSize(1);
        assertThat(response.getData().getTraces().get(0).getTraceContent()).isEqualTo("已揽收");
        verify(sampleLogisticsSyncService).syncOne(sampleId);
    }

    @Test
    void getSampleLogistics_shouldReturnStoredTraceList() {
        UUID sampleId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(3);
        sample.setRequestNo("SR-LOG-2");
        sample.setTrackingNo("YT123");
        sample.setShipperCode("yuantong");
        sample.setLogisticsStatus("IN_TRANSIT");
        sample.setLogisticsStatusName("运输中");
        sample.setLogisticsLastQueryAt(LocalDateTime.of(2026, 5, 25, 11, 0));
        sample.setLogisticsLastError("last error");

        SampleLogisticsTrace trace = new SampleLogisticsTrace();
        trace.setTraceTime(LocalDateTime.of(2026, 5, 25, 11, 30));
        trace.setTraceContent("到达转运中心");
        trace.setStatusCode("IN_TRANSIT");
        trace.setStatusName("运输中");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(sampleLogisticsSyncService.listTraces(sampleId)).thenReturn(List.of(trace));

        var response = sampleController.getSampleLogistics(
                sampleId,
                UUID.randomUUID(),
                null,
                DataScope.ALL,
                List.of(RoleCodes.OPS_STAFF));

        assertThat(response.getData().getRequestNo()).isEqualTo("SR-LOG-2");
        assertThat(response.getData().getLogisticsLastError()).isEqualTo("last error");
        assertThat(response.getData().getTraces()).hasSize(1);
        verify(sampleLogisticsSyncService).listTraces(sampleId);
    }

    @Test
    void syncAllLogistics_shouldReturnSummaryAndRejectUnauthorizedRole() {
        when(sampleLogisticsSyncService.syncPendingInTransit(100))
                .thenReturn(new SampleLogisticsSyncService.SyncBatchSummary(4, 2, 1, 1));

        var response = sampleController.syncAllLogistics(List.of(RoleCodes.ADMIN));

        assertThat(response.getData())
                .containsEntry("total", 4)
                .containsEntry("success", 2)
                .containsEntry("failed", 1)
                .containsEntry("skipped", 1);

        assertThatThrownBy(() -> sampleController.syncAllLogistics(List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅运营或管理员可触发物流同步");
    }

    @Test
    void logisticsImportTemplateAndUpload_shouldDelegateToServices() throws Exception {
        byte[] template = new byte[] {1, 2, 3};
        when(sampleLogisticsImportService.generateTemplate()).thenReturn(template);
        MockHttpServletResponse response = new MockHttpServletResponse();

        sampleController.downloadLogisticsImportTemplate(response);

        assertThat(response.getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeader("Content-Disposition")).contains("sample-logistics-import-template.xlsx");
        assertThat(response.getContentAsByteArray()).containsExactly(template);

        LogisticsImportResult importResult = LogisticsImportResult.builder()
                .total(1)
                .successCount(1)
                .failedCount(0)
                .items(List.of())
                .build();
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", template);
        UUID userId = UUID.randomUUID();
        when(sampleLogisticsImportService.importTrackingNumbers(file, userId, List.of(RoleCodes.OPS_STAFF), true))
                .thenReturn(importResult);

        var uploadResponse = sampleController.importLogisticsTracking(
                file,
                true,
                userId,
                List.of(RoleCodes.OPS_STAFF));

        assertThat(uploadResponse.getData()).isSameAs(importResult);
        verify(sampleLogisticsImportService).importTrackingNumbers(file, userId, List.of(RoleCodes.OPS_STAFF), true);
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
        SampleBatchActionRequest rejectRequest = new SampleBatchActionRequest();
        rejectRequest.setRequestNos(List.of("SR-1", "SR-2"));
        rejectRequest.setRemark("缺货");

        SampleBatchShipItem shipItem = new SampleBatchShipItem();
        shipItem.setRequestNo("SR-1");
        shipItem.setTrackingNo("SF123");
        shipItem.setShipperCode("SF");
        SampleBatchShipRequest shipRequest = new SampleBatchShipRequest();
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
        SampleProductVO product = new SampleProductVO(productPk, "P-1", "样品");
        product.setProductId("P-2");
        product.setProductName("更新样品");

        SampleEligibilityCheckVO eligibility = new SampleEligibilityCheckVO();
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
        SampleBoardCard card = new SampleBoardCard();
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
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "isExemptFromSevenDaysLimit", (Object) null))
                .isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "isExemptFromSevenDaysLimit", List.of(RoleCodes.ADMIN)))
                .isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "isExemptFromSevenDaysLimit", "[" + RoleCodes.CHANNEL_LEADER + "]"))
                .isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "isExemptFromSevenDaysLimit", " "))
                .isFalse();

        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "hasAnyRole", null, (Object) new String[]{RoleCodes.ADMIN}))
                .isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "hasAnyRole", List.of(RoleCodes.OPS_STAFF), (Object) new String[]{RoleCodes.OPS_STAFF}))
                .isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "hasAnyRole", "[" + RoleCodes.BIZ_STAFF + "]", (Object) new String[]{RoleCodes.BIZ_STAFF}))
                .isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "isOpsStaffOnly", List.of(RoleCodes.OPS_STAFF)))
                .isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "isOpsStaffOnly", List.of(RoleCodes.OPS_STAFF, RoleCodes.ADMIN)))
                .isFalse();

        assertThat(SampleStateMachine.normalizeAction("APPROVED")).isEqualTo("PENDING_SHIP");
        assertThat(SampleStateMachine.normalizeAction("SHIPPED")).isEqualTo("SHIPPING");
        assertThat(SampleStateMachine.normalizeAction("SIGNED")).isEqualTo("PENDING_HOMEWORK");
        assertThat(SampleStateMachine.normalizeAction("PENDING_TASK")).isEqualTo("PENDING_HOMEWORK");
        assertThat(SampleStateMachine.normalizeAction("FINISHED")).isEqualTo("COMPLETED");
        assertThat(SampleStateMachine.normalizeAction(" closed ")).isEqualTo("CLOSED");
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "isOpsVisibleStatusCode", (Integer) null)).isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "isOpsVisibleStatusCode", 2)).isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(applicationDelegate, "isOpsVisibleStatusCode", 1)).isFalse();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(applicationDelegate, "ensureOpsVisibleStatus", "PENDING_AUDIT"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(applicationDelegate, "parseStatus", "missing"))
                .hasMessageContaining("Invalid status");
    }

    @Test
    void sampleStatusEnum_shouldMapCodesAliasesAndUnknownValues() throws Exception {
        Class<?> statusClass = SampleStatus.class;
        Method fromCode = statusClass.getDeclaredMethod("fromCode", Integer.class);
        Method fromApiStatus = statusClass.getDeclaredMethod("fromApiStatus", String.class);
        fromCode.setAccessible(true);
        fromApiStatus.setAccessible(true);

        assertThat(((Enum<?>) fromCode.invoke(null, 1)).name()).isEqualTo("PENDING_AUDIT");
        assertThat(((Enum<?>) fromCode.invoke(null, 2)).name()).isEqualTo("PENDING_SHIP");
        assertThat(((Enum<?>) fromCode.invoke(null, 3)).name()).isEqualTo("SHIPPING");
        assertThat(((Enum<?>) fromCode.invoke(null, 4)).name()).isEqualTo("DELIVERED");
        assertThat(((Enum<?>) fromCode.invoke(null, 5)).name()).isEqualTo("PENDING_HOMEWORK");
        assertThat(((Enum<?>) fromCode.invoke(null, 6)).name()).isEqualTo("COMPLETED");
        assertThat(((Enum<?>) fromCode.invoke(null, 7)).name()).isEqualTo("REJECTED");
        assertThat(((Enum<?>) fromCode.invoke(null, 8)).name()).isEqualTo("CLOSED");

        assertThat(((Enum<?>) fromApiStatus.invoke(null, "PENDING_TASK")).name()).isEqualTo("PENDING_HOMEWORK");
        assertThat(((Enum<?>) fromApiStatus.invoke(null, "SHIPPED")).name()).isEqualTo("SHIPPING");
        assertThat(((Enum<?>) fromApiStatus.invoke(null, "FINISHED")).name()).isEqualTo("COMPLETED");
        assertThat(((Enum<?>) fromApiStatus.invoke(null, "PENDING_AUDIT")).name()).isEqualTo("PENDING_AUDIT");

        assertThatThrownBy(() -> fromCode.invoke(null, 99))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(BusinessException.class)
                .hasRootCauseMessage("Unknown sample status: 99");
        assertThatThrownBy(() -> fromApiStatus.invoke(null, "UNKNOWN"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
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

        Map<String, Object> extra = ReflectionTestUtils.invokeMethod(applicationDelegate, "buildSampleExtraData", request, ineligible);
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

        SampleEligibilityCheckVO vo = ReflectionTestUtils.invokeMethod(applicationDelegate, "toEligibilityVO", ineligible);
        assertThat(vo.isEligible()).isFalse();
        assertThat(vo.isNeedReason()).isTrue();
        assertThat(vo.getReasons()).hasSize(3);

        when(sampleEligibilityService.evaluate(any(), any())).thenReturn(ineligible);
        SampleApplyRequest noReason = new SampleApplyRequest();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                applicationDelegate,
                "ensureEligibilityReasonIfNeeded",
                noReason,
                new Talent(),
                new CrawlerTalentInfo()))
                .hasMessageContaining("请先填写申请原因");

        assertThat(ReflectionTestUtils.<Object>invokeMethod(
                applicationDelegate,
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
                applicationDelegate,
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
        when(talentDomainFacade.findOrCreateSampleTalent(any(), any(), any()))
                .thenReturn(toTalentRead(existing))
                .thenReturn(new TalentReadDTO(
                        UUID.randomUUID(), "talent-new", null, "新达人", 66L, 1, null, null, null, null));

        assertThat(ReflectionTestUtils.<Talent>invokeMethod(applicationDelegate, "findOrCreateTalentFromCrawler", info).getId())
                .isEqualTo(existing.getId());
        Talent created = ReflectionTestUtils.invokeMethod(applicationDelegate, "findOrCreateTalentFromCrawler", info);
        assertThat(created.getDouyinUid()).isEqualTo("talent-new");
        verify(talentDomainFacade, times(2)).findOrCreateSampleTalent(eq("talent-new"), eq("新达人"), eq(66L));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                applicationDelegate,
                "ensureChannelTalentClaim",
                null,
                UUID.randomUUID(),
                List.of(RoleCodes.CHANNEL_STAFF)))
                .hasMessageContaining("达人信息不完整");

        UUID userId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        when(talentDomainFacade.hasActiveClaim(talentId, userId)).thenReturn(false);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                applicationDelegate,
                "ensureChannelTalentClaim",
                userId,
                talentId,
                List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(ForbiddenException.class);

        when(configDomainFacade.isSampleLimitEnabled()).thenReturn(false);
        ReflectionTestUtils.invokeMethod(applicationDelegate, "checkSevenDaysLimit", userId, talentId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));
        ReflectionTestUtils.invokeMethod(applicationDelegate, "checkSevenDaysLimit", userId, talentId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_LEADER));
    }

    @Test
    void privateLoadAndBoardHelpers_shouldHandleEmptyAndMappedValues() {
        assertThat(ReflectionTestUtils.<Map<UUID, Product>>invokeMethod(applicationDelegate, "loadProducts", (Object) null)).isEmpty();
        assertThat(ReflectionTestUtils.<Set<UUID>>invokeMethod(applicationDelegate, "loadMatchedProductIds", " ")).isEmpty();

        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setProductId("P-1");
        product.setName("商品");
        when(productDomainFacade.loadProductsByIds(any())).thenReturn(java.util.Map.of(productId, toProductRead(product)));
        when(productDomainFacade.findProductIdsByKeyword(any(), anyLong())).thenReturn(java.util.Set.of(productId));

        assertThat(ReflectionTestUtils.<Map<UUID, Product>>invokeMethod(applicationDelegate, "loadProducts", Set.of(productId)))
                .containsEntry(productId, product);
        assertThat(ReflectionTestUtils.<Set<UUID>>invokeMethod(applicationDelegate, "loadMatchedProductIds", "商品"))
                .containsExactly(productId);

        UUID snapshotId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(snapshotId);
        snapshot.setProductId("SNAP-1");
        snapshot.setTitle(" ");
        snapshot.setStatus(null);
        Product materializedEntity = new Product();
        materializedEntity.setId(snapshotId);
        materializedEntity.setName("SNAP-1");
        materializedEntity.setStatus(1);
        when(productDomainFacade.findOrMaterializeSampleProduct(snapshotId)).thenReturn(toProductRead(materializedEntity));

        Product materialized = ReflectionTestUtils.invokeMethod(applicationDelegate, "requireProduct", snapshotId);

        assertThat(materialized.getName()).isEqualTo("SNAP-1");
        assertThat(materialized.getStatus()).isEqualTo(1);
        verify(productDomainFacade).findOrMaterializeSampleProduct(snapshotId);

        Product noAssigneeProduct = new Product();
        noAssigneeProduct.setProductId("P-NO-ASSIGNEE");
        noAssigneeProduct.setActivityId(UUID.randomUUID());
        when(productDomainFacade.isSelectedToLibraryForSample(any())).thenReturn(true);
        assertThat(ReflectionTestUtils.<UUID>invokeMethod(applicationDelegate, "resolveColonelUserId", noAssigneeProduct)).isNull();

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
        Object pendingShip = ReflectionTestUtils.invokeMethod(applicationDelegate, "parseStatus", "PENDING_SHIP");
        SampleBoardCard card = ReflectionTestUtils.invokeMethod(applicationDelegate, "toBoardCard", sample, product, pendingShip);
        assertThat(card.getProductName()).isEqualTo("商品");
        assertThat(card.getQuantity()).isEqualTo(1);
        assertThat(card.getStatus()).isEqualTo("PENDING_SHIP");
        assertThat(card.getStateEnterTime()).isEqualTo(sample.getAuditTime());

        Object pendingHomework = ReflectionTestUtils.invokeMethod(applicationDelegate, "parseStatus", "PENDING_HOMEWORK");
        Object completed = ReflectionTestUtils.invokeMethod(applicationDelegate, "parseStatus", "COMPLETED");
        Object closed = ReflectionTestUtils.invokeMethod(applicationDelegate, "parseStatus", "CLOSED");
        try {
        java.lang.reflect.Method toLegacyStatus = SampleApplicationService.class.getDeclaredMethod("toLegacyStatus", pendingHomework.getClass());
            toLegacyStatus.setAccessible(true);
            assertThat((String) toLegacyStatus.invoke(applicationDelegate, pendingHomework)).isEqualTo("PENDING_TASK");
            assertThat((String) toLegacyStatus.invoke(applicationDelegate, completed)).isEqualTo("FINISHED");
            java.lang.reflect.Method resolveStateEnterTime =
        SampleApplicationService.class.getDeclaredMethod("resolveStateEnterTime", SampleRequest.class, closed.getClass());
            resolveStateEnterTime.setAccessible(true);
            assertThat((LocalDateTime) resolveStateEnterTime.invoke(applicationDelegate, sample, closed)).isEqualTo(sample.getCloseTime());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "generateRequestNo")).startsWith("SM");
    }

    @Test
    void privatePresentationAndEventHelpers_shouldCoverFallbackBranches() {
        Product priceProduct = new Product();
        priceProduct.setPrice(2190L);
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveProductPriceText", priceProduct, null))
                .isEqualTo("¥21.9");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveProductPriceText", null, null))
                .isNull();

        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveApplySourceLabel", "INTERNAL_QUICK_SAMPLE"))
                .isEqualTo("内部寄样");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveApplySourceLabel", "MANUAL"))
                .isEqualTo("手动申请");

        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveOptionLabel", null, "默认"))
                .isEqualTo("默认");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveOptionLabel", "PAID_SAMPLE", "默认"))
                .isEqualTo("付费寄样");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveOptionLabel", "EXCHANGE_SAMPLE", "默认"))
                .isEqualTo("置换寄样");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveOptionLabel", "COLONEL", "默认"))
                .isEqualTo("团长");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveOptionLabel", "OTHER", "默认"))
                .isEqualTo("其他");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveOptionLabel", "CUSTOM", "默认"))
                .isEqualTo("CUSTOM");

        SampleRequest completed = new SampleRequest();
        completed.setStatus(6);
        SampleRequest pendingHomework = new SampleRequest();
        pendingHomework.setStatus(5);
        SampleRequest pendingShip = new SampleRequest();
        pendingShip.setStatus(2);
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveHomeworkTypeLabel", "NO_ORDER", pendingShip))
                .isEqualTo("无订单");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveHomeworkTypeLabel", "PARTIAL", pendingShip))
                .isEqualTo("部分完成");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveHomeworkTypeLabel", "CUSTOM", pendingShip))
                .isEqualTo("CUSTOM");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveHomeworkTypeLabel", null, completed))
                .isEqualTo("有订单");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveHomeworkTypeLabel", null, pendingHomework))
                .isEqualTo("待交作业");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveHomeworkTypeLabel", null, pendingShip))
                .isNull();

        assertThat(ReflectionTestUtils.<Map<String, Object>>invokeMethod(applicationDelegate, "readExtraMap", (Map<String, Object>) null, "x"))
                .isEmpty();
        assertThat(ReflectionTestUtils.<Map<String, Object>>invokeMethod(applicationDelegate, "readExtraMap", Map.of("x", "not-map"), "x"))
                .isEmpty();
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "readExtraText", (Map<String, Object>) null, "x"))
                .isNull();
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "readExtraText", Map.of("x", 123), "x"))
                .isEqualTo("123");

        UUID realNameOnlyId = UUID.randomUUID();
        UUID usernameOnlyId = UUID.randomUUID();
        UUID blankUserId = UUID.randomUUID();
        UserOptionResponse realNameOnly = new UserOptionResponse(realNameOnlyId, null, "张三", null, List.of(), null);
        UserOptionResponse usernameOnly = new UserOptionResponse(usernameOnlyId, "zhangsan", null, null, List.of(), null);
        UserOptionResponse blankUser = new UserOptionResponse(blankUserId, null, null, null, List.of(), null);
        when(userDomainFacade.getUserById(realNameOnlyId)).thenReturn(realNameOnly);
        when(userDomainFacade.getUserById(usernameOnlyId)).thenReturn(usernameOnly);
        when(userDomainFacade.getUserById(blankUserId)).thenReturn(blankUser);

        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveUserDisplayName", (UUID) null)).isNull();
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveUserDisplayName", realNameOnlyId)).isEqualTo("张三");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveUserDisplayName", usernameOnlyId)).isEqualTo("zhangsan");
        assertThat(ReflectionTestUtils.<String>invokeMethod(applicationDelegate, "resolveUserDisplayName", blankUserId)).isNull();

        UUID assigneeId = UUID.randomUUID();
        Product assignedProduct = new Product();
        assignedProduct.setId(UUID.randomUUID());
        assignedProduct.setProductId("P-ASSIGNED");
        assignedProduct.setActivityId(UUID.randomUUID());
        when(productDomainFacade.findProductAssigneeId(anyString(), eq("P-ASSIGNED"))).thenReturn(assigneeId);
        when(productDomainFacade.findProductSnapshotAssigneeId(any())).thenReturn(assigneeId);
        assertThat(ReflectionTestUtils.<UUID>invokeMethod(applicationDelegate, "resolveColonelUserId", assignedProduct))
                .isEqualTo(assigneeId);

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent-ok");
        when(crawlerTalentInfoService.findByTalentId("talent-ok")).thenReturn(crawlerTalentInfo);
        when(crawlerTalentInfoService.findByTalentId("talent-missing")).thenReturn(null);
        assertThat(ReflectionTestUtils.<CrawlerTalentInfo>invokeMethod(applicationDelegate, "requireCrawlerTalent", "talent-ok"))
                .isSameAs(crawlerTalentInfo);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(applicationDelegate, "requireCrawlerTalent", "talent-missing"))
                .hasMessageContaining("Selected talent does not exist");

        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 12, 0);
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setProductId(productId);
        when(productDomainFacade.findProductById(productId)).thenReturn(null);
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(null);
        ReflectionTestUtils.invokeMethod(applicationDelegate, "publishActionDomainEvent", "COMPLETED", sample, userId, now, null);
        ReflectionTestUtils.invokeMethod(applicationDelegate, "publishActionDomainEvent", "CLOSED", sample, userId, now, "超时关闭");
        ReflectionTestUtils.invokeMethod(applicationDelegate, "publishActionDomainEvent", "UNKNOWN", sample, userId, now, null);
        verify(sampleDomainEventPublisher).publishSampleCompleted(sample, null, now);
        verify(sampleDomainEventPublisher).publishSampleClosed(sample, "超时关闭", now);
    }

    @Test
    void getSamplePage_shouldReturnAllExpectedListFields() {
        UUID productId = UUID.randomUUID();
        UUID sampleId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setProductId("10901825");
        product.setName("列表测试商品");
        product.setCover("https://example.test/cover.png");
        product.setPrice(9900L);

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(productId);
        snapshot.setProductId("3650575210828268564");
        snapshot.setCover("https://example.test/snapshot-cover.png");
        snapshot.setPriceText("¥99.0");
        snapshot.setShopId(123456L);
        snapshot.setShopName("测试店铺");

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setProductId(productId);
        sample.setUserId(UUID.randomUUID());
        sample.setChannelUserId(UUID.randomUUID());
        sample.setTalentNickname("列表达人");
        sample.setTalentUid("talent_list_001");
        sample.setTalentFansCount(50000L);
        sample.setTalentCreditScore(new BigDecimal("4.75"));
        sample.setTalentMainCategory("美妆");
        sample.setRequestNo("SR-LIST-001");
        sample.setTrackingNo("SF-LIST-123");
        sample.setShipperCode("SF");
        sample.setRecipientName("李四");
        sample.setRecipientPhone("13900139000");
        sample.setRecipientAddress("北京市朝阳区测试路 2 号");
        sample.setStatus(1);
        sample.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        sample.setUpdateTime(LocalDateTime.of(2026, 6, 1, 11, 0));
        sample.setExtraData(Map.of(
                "applySource", "MANUAL",
                "cooperationType", "FREE_SAMPLE",
                "sampleOwnerType", "MERCHANT",
                "homeworkType", "HAS_ORDER",
                "applyReason", "测试申请理由"
        ));

        IPage<SampleRequest> page = new Page<>(1, 10);
        page.setRecords(List.of(sample));
        page.setTotal(1);

        when(productDomainFacade.loadProductsByIds(any())).thenReturn(java.util.Map.of(productId, toProductRead(product)));
        when(productDomainFacade.findSnapshotById(productId)).thenReturn(toSnapshotRead(snapshot));
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        var response = sampleController.getSamplePage(
                1, 10, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                UUID.randomUUID(), null, DataScope.ALL, List.of(RoleCodes.ADMIN));

        assertThat(response.getData().getRecords()).hasSize(1);
        var vo = response.getData().getRecords().get(0);

        assertThat(vo.getId()).isEqualTo(sampleId);
        assertThat(vo.getRequestNo()).isEqualTo("SR-LIST-001");
        assertThat(vo.getTalentUid()).isEqualTo("talent_list_001");
        assertThat(vo.getTalentName()).isEqualTo("列表达人");
        assertThat(vo.getTalentFansCount()).isEqualTo(50000L);
        assertThat(vo.getTalentCreditScore()).isEqualTo("4.75");
        assertThat(vo.getTalentMainCategory()).isEqualTo("美妆");
        assertThat(vo.getProductId()).isEqualTo(productId);
        assertThat(vo.getProductExternalId()).isEqualTo("10901825");
        assertThat(vo.getProductName()).isEqualTo("列表测试商品");
        assertThat(vo.getProductCover()).isEqualTo("https://example.test/cover.png");
        assertThat(vo.getProductPriceText()).isEqualTo("¥99.0");
        assertThat(vo.getShopId()).isEqualTo("123456");
        assertThat(vo.getShopName()).isEqualTo("测试店铺");
        assertThat(vo.getQuantity()).isEqualTo(1);
        assertThat(vo.getApplicantUserId()).isNotNull();
        assertThat(vo.getChannelUserId()).isNotNull();
        assertThat(vo.getTrackingNo()).isEqualTo("SF-LIST-123");
        assertThat(vo.getLogisticsCompany()).isEqualTo("SF");
        assertThat(vo.getRecipientName()).isEqualTo("李四");
        assertThat(vo.getRecipientPhone()).isEqualTo("13900139000");
        assertThat(vo.getRecipientAddress()).isEqualTo("北京市朝阳区测试路 2 号");
        assertThat(vo.getStatus()).isEqualTo("PENDING_AUDIT");
        assertThat(vo.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 0));
        assertThat(vo.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 6, 1, 11, 0));
        assertThat(vo.getApplyReason()).isEqualTo("测试申请理由");
        assertThat(vo.getApplySource()).isEqualTo("MANUAL");
        assertThat(vo.getApplySourceLabel()).isEqualTo("手动申请");
        assertThat(vo.getCooperationType()).isEqualTo("FREE_SAMPLE");
        assertThat(vo.getCooperationTypeLabel()).isEqualTo("免费寄样");
        assertThat(vo.getSampleOwnerType()).isEqualTo("MERCHANT");
        assertThat(vo.getSampleOwnerTypeLabel()).isEqualTo("商家");
        assertThat(vo.getHomeworkType()).isEqualTo("HAS_ORDER");
        assertThat(vo.getHomeworkTypeLabel()).isEqualTo("有订单");
    }

    @Test
    void getSampleById_shouldReturnAllExpectedDetailFields() {
        UUID sampleId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setProductId("DETAIL-PRODUCT-001");
        product.setName("详情测试商品");
        product.setCover("https://example.test/detail-cover.png");
        product.setPrice(19900L);

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(productId);
        snapshot.setProductId("DETAIL-SNAPSHOT-001");
        snapshot.setCover("https://example.test/detail-snapshot-cover.png");
        snapshot.setPriceText("¥199.0");
        snapshot.setShopId(789012L);
        snapshot.setShopName("详情测试店铺");

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setProductId(productId);
        sample.setUserId(ownerId);
        sample.setChannelUserId(ownerId);
        sample.setDeptId(deptId);
        sample.setTalentId(UUID.randomUUID());
        sample.setTalentUid("talent_detail_001");
        sample.setTalentNickname("详情达人");
        sample.setTalentFansCount(150000L);
        sample.setTalentCreditScore(new BigDecimal("4.92"));
        sample.setTalentMainCategory("服饰");
        sample.setRequestNo("SR-DETAIL-001");
        sample.setTrackingNo("SF-DETAIL-456");
        sample.setShipperCode("SF");
        sample.setRecipientName("王五");
        sample.setRecipientPhone("13700137000");
        sample.setRecipientAddress("深圳市南山区测试路 3 号");
        sample.setStatus(3);
        sample.setCreateTime(LocalDateTime.of(2026, 5, 15, 9, 0));
        sample.setUpdateTime(LocalDateTime.of(2026, 5, 20, 14, 30));
        sample.setShipTime(LocalDateTime.of(2026, 5, 16, 10, 0));
        sample.setDeliverTime(LocalDateTime.of(2026, 5, 18, 15, 0));
        sample.setRejectReason(null);
        sample.setCloseReason(null);
        sample.setRemark("详情测试备注");
        sample.setExtraData(Map.of(
                "applySource", "INTERNAL_QUICK_SAMPLE",
                "cooperationType", "PAID_SAMPLE",
                "sampleOwnerType", "COLONEL",
                "homeworkType", "VIDEO",
                "applyReason", "详情测试申请理由",
                "eligibilityCheck", Map.of("passed", true, "failedRules", List.of()),
                "requirementSnapshot", Map.of("minLevel", "LV1", "actualLevel", "LV2")
        ));

        UserOptionResponse owner = new UserOptionResponse(ownerId, "owner_user", "负责人", null, List.of(), null);

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productDomainFacade.findProductById(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findOrMaterializeSampleProduct(productId)).thenReturn(toProductRead(product));
        when(productDomainFacade.findSnapshotById(productId)).thenReturn(toSnapshotRead(snapshot));
        when(userDomainFacade.getUserById(ownerId)).thenReturn(owner);

        var response = sampleController.getSampleById(sampleId, ownerId, deptId, DataScope.PERSONAL, null);
        var vo = response.getData();

        assertThat(vo.getId()).isEqualTo(sampleId);
        assertThat(vo.getRequestNo()).isEqualTo("SR-DETAIL-001");
        assertThat(vo.getTalentId()).isNotNull();
        assertThat(vo.getTalentUid()).isEqualTo("talent_detail_001");
        assertThat(vo.getTalentName()).isEqualTo("详情达人");
        assertThat(vo.getTalentFansCount()).isEqualTo(150000L);
        assertThat(vo.getTalentCreditScore()).isEqualTo("4.92");
        assertThat(vo.getTalentMainCategory()).isEqualTo("服饰");
        assertThat(vo.getProductId()).isEqualTo(productId);
        assertThat(vo.getProductExternalId()).isEqualTo("DETAIL-PRODUCT-001");
        assertThat(vo.getProductName()).isEqualTo("详情测试商品");
        assertThat(vo.getProductCover()).isEqualTo("https://example.test/detail-cover.png");
        assertThat(vo.getProductPriceText()).isEqualTo("¥199.0");
        assertThat(vo.getShopId()).isEqualTo("789012");
        assertThat(vo.getShopName()).isEqualTo("详情测试店铺");
        assertThat(vo.getQuantity()).isEqualTo(1);
        assertThat(vo.getApplicantUserId()).isNotNull();
        assertThat(vo.getChannelUserId()).isEqualTo(ownerId);
        assertThat(vo.getChannelUserName()).isEqualTo("负责人 (owner_user)");
        assertThat(vo.getTrackingNo()).isEqualTo("SF-DETAIL-456");
        assertThat(vo.getLogisticsCompany()).isEqualTo("SF");
        assertThat(vo.getRecipientName()).isEqualTo("王五");
        assertThat(vo.getRecipientPhone()).isEqualTo("13700137000");
        assertThat(vo.getRecipientAddress()).isEqualTo("深圳市南山区测试路 3 号");
        assertThat(vo.getStatus()).isEqualTo("SHIPPED");
        assertThat(vo.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 5, 15, 9, 0));
        assertThat(vo.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 5, 20, 14, 30));
        assertThat(vo.getShipTime()).isEqualTo(LocalDateTime.of(2026, 5, 16, 10, 0));
        assertThat(vo.getDeliverTime()).isEqualTo(LocalDateTime.of(2026, 5, 18, 15, 0));
        assertThat(vo.getRejectReason()).isNull();
        assertThat(vo.getCloseReason()).isNull();
        assertThat(vo.getRemark()).isEqualTo("详情测试备注");
        assertThat(vo.getApplyReason()).isEqualTo("详情测试申请理由");
        assertThat(vo.getApplySource()).isEqualTo("INTERNAL_QUICK_SAMPLE");
        assertThat(vo.getApplySourceLabel()).isEqualTo("内部寄样");
        assertThat(vo.getCooperationType()).isEqualTo("PAID_SAMPLE");
        assertThat(vo.getCooperationTypeLabel()).isEqualTo("付费寄样");
        assertThat(vo.getSampleOwnerType()).isEqualTo("COLONEL");
        assertThat(vo.getSampleOwnerTypeLabel()).isEqualTo("团长");
        assertThat(vo.getHomeworkType()).isEqualTo("VIDEO");
        assertThat(vo.getHomeworkTypeLabel()).isEqualTo("VIDEO");
        assertThat(vo.getEligibilityCheck()).containsEntry("passed", true);
        assertThat(vo.getRequirementSnapshot()).containsEntry("actualLevel", "LV2");
    }

    @Test
    void exportSamples_shouldMaintainColumnOrder() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID channelUserId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("导出测试商品");

        UserOptionResponse channelUser = new UserOptionResponse(channelUserId, "export_user", "导出负责人", null, List.of(), null);

        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setRequestNo("SR-EXPORT-001");
        sample.setTalentNickname("导出达人");
        sample.setProductId(productId);
        sample.setStatus(5);
        sample.setChannelUserId(channelUserId);
        sample.setRecipientName("赵六");
        sample.setRecipientPhone("13600136000");
        sample.setRecipientAddress("广州市天河区测试路 4 号");
        sample.setTrackingNo("SF-EXPORT-789");
        sample.setRejectReason("导出驳回原因");
        sample.setRemark("导出备注");
        sample.setCreateTime(LocalDateTime.of(2026, 6, 5, 10, 0));

        Page<SampleRequest> exportPage = new Page<>(1, 500, 1);
        exportPage.setRecords(List.of(sample));

        when(productDomainFacade.loadProductsByIds(any())).thenReturn(java.util.Map.of(productId, toProductRead(product)));
        when(userDomainFacade.getUserById(channelUserId)).thenReturn(channelUser);
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any())).thenReturn(exportPage);

        MockHttpServletResponse response = new MockHttpServletResponse();

        sampleController.exportSamples(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                UUID.randomUUID(), null, DataScope.ALL, List.of(RoleCodes.ADMIN), response);

        String content = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = content.split("\n");
        assertThat(lines.length).isEqualTo(2);

        String header = lines[0].replace("\ufeff", "").trim();
        assertThat(header).isEqualTo("寄样单号,达人昵称,商品名称,状态,招商负责人,收件人,收件电话,收件地址,物流单号,驳回原因,备注,创建时间");

        String dataLine = lines[1].trim();
        assertThat(dataLine).contains("SR-EXPORT-001");
        assertThat(dataLine).contains("导出达人");
        assertThat(dataLine).contains("导出测试商品");
        assertThat(dataLine).contains("PENDING_HOMEWORK");
        assertThat(dataLine).contains("导出负责人 (export_user)");
        assertThat(dataLine).contains("赵六");
        assertThat(dataLine).contains("13600136000");
        assertThat(dataLine).contains("广州市天河区测试路 4 号");
        assertThat(dataLine).contains("SF-EXPORT-789");
        assertThat(dataLine).contains("导出驳回原因");
        assertThat(dataLine).contains("导出备注");
        assertThat(dataLine).contains("2026-06-05");
    }

    private com.colonel.saas.common.result.ApiResult<com.colonel.saas.common.result.PageResult<com.colonel.saas.vo.sample.SampleVO>> getSamplePageBasic(
            long page,
            long size,
            String keyword,
            String status,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes) {
        return sampleController.getSamplePage(
                page, size, keyword, status,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null,
                userId, deptId, dataScope, roleCodes);
    }

    private void exportSamplesBasic(
            String status,
            String keyword,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes,
            MockHttpServletResponse response) throws Exception {
        sampleController.exportSamples(
                status, keyword,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null,
                userId, deptId, dataScope, roleCodes, response);
    }

    private static ProductReadDTO toProductRead(Product product) {
        if (product == null) {
            return null;
        }
        return new ProductReadDTO(
                product.getId(),
                product.getProductId(),
                product.getOuterProductId(),
                product.getName(),
                product.getCover(),
                product.getPrice(),
                product.getActivityId(),
                product.getDetailUrl(),
                product.getStatus(),
                product.getCheckStatus());
    }

    private static ProductSnapshotReadDTO toSnapshotRead(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new ProductSnapshotReadDTO(
                snapshot.getId(),
                snapshot.getActivityId(),
                snapshot.getProductId(),
                snapshot.getTitle(),
                snapshot.getCover(),
                snapshot.getShopId(),
                snapshot.getShopName(),
                snapshot.getPrice(),
                snapshot.getPriceText(),
                snapshot.getStatus(),
                snapshot.getDetailUrl());
    }

    private static TalentReadDTO toTalentRead(Talent talent) {
        if (talent == null) {
            return null;
        }
        return new TalentReadDTO(
                talent.getId(),
                talent.getDouyinUid(),
                talent.getDouyinNo(),
                talent.getNickname(),
                talent.getFans(),
                talent.getStatus(),
                talent.getAvatarUrl(),
                talent.getMainCategory(),
                talent.getCategories(),
                talent.getIpLocation());
    }

}
