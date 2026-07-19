package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HealthcheckContractTest {

    @Test
    void backendHealthchecks_shouldBoundWgetRetriesAndTimeout() throws IOException {
        for (String composeFile : List.of(
                "docker-compose.real-pre.yml",
                "docker-compose.test.yml",
                "docker-compose.yml")) {
            String compose = Files.readString(repoRoot().resolve(composeFile)).replace("\r\n", "\n");

            assertThat(compose)
                    .withFailMessage("%s backend healthcheck must use wget -T 5 -t 1", composeFile)
                    .contains("wget -qO- -T 5 -t 1 http://127.0.0.1:8080/");
            assertThat(compose)
                    .withFailMessage("%s still has an unbounded wget healthcheck", composeFile)
                    .doesNotContain("wget -qO- http://127.0.0.1:8080/");
        }
    }

    private static Path repoRoot() {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (userDir.getFileName() != null && "backend".equals(userDir.getFileName().toString())) {
            return userDir.getParent();
        }
        return userDir;
    }
}
