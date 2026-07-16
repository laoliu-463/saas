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
