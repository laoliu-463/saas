package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderAttributionReplayRoutingContractTest {

    @Test
    void replayShouldUseTheSameAttributionRouterAsOrderSync() throws IOException {
        String service = readProjectFile(
                "src/main/java/com/colonel/saas/service/OrderAttributionReplayService.java");

        assertThat(service)
                .contains(
                        "private final OrderAttributionRouter orderAttributionRouter;",
                        "orderAttributionRouter.resolveAndApply(")
                .doesNotContain("attributionService.resolveAttribution(");
    }

    @Test
    void executableUnitEvidenceShouldPinDualStatusPersistenceDuringReplay() throws IOException {
        String test = readProjectFile(
                "src/test/java/com/colonel/saas/service/OrderAttributionReplayServiceTest.java");

        assertThat(test)
                .contains(
                        "replay_shouldUseOrderAttributionRouterAndPersistDualStatuses",
                        "getChannelAttributionStatus()).isEqualTo(\"CHANNEL_ATTRIBUTED\")",
                        "getRecruiterAttributionStatus()).isEqualTo(\"RECRUITER_ATTRIBUTED\")");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
