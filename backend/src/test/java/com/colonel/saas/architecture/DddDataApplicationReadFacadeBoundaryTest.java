package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddDataApplicationReadFacadeBoundaryTest {

    @Test
    void dataApplicationShouldReadCrossDomainFactsThroughFacades() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/service/data/DataApplicationService.java"));

        assertThat(source)
                .contains("DataOrderQueryFacade")
                .contains("ProductActivityReadFacade")
                .contains("ExclusiveTalentReadFacade")
                .contains("ExclusiveMerchantReadFacade")
                .contains("dataOrderQueryFacade.findPageWithScope")
                .contains("dataOrderQueryFacade.selectMaps")
                .contains("productActivityReadFacade.selectNamesByActivityIds")
                .contains("productActivityReadFacade.selectExportPage")
                .contains("exclusiveTalentReadFacade.selectPage")
                .contains("exclusiveMerchantReadFacade.selectPage")
                .doesNotContain("ColonelsettlementOrderMapper")
                .doesNotContain("ColonelsettlementActivityMapper")
                .doesNotContain("ExclusiveTalentMapper")
                .doesNotContain("ExclusiveMerchantMapper");
    }

    @Test
    void dataControllerShouldInjectFacadesInsteadOfCrossDomainMappers() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/controller/DataController.java"));

        assertThat(source)
                .contains("DataOrderQueryFacade")
                .contains("ProductActivityReadFacade")
                .contains("ExclusiveTalentReadFacade")
                .contains("ExclusiveMerchantReadFacade")
                .doesNotContain("ColonelsettlementOrderMapper")
                .doesNotContain("ColonelsettlementActivityMapper")
                .doesNotContain("ExclusiveTalentMapper")
                .doesNotContain("ExclusiveMerchantMapper");
    }
}
