package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddTalentOrderFacadeBoundaryTest {

    @Test
    void talentServiceShouldReadOrdersThroughOrderReadFacade() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/TalentService.java"));

        assertThat(source)
                .doesNotContain("ColonelsettlementOrderMapper")
                .doesNotContain("orderMapper")
                .contains("OrderReadFacade")
                .contains("orderReadFacade.findOrdersSettledSince");
    }
}
