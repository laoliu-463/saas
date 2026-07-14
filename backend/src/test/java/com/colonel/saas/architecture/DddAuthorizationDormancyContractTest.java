package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DddAuthorizationDormancyContractTest {

    private static final Set<String> FOUNDATION_FILE_ALLOWLIST = Set.of(
            "com/colonel/saas/config/DomainPolicyConfig.java",
            "com/colonel/saas/domain/user/api/AuthorizationDecision.java",
            "com/colonel/saas/domain/user/api/AuthorizationReason.java",
            "com/colonel/saas/domain/user/api/AuthorizationScope.java",
            "com/colonel/saas/domain/user/api/PermissionCode.java",
            "com/colonel/saas/domain/user/application/AuthorizationApplicationService.java",
            "com/colonel/saas/domain/user/domain/AuthorizationSnapshot.java",
            "com/colonel/saas/domain/user/domain/AuthorizationSubject.java",
            "com/colonel/saas/domain/user/domain/GrantedRolePermission.java",
            "com/colonel/saas/domain/user/facade/AuthorizationFacade.java",
            "com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapter.java",
            "com/colonel/saas/domain/user/policy/AuthorizationDecisionPolicy.java",
            "com/colonel/saas/domain/user/port/AuthorizationSnapshotStore.java",
            "com/colonel/saas/mapper/AuthorizationSnapshotMapper.java",
            "com/colonel/saas/mapper/projection/AuthorizationSnapshotRow.java");

    private static final Set<String> FOUNDATION_TYPE_MARKERS = Set.of(
            "AuthorizationDecision",
            "AuthorizationReason",
            "AuthorizationScope",
            "PermissionCode",
            "AuthorizationApplicationService",
            "AuthorizationSnapshot",
            "AuthorizationSubject",
            "GrantedRolePermission",
            "AuthorizationFacade",
            "SysAuthorizationSnapshotStoreAdapter",
            "AuthorizationDecisionPolicy",
            "AuthorizationSnapshotStore",
            "AuthorizationSnapshotMapper",
            "AuthorizationSnapshotRow");

    @Test
    void authorizationFoundation_shouldRemainDormantOutsideUserDomain() throws IOException {
        Path root = Path.of("src/main/java");

        assertThat(findUnauthorizedConsumers(root)).isEmpty();
    }

    @Test
    void detector_shouldFindUserDomainAndDirectPortPolicyConsumers(
            @TempDir Path root) throws IOException {
        Path userDomainConsumer = root.resolve(
                "com/colonel/saas/domain/user/application/LegacyAuthorizationConsumer.java");
        Path directPortPolicyConsumer = root.resolve(
                "com/colonel/saas/controller/DirectAuthorizationConsumer.java");
        Files.createDirectories(userDomainConsumer.getParent());
        Files.createDirectories(directPortPolicyConsumer.getParent());
        Files.writeString(userDomainConsumer, "class LegacyAuthorizationConsumer { AuthorizationFacade facade; }");
        Files.writeString(
                directPortPolicyConsumer,
                "class DirectAuthorizationConsumer { AuthorizationSnapshotStore store; AuthorizationDecisionPolicy policy; }");

        assertThat(findUnauthorizedConsumers(root))
                .containsExactlyInAnyOrder(
                        normalize(root.relativize(userDomainConsumer)),
                        normalize(root.relativize(directPortPolicyConsumer)));
    }

    private static List<String> findUnauthorizedConsumers(Path root) throws IOException {
        // Phase 2 shadow consumers must be approved by adding their exact path here.
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !FOUNDATION_FILE_ALLOWLIST.contains(
                            normalize(root.relativize(path))))
                    .filter(DddAuthorizationDormancyContractTest::containsFoundationType)
                    .map(path -> normalize(root.relativize(path)))
                    .sorted()
                    .toList();
        }
    }

    private static boolean containsFoundationType(Path path) {
        try {
            String source = Files.readString(path);
            return FOUNDATION_TYPE_MARKERS.stream().anyMatch(marker -> Pattern
                    .compile("\\b" + Pattern.quote(marker) + "\\b")
                    .matcher(source)
                    .find());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }
}
