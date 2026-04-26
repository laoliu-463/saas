package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributionServiceTest {

    @Mock
    private PickSourceMappingMapper pickSourceMappingMapper;
    @Mock
    private ProductOperationStateMapper productOperationStateMapper;
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
                exclusiveTalentService,
                exclusiveMerchantService
        );
    }

    @Test
    void resolveAttribution_shouldUseExclusiveMerchantFirst() {
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
        UUID talentOwner = UUID.randomUUID();
        UUID talentDept = UUID.randomUUID();
        when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
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
    void resolveAttribution_shouldFallbackToPickSourceMapping() {
        UUID mappingUser = UUID.randomUUID();
        UUID mappingDept = UUID.randomUUID();
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(mappingUser);
        mapping.setDeptId(mappingDept);
        when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
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
    void resolveAttribution_shouldExposeColonelOwnerFromProductState() {
        UUID colonelUserId = UUID.randomUUID();
        ProductOperationState state = new ProductOperationState();
        state.setAssigneeId(colonelUserId);
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(UUID.randomUUID());
        mapping.setDeptId(UUID.randomUUID());
        when(productOperationStateMapper.selectOne(any())).thenReturn(state);
        when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
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
        when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setProductId("pid-5");
        order.setPickSource("unknown");

        AttributionService.AttributionResult result = service.resolveAttribution(order, Map.of());

        assertThat(result.attributionStatus()).isEqualTo(AttributionService.STATUS_UNATTRIBUTED);
        assertThat(result.attributionRemark()).isEqualTo(AttributionService.REASON_MAPPING_NOT_FOUND);
        assertThat(result.userId()).isNull();
    }
}
