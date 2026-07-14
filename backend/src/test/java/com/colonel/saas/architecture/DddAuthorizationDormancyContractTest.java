package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DddAuthorizationDormancyContractTest {

    @Test
    void authorizationFoundation_shouldRemainDormantOutsideUserDomain() throws IOException {
        Path root = Path.of("src/main/java");

        // Temporary Phase 1 guard; Phase 2 shadow consumers require an explicit allowlist.
        try (Stream<Path> paths = Files.walk(root)) {
            List<String> consumers = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !normalize(path).contains("/domain/user/"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("AuthorizationFacade")
                                    || source.contains("AuthorizationApplicationService");
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .map(DddAuthorizationDormancyContractTest::normalize)
                    .toList();

            assertThat(consumers).isEmpty();
        }
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }
}
