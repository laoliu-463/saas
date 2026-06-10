package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-BASE-004: verifies target bounded-context package skeleton exists without moving legacy code.
 */
class DddPackageStructureContractTest {

    private static final List<String> BOUNDED_CONTEXTS = List.of(
            "user", "config", "product", "talent", "sample", "order", "performance", "analytics", "shared");

    private static final List<String> LAYERS = List.of(
            "application", "domain", "policy", "event", "facade", "infrastructure", "query", "api");

    @Test
    void allBoundedContextsShouldExposeLayerPackages() {
        Path domainRoot = Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/colonel/saas/domain");

        for (String bc : BOUNDED_CONTEXTS) {
            Path bcRoot = domainRoot.resolve(bc);
            assertThat(bcRoot).as("bounded context root: %s", bc).isDirectory();
            assertThat(bcRoot.resolve("package-info.java"))
                    .as("BC package-info: %s", bc)
                    .isRegularFile();

            for (String layer : LAYERS) {
                Path layerDir = bcRoot.resolve(layer);
                assertThat(layerDir).as("%s.%s", bc, layer).isDirectory();
                assertThat(layerDir.resolve("package-info.java"))
                        .as("%s.%s package-info", bc, layer)
                        .isRegularFile();
            }
        }
    }

    @Test
    void boundedContextRootsShouldDocumentDddBase004() throws Exception {
        Path domainRoot = Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/colonel/saas/domain");

        try (Stream<Path> paths = Files.walk(domainRoot, 2)) {
            List<Path> bcPackageInfos = paths
                    .filter(p -> p.getFileName().toString().equals("package-info.java"))
                    .filter(p -> p.getParent() != null && BOUNDED_CONTEXTS.contains(p.getParent().getFileName().toString()))
                    .toList();

            assertThat(bcPackageInfos).hasSize(BOUNDED_CONTEXTS.size());
            for (Path packageInfo : bcPackageInfos) {
                String content = Files.readString(packageInfo);
                assertThat(content).contains("DDD-BASE-004");
            }
        }
    }
}
