package com.colonel.saas.domain.talent.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.entity.TalentClaim;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 达人认领策略 Policy（DDD-TALENT-002）。
 *
 * <p>抽取认领/释放的纯规则判断，不含持久化与分布式锁。</p>
 */
public final class TalentClaimPolicy {

    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_EXPIRED = 2;
    public static final int STATUS_RELEASED = 3;
    public static final int TYPE_MANUAL = 1;

    private TalentClaimPolicy() {
    }

    public static void requireClaimUser(UUID userId) {
        if (userId == null) {
            throw BusinessException.param("缺少登录用户");
        }
    }

    public static void assertNotDuplicateActiveClaim(TalentClaim activeClaimByUser) {
        if (activeClaimByUser != null) {
            throw BusinessException.duplicate("你已认领该达人，无需重复认领");
        }
    }

    public static void requireActiveClaims(List<TalentClaim> activeClaims) {
        if (activeClaims == null || activeClaims.isEmpty()) {
            throw BusinessException.stateInvalid("达人当前无有效认领记录");
        }
    }

    public static LocalDateTime protectedUntil(LocalDateTime claimedAt, int protectDays) {
        LocalDateTime base = claimedAt == null ? LocalDateTime.now() : claimedAt;
        int days = Math.max(protectDays, 0);
        return base.plusDays(days);
    }

    public static boolean canRelease(TalentClaim claim, UUID userId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        return claim != null && userId != null && userId.equals(claim.getUserId());
    }

    /**
     * 在有效认领列表中选出可释放的目标：优先当前用户，再按认领时间倒序。
     */
    public static TalentClaim selectReleaseTarget(
            List<TalentClaim> activeClaims,
            UUID userId,
            boolean isAdmin) {
        requireActiveClaims(activeClaims);
        return activeClaims.stream()
                .sorted(Comparator.comparing((TalentClaim claim) -> !userId.equals(claim.getUserId()))
                        .thenComparing(TalentClaim::getClaimedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(claim -> canRelease(claim, userId, isAdmin))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("仅认领人或管理员可以释放达人"));
    }
}
