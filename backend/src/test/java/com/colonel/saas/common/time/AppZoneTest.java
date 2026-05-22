package com.colonel.saas.common.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AppZoneTest {

    @Test
    void fromEpochSecond_shouldUseShanghaiZone() {
        LocalDateTime dateTime = AppZone.fromEpochSecond(1_704_038_400L);
        assertThat(dateTime.getYear()).isEqualTo(2024);
        assertThat(dateTime.getMonthValue()).isEqualTo(1);
        assertThat(dateTime.getDayOfMonth()).isEqualTo(1);
        assertThat(dateTime.getHour()).isZero();
        assertThat(dateTime.getMinute()).isZero();
    }
}
