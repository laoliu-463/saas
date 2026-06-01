package com.colonel.saas.service.activity;

import com.colonel.saas.entity.ColonelsettlementActivity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityPromotionSupportTest {

    @Test
    void isPromoting_shouldMatchStatusCodeFive() {
        assertThat(ActivityPromotionSupport.isPromoting(5, null)).isTrue();
    }

    @Test
    void isPromoting_shouldMatchStatusText() {
        assertThat(ActivityPromotionSupport.isPromoting(null, "推广中")).isTrue();
    }

    @Test
    void isPromoting_shouldRejectOtherStates() {
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityStatusCode(3);
        activity.setActivityStatusText("报名中");
        assertThat(ActivityPromotionSupport.isPromoting(activity)).isFalse();
    }

    @Test
    void isPromoting_shouldPreferStatusCodeWhenPresent() {
        assertThat(ActivityPromotionSupport.isPromoting(3, "推广中")).isFalse();
        assertThat(ActivityPromotionSupport.isPromoting(4, "推广中")).isFalse();
    }

    @Test
    void isPromoting_shouldIgnoreRecruiterAssignment() {
        ColonelsettlementActivity assignedPromoting = new ColonelsettlementActivity();
        assignedPromoting.setActivityStatusCode(5);
        assertThat(ActivityPromotionSupport.isPromoting(assignedPromoting)).isTrue();

        ColonelsettlementActivity unassignedPromoting = new ColonelsettlementActivity();
        unassignedPromoting.setActivityStatusCode(5);
        assertThat(ActivityPromotionSupport.isPromoting(unassignedPromoting)).isTrue();
    }
}
