package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddTalentSampleFacadeBoundaryTest {

    @Test
    void talentQueryShouldReadSampleSummaryThroughSampleDomainFacade() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/TalentQueryService.java"));

        assertThat(source)
                .doesNotContain("SampleRequestMapper")
                .doesNotContain("sample_request")
                .doesNotContain("FROM sample_request")
                .contains("SampleDomainFacade")
                .contains("sampleDomainFacade.countSamplesByTalentIds")
                .contains("sampleDomainFacade.listRecentSamplesByTalentId");
    }
}
