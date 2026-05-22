package com.colonel.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TalentEnrichModeGuardTest {

    @Test
    void validate_shouldRejectTestModeInProdProfile() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        TalentEnrichModeGuard guard = new TalentEnrichModeGuard(environment);
        ReflectionTestUtils.setField(guard, "enrichMode", "test");

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod profile");
    }

    @Test
    void validate_shouldAllowRealModeInProdAndTestModeOutsideProd() {
        Environment prodEnvironment = mock(Environment.class);
        when(prodEnvironment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        TalentEnrichModeGuard prodGuard = new TalentEnrichModeGuard(prodEnvironment);
        ReflectionTestUtils.setField(prodGuard, "enrichMode", "real");

        Environment testEnvironment = mock(Environment.class);
        when(testEnvironment.getActiveProfiles()).thenReturn(new String[]{"test"});
        TalentEnrichModeGuard testGuard = new TalentEnrichModeGuard(testEnvironment);
        ReflectionTestUtils.setField(testGuard, "enrichMode", "test");

        assertThatCode(prodGuard::validate).doesNotThrowAnyException();
        assertThatCode(testGuard::validate).doesNotThrowAnyException();
    }
}
