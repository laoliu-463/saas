package com.colonel.saas.architecture;

import com.colonel.saas.domain.order.application.OrderDefaultAttributionResolver;
import com.colonel.saas.domain.order.infrastructure.OrderPickSourceMappingAdapter;
import com.colonel.saas.domain.order.policy.OrderAttributionInput;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionResult;
import com.colonel.saas.domain.product.facade.ProductDomainFacade;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.service.AttributionService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DddOrderDefaultAttributionInputContractTest {

    @Test
    void attributionInputShouldUseOrderFactsAndRawPayloadAliases() {
        UUID talentId = UUID.randomUUID();
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("prod-1");
        order.setActivityId("order-act");
        order.setPickSource("ps-1");
        order.setTalentId(talentId);
        order.setTalentName("fallback-talent");

        OrderAttributionInput input = OrderAttributionInput.from(order, Map.of(
                "colonel_activity_id", " raw-act ",
                "activity_id", "ignored-act",
                "pick_extra", " extra-1 ",
                "author_id", " uid-author ",
                "talent_uid", "ignored-talent",
                "promotion_talent_uid", "ignored-promotion"));

        assertThat(input.productId()).isEqualTo("prod-1");
        assertThat(input.activityId()).isEqualTo("raw-act");
        assertThat(input.pickSource()).isEqualTo("ps-1");
        assertThat(input.pickExtra()).isEqualTo("extra-1");
        assertThat(input.talentUid()).isEqualTo("uid-author");
        assertThat(input.talentId()).isEqualTo(talentId);
    }

    @Test
    void attributionInputShouldFallbackToOrderActivityAndTalentName() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("prod-1");
        order.setActivityId("order-act");
        order.setTalentName("talent-name-fallback");

        OrderAttributionInput input = OrderAttributionInput.from(order, Map.of());

        assertThat(input.activityId()).isEqualTo("order-act");
        assertThat(input.talentUid()).isEqualTo("talent-name-fallback");
        assertThat(input.pickSource()).isNull();
        assertThat(input.pickExtra()).isNull();
    }

    @Test
    void resolverShouldPassDefaultInputsToMappingTalentAndRecruiterLookups() {
        OrderPickSourceMappingAdapter mappingAdapter = mock(OrderPickSourceMappingAdapter.class);
        ProductDomainFacade productDomainFacade = mock(ProductDomainFacade.class);
        TalentDomainFacade talentDomainFacade = mock(TalentDomainFacade.class);
        OrderDefaultAttributionResolver resolver = new OrderDefaultAttributionResolver(
                mappingAdapter,
                productDomainFacade,
                talentDomainFacade);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("prod-1");
        order.setActivityId("order-act");
        order.setPickSource("ps-1");

        UUID channelUserId = UUID.randomUUID();
        UUID channelDeptId = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(channelUserId);
        mapping.setDeptId(channelDeptId);
        mapping.setActivityId("mapping-act");

        UUID talentId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        when(mappingAdapter.findByPickSourceOrExtra("ps-1", "extra-1")).thenReturn(mapping);
        when(talentDomainFacade.findByDouyinUid("uid-1"))
                .thenReturn(new TalentReadDTO(talentId, "uid-1", null, "Talent", null, 1, null, null, null, null));
        when(productDomainFacade.findProductAssigneeId("raw-act", "prod-1")).thenReturn(recruiterId);
        when(productDomainFacade.findActivityDefaultRecruiterId("raw-act")).thenReturn(UUID.randomUUID());

        OrderDefaultAttributionResult result = resolver.resolve(order, Map.of(
                "colonel_activity_id", "raw-act",
                "pick_extra", "extra-1",
                "promotion_talent_uid", "uid-1"));

        assertThat(result.defaultChannelUserId()).isEqualTo(channelUserId);
        assertThat(result.channelDeptId()).isEqualTo(channelDeptId);
        assertThat(result.defaultRecruiterId()).isEqualTo(recruiterId);
        assertThat(result.talentId()).isEqualTo(talentId);
        assertThat(result.talentUid()).isEqualTo("uid-1");
        assertThat(result.activityId()).isEqualTo("mapping-act");
        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);

        verify(mappingAdapter).findByPickSourceOrExtra("ps-1", "extra-1");
        verify(productDomainFacade).findProductAssigneeId("raw-act", "prod-1");
        verify(productDomainFacade).findActivityDefaultRecruiterId("raw-act");
        verify(talentDomainFacade).findByDouyinUid("uid-1");
    }

    @Test
    void attributionResultContractShouldExposeDefaultAttributionFieldsWithDualDimension() {
        assertThat(Arrays.stream(OrderDefaultAttributionResult.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly(
                        "defaultChannelUserId",
                        "channelDeptId",
                        "defaultRecruiterId",
                        "recruiterDeptId",
                        "talentId",
                        "talentUid",
                        "activityId",
                        "channelAttributionStatus",
                        "recruiterAttributionStatus",
                        "attributionStatus",
                        "attributionRemark");
    }
}
