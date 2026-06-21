package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddUserDataScopePolicyDataApplicationBoundaryTest {

    @Test
    void dataApplication_shouldConsumeUserDomainDataScopePolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/data/DataApplicationService.java"));

        assertThat(source)
                .contains("DataScopePolicy")
                .contains("dataScopePolicy")
                .doesNotContain("switch (dataScope)");
    }
}
