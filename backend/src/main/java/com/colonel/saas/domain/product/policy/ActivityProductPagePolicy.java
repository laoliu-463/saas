package com.colonel.saas.domain.product.policy;

/**
 * 活动商品列表分页语义 Policy。
 *
 * <p>只收口后端过滤后的 total、cursor 和 hasMore 计算，不持有查询或展示逻辑。</p>
 */
public final class ActivityProductPagePolicy {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 20;

    private ActivityProductPagePolicy() {
    }

    public static int normalizePageSize(Integer requestedCount) {
        int count = requestedCount == null ? DEFAULT_PAGE_SIZE : requestedCount;
        return Math.min(Math.max(count, 1), MAX_PAGE_SIZE);
    }

    public static int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static boolean hasMore(Long filteredTotal, int nextOffset) {
        return filteredTotal != null && nextOffset < filteredTotal;
    }

    public static String nextCursor(Long filteredTotal, int nextOffset) {
        return hasMore(filteredTotal, nextOffset) ? String.valueOf(nextOffset) : "";
    }

    public static long responseTotal(Long filteredTotal, int currentPageSize) {
        return filteredTotal == null ? currentPageSize : filteredTotal;
    }
}
