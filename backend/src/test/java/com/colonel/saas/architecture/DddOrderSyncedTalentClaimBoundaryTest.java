package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddOrderSyncedTalentClaimBoundaryTest {

    @Test
    void orderSyncedListenerShouldDelegateTalentClaimsToTalentApplicationService() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/listener/OrderSyncedEventListener.java"));

        assertThat(source)
                .doesNotContain("TalentClaimMapper")
                .doesNotContain("talentClaimMapper")
                .contains("TalentClaimApplicationService")
                .contains("extendActiveClaimProtectionByTalentUid");
    }
}
