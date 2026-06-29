package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.domain.product.facade.dto.PickSourceAttributionMappingDTO;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.mapper.ProductOperationStateMapper;
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
    private PickSourceMappingService pickSourceMappingService;
    @Mock
    private ProductOperationStateMapper productOperationStateMapper;
    @Mock
    private TalentDomainFacade talentDomainFacade;
    @Mock
    private ExclusiveTalentService exclusiveTalentService;
    @Mock
    private ExclusiveMerchantService exclusiveMerchantService;

    private AttributionService service;

    @BeforeEach
    void setUp() {
        service = new AttributionService(
                pickSourceMappingService,
                productOperationStateMapper,
                talentDomainFacade,
                exclusiveTalentService,
                exclusiveMerchantService,
                false
        );
        lenient().when(talentDomainFacade.findByDouyinUid(any()))
                .thenReturn(new TalentReadDTO(UUID.randomUUID(), "talent-uid", null, null, null, null, null, null, null, null));
    }

    private AttributionService attributionService(boolean exclusiveEnabled) {
        return new AttributionService(
                pickSourceMappingService,
                productOperationStateMapper,
                talentDomainFacade,
                exclusiveTalentService,
                exclusiveMerchantService,
                exclusiveEnabled
        );
    }

    private PickSourceAttributionMappingDTO mapping(UUID userId, UUID deptId) {
        return new PickSourceAttributionMappingDTO(userId, deptId, null, null, null, null, null, null);
    }

    private PickSourceAttributionMappingDTO nativeMapping(
            UUID userId,
            UUID deptId,
            String activityId,
            String productId,
            String colonelBuyinId) {
        return new PickSourceAttributionMappingDTO(
                userId,
                deptId,
                activityId,
                productId,
                colonelBuyinId,
                PickSourceMappingService.SOURCE_TYPE_NATIVE,
                null,
                null);
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
        verify(pickSourceMappingService, never()).findActiveAttributionMapping(any(), any());
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
        verify(pickSourceMappingService, never()).findActiveAttributionMapping(any(), any());
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
        verify(pickSourceMappingService, never()).findActiveAttributionMapping(any(), any());
    }

    @Test
    void resolveAttribution_shouldSkipExclusiveOwnersByDefaultAndUsePickSourceMapping() {
        UUID exclusiveOwner = UUID.randomUUID();
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        PickSourceAttributionMappingDTO mapping = mapping(mappingUser, mappingDept);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId("merchant-v1"))
                .thenReturn(new AttributionService.ExclusiveOwner(exclusiveOwner, UUID.randomUUID()));
        when(pickSourceMappingService.findActiveAttributionMapping(any(), any())).thenReturn(mapping);

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
        PickSourceAttributionMappingDTO mapping = nativeMapping(mappingUser, mappingDept, null, "pid-native", "7351155267604218149");
        when(productOperationStateMapper.selectOne(any())).thenReturn(state);
        when(pickSourceMappingService.findNativeAttributionMappings(any(), any(), any())).thenReturn(List.of(mapping));

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
        PickSourceAttributionMappingDTO mapping = nativeMapping(mappingUser, UUID.randomUUID(), null, "pid-native", "7351155267604218149");
        when(pickSourceMappingService.findNativeAttributionMappings(any(), any(), any())).thenReturn(List.of(mapping));
        AttributionService serviceRejectingShortIdLookup = new AttributionService(
                pickSourceMappingService,
                productOperationStateMapper,
                talentDomainFacade,
                exclusiveTalentService,
                exclusiveMerchantService
        ) {
            @Override
            protected PickSourceAttributionMappingDTO findPickSourceMappingByShortId(String colonelsBuyinId) {
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
        PickSourceAttributionMappingDTO mapping = nativeMapping(mappingUser, mappingDept, null, "pid-native", "7351155267604218149");
        when(pickSourceMappingService.findNativeAttributionMappings(any(), any(), any())).thenReturn(List.of());
        when(pickSourceMappingService.findNativeAttributionMappingsByActivityProduct(any(), any())).thenReturn(List.of(mapping));

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
        verify(pickSourceMappingService, times(1)).findNativeAttributionMappings(any(), any(), any());
        verify(pickSourceMappingService, times(1)).findNativeAttributionMappingsByActivityProduct(any(), any());
    }

    @Test
    void resolveAttribution_shouldUseSecondColonelOrderInfoWhenPrimaryActivityMissing() {
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        PickSourceAttributionMappingDTO secondMapping =
                nativeMapping(mappingUser, mappingDept, "3543332", "3633722889687181734", "7351155267604218149");
        when(pickSourceMappingService.findNativeAttributionMappings(any(), any(), any())).thenReturn(List.of(secondMapping));

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
        when(pickSourceMappingService.findNativeAttributionMappings(any(), any(), any())).thenReturn(List.of());
        when(pickSourceMappingService.findNativeAttributionMappingsByActivityProduct(any(), any())).thenReturn(List.of());

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
        verify(pickSourceMappingService, never()).findNativeAttributionMappingsByColonelBuyinId(any());
    }

    @Test
    void resolveAttribution_shouldNotFallbackWhenActivityProductMappingIsAmbiguous() {
        PickSourceAttributionMappingDTO first = mapping(UUID.randomUUID(), UUID.randomUUID());
        PickSourceAttributionMappingDTO second = mapping(UUID.randomUUID(), UUID.randomUUID());
        when(pickSourceMappingService.findNativeAttributionMappings(any(), any(), any())).thenReturn(List.of());
        when(pickSourceMappingService.findNativeAttributionMappingsByActivityProduct(any(), any()))
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
        PickSourceAttributionMappingDTO mapping = mapping(mappingUser, mappingDept);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingService.findActiveAttributionMapping(any(), any())).thenReturn(mapping);

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
        PickSourceAttributionMappingDTO mapping = mapping(mappingUser, UUID.randomUUID());

        when(talentDomainFacade.findByDouyinUid("talent-claim-conflict"))
                .thenReturn(new TalentReadDTO(talentId, "talent-claim-conflict", null, null, null, null, null, null, null, null));
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingService.findActiveAttributionMapping(any(), any())).thenReturn(mapping);
        when(talentDomainFacade.hasActiveClaimOwnerConflict(talentId, mappingUser)).thenReturn(true);

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
        PickSourceAttributionMappingDTO mapping = mapping(mappingUser, mappingDept);

        when(talentDomainFacade.findByDouyinUid("talent-claim-match"))
                .thenReturn(new TalentReadDTO(talentId, "talent-claim-match", null, null, null, null, null, null, null, null));
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingService.findActiveAttributionMapping(any(), any())).thenReturn(mapping);
        when(talentDomainFacade.hasActiveClaimOwnerConflict(talentId, mappingUser)).thenReturn(false);

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
        PickSourceAttributionMappingDTO exact = mapping(mappingUser, mappingDept);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingService.findActiveAttributionMapping(any(), any())).thenReturn(exact);

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
        PickSourceAttributionMappingDTO mapping = mapping(mappingUser, mappingDept);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingService.findActiveAttributionMapping(any(), any())).thenReturn(mapping);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-3");
        order.setPickSource("pick_x");

        AttributionService.AttributionResult result = service.resolveAttribution(
                order,
                Map.of("pick_extra", "channel_user-1")
        );

        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.deptId()).isEqualTo(mappingDept);
        verify(pickSourceMappingService, times(1)).findActiveAttributionMapping(any(), any());
    }

    @Test
    void resolveAttribution_shouldExposeColonelOwnerFromProductState() {
        UUID colonelUserId = UUID.randomUUID();
        ProductOperationState state = new ProductOperationState();
        state.setAssigneeId(colonelUserId);
        PickSourceAttributionMappingDTO mapping = mapping(UUID.randomUUID(), UUID.randomUUID());
        when(productOperationStateMapper.selectOne(any())).thenReturn(state);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingService.findActiveAttributionMapping(any(), any())).thenReturn(mapping);

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
        when(pickSourceMappingService.findActiveAttributionMapping(any(), any())).thenReturn(null);

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
        PickSourceAttributionMappingDTO first = nativeMapping(
                UUID.randomUUID(), UUID.randomUUID(), "3859423", "3816127512791089531", "7293293346398011698");
        PickSourceAttributionMappingDTO second = nativeMapping(
                UUID.randomUUID(), UUID.randomUUID(), "3859423", "3816127512791089531", "7293293346398011698");
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingService.findNativeAttributionMappings(any(), any(), any()))
                .thenReturn(java.util.List.of(first, second));

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
        PickSourceAttributionMappingDTO mapping = new PickSourceAttributionMappingDTO(
                mappingUser,
                mappingDept,
                "3859423",
                "3816127512791089531",
                "7351155267604218149",
                PickSourceMappingService.SOURCE_TYPE_NATIVE,
                java.time.LocalDateTime.of(2026, 5, 10, 6, 41, 19),
                null);
        lenient().when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        lenient().when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingService.findNativeAttributionMappings(any(), any(), any())).thenReturn(java.util.List.of());
        when(pickSourceMappingService.findNativeAttributionMappingsByActivityProduct(any(), any()))
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
