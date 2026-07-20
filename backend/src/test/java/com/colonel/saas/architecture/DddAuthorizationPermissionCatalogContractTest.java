package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DddAuthorizationPermissionCatalogContractTest {

    private static final Pattern ANNOTATION = Pattern.compile("@RequirePermission\\(\"([^\"]+)\"\\)");
    private static final Pattern SQL_PERMISSION = Pattern.compile("\\('([a-z][a-z0-9-]*:[a-z][a-z0-9-]*)', 'system'");

    @Test
    void controllerPermissionsShouldUseStableCodesWithoutLegacyRoleGuards() throws IOException {
        String controllers = readJavaTree("src/main/java/com/colonel/saas/controller");

        assertThat(controllers).doesNotContain("import com.colonel.saas.annotation.RequireRoles;");
        assertThat(permissionCodes(controllers)).hasSizeGreaterThan(100);
        assertThat(Files.exists(projectPath("src/main/java/com/colonel/saas/annotation/RequireRoles.java"))).isFalse();
        assertThat(Files.exists(projectPath("src/main/java/com/colonel/saas/aspect/RoleGuardAspect.java"))).isFalse();
    }

    @Test
    void databaseSeedShouldExactlyCoverControllerPermissionCatalog() throws IOException {
        Set<String> controllerCodes = permissionCodes(readJavaTree("src/main/java/com/colonel/saas/controller"));
        String sql = Files.readString(projectPath(
                "src/main/resources/db/alter-authorization-permission-catalog-20260720.sql"));

        assertThat(permissionCodes(sql, SQL_PERMISSION)).containsExactlyElementsOf(controllerCodes);
        assertThat(sql).contains("ON CONFLICT (role_id, permission_id) DO NOTHING");
        assertThat(sql).contains("WHERE configured.role_id = r.id");
    }

    @Test
    void migrationEntrypointsShouldIncludePermissionCatalogExactlyOnce() throws IOException {
        String migrate = Files.readString(projectPath("src/main/resources/db/migrate-all.sql"));
        String init = Files.readString(projectPath("src/main/resources/db/init-db.sql"));

        assertThat(count(migrate, "\\i alter-authorization-permission-catalog-20260720.sql")).isOne();
        assertThat(count(init, "\\ir alter-authorization-permission-catalog-20260720.sql")).isOne();
    }

    private static Set<String> permissionCodes(String source) {
        return permissionCodes(source, ANNOTATION);
    }

    private static Set<String> permissionCodes(String source, Pattern pattern) {
        Set<String> codes = new TreeSet<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private static long count(String source, String token) {
        return source.lines().filter(line -> line.trim().equals(token)).count();
    }

    private static String readJavaTree(String relativePath) throws IOException {
        StringBuilder source = new StringBuilder();
        try (Stream<Path> paths = Files.walk(projectPath(relativePath))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
                source.append(Files.readString(path)).append('\n');
            }
        }
        return source.toString();
    }

    private static Path projectPath(String relativePath) {
        return Path.of(System.getProperty("user.dir")).resolve(relativePath);
    }
}
