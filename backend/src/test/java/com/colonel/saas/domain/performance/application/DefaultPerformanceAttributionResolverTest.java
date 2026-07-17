package com.colonel.saas.domain.performance.application;

import com.colonel.saas.domain.performance.domain.ExclusiveMerchantRepository;
import com.colonel.saas.domain.performance.policy.PerformanceAttributionPolicy;
import com.colonel.saas.domain.talent.domain.ExclusiveTalentRepository;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.entity.ExclusiveTalent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPerformanceAttributionResolverTest {

    @Mock
    private ExclusiveMerchantRepository merchantRepository;
    @Mock
    private ExclusiveTalentRepository talentRepository;

    private DefaultPerformanceAttributionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultPerformanceAttributionResolver(
                merchantRepository,
                talentRepository,
                true);
    }

    @Test
    void resolve_shouldApplyActiveMerchantExclusiveOwnerToRecruiterOnly() {
        UUID defaultChannelId = UUID.randomUUID();
        UUID defaultRecruiterId = UUID.randomUUID();
        UUID defaultChannelDeptId = UUID.randomUUID();
        UUID exclusiveRecruiterId = UUID.randomUUID();
        UUID exclusiveRecruiterDeptId = UUID.randomUUID();
        ColonelsettlementOrder order = order(defaultChannelId, defaultRecruiterId, defaultChannelDeptId);
        order.setShopId(90000003L);

        ExclusiveMerchant exclusive = new ExclusiveMerchant();
        exclusive.setUserId(exclusiveRecruiterId);
        exclusive.setDeptId(exclusiveRecruiterDeptId);
        when(merchantRepository.findActiveByMerchantIdAndMonth(
                eq("90000003"), eq(YearMonth.now().toString())))
                .thenReturn(Optional.of(exclusive));

        PerformanceAttributionPolicy.AttributionResult result = resolver.resolve(order);

        assertThat(result.finalChannelId()).isEqualTo(defaultChannelId);
        assertThat(result.finalRecruiterId()).isEqualTo(exclusiveRecruiterId);
        assertThat(result.finalRecruiterDeptId()).isEqualTo(exclusiveRecruiterDeptId);
        assertThat(result.recruiterAttributionType()).isEqualTo("EXCLUSIVE_MERCHANT");
    }

    @Test
    void resolve_shouldApplyActiveTalentExclusiveOwnerToBothDimensions() {
        UUID defaultChannelId = UUID.randomUUID();
        UUID defaultRecruiterId = UUID.randomUUID();
        UUID exclusiveUserId = UUID.randomUUID();
        UUID exclusiveDeptId = UUID.randomUUID();
        ColonelsettlementOrder order = order(defaultChannelId, defaultRecruiterId, UUID.randomUUID());
        order.setExtraData(Map.of("author_id", "talent-uid-1"));

        ExclusiveTalent exclusive = new ExclusiveTalent();
        exclusive.setUserId(exclusiveUserId);
        exclusive.setDeptId(exclusiveDeptId);
        when(talentRepository.findActiveByTalentUid(
                eq("talent-uid-1"), eq(YearMonth.now().toString())))
                .thenReturn(Optional.of(exclusive));

        PerformanceAttributionPolicy.AttributionResult result = resolver.resolve(order);

        assertThat(result.finalChannelId()).isEqualTo(exclusiveUserId);
        assertThat(result.finalRecruiterId()).isEqualTo(exclusiveUserId);
        assertThat(result.finalChannelDeptId()).isEqualTo(exclusiveDeptId);
        assertThat(result.finalRecruiterDeptId()).isEqualTo(exclusiveDeptId);
        assertThat(result.channelAttributionType()).isEqualTo("EXCLUSIVE_TALENT");
        assertThat(result.recruiterAttributionType()).isEqualTo("EXCLUSIVE_TALENT");
    }

    @Test
    void resolve_shouldIgnoreExclusiveRecordsWhenFeatureDisabled() {
        resolver = new DefaultPerformanceAttributionResolver(
                merchantRepository,
                talentRepository,
                false);
        ColonelsettlementOrder order = order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        PerformanceAttributionPolicy.AttributionResult result = resolver.resolve(order);

        assertThat(result.finalChannelId()).isEqualTo(order.getChannelUserId());
        assertThat(result.finalRecruiterId()).isEqualTo(order.getColonelUserId());
    }

    @Test
    void resolve_shouldNotUseLegacyChannelUserIdAsRecruiterFallback() {
        resolver = new DefaultPerformanceAttributionResolver(
                merchantRepository,
                talentRepository,
                false);
        UUID channelUserId = UUID.randomUUID();
        ColonelsettlementOrder order = order(channelUserId, null, UUID.randomUUID());
        order.setUserId(channelUserId);

        PerformanceAttributionPolicy.AttributionResult result = resolver.resolve(order);

        assertThat(result.finalChannelId()).isEqualTo(channelUserId);
        assertThat(result.finalRecruiterId()).isNull();
    }

    private ColonelsettlementOrder order(UUID channelId, UUID recruiterId, UUID channelDeptId) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setChannelUserId(channelId);
        order.setColonelUserId(recruiterId);
        order.setChannelDeptId(channelDeptId);
        return order;
    }
}
