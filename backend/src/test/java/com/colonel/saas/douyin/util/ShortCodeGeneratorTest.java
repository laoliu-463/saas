package com.colonel.saas.douyin.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    @Test
    void generate_shouldReturnEightCharBase36Code() {
        // Arrange + Act
        String code = ShortCodeGenerator.generate();

        // Assert
        assertThat(code).hasSize(8);
        assertThat(code).matches("^[0-9A-Z]{8}$");
    }

    @Test
    void generate_withSameUuid_shouldBeDeterministic() {
        // Arrange
        UUID seed = UUID.nameUUIDFromBytes("promotion-seed".getBytes(StandardCharsets.UTF_8));

        // Act
        String first = ShortCodeGenerator.generate(seed);
        String second = ShortCodeGenerator.generate(seed);

        // Assert
        assertThat(first).isEqualTo(second);
    }

    @Test
    void generate_shouldHaveNoCollisionInTenThousandRuns() {
        // Arrange
        Set<String> codes = new HashSet<>();

        // Act
        for (int i = 0; i < 10_000; i++) {
            UUID seed = UUID.nameUUIDFromBytes(("seed-" + i).getBytes(StandardCharsets.UTF_8));
            codes.add(ShortCodeGenerator.generate(seed));
        }

        // Assert
        assertThat(codes).hasSize(10_000);
    }
}
