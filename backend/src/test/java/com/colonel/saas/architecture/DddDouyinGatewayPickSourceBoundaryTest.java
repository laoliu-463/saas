package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddDouyinGatewayPickSourceBoundaryTest {

    @Test
    void douyinContractFixtureShouldReadPickSourceThroughServiceBoundary() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/gateway/douyin/contract/DouyinContractFixtureProvider.java"));

        assertThat(source)
                .doesNotContain("PickSourceMappingMapper")
                .doesNotContain("LambdaQueryWrapper")
                .contains("PickSourceMappingService")
                .contains("findLatestActiveMapping");
    }

    @Test
    void testDouyinOrderGatewayShouldReadPickSourceThroughServiceBoundary() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/gateway/douyin/test/TestDouyinOrderGateway.java"));

        assertThat(source)
                .doesNotContain("PickSourceMappingMapper")
                .doesNotContain("LambdaQueryWrapper")
                .contains("PickSourceMappingService")
                .contains("findLatestActiveMapping");
    }
}
