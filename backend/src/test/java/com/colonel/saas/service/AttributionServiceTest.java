package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttributionServiceTest {

    @Mock
    private PickSourceMappingMapper pickSourceMappingMapper;
    @Mock
    private ExclusiveTalentService exclusiveTalentService;
    @Mock
    private ExclusiveMerchantService exclusiveMerchantService;

    private AttributionService attributionService;

    @BeforeEach
    void setUp() {
        attributionService = new AttributionService(
                pickSourceMappingMapper,
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
        order.setShopId(1001L);
        order.setPickSource("ps_1");

        AttributionService.AttributionResult result = attributionService.resolveAttribution(
                order,
                Map.of("merchant_id", "m-1", "talent_uid", "t-1")
        );

        assertThat(result.userId()).isEqualTo(merchantOwner);
        assertThat(result.deptId()).isEqualTo(merchantDept);
        verify(exclusiveTalentService, never()).findActiveOwnerByTalentUid(any());
        verify(pickSourceMappingMapper, never()).selectOne(any());
    }

    @Test
    void resolveAttribution_shouldUseExclusiveTalentWhenNoMerchantExclusive() {
        UUID talentOwner = UUID.randomUUID();
        UUID talentDept = UUID.randomUUID();
        when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        when(exclusiveTalentService.findActiveOwnerByTalentUid("t-2"))
                .thenReturn(new AttributionService.ExclusiveOwner(talentOwner, talentDept));

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setPickSource("ps_2");

        AttributionService.AttributionResult result = attributionService.resolveAttribution(
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
        when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);

        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setUserId(mappingUser);
        mapping.setDeptId(mappingDept);
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(mapping);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setPickSource("pick_x");

        AttributionService.AttributionResult result = attributionService.resolveAttribution(
                order,
                Map.of("pick_extra", "short_123")
        );

        assertThat(result.userId()).isEqualTo(mappingUser);
        assertThat(result.deptId()).isEqualTo(mappingDept);
    }

    @Test
    void resolveAttribution_shouldThrowWhenNoAttributionMatched() {
        when(exclusiveMerchantService.findActiveOwnerByMerchantId(any())).thenReturn(null);
        when(exclusiveTalentService.findActiveOwnerByTalentUid(any())).thenReturn(null);
        when(pickSourceMappingMapper.selectOne(any())).thenReturn(null);

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setPickSource("unknown");

        assertThatThrownBy(() -> attributionService.resolveAttribution(order, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pick_source");
    }
}
