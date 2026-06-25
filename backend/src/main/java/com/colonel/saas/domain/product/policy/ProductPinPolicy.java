package com.colonel.saas.domain.product.policy;

import com.colonel.saas.entity.ProductOperationState;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品置顶纯规则 Policy（DDD-PRODUCT-003）。
 *
 * <p>从 {@link com.colonel.saas.service.ProductPinService} 抽离的置顶判定、配额与权限规则，
 * 无 Spring 依赖，便于单测与复用。</p>
 */
public final class ProductPinPolicy {

    /** 每位用户最多同时置顶的规格数量。 */
    public static final int MAX_PINNED_PER_USER = 10;
    /** 置顶有效期（小时）。 */
    public static final int PIN_HOURS = 24;

    private ProductPinPolicy() {
    }

    /** 运营状态是否仍在置顶有效期内。 */
    public static boolean isPinned(ProductOperationState state, LocalDateTime now) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        return state != null
                && state.getPinnedUntil() != null
                && state.getPinnedUntil().isAfter(effectiveNow);
    }

    /**
     * 列表展示用置顶判定（兼容 legacy {@code Product.pinned} 无截止时间字段）。
     */
    public static boolean isPinnedForPresentation(boolean pinned, LocalDateTime pinnedUntil, LocalDateTime now) {
        if (!pinned) {
            return false;
        }
        if (pinnedUntil == null) {
            return true;
        }
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        return pinnedUntil.isAfter(effectiveNow);
    }

    /** 非刷新置顶时是否超过用户配额。 */
    public static boolean exceedsQuota(long activePins, boolean alreadyPinned) {
        if (alreadyPinned) {
            return false;
        }
        return activePins >= MAX_PINNED_PER_USER;
    }

    /** 计算新的置顶截止时间。 */
    public static LocalDateTime pinExpiresAt(LocalDateTime now) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        return effectiveNow.plusHours(PIN_HOURS);
    }

    /** 取消置顶权限：管理员（requesterId=null）或置顶人本人。 */
    public static boolean canUnpin(UUID pinnedBy, UUID requesterId) {
        if (requesterId == null) {
            return true;
        }
        if (pinnedBy == null) {
            return true;
        }
        return pinnedBy.equals(requesterId);
    }
}
