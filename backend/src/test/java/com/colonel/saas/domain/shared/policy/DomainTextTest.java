package com.colonel.saas.domain.shared.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainTextTest {

    @Test
    void hasTextShouldMatchDomainPolicyBlankSemantics() {
        assertThat(DomainText.hasText(null)).isFalse();
        assertThat(DomainText.hasText("")).isFalse();
        assertThat(DomainText.hasText("   ")).isFalse();
        assertThat(DomainText.hasText("\t")).isFalse();
        assertThat(DomainText.hasText(" value ")).isTrue();
    }

    @Test
    void trimToNullShouldTrimNonBlankTextAndNullBlankText() {
        assertThat(DomainText.trimToNull(null)).isNull();
        assertThat(DomainText.trimToNull("")).isNull();
        assertThat(DomainText.trimToNull("   ")).isNull();
        assertThat(DomainText.trimToNull(" value ")).isEqualTo("value");
    }
}
