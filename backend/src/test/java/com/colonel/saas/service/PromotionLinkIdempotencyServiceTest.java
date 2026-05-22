package com.colonel.saas.service;

import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionLinkIdempotencyServiceTest {

    private PromotionLinkIdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new PromotionLinkIdempotencyService(new ObjectMapper());
    }

    @Test
    void findCompleted_shouldReturnStoredResult() {
        UUID userId = UUID.randomUUID();
        String scopeKey = service.buildScopeKey(userId, "act-1", "prod-1", "idem-001");
        DouyinPromotionGateway.PromotionLinkResult result = new DouyinPromotionGateway.PromotionLinkResult(
                "pick-1",
                "extra-1",
                "short-1",
                "https://short",
                "https://promote",
                "seed-1");

        service.markCompleted(scopeKey, result);

        assertThat(service.findCompleted(scopeKey)).contains(result);
    }

    @Test
    void tryAcquireInFlight_shouldRejectDuplicateUntilCompleted() {
        String scopeKey = "scope:inflight";
        assertThat(service.tryAcquireInFlight(scopeKey)).isTrue();
        assertThat(service.tryAcquireInFlight(scopeKey)).isFalse();
        service.markCompleted(scopeKey, new DouyinPromotionGateway.PromotionLinkResult(
                "pick", null, "short", null, "https://promote", null));
        assertThat(service.tryAcquireInFlight(scopeKey)).isTrue();
    }
}
