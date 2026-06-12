package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributionServiceTest {

    @Mock
    private PickSourceMappingMapper pickSourceMappingMapper;
    @Mock
    private ProductOperationStateMapper productOperationStateMapper;
    @Mock
    private TalentMapper talentMapper;
    @Mock
    private TalentClaimMapper talentClaimMapper;
    @Mock
    private ExclusiveTalentService exclusiveTalentService;
    @Mock
    private ExclusiveMerchantService exclusiveMerchantService;

    private AttributionService service;

    @BeforeEach
    void setUp() {
        service = new AttributionService(
                pickSourceMappingMapper,
                productOperationStateMapper,
                talentMapper,
                talentClaimMapper,
                exclusiveTalentService,
                exclusiveMerchantService,
                false,
                null
        );
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        lenient().when(talentMapper.selectOne(any())).thenReturn(talent);
    }

    private AttributionService attributionService(boolean exclusiveEnabled) {
        return new AttributionService(
                pickSourceMappingMapper,
                productOperationStateMapper,
                talentMapper,
                talentClaimMapper,
                exclusiveTalentService,
                exclusiveMerchantService,
                exclusiveEnabled,
                null
        );
    }

    @Test
    void resolveAttribution_shouldUseExclusiveMerchantFirst() {
        service = attributionService(true);
        UUID merchantOwner = UUID.randomUUID();
        UUID merchantDept = UUID.randomUUID();
        when(exclusiveMerchantService.findActiveOwnerByMerchantId("m-1"))
                .thenReturn(new AttributionService.ExclusiveOwner(merchantOwner, merchantDept));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-1");
        order.setPickSource("ps_1");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("merchant_id", "m-1", "talent_uid", "t-1")
        );

        assertThat(result.userId()).isEqualTo(merchantOwner);
        assertThat(result.deptId()).isEqualTo(merchantDept);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        verify(exclusiveTalentService, never()).findActiveOwnerByTalentUid(any());
        verify(pickSourceMappingMapper, never()).selectOne(any());
    }

    @Test
    void resolveAttribution_shouldUseExclusiveTalentWhenMerchantNotMatched() {
        service = attributionService(true);
        UUID talentOwner = UUID.randomUUID();
        UUID talentDept = UUID.randomUUID();
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        when(exclusiveTalentService.findActiveOwnerByTalentUid("t-2"))
                .thenReturn(new AttributionService.ExclusiveOwner(talentOwner, talentDept));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-2");
        order.setPickSource("ps_2");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("talent_uid", "t-2")
        );

        assertThat(result.userId()).isEqualTo(talentOwner);
        assertThat(result.deptId()).isEqualTo(talentDept);
        assertThat(result.talentUid()).isEqualTo("t-2");
        verify(pickSourceMappingMapper, never()).selectOne(any());
    }

    @Test
    void resolveAttribution_shouldUseAuthorBuyinIdForExclusiveTalent() {
        service = attributionService(true);
        UUID talentOwner = UUID.randomUUID();
        UUID talentDept = UUID.randomUUID();
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        when(exclusiveTalentService.findActiveOwnerByTalentUid("7137334329718292775"))
                .thenReturn(new AttributionService.ExclusiveOwner(talentOwner, talentDept));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("3745254399715443024");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("author_buyin_id", "7137334329718292775")
        );

        assertThat(result.userId()).isEqualTo(talentOwner);
        assertThat(result.deptId()).isEqualTo(talentDept);
        assertThat(result.talentUid()).isEqualTo("7137334329718292775");
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        verify(pickSourceMappingMapper, never()).selectOne(any());
    }

    @Test
    void resolveAttribution_shouldSkipExclusiveOwnersByDefaultAndUsePickSourceMapping() {
        UUID exclusiveOwner = UUID.randomUUID();
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(mappingUser);
        mapping.setDeptId(mappingDept);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId("merchant-v1"))
                .thenReturn(new AttributionService.ExclusiveOwner(exclusiveOwner, UUID.randomUUID()));
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(mapping);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-v1");
        order.setPickSource("pick-v1");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("merchant_id", "merchant-v1")
        );

        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.deptId()).isEqualTo(mappingDept);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        verify(exclusiveMerchantService, never()).findActiveOwnerByMerchantId(any());
        verify(exclusiveTalentService, never()).findActiveOwnerByTalentUid(any());
    }

    @Test
    void resolveAttribution_shouldUseNativeColonelBuyinMappingWhenPickSourceMissing() {
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        UUID colonelUserId = UUID.randomUUID();
        ProductOperationState state = new ProductOperationState();
        state.setAssigneeId(colonelUserId);
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(mappingUser);
        mapping.setDeptId(mappingDept);
        when(productOperationStateMapper.selectOne(any())).thenReturn(state);
        when(pickSourceMappingMapper.selectList(any())).thenReturn(List.of(mapping));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-native");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of(
                        "colonel_buyin_id", "7351155267604218149",
                        "colonel_activity_id", "3916506"
                )
        );

        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.deptId()).isEqualTo(mappingDept);
        assertThat(result.channelUserId()).isEqualTo(mappingUser);
        assertThat(result.colonelUserId()).isEqualTo(colonelUserId);
        assertThat(result.activityId()).isEqualTo("3916506");
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_COLONEL_ORDER_INFO);
    }

    @Test
    void resolveAttribution_shouldNotUseShortIdLookupForNativeColonelBuyinId() {
        UUID mappingUser = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(mappingUser);
        mapping.setDeptId(UUID.randomUUID());
        when(pickSourceMappingMapper.selectList(any())).thenReturn(List.of(mapping));
        AttributionService serviceRejectingShortIdLookup = new AttributionService(
                pickSourceMappingMapper,
                productOperationStateMapper,
                talentMapper,
                talentClaimMapper,
                exclusiveTalentService,
                exclusiveMerchantService
        ) {
            @Override
            protected PickSourceMapping findPickSourceMappingByShortId(String colonelsBuyinId) {
                throw new AssertionError("colonel_buyin_id must not be queried through short_id");
            }
        };

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-native");

        AttributionService.AttributionResult result = serviceRejectingShortIdLookup.resolveAttribution(
                order,
                Map.of(
                        "colonel_buyin_id", "7351155267604218149",
                        "colonel_activity_id", "3916506"
                )
        );

        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_COLONEL_ORDER_INFO);
    }

    @Test
    void resolveAttribution_shouldRemainUnattributedWhenColonelBuyinIdHasNoMapping() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-native-missing");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("colonelBuyinId", "7351155267604218149")
        );

        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_UNATTRIBUTED);
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND);
        assertThat(result.userId()).isNull();
    }

    @Test
    void resolveAttribution_shouldFallbackToActivityProductMappingForNativeOrder() {
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(mappingUser);
        mapping.setDeptId(mappingDept);
        when(pickSourceMappingMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(mapping));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-native");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of(
                        "colonel_buyin_id", "7351155267604218149",
                        "colonel_activity_id", "3916506"
                )
        );

        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.deptId()).isEqualTo(mappingDept);
        assertThat(result.activityId()).isEqualTo("3916506");
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_COLONEL_ORDER_INFO);
        verify(pickSourceMappingMapper, times(2)).selectList(any());
    }

    @Test
    void resolveAttribution_shouldUseSecondColonelOrderInfoWhenPrimaryActivityMissing() {
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        PickSourceMapping secondMapping = new PickSourceMapping();
        secondMapping.setUserId(mappingUser);
        secondMapping.setDeptId(mappingDept);
        secondMapping.setActivityId("3543332");
        when(pickSourceMappingMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(secondMapping));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("3633722889687181734");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of(
                        "colonel_buyin_id", "7392822694083707171",
                        "second_colonel_buyin_id", "7351155267604218149",
                        "second_colonel_activity_id", "3543332"
                )
        );

        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.deptId()).isEqualTo(mappingDept);
        assertThat(result.activityId()).isEqualTo("3543332");
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_COLONEL_ORDER_INFO);
    }

    @Test
    void resolveAttribution_shouldNotFallbackToGenericSeedWhenSecondActivityExistsButExactMappingMissing() {
        UUID adminUser = UUID.randomUUID();
        PickSourceMapping genericSeed = new PickSourceMapping();
        genericSeed.setUserId(adminUser);
        genericSeed.setScene("COLONEL_NATIVE");
        when(pickSourceMappingMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(genericSeed));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("3633722889687181734");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of(
                        "colonel_buyin_id", "7392822694083707171",
                        "second_colonel_buyin_id", "7351155267604218149",
                        "second_colonel_activity_id", "3543332"
                )
        );

        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_UNATTRIBUTED);
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND);
        assertThat(result.userId()).isNull();
    }

    @Test
    void resolveAttribution_shouldNotFallbackWhenActivityProductMappingIsAmbiguous() {
        PickSourceMapping first = new PickSourceMapping();
        first.setUserId(UUID.randomUUID());
        PickSourceMapping second = new PickSourceMapping();
        second.setUserId(UUID.randomUUID());
        when(pickSourceMappingMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(first, second));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-native");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of(
                        "colonel_buyin_id", "7351155267604218149",
                        "colonel_activity_id", "3916506"
                )
        );

        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_UNATTRIBUTED);
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_COLONEL_MAPPING_AMBIGUOUS);
        assertThat(result.userId()).isNull();
    }

    @Test
    void resolveAttribution_shouldFallbackToPickSourceMapping() {
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(mappingUser);
        mapping.setDeptId(mappingDept);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(mapping);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-3");
        order.setPickSource("pick_x");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("pick_extra", "short_123")
        );

        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.deptId()).isEqualTo(mappingDept);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
    }

    @Test
    void resolveAttribution_shouldRejectMappingWhenTalentClaimOwnerDiffers() {
        UUID talentId = UUID.randomUUID();
        UUID mappingUser = UUID.randomUUID();
        UUID claimOwner = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(mappingUser);
        mapping.setDeptId(UUID.randomUUID());
        TalentClaim activeClaim = new TalentClaim();
        activeClaim.setTalentId(talentId);
        activeClaim.setUserId(claimOwner);
        activeClaim.setStatus(1);

        when(talentMapper.selectOne(any())).thenReturn(talent);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(mapping);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(activeClaim));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-claim-conflict");
        order.setPickSource("pick_claim_conflict");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("talent_uid", "talent-claim-conflict")
        );

        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_UNATTRIBUTED);
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_TALENT_CLAIM_OWNER_CONFLICT);
        assertThat(result.userId()).isNull();
        assertThat(result.channelUserId()).isNull();
        assertThat(result.talentId()).isEqualTo(talentId);
    }

    @Test
    void resolveAttribution_shouldAcceptMappingWhenTalentClaimOwnerMatches() {
        UUID talentId = UUID.randomUUID();
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(mappingUser);
        mapping.setDeptId(mappingDept);
        TalentClaim activeClaim = new TalentClaim();
        activeClaim.setTalentId(talentId);
        activeClaim.setUserId(mappingUser);
        activeClaim.setStatus(1);

        when(talentMapper.selectOne(any())).thenReturn(talent);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(mapping);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(activeClaim));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-claim-match");
        order.setPickSource("pick_claim_match");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("talent_uid", "talent-claim-match")
        );

        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.channelUserId()).isEqualTo(mappingUser);
        assertThat(result.deptId()).isEqualTo(mappingDept);
    }

    @Test
    void resolveAttribution_shouldPreferExactActivityProductWhenPickSourceIsReused() {
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        PickSourceMapping exact = new PickSourceMapping();
        exact.setUserId(mappingUser);
        exact.setDeptId(mappingDept);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(exact);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-reused");
        order.setPickSource("v.MxZLIw");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of(
                        "activity_id", "act-reused",
                        "pick_extra", "channel_user"
                )
        );

        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.deptId()).isEqualTo(mappingDept);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
    }

    @Test
    void resolveAttribution_shouldPreferExactPickExtraBeforeLegacyShortIdFallback() {
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(mappingUser);
        mapping.setDeptId(mappingDept);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(mapping);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-3");
        order.setPickSource("pick_x");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("pick_extra", "channel_user-1")
        );

        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.deptId()).isEqualTo(mappingDept);
        verify(pickSourceMappingMapper, times(2)).selectOne(any());
    }

    @Test
    void resolveAttribution_shouldExposeColonelOwnerFromProductState() {
        UUID colonelUserId = UUID.randomUUID();
        ProductOperationState state = new ProductOperationState();
        state.setAssigneeId(colonelUserId);
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(UUID.randomUUID());
        mapping.setDeptId(UUID.randomUUID());
        when(productOperationStateMapper.selectOne(any())).thenReturn(state);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(mapping);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-4");
        order.setActivityId("act-4");
        order.setPickSource("pick_4");

        AttributionService.AttributionResult result = service.resolveAttribution(order, Map.of());

        assertThat(result.colonelUserId()).isEqualTo(colonelUserId);
    }

    @Test
    void resolveAttribution_shouldReturnUnattributedWhenMappingMissing() {
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-5");
        order.setPickSource("unknown");

        AttributionService.AttributionResult result = service.resolveAttribution(order, Map.of());

        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_UNATTRIBUTED);
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_MAPPING_NOT_FOUND);
        assertThat(result.userId()).isNull();
    }

    @Test
    void resolveAttribution_shouldReturnAmbiguousWhenNativeKeyMapsToMultipleUsers() {
        PickSourceMapping first = new PickSourceMapping();
        first.setUserId(UUID.randomUUID());
        first.setColonelBuyinId("7293293346398011698");
        first.setActivityId("3859423");
        first.setProductId("3816127512791089531");
        first.setSourceType(PickSourceMappingService.SOURCE_TYPE_NATIVE);
        PickSourceMapping second = new PickSourceMapping();
        second.setUserId(UUID.randomUUID());
        second.setColonelBuyinId("7293293346398011698");
        second.setActivityId("3859423");
        second.setProductId("3816127512791089531");
        second.setSourceType(PickSourceMappingService.SOURCE_TYPE_NATIVE);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingMapper.selectList(any())).thenReturn(java.util.List.of(first, second));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("3816127512791089531");
        order.setActivityId("3859423");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("colonel_buyin_id", "7293293346398011698")
        );

        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_UNATTRIBUTED);
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_COLONEL_MAPPING_AMBIGUOUS);
        assertThat(result.nativeTrace().ambiguousMapping()).isTrue();
    }

    @Test
    void resolveAttribution_shouldUseActivityProductFallbackAndMarkBuyinMismatch() {
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(mappingUser);
        mapping.setDeptId(mappingDept);
        mapping.setColonelBuyinId("7351155267604218149");
        mapping.setActivityId("3859423");
        mapping.setProductId("3816127512791089531");
        mapping.setSourceType(PickSourceMappingService.SOURCE_TYPE_NATIVE);
        mapping.setCreateTime(java.time.LocalDateTime.of(2026, 5, 10, 6, 41, 19));
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingMapper.selectList(any()))
                .thenReturn(java.util.List.of())
                .thenReturn(java.util.List.of(mapping));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("3816127512791089531");
        order.setActivityId("3859423");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("colonel_buyin_id", "7293293346398011698")
        );

        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_COLONEL_ORDER_INFO);
        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.nativeTrace().nativeKeyMatched()).isTrue();
        assertThat(result.nativeTrace().usedActivityProductFallback()).isTrue();
        assertThat(result.nativeTrace().colonelBuyinIdMismatch()).isTrue();
    }
}
