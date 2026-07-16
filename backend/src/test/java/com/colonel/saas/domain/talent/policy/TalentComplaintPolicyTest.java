package com.colonel.saas.domain.talent.policy;

import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TalentComplaintPolicyTest {

    private final TalentComplaintPolicy policy = new TalentComplaintPolicy();

    @Test
    void validate_shouldNormalizeMissingNonOtherContentToEmptyString() {
        assertThat(policy.validate(
                TalentComplaintPolicy.REPEATED_NO_FULFILLMENT, null).content())
                .isEmpty();
        assertThat(policy.validate(
                TalentComplaintPolicy.LOW_PRICE_RESALE, "   ").content())
                .isEmpty();
    }

    @Test
    void validate_shouldStillRequireTrimmedContentForOtherReason() {
        assertThatThrownBy(() -> policy.validate(TalentComplaintPolicy.OTHER, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投诉内容");
        assertThat(policy.validate(TalentComplaintPolicy.OTHER, "  其他原因  ").content())
                .isEqualTo("其他原因");
        assertThatThrownBy(() -> policy.validate(
                TalentComplaintPolicy.OTHER, "\u2003\u2003"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投诉内容");
        assertThat(policy.validate(
                TalentComplaintPolicy.OTHER, "\u2003其他原因\u2003").content())
                .isEqualTo("其他原因");
    }

    @Test
    void validate_shouldKeepTwoHundredUnicodeCodePointBoundary() {
        assertThat(policy.validate(
                TalentComplaintPolicy.LOW_PRICE_RESALE, "😀".repeat(200)).content())
                .isEqualTo("😀".repeat(200));
        assertThatThrownBy(() -> policy.validate(
                TalentComplaintPolicy.LOW_PRICE_RESALE, "😀".repeat(201)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("200");
    }

    @Test
    void validate_shouldRejectOneMegabyteContentBeforePersistence() {
        String oversized = "a".repeat(1024 * 1024);

        assertThatThrownBy(() -> policy.validate(TalentComplaintPolicy.OTHER, oversized))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("200");
    }
}
