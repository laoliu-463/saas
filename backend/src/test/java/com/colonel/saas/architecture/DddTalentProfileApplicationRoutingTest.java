package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddTalentProfileApplicationRoutingTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void talentControllerProfileCommandsShouldRouteThroughApplicationService() throws Exception {
        String source = Files.readString(ROOT.resolve(
                "src/main/java/com/colonel/saas/controller/TalentController.java"));

        assertThat(source).contains("TalentProfileApplicationService");
        assertThat(source).contains("talentProfileApplicationService.create");
        assertThat(source).contains("talentProfileApplicationService.update");
        assertThat(source).contains("talentProfileApplicationService.updateTags");
        assertThat(source).contains("talentProfileApplicationService.manualFill");
        assertThat(source).contains("talentQueryApplicationService.assertCanOperate");
    }

    @Test
    void talentControllerAddressCommandsShouldRouteThroughAddressApplicationService() throws Exception {
        String source = Files.readString(ROOT.resolve(
                "src/main/java/com/colonel/saas/controller/TalentController.java"));

        assertThat(source).contains("TalentAddressApplicationService");
        assertThat(source).contains("talentAddressApplicationService.getShippingAddress");
        assertThat(source).contains("talentAddressApplicationService.updateShippingAddress");
        assertThat(source).doesNotContain("talentService.getShippingAddress(id, userId)");
        assertThat(source).doesNotContain("talentService.updateShippingAddress(");
    }

    @Test
    void talentFacadeShouldExposeClaimShippingAddressFact() throws Exception {
        String source = Files.readString(ROOT.resolve(
                "src/main/java/com/colonel/saas/domain/talent/facade/TalentDomainFacade.java"));

        assertThat(source).contains("findClaimShippingAddress");
        assertThat(source).contains("TalentShippingAddressDTO");
    }

    @Test
    void productServiceShouldConsumeTalentFollowApplicationService() throws Exception {
        String source = Files.readString(ROOT.resolve(
                "src/main/java/com/colonel/saas/service/ProductService.java"));

        assertThat(source).contains("TalentFollowApplicationService");
        assertThat(source).contains("talentFollowApplicationService.createRecord");
        assertThat(source).contains("talentFollowApplicationService.listByProduct");
        assertThat(source).doesNotContain("private final TalentFollowService talentFollowService");
    }
}
