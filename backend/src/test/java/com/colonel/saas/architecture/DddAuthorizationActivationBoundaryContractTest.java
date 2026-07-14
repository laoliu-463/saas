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

class DddAuthorizationActivationBoundaryContractTest {

    private static final Set<String> RUNTIME_SERVICE_CONSUMER_ALLOWLIST = Set.of(
            "com/colonel/saas/domain/user/application/AuthorizationRuntimeService.java");

    private static final Set<String> PRINCIPAL_FACADE_CONSUMER_ALLOWLIST = Set.of(
            "com/colonel/saas/domain/user/facade/AuthorizationPrincipalFacade.java",
            "com/colonel/saas/domain/user/application/AuthorizationPrincipalApplicationService.java",
            "com/colonel/saas/security/JwtAuthenticationFilter.java");

    private static final Set<String> SNAPSHOT_PORT_CONSUMER_ALLOWLIST = Set.of(
            "com/colonel/saas/domain/user/port/AuthorizationSnapshotStore.java",
            "com/colonel/saas/domain/user/application/AuthorizationApplicationService.java",
            "com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapter.java",
            "com/colonel/saas/domain/user/infrastructure/VersionedAuthorizationSnapshotStore.java");

    private static final Set<String> AUTHORIZATION_FACADE_CONSUMER_ALLOWLIST = Set.of(
            "com/colonel/saas/domain/user/facade/AuthorizationFacade.java",
            "com/colonel/saas/domain/user/application/AuthorizationApplicationService.java",
            "com/colonel/saas/domain/user/application/AuthorizationRuntimeService.java");

    @Test
    void phaseTwoRuntimeMustHaveNoBusinessRequestConsumer() throws IOException {
        assertThat(findConsumers("AuthorizationRuntimeService"))
                .containsExactlyElementsOf(RUNTIME_SERVICE_CONSUMER_ALLOWLIST);
    }

    @Test
    void principalResolutionMustStayAtSecurityBoundary() throws IOException {
        assertThat(findConsumers("AuthorizationPrincipalFacade"))
                .containsExactlyInAnyOrderElementsOf(PRINCIPAL_FACADE_CONSUMER_ALLOWLIST);
    }

    @Test
    void controllersAndAspectsMustNotReadSnapshotPortDirectly() throws IOException {
        assertThat(findConsumers("AuthorizationSnapshotStore"))
                .containsExactlyInAnyOrderElementsOf(SNAPSHOT_PORT_CONSUMER_ALLOWLIST);
    }

    @Test
    void authorizationFacadeMustHaveOneRuntimeCoordinator() throws IOException {
        assertThat(findConsumers("AuthorizationFacade"))
                .containsExactlyInAnyOrderElementsOf(AUTHORIZATION_FACADE_CONSUMER_ALLOWLIST);
    }

    @Test
    void detectorMustFindDirectSourceReferences(@TempDir Path root) throws IOException {
        Path directConsumer = writeSource(
                root,
                "com/colonel/saas/controller/DirectAuthorizationConsumer.java",
                "class DirectAuthorizationConsumer { AuthorizationRuntimeService service; }");
        writeSource(
                root,
                "com/colonel/saas/controller/UnrelatedConsumer.java",
                "class UnrelatedConsumer { AuthorizationRuntimeServiceAdapter adapter; }");

        assertThat(findConsumers(root, "AuthorizationRuntimeService"))
                .containsExactly(normalize(root.relativize(directConsumer)));
    }

    @Test
    void detectorKeepsCommentsAndStringsVisibleAsLexicalReferences(
            @TempDir Path root) throws IOException {
        Path commentConsumer = writeSource(
                root,
                "com/colonel/saas/controller/CommentConsumer.java",
                "// AuthorizationFacade\nclass CommentConsumer {}");
        Path stringConsumer = writeSource(
                root,
                "com/colonel/saas/controller/StringConsumer.java",
                "class StringConsumer { String type = \"AuthorizationFacade\"; }");

        assertThat(findConsumers(root, "AuthorizationFacade"))
                .containsExactly(
                        normalize(root.relativize(commentConsumer)),
                        normalize(root.relativize(stringConsumer)));
    }

    private static List<String> findConsumers(String typeName) throws IOException {
        return findConsumers(Path.of("src/main/java"), typeName);
    }

    private static List<String> findConsumers(Path root, String typeName) throws IOException {
        // This lexical guard catches direct source references only. It is an architecture
        // regression detector, not a runtime security boundary; reflection and dynamic
        // loading remain covered by integration/security tests.
        Pattern typeReference = Pattern.compile("\\b" + Pattern.quote(typeName) + "\\b");
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsType(path, typeReference))
                    .map(path -> normalize(root.relativize(path)))
                    .sorted()
                    .toList();
        }
    }

    private static boolean containsType(Path path, Pattern typeReference) {
        try {
            return typeReference.matcher(Files.readString(path)).find();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path writeSource(Path root, String relativePath, String source)
            throws IOException {
        Path path = root.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
        return path;
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }
}
