package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserFacadeCallSurfaceBoundaryTest {

    private static final String USER_DOMAIN_FACADE_IMPORT =
            "import com.colonel.saas.domain.user.facade.UserDomainFacade;";
    private static final Pattern FACADE_METHOD_CALL =
            Pattern.compile("userDomainFacade\\.(\\w+)\\s*\\(");

    private static final Set<String> EXPECTED_CONSUMERS = Set.of(
            "com/colonel/saas/controller/ColonelActivityController.java",
            "com/colonel/saas/controller/DataController.java",
            "com/colonel/saas/controller/OrderController.java",
            "com/colonel/saas/domain/performance/application/ExclusiveMerchantApplicationService.java",
            "com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java",
            "com/colonel/saas/domain/user/infrastructure/aspect/DataScopeAspect.java",
            "com/colonel/saas/service/ExclusiveMerchantQueryService.java",
            "com/colonel/saas/service/MerchantService.java",
            "com/colonel/saas/service/OperationLogService.java",
            "com/colonel/saas/service/OrderSyncPersistenceService.java",
            "com/colonel/saas/service/ProductService.java",
            "com/colonel/saas/service/SampleFilterOptionsService.java",
            "com/colonel/saas/service/TalentQueryService.java",
            "com/colonel/saas/service/TalentService.java",
            "com/colonel/saas/service/activity/ActivityAccessService.java",
            "com/colonel/saas/service/data/DataApplicationService.java",
            "com/colonel/saas/service/sample/SampleApplicationService.java",
            "com/colonel/saas/service/sample/SampleQueryConfiguration.java"
    );

    private static final Map<String, Set<String>> EXPECTED_METHOD_CALLS = Map.ofEntries(
            entry("com/colonel/saas/controller/ColonelActivityController.java",
                    "loadUserDisplayNamesByIds"),
            entry("com/colonel/saas/controller/DataController.java"),
            entry("com/colonel/saas/controller/OrderController.java",
                    "listDepartments"),
            entry("com/colonel/saas/domain/performance/application/ExclusiveMerchantApplicationService.java",
                    "loadUserOwnershipReferencesByIds"),
            entry("com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java",
                    "loadUserOwnershipReferencesByIds"),
            entry("com/colonel/saas/domain/user/infrastructure/aspect/DataScopeAspect.java",
                    "resolveDataScope"),
            entry("com/colonel/saas/service/ExclusiveMerchantQueryService.java",
                    "getUsername"),
            entry("com/colonel/saas/service/MerchantService.java",
                    "loadUserOwnershipReferencesByIds"),
            entry("com/colonel/saas/service/OperationLogService.java",
                    "getUsername"),
            entry("com/colonel/saas/service/OrderSyncPersistenceService.java",
                    "getUserName", "loadUserNamesByIds"),
            entry("com/colonel/saas/service/ProductService.java",
                    "getUserName",
                    "loadUserChannelCodesByIds",
                    "loadUserDisplayLabelsByIds",
                    "loadUserOwnershipReferencesByIds"),
            entry("com/colonel/saas/service/SampleFilterOptionsService.java",
                    "loadUserDisplayLabelsByIds"),
            entry("com/colonel/saas/service/TalentQueryService.java",
                    "loadUserDisplayLabelsByIds"),
            entry("com/colonel/saas/service/TalentService.java",
                    "loadUserOwnershipReferencesByIds"),
            entry("com/colonel/saas/service/activity/ActivityAccessService.java",
                    "hasAnyRole", "normalizeRoleCodes"),
            entry("com/colonel/saas/service/data/DataApplicationService.java",
                    "loadUserDisplayNamesByIds"),
            entry("com/colonel/saas/service/sample/SampleApplicationService.java",
                    "loadUserDisplayLabelsByIds", "loadUserOwnershipReferencesByIds"),
            entry("com/colonel/saas/service/sample/SampleQueryConfiguration.java")
    );

    @Test
    void userDomainFacadeConsumerSurface_shouldStayExplicitAndComplete() throws IOException {
        assertThat(findFacadeConsumers())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_CONSUMERS);
        assertThat(EXPECTED_METHOD_CALLS.keySet())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_CONSUMERS);
    }

    @Test
    void userDomainFacadeConsumers_shouldUsePurposeBuiltFacadeMethods() throws IOException {
        Path sourceRoot = sourceRoot();
        Map<String, Set<String>> actualCalls = new LinkedHashMap<>();
        for (String consumer : EXPECTED_CONSUMERS) {
            String source = Files.readString(sourceRoot.resolve(consumer));
            actualCalls.put(consumer, facadeMethodCalls(source));
        }

        assertThat(actualCalls).containsExactlyInAnyOrderEntriesOf(EXPECTED_METHOD_CALLS);
        assertThat(actualCalls.values().stream().flatMap(Set::stream))
                .doesNotContain("getUserById", "getUsersByIds");
    }

    private static Set<String> findFacadeConsumers() throws IOException {
        Path sourceRoot = sourceRoot();
        Set<String> consumers = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                if (source.contains(USER_DOMAIN_FACADE_IMPORT)) {
                    consumers.add(toUnixPath(sourceRoot.relativize(path)));
                }
            }
        }
        return consumers;
    }

    private static Set<String> facadeMethodCalls(String source) {
        Set<String> calls = new LinkedHashSet<>();
        Matcher matcher = FACADE_METHOD_CALL.matcher(source);
        while (matcher.find()) {
            calls.add(matcher.group(1));
        }
        return calls;
    }

    private static Path sourceRoot() {
        return Paths.get(System.getProperty("user.dir")).resolve("src/main/java");
    }

    private static String toUnixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static Map.Entry<String, Set<String>> entry(String consumer, String... methods) {
        return Map.entry(consumer, Set.of(methods));
    }
}
