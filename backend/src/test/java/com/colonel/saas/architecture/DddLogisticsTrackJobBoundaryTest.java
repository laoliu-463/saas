package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddLogisticsTrackJobBoundaryTest {

    @Test
    void logisticsTrackJobShouldDelegateSampleSelectionToSampleService() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/job/LogisticsTrackJob.java"));

        assertThat(source)
                .doesNotContain("SampleRequestMapper")
                .doesNotContain("LambdaQueryWrapper")
                .contains("LogisticsTrackService")
                .contains("refreshShippingSamples");
    }
}
