package com.colonel.saas.domain.order.application;

import com.colonel.saas.domain.order.infrastructure.OrderPickSourceMappingAdapter;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionResult;
import com.colonel.saas.domain.product.facade.ProductDomainFacade;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
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
    void resolve_shouldUsePickSourceMappingForChannel() {
        ColonelsettlementOrder order = baseOrder();
        UUID channelUserId = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(channelUserId);
        mapping.setDeptId(UUID.randomUUID());

        when(pickSourceMappingAdapter.findByPickSourceOrExtra("ps-1", null)).thenReturn(mapping);
        when(productDomainFacade.findProductAssigneeId("act-1", "prod-1")).thenReturn(null);
        when(productDomainFacade.findActivityDefaultRecruiterId("act-1")).thenReturn(null);

        OrderDefaultAttributionResult result = resolver.resolve(order, Map.of());

        assertThat(result.defaultChannelUserId()).isEqualTo(channelUserId);
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
    }

    @Test
    void resolve_productFacadeException_shouldStillReturnChannelResult() {
        ColonelsettlementOrder order = baseOrder();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(UUID.randomUUID());

        when(pickSourceMappingAdapter.findByPickSourceOrExtra("ps-1", null)).thenReturn(mapping);
        when(productDomainFacade.findProductAssigneeId(any(), any()))
                .thenThrow(new RuntimeException("product domain down"));

        OrderDefaultAttributionResult result = resolver.resolve(order, Map.of());

        assertThat(result.defaultChannelUserId()).isNotNull();
        assertThat(result.defaultRecruiterId()).isNull();
    }

    @Test
    void resolve_shouldNotCallExclusiveServices() {
        ColonelsettlementOrder order = baseOrder();
        when(pickSourceMappingAdapter.findByPickSourceOrExtra("ps-1", null)).thenReturn(null);
        when(productDomainFacade.findProductAssigneeId("act-1", "prod-1")).thenReturn(UUID.randomUUID());
        when(productDomainFacade.findActivityDefaultRecruiterId("act-1")).thenReturn(null);

        resolver.resolve(order, Map.of("author_id", "uid-1"));

        verify(productDomainFacade).findProductAssigneeId(eq("act-1"), eq("prod-1"));
        verify(talentDomainFacade).findByDouyinUid("uid-1");
    }

    @Test
    void resolve_shouldResolveTalentIdFromUid() {
        ColonelsettlementOrder order = baseOrder();
        UUID talentId = UUID.randomUUID();
        TalentReadDTO talent = new TalentReadDTO(talentId, "uid-1", null, "达人A", null, 1, null, null, null, null);

        when(pickSourceMappingAdapter.findByPickSourceOrExtra("ps-1", null)).thenReturn(null);
        when(productDomainFacade.findProductAssigneeId(any(), any())).thenReturn(null);
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
}
