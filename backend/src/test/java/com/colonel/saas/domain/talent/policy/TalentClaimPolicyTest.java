package com.colonel.saas.domain.talent.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.entity.TalentClaim;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TalentClaimPolicyTest {

    private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void requireClaimUser_shouldRejectNull() {
        assertThatThrownBy(() -> TalentClaimPolicy.requireClaimUser(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少登录用户");
    }

    @Test
    void assertNotDuplicateActiveClaim_shouldRejectExisting() {
        TalentClaim existing = new TalentClaim();
        assertThatThrownBy(() -> TalentClaimPolicy.assertNotDuplicateActiveClaim(existing))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无需重复认领");
    }

    @Test
    void protectedUntil_shouldAddDays() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 6, 1, 10, 0);
        assertThat(TalentClaimPolicy.protectedUntil(claimedAt, 7))
                .isEqualTo(LocalDateTime.of(2026, 6, 8, 10, 0));
    }

    @Test
    void selectReleaseTarget_shouldPreferCurrentUser() {
        TalentClaim older = claim(USER_B, LocalDateTime.of(2026, 6, 1, 0, 0));
        TalentClaim mine = claim(USER_A, LocalDateTime.of(2026, 6, 2, 0, 0));
        TalentClaim selected = TalentClaimPolicy.selectReleaseTarget(List.of(older, mine), USER_A, false);
        assertThat(selected.getUserId()).isEqualTo(USER_A);
    }

    @Test
    void selectReleaseTarget_shouldRejectNonOwnerNonAdmin() {
        TalentClaim other = claim(USER_B, LocalDateTime.now());
        assertThatThrownBy(() -> TalentClaimPolicy.selectReleaseTarget(List.of(other), USER_A, false))
                .isInstanceOf(ForbiddenException.class);
    }

    private static TalentClaim claim(UUID userId, LocalDateTime claimedAt) {
        TalentClaim claim = new TalentClaim();
        claim.setUserId(userId);
        claim.setClaimedAt(claimedAt);
        return claim;
    }
}
