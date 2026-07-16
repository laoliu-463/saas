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
import java.time.LocalDateTime;
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
        order.setColonelBuyinId(7351155267604218149L);
        order.setSecondColonelBuyinId(7392822694083707171L);
        order.setSecondActivityId("order-second-act");
        LocalDateTime payTime = LocalDateTime.of(2026, 7, 16, 14, 6, 24);
        order.setPayTime(payTime);
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
        assertThat(input.activityId()).isEqualTo("order-act");
        assertThat(input.pickSource()).isEqualTo("ps-1");
        assertThat(input.pickExtra()).isEqualTo("extra-1");
        assertThat(input.talentUid()).isEqualTo("uid-author");
        assertThat(input.talentId()).isEqualTo(talentId);
        assertThat(input.colonelBuyinId()).isEqualTo("7351155267604218149");
        assertThat(input.secondColonelBuyinId()).isEqualTo("7392822694083707171");
        assertThat(input.secondActivityId()).isEqualTo("order-second-act");
        assertThat(input.businessTime()).isEqualTo(payTime);
    }

    @Test
    void attributionInputShouldFallbackToOrderActivityAndTalentName() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("prod-1");
        order.setActivityId("order-act");
        order.setTalentName("talent-name-fallback");
        LocalDateTime orderCreateTime = LocalDateTime.of(2026, 7, 16, 13, 5);
        order.setOrderCreateTime(orderCreateTime);

        OrderAttributionInput input = OrderAttributionInput.from(order, Map.of(
                "colonel_buyin_id", "raw-buyin",
                "second_colonel_buyin_id", "raw-second-buyin",
                "second_colonel_activity_id", "raw-second-act"));

        assertThat(input.activityId()).isEqualTo("order-act");
        assertThat(input.talentUid()).isEqualTo("talent-name-fallback");
        assertThat(input.pickSource()).isNull();
        assertThat(input.pickExtra()).isNull();
        assertThat(input.colonelBuyinId()).isEqualTo("raw-buyin");
        assertThat(input.secondColonelBuyinId()).isEqualTo("raw-second-buyin");
        assertThat(input.secondActivityId()).isEqualTo("raw-second-act");
        assertThat(input.businessTime()).isEqualTo(orderCreateTime);
    }

    @Test
    void attributionInputShouldFallbackBusinessTimeToLocalCreateTime() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 16, 12, 4);
        order.setCreateTime(createTime);

        OrderAttributionInput input = OrderAttributionInput.from(order, Map.of());

        assertThat(input.businessTime()).isEqualTo(createTime);
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
        when(productDomainFacade.findProductAssigneeId("order-act", "prod-1")).thenReturn(recruiterId);
        when(productDomainFacade.findActivityDefaultRecruiterId("order-act")).thenReturn(UUID.randomUUID());

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
        verify(productDomainFacade).findProductAssigneeId("order-act", "prod-1");
        verify(productDomainFacade).findActivityDefaultRecruiterId("order-act");
        verify(talentDomainFacade).findByDouyinUid("uid-1");
    }

    @Test
    void attributionResultContractShouldOnlyExposeDefaultAttributionFields() {
        assertThat(Arrays.stream(OrderDefaultAttributionResult.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly(
                        "defaultChannelUserId",
                        "channelDeptId",
                        "defaultRecruiterId",
                        "talentId",
                        "talentUid",
                        "activityId",
                        "attributionStatus",
                        "attributionRemark");
    }
}
