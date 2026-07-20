package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class DddTalentLegacyEntrypointMigrationTest {

    @Test
    void talentClaimReleaseJobShouldDelegateToApplicationService() throws IOException {
        String source = readProjectFile("src/main/java/com/colonel/saas/job/TalentClaimReleaseJob.java");

        assertThat(source)
                .contains(
                        "TalentClaimReleaseApplicationService",
                        "talentClaimReleaseApplicationService.releaseExpiredClaims(now)")
                .doesNotContain(
                        "private final TalentService",
                        "talentService.releaseExpiredClaims(now)");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(projectPath(relativePath));
    }

    private static Path projectPath(String relativePath) {
        return Paths.get(System.getProperty("user.dir")).resolve(relativePath);
    }
}
