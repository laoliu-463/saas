package com.colonel.saas.service;

import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void blankInputs_shouldBeIgnoredExceptForIdempotencyKeyValidation() {
        assertThat(service.findCompleted(" ")).isEmpty();
        assertThat(service.tryAcquireInFlight(" ")).isTrue();
        service.releaseInFlight(" ");
        service.markCompleted(" ", new DouyinPromotionGateway.PromotionLinkResult("pick", null, null, null, null, null));
        service.markCompleted("scope", null);

        assertThatThrownBy(() -> service.buildScopeKey(UUID.randomUUID(), "act", "prod", " "))
                .hasMessageContaining("Idempotency-Key");
        assertThatThrownBy(() -> service.buildScopeKey(UUID.randomUUID(), "act", "prod", "x".repeat(129)))
                .hasMessageContaining("长度不能超过");
    }

    @Test
    void findCompleted_shouldDeleteCorruptLocalPayload() {
        String scopeKey = "scope:corrupt";
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, String> store =
                (ConcurrentHashMap<String, String>) ReflectionTestUtils.getField(service, "localStore");
        store.put(scopeKey + ":completed", "{bad-json");

        assertThat(service.findCompleted(scopeKey)).isEmpty();
        assertThat(store).doesNotContainKey(scopeKey + ":completed");
    }
}
