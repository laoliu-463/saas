package com.colonel.saas.controller;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ColonelActivityProductControllerAuditRequestTest {

    @Test
    void auditRequest_shouldMapExclusivePriceAmountToSupplement() {
        ColonelActivityProductController.AuditRequest request =
                new ColonelActivityProductController.AuditRequest();
        request.setExclusivePriceAmount(new BigDecimal("129.50"));
        request.setExclusivePriceRemark("直播间专属价");

        Map<String, Object> supplement = request.toSupplementMap();

        assertThat(supplement)
                .containsEntry("exclusivePriceAmount", new BigDecimal("129.50"))
                .containsEntry("exclusivePriceRemark", "直播间专属价");
    }
}
