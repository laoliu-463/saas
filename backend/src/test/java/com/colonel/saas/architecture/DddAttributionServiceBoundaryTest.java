package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddAttributionServiceBoundaryTest {

    @Test
    void attributionServiceShouldReadPickSourceAndTalentThroughBoundaries() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/AttributionService.java"));

        assertThat(source)
                .doesNotContain("PickSourceMappingMapper")
                .doesNotContain("TalentMapper")
                .doesNotContain("TalentClaimMapper")
                .contains("PickSourceMappingService")
                .contains("TalentDomainFacade")
                .contains("findActiveAttributionMapping")
                .contains("hasActiveClaimOwnerConflict");
    }
}
