package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddTalentDomainStatusEvidenceIndexTest {

    @Test
    void talentDomainStatusShouldIndexEveryTalentEvidenceCard() throws IOException {
        String matrix = readProjectFile("docs/ddd-completion-evidence-matrix.md");
        String domainStatus = readProjectFile("docs/harness-maintenance/legacy-rules/state/snapshots/DOMAIN_STATUS.md");

        for (int card = 1; card <= 18; card++) {
            assertThat(matrix).contains("| T-" + card + " |");
        }

        assertThat(domainStatus)
                .contains("达人域逐卡 evidence index")
                .contains("T-1/T-2/T-3/T-4")
                .contains("T-5/T-6/T-7")
                .contains("T-8/T-9/T-10")
                .contains("T-11")
                .contains("T-12/T-13")
                .contains("T-14")
                .contains("T-15")
                .contains("T-16/T-17")
                .contains("T-18");

        assertThat(domainStatus)
                .contains("真实 admin/group/self 账号 API")
                .contains("浏览器 E2E")
                .contains("第三方达人响应")
                .contains("T-11/T-14/G-4");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(repoRoot().resolve(relativePath)).replace("\r\n", "\n");
    }

    private static Path repoRoot() {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (userDir.getFileName() != null && "backend".equals(userDir.getFileName().toString())) {
            return userDir.getParent();
        }
        return userDir;
    }
}
