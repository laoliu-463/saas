package com.colonel.saas.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AttributionSourceNormalizerTest {

    @Test
    void normalize_shouldFlattenFirstAndSecondColonelInfo() {
        Map<String, Object> normalized = AttributionSourceNormalizer.normalize(Map.of(
                "order_id", "o-1",
                "colonel_order_info", Map.of(
                        "colonel_buyin_id", "first-buyin",
                        "activity_id", "first-activity"
                ),
                "colonel_order_info_second", Map.of(
                        "colonel_buyin_id", "second-buyin",
                        "activity_id", "second-activity"
                )
        ));

        assertThat(normalized.get("colonel_buyin_id")).isEqualTo("first-buyin");
        assertThat(normalized.get("colonel_activity_id")).isEqualTo("first-activity");
        assertThat(normalized.get("second_colonel_buyin_id")).isEqualTo("second-buyin");
        assertThat(normalized.get("second_colonel_activity_id")).isEqualTo("second-activity");
    }
}
