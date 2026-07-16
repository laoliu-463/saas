package com.colonel.saas.domain.order.application;

import com.colonel.saas.domain.order.infrastructure.OrderPickSourceMappingAdapter;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionResult;
import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution;
import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution.Status;
import com.colonel.saas.domain.product.facade.ProductDomainFacade;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;
import com.colonel.saas.domain.shared.attribution.AttributionSource;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.AttributionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDefaultAttributionResolverTest {

    @Mock
    private OrderPickSourceMappingAdapter pickSourceMappingAdapter;
    @Mock
    private ProductDomainFacade productDomainFacade;
    @Mock
    private TalentDomainFacade talentDomainFacade;

    private OrderDefaultAttributionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OrderDefaultAttributionResolver(
                pickSourceMappingAdapter,
                productDomainFacade,
                talentDomainFacade);
    }

    @Test
    void resolveShouldUseRecruiterLinkBeforeActivityRecruiter() {
        ColonelsettlementOrder order = baseOrder();
        UUID linkRecruiter = UUID.randomUUID();
        UUID activityRecruiter = UUID.randomUUID();
        when(pickSourceMappingAdapter.resolve(any())).thenReturn(unique(linkRecruiter, AttributionOwnerType.RECRUITER));
        when(productDomainFacade.findActivityDefaultRecruiterId("act-1")).thenReturn(activityRecruiter);

        OrderDefaultAttributionResolver.Resolution resolution = resolver.resolveWithTrace(order, Map.of());
        OrderDefaultAttributionResult result = resolution.result();

        assertThat(result.defaultChannelUserId()).isEqualTo(channelUserId);
        assertThat(result.channelAttributionStatus())
                .isEqualTo(OrderDefaultAttributionResult.CHANNEL_ATTRIBUTED);
        assertThat(result.recruiterAttributionStatus())
                .isEqualTo(OrderDefaultAttributionResult.RECRUITER_UNATTRIBUTED);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(resolution.nativeMappingMatched()).isTrue();
        assertThat(resolution.mappingCreatedAt()).isEqualTo(mapping.getCreateTime());
    }

    @Test
    void resolve_shouldUseNativeColonelMappingWhenPickSourceIsMissing() {
        ColonelsettlementOrder order = baseOrder();
        order.setPickSource(null);
        order.setColonelBuyinId(3859423L);
        UUID channelUserId = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(channelUserId);
        mapping.setDeptId(UUID.randomUUID());

        when(pickSourceMappingAdapter.findByNativeOrder("3859423", "act-1", "prod-1", true))
                .thenReturn(new OrderPickSourceMappingAdapter.NativeMappingLookup(mapping, false));
        when(productDomainFacade.findProductAssigneeId("act-1", "prod-1")).thenReturn(null);
        when(productDomainFacade.findActivityDefaultRecruiterId("act-1")).thenReturn(null);

        OrderDefaultAttributionResult result = resolver.resolve(order, Map.of());

        verify(pickSourceMappingAdapter).findByNativeOrder("3859423", "act-1", "prod-1", true);
        assertThat(result.defaultChannelUserId()).isEqualTo(channelUserId);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        verify(pickSourceMappingAdapter, never()).findByPickSourceOrExtra(any(), any());
    }

    @Test
    void resolve_shouldUseUniqueActivityProductNativeMappingWhenBuyinKeyDiffers() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("3829691670191014167");
        order.setActivityId("3916506");
        order.setPickSource(null);
        order.setColonelBuyinId(7351155267604218149L);

        UUID channelUserId = UUID.randomUUID();
        UUID recruiterUserId = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(channelUserId);
        mapping.setDeptId(UUID.randomUUID());
        mapping.setActivityId("3916506");
        mapping.setProductId("3829691670191014167");
        mapping.setColonelBuyinId("0");
        mapping.setSourceType("NATIVE");

        when(pickSourceMappingAdapter.findByNativeOrder(
                "7351155267604218149", "3916506", "3829691670191014167", true))
                .thenReturn(new OrderPickSourceMappingAdapter.NativeMappingLookup(mapping, false));
        when(productDomainFacade.findProductAssigneeId("3916506", "3829691670191014167"))
                .thenReturn(recruiterUserId);
        when(productDomainFacade.findActivityDefaultRecruiterId("3916506"))
                .thenReturn(null);

        OrderDefaultAttributionResult result = resolver.resolve(order, Map.of());

        assertThat(result.defaultRecruiterId()).isEqualTo(linkRecruiter);
        assertThat(result.recruiterAttributionSource()).isEqualTo(AttributionSource.PICK_SOURCE);
        verify(productDomainFacade, never()).findProductAssigneeId(any(), any());
    }

    @Test
    void resolveProductFacadeExceptionShouldStillReturnChannelResult() {
        ColonelsettlementOrder order = baseOrder();
        UUID channelUser = UUID.randomUUID();
        when(pickSourceMappingAdapter.resolve(any())).thenReturn(unique(channelUser, AttributionOwnerType.CHANNEL));
        when(productDomainFacade.findActivityDefaultRecruiterId("act-1"))
                .thenThrow(new RuntimeException("product domain down"));

        OrderDefaultAttributionResult result = resolver.resolve(order, Map.of());

        assertThat(result.defaultChannelUserId()).isEqualTo(channelUser);
        assertThat(result.defaultRecruiterId()).isNull();
    }

    @Test
    void resolveShouldOnlyLookupActivityRecruiterAndTalent() {
        ColonelsettlementOrder order = baseOrder();
        when(pickSourceMappingAdapter.resolve(any())).thenReturn(notFound());
        when(productDomainFacade.findActivityDefaultRecruiterId("act-1")).thenReturn(UUID.randomUUID());

        resolver.resolve(order, Map.of("author_id", "uid-1"));

        verify(productDomainFacade).findActivityDefaultRecruiterId(eq("act-1"));
        verify(productDomainFacade, never()).findProductAssigneeId(any(), any());
        verify(talentDomainFacade).findByDouyinUid("uid-1");
    }

    @Test
    void resolveShouldResolveTalentIdFromUid() {
        ColonelsettlementOrder order = baseOrder();
        UUID talentId = UUID.randomUUID();
        TalentReadDTO talent = new TalentReadDTO(talentId, "uid-1", null, "达人A", null, 1, null, null, null, null);
        when(pickSourceMappingAdapter.resolve(any())).thenReturn(notFound());
        when(productDomainFacade.findActivityDefaultRecruiterId(any())).thenReturn(null);
        when(talentDomainFacade.findByDouyinUid("uid-1")).thenReturn(talent);

        OrderDefaultAttributionResult result = resolver.resolve(order, Map.of("author_id", "uid-1"));

        assertThat(result.talentId()).isEqualTo(talentId);
    }

    private static ColonelsettlementOrder baseOrder() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("prod-1");
        order.setActivityId("act-1");
        order.setPickSource("ps-1");
        return order;
    }

    private OrderLinkAttributionResolution unique(UUID userId, AttributionOwnerType ownerType) {
        return new OrderLinkAttributionResolution(
                Status.UNIQUE, userId, UUID.randomUUID(), ownerType, AttributionSource.PICK_SOURCE,
                "UNIQUE_LINK_OWNER", false, false, null);
    }

    private OrderLinkAttributionResolution notFound() {
        return new OrderLinkAttributionResolution(
                Status.NOT_FOUND, null, null, null, AttributionSource.UNATTRIBUTED,
                "MAPPING_NOT_FOUND", false, false, null);
    }
}
