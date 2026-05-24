package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.product.QuickSampleApplyRequest;
import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickSampleApplyTest {

    @Mock private ProductService productService;
    @Mock private ProductSnapshotMapper productSnapshotMapper;
    @Mock private ProductOperationStateMapper productOperationStateMapper;
    @Mock private SampleRequestMapper sampleRequestMapper;
    @Mock private TalentMapper talentMapper;
    @Mock private TalentClaimMapper talentClaimMapper;
    @Mock private CrawlerTalentInfoService crawlerTalentInfoService;
    @Mock private SampleEligibilityService sampleEligibilityService;
    @Mock private BusinessRuleConfigService businessRuleConfigService;
    @Mock private SampleStatusLogService sampleStatusLogService;
    @Mock private DouyinQuickSampleGateway douyinQuickSampleGateway;
    @Mock private com.colonel.saas.domain.sample.event.SampleDomainEventPublisher sampleDomainEventPublisher;

    private ProductQuickSampleService service;

    @BeforeEach
    void setUp() {
        service = new ProductQuickSampleService(
                productService,
                productSnapshotMapper,
                productOperationStateMapper,
                sampleRequestMapper,
                talentMapper,
                talentClaimMapper,
                crawlerTalentInfoService,
                sampleEligibilityService,
                businessRuleConfigService,
                sampleStatusLogService,
                douyinQuickSampleGateway,
                sampleDomainEventPublisher,
                false
        );
        org.mockito.Mockito.lenient().when(douyinQuickSampleGateway.isSupported()).thenReturn(false);
        org.mockito.Mockito.lenient().when(douyinQuickSampleGateway.supportStatus())
                .thenReturn(DouyinQuickSampleGateway.SupportStatus.UNSUPPORTED_BY_SDK);
    }

    @Test
    void applyQuickSample_shouldRejectNonChannelRole() {
        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("talent_001"));

        assertThatThrownBy(() -> service.applyQuickSample(
                UUID.randomUUID(), request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void applyQuickSample_shouldThrowWhenProductNotFound() {
        UUID relationId = UUID.randomUUID();
        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        when(productService.getById(relationId)).thenReturn(null);

        assertThatThrownBy(() -> service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    void applyQuickSample_shouldThrowWhenProductNotDisplaying() {
        UUID relationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setProductId("9001");

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(relationId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");

        ProductOperationState state = new ProductOperationState();
        state.setDisplayStatus(ProductDisplayStatus.HIDDEN.name());

        when(productService.getById(relationId)).thenReturn(product);
        when(productSnapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        assertThatThrownBy(() -> service.applyQuickSample(
                relationId, request, userId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("仅展示中的商品可发起快速寄样");
    }

    @Test
    void applyQuickSample_shouldThrowWhenSampleRestrictEnabledAndWithinSevenDays() {
        UUID relationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setProductId("9001");

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(relationId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");

        ProductOperationState state = new ProductOperationState();
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());

        CrawlerTalentInfo talentInfo = new CrawlerTalentInfo();
        talentInfo.setTalentId("douyin_talent_001");
        talentInfo.setNickname("达人A");

        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("douyin_talent_001");

        when(productService.getById(relationId)).thenReturn(product);
        when(productSnapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);
        when(crawlerTalentInfoService.findByTalentId("douyin_talent_001")).thenReturn(talentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talent.getId(), userId))
                .thenReturn(new com.colonel.saas.entity.TalentClaim());
        when(businessRuleConfigService.isSampleRestrictEnabled()).thenReturn(true);
        when(businessRuleConfigService.getSampleRestrictDays()).thenReturn(7);
        when(sampleRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        var response = service.applyQuickSample(
                relationId, request, userId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.getFailureCount()).isEqualTo(1);
        assertThat(response.getItems().get(0).getMessage()).contains("Duplicate sample request is blocked");
    }

    @Test
    void applyQuickSample_shouldThrowWhenEligibilityFailsAndNoRemark() {
        UUID relationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setProductId("9001");

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(relationId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");

        ProductOperationState state = new ProductOperationState();
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());

        CrawlerTalentInfo talentInfo = new CrawlerTalentInfo();
        talentInfo.setTalentId("douyin_talent_001");
        talentInfo.setNickname("达人A");

        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("douyin_talent_001");

        when(productService.getById(relationId)).thenReturn(product);
        when(productSnapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);
        when(crawlerTalentInfoService.findByTalentId("douyin_talent_001")).thenReturn(talentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talent.getId(), userId))
                .thenReturn(new com.colonel.saas.entity.TalentClaim());
        when(businessRuleConfigService.isSampleRestrictEnabled()).thenReturn(false);
        when(sampleEligibilityService.evaluate(any(), any())).thenReturn(
                new SampleEligibilityService.EligibilityResult(false, List.of("粉丝数不足"), null, null));

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));
        request.setRemark(null);

        var response = service.applyQuickSample(
                relationId, request, userId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.getFailureCount()).isEqualTo(1);
        assertThat(response.getItems().get(0).getMessage()).contains("达人未满足默认寄样标准");
    }

    @Test
    void applyQuickSample_shouldCreateSampleWithQuickProductLibrarySource() {
        UUID relationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setProductId("9001");

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(relationId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");

        ProductOperationState state = new ProductOperationState();
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());

        CrawlerTalentInfo talentInfo = new CrawlerTalentInfo();
        talentInfo.setTalentId("douyin_talent_001");
        talentInfo.setNickname("达人A");

        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("douyin_talent_001");

        when(productService.getById(relationId)).thenReturn(product);
        when(productSnapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);
        when(crawlerTalentInfoService.findByTalentId("douyin_talent_001")).thenReturn(talentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talent.getId(), userId))
                .thenReturn(new com.colonel.saas.entity.TalentClaim());
        when(businessRuleConfigService.isSampleRestrictEnabled()).thenReturn(false);
        when(sampleEligibilityService.evaluate(any(), any())).thenReturn(
                new SampleEligibilityService.EligibilityResult(true, List.of(), null, null));
        when(sampleRequestMapper.insert(any(SampleRequest.class))).thenReturn(1);

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));
        request.setQuantity(2);
        request.setSpecification("红色/L");
        request.setRecipientAddress("上海");

        var response = service.applyQuickSample(
                relationId, request, userId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getItems()).singleElement()
                .satisfies(item -> assertThat(item.isSuccess()).isTrue());
    }

    @Test
    void applyQuickSample_shouldUseLocalFallbackWhenGatewayUnsupported() {
        UUID relationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setProductId("9001");

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(relationId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");

        ProductOperationState state = new ProductOperationState();
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());

        CrawlerTalentInfo talentInfo = new CrawlerTalentInfo();
        talentInfo.setTalentId("douyin_talent_001");
        talentInfo.setNickname("达人A");

        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("douyin_talent_001");

        when(productService.getById(relationId)).thenReturn(product);
        when(productSnapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);
        when(crawlerTalentInfoService.findByTalentId("douyin_talent_001")).thenReturn(talentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talent.getId(), userId))
                .thenReturn(new com.colonel.saas.entity.TalentClaim());
        when(businessRuleConfigService.isSampleRestrictEnabled()).thenReturn(false);
        when(sampleEligibilityService.evaluate(any(), any())).thenReturn(
                new SampleEligibilityService.EligibilityResult(true, List.of(), null, null));
        when(sampleRequestMapper.insert(any(SampleRequest.class))).thenReturn(1);
        when(douyinQuickSampleGateway.isSupported()).thenReturn(false);

        ProductQuickSampleService enabledService = new ProductQuickSampleService(
                productService,
                productSnapshotMapper,
                productOperationStateMapper,
                sampleRequestMapper,
                talentMapper,
                talentClaimMapper,
                crawlerTalentInfoService,
                sampleEligibilityService,
                businessRuleConfigService,
                sampleStatusLogService,
                douyinQuickSampleGateway,
                sampleDomainEventPublisher,
                true);

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));
        request.setQuantity(1);
        request.setRemark("测试备注");

        var response = enabledService.applyQuickSample(
                relationId, request, userId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.isExternalSupported()).isFalse();
        assertThat(response.getItems()).singleElement().satisfies(item -> {
            assertThat(item.isSuccess()).isTrue();
            assertThat(item.isExternalApplied()).isFalse();
            assertThat(item.isFallback()).isTrue();
            assertThat(item.getExternalApplyId()).isNull();
            assertThat(item.getGatewayStatus()).isEqualTo(ProductQuickSampleService.GATEWAY_STATUS_UNSUPPORTED);
            assertThat(item.getFallbackType()).isEqualTo(ProductQuickSampleService.FALLBACK_TYPE_LOCAL);
            assertThat(item.getMessage()).contains("系统内寄样申请");
        });
    }

    @Test
    void applyQuickSample_shouldCollectPartialFailures() {
        UUID relationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setProductId("9001");

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(relationId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");

        ProductOperationState state = new ProductOperationState();
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());

        CrawlerTalentInfo talentInfo = new CrawlerTalentInfo();
        talentInfo.setTalentId("douyin_talent_001");
        talentInfo.setNickname("达人A");

        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("douyin_talent_001");

        when(productService.getById(relationId)).thenReturn(product);
        when(productSnapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);
        when(crawlerTalentInfoService.findByTalentId("douyin_talent_001")).thenReturn(talentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentAndUser(talent.getId(), userId))
                .thenReturn(new com.colonel.saas.entity.TalentClaim());
        when(businessRuleConfigService.isSampleRestrictEnabled()).thenReturn(false);
        when(sampleEligibilityService.evaluate(any(), any())).thenReturn(
                new SampleEligibilityService.EligibilityResult(false, List.of("粉丝数不足"), null, null));
        when(sampleRequestMapper.insert(any(SampleRequest.class))).thenReturn(1);

        // Second talent causes failure by not being found
        when(crawlerTalentInfoService.findByTalentId("invalid_talent")).thenReturn(null);

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001", "invalid_talent"));
        request.setRemark("申请原因");

        var response = service.applyQuickSample(
                relationId, request, userId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailureCount()).isEqualTo(1);
    }
}
