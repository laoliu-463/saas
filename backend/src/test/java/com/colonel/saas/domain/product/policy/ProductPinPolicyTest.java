package com.colonel.saas.domain.product.policy;

import com.colonel.saas.entity.ProductOperationState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductPinPolicyTest {

    @Test
    void isPinned_shouldUseOperationStateDeadline() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 13, 10, 0);
        ProductOperationState state = new ProductOperationState();
        state.setPinnedUntil(now.plusHours(1));

        assertThat(ProductPinPolicy.isPinned(state, now)).isTrue();

        state.setPinnedUntil(now.minusMinutes(1));
        assertThat(ProductPinPolicy.isPinned(state, now)).isFalse();
    }

    @Test
    void isPinnedForPresentation_shouldTreatMissingDeadlineAsActiveLegacyPin() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 13, 10, 0);

        assertThat(ProductPinPolicy.isPinnedForPresentation(true, null, now)).isTrue();
        assertThat(ProductPinPolicy.isPinnedForPresentation(false, now.plusHours(1), now)).isFalse();
        assertThat(ProductPinPolicy.isPinnedForPresentation(true, now.minusMinutes(1), now)).isFalse();
    }

    @Test
    void exceedsQuota_shouldIgnoreRefreshOfExistingActivePin() {
        assertThat(ProductPinPolicy.exceedsQuota(ProductPinPolicy.MAX_PINNED_PER_USER, false)).isTrue();
        assertThat(ProductPinPolicy.exceedsQuota(ProductPinPolicy.MAX_PINNED_PER_USER, true)).isFalse();
        assertThat(ProductPinPolicy.exceedsQuota(ProductPinPolicy.MAX_PINNED_PER_USER - 1, false)).isFalse();
    }

    @Test
    void pinExpiresAt_shouldUseConfiguredDuration() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 13, 10, 0);

        assertThat(ProductPinPolicy.pinExpiresAt(now)).isEqualTo(now.plusHours(ProductPinPolicy.PIN_HOURS));
    }

    @Test
    void canUnpin_shouldAllowOwnerOrAdministratorOnly() {
        UUID owner = UUID.randomUUID();

        assertThat(ProductPinPolicy.canUnpin(owner, owner)).isTrue();
        assertThat(ProductPinPolicy.canUnpin(owner, null)).isTrue();
        assertThat(ProductPinPolicy.canUnpin(null, UUID.randomUUID())).isTrue();
        assertThat(ProductPinPolicy.canUnpin(owner, UUID.randomUUID())).isFalse();
    }
}
