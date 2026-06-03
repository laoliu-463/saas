package com.colonel.saas.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeptTypeTest {

    @Test
    void isGroup_shouldRecognizeOnlyStandardGroupTypes() {
        assertThat(DeptType.isGroup(DeptType.RECRUITER_GROUP)).isTrue();
        assertThat(DeptType.isGroup(DeptType.CHANNEL_GROUP)).isTrue();
        assertThat(DeptType.isGroup(DeptType.OPS_GROUP)).isTrue();
        assertThat(DeptType.isGroup(DeptType.DEPARTMENT)).isFalse();
    }

    @Test
    void isAllowed_shouldRejectLegacyDeptTypeValuesAsStandardValues() {
        assertThat(DeptType.isAllowed("recruiter")).isFalse();
        assertThat(DeptType.isAllowed("channel")).isFalse();
        assertThat(DeptType.isAllowed("dept")).isFalse();
    }

    @Test
    void normalize_shouldKeepLegacyBusinessAsReadCompatibilityOnly() {
        assertThat(DeptType.normalize("BUSINESS")).isEqualTo(DeptType.DEPARTMENT);
        assertThat(DeptType.isAllowed("BUSINESS")).isFalse();
    }
}
