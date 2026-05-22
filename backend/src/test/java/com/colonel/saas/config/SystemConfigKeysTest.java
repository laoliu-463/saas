package com.colonel.saas.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class SystemConfigKeysTest {

    @Test
    void constants_shouldExposeRegisteredConfigKeys() throws Exception {
        assertThat(SystemConfigKeys.TALENT_PROTECTION_DAYS).isEqualTo("talent.protection_days");
        assertThat(SystemConfigKeys.TALENT_EXCLUSIVE_RATIO).isEqualTo("talent.exclusive.service_fee_ratio");
        assertThat(SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES).isEqualTo("talent.exclusive.monthly_samples");
        assertThat(SystemConfigKeys.MERCHANT_EXCLUSIVE_SERVICE_FEE_RATIO)
                .isEqualTo("merchant.exclusive.service_fee_ratio");
        assertThat(SystemConfigKeys.SAMPLE_RESTRICT_DAYS).isEqualTo("sample.restrict_days");
        assertThat(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED).isEqualTo("sample.restrict_enabled");
        assertThat(SystemConfigKeys.SAMPLE_TIMEOUT_HOMEWORK_DAYS).isEqualTo("sample.timeout_homework_days");
        assertThat(SystemConfigKeys.SAMPLE_TIMEOUT_PENDING_SHIP_DAYS).isEqualTo("sample.timeout_pending_ship_days");
        assertThat(SystemConfigKeys.SAMPLE_DEFAULT_STANDARD).isEqualTo("sample.default_standard");
        assertThat(SystemConfigKeys.COMMISSION_BUSINESS_DEFAULT_RATIO).isEqualTo("commission.business_default_ratio");
        assertThat(SystemConfigKeys.COMMISSION_CHANNEL_DEFAULT_RATIO).isEqualTo("commission.channel_default_ratio");

        Constructor<SystemConfigKeys> constructor = SystemConfigKeys.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThat(constructor.newInstance()).isInstanceOf(SystemConfigKeys.class);
    }
}
