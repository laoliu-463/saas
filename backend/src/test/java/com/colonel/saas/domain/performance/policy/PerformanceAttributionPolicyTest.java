package com.colonel.saas.domain.performance.policy;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class PerformanceAttributionPolicyTest {

    @Test
    void manualAdjustmentShouldOverrideExclusiveAndDefaultAttribution() {
        UUID defaultChannel = UUID.randomUUID();
        UUID defaultRecruiter = UUID.randomUUID();
        UUID merchantRecruiter = UUID.randomUUID();
        UUID manualChannel = UUID.randomUUID();
        UUID manualRecruiter = UUID.randomUUID();

        PerformanceAttributionPolicy.AttributionResult result = PerformanceAttributionPolicy.resolve(
                new PerformanceAttributionPolicy.AttributionInput(
                        defaultChannel,
                        defaultRecruiter,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new PerformanceAttributionPolicy.ExclusiveOwner(merchantRecruiter, UUID.randomUUID()),
                        null,
                        new PerformanceAttributionPolicy.ManualOwner(
                                manualChannel, manualRecruiter, UUID.randomUUID(), UUID.randomUUID())));

        assertThat(result.finalChannelId()).isEqualTo(manualChannel);
        assertThat(result.finalRecruiterId()).isEqualTo(manualRecruiter);
        assertThat(result.channelAttributionType()).isEqualTo("MANUAL_ADJUSTMENT");
        assertThat(result.recruiterAttributionType()).isEqualTo("MANUAL_ADJUSTMENT");
    }

    @Test
    void resolve_defaultAttribution_noExclusive() {
        UUID channelId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        UUID channelDept = UUID.randomUUID();
        UUID recruiterDept = UUID.randomUUID();

        PerformanceAttributionPolicy.AttributionInput input = new PerformanceAttributionPolicy.AttributionInput(
                channelId, recruiterId, channelDept, recruiterDept, null, null
        );

        PerformanceAttributionPolicy.AttributionResult result = PerformanceAttributionPolicy.resolve(input);

        assertThat(result.finalChannelId()).isEqualTo(channelId);
        assertThat(result.finalRecruiterId()).isEqualTo(recruiterId);
        assertThat(result.channelAttributionType()).isEqualTo("DEFAULT");
        assertThat(result.recruiterAttributionType()).isEqualTo("DEFAULT");
    }

    @Test
    void resolve_merchantExclusive_overridesRecruiterOnly() {
        UUID channelId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        UUID channelDept = UUID.randomUUID();
        UUID recruiterDept = UUID.randomUUID();
        UUID exclusiveMerchantUser = UUID.randomUUID();
        UUID exclusiveMerchantDept = UUID.randomUUID();

        PerformanceAttributionPolicy.AttributionInput input = new PerformanceAttributionPolicy.AttributionInput(
                channelId, recruiterId, channelDept, recruiterDept,
                new PerformanceAttributionPolicy.ExclusiveOwner(exclusiveMerchantUser, exclusiveMerchantDept),
                null
        );

        PerformanceAttributionPolicy.AttributionResult result = PerformanceAttributionPolicy.resolve(input);

        assertThat(result.finalChannelId()).isEqualTo(channelId);
        assertThat(result.finalRecruiterId()).isEqualTo(exclusiveMerchantUser);
        assertThat(result.channelAttributionType()).isEqualTo("DEFAULT");
        assertThat(result.recruiterAttributionType()).isEqualTo("EXCLUSIVE_MERCHANT");
    }

    @Test
    void resolve_talentExclusive_overridesBoth() {
        UUID channelId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        UUID channelDept = UUID.randomUUID();
        UUID recruiterDept = UUID.randomUUID();
        UUID exclusiveTalentUser = UUID.randomUUID();
        UUID exclusiveTalentDept = UUID.randomUUID();

        PerformanceAttributionPolicy.AttributionInput input = new PerformanceAttributionPolicy.AttributionInput(
                channelId, recruiterId, channelDept, recruiterDept, null,
                new PerformanceAttributionPolicy.ExclusiveOwner(exclusiveTalentUser, exclusiveTalentDept)
        );

        PerformanceAttributionPolicy.AttributionResult result = PerformanceAttributionPolicy.resolve(input);

        assertThat(result.finalChannelId()).isEqualTo(exclusiveTalentUser);
        assertThat(result.finalRecruiterId()).isEqualTo(exclusiveTalentUser);
        assertThat(result.channelAttributionType()).isEqualTo("EXCLUSIVE_TALENT");
        assertThat(result.recruiterAttributionType()).isEqualTo("EXCLUSIVE_TALENT");
    }
}
