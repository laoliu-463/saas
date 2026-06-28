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

    @Test
    void talentServiceShouldReadSampleCountsThroughSampleDomainFacade() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/TalentService.java"));

        assertThat(source)
                .doesNotContain("SampleRequestMapper")
                .doesNotContain("sampleRequestMapper")
                .doesNotContain("com.colonel.saas.entity.SampleRequest")
                .contains("SampleDomainFacade")
                .contains("sampleDomainFacade.countSamplesByTalentIdSince");
    }
}
