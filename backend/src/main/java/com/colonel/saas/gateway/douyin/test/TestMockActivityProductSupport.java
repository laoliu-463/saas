package com.colonel.saas.gateway.douyin.test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Test Mock 活动商品生成口径：推广期相对当前日期，活动「推广中」时商品以联盟「推广中」为主。
 */
final class TestMockActivityProductSupport {

    private static final int[] ACTIVITY_STATUS_CYCLE = {1, 2, 3, 4, 5, 7};
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private TestMockActivityProductSupport() {
    }

    static int resolveMockActivityStatus(long activitySeed) {
        if (activitySeed >= 100001L && activitySeed <= 100036L) {
            int index = (int) (activitySeed - 100001L);
            return ACTIVITY_STATUS_CYCLE[Math.floorMod(index, ACTIVITY_STATUS_CYCLE.length)];
        }
        // 演示活动列表外的 activityId 默认按「推广中」生成商品，便于本地联调商品库展示。
        return 5;
    }

    static boolean isPromotingActivity(long activitySeed) {
        return resolveMockActivityStatus(activitySeed) == 5;
    }

    static int resolveMockProductStatus(long activitySeed, int rank) {
        if (isPromotingActivity(activitySeed)) {
            return switch (Math.floorMod(rank, 8)) {
                case 0 -> 0;
                case 1 -> 2;
                case 2 -> 3;
                case 3 -> 6;
                default -> 1;
            };
        }
        return switch (Math.floorMod(rank, 6)) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 6;
            default -> 4;
        };
    }

    static String mockActivityStartDate() {
        return LocalDate.now().minusDays(14).format(DATE_FORMAT);
    }

    static String mockActivityEndDate() {
        return LocalDate.now().plusDays(45).format(DATE_FORMAT);
    }

    static String mockPromotionStartDate() {
        return LocalDate.now().minusDays(7).format(DATE_FORMAT);
    }

    static String mockPromotionEndDate() {
        return LocalDate.now().plusDays(30).format(DATE_FORMAT);
    }

    static String productStatusText(int status) {
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "推广中";
            case 2 -> "申请未通过";
            case 3 -> "合作已终止";
            case 4 -> "合作前取消";
            case 6 -> "合作已到期";
            default -> "未知状态";
        };
    }

    static long asLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    static int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (Exception ex) {
            return 0;
        }
    }

    static String formatPriceText(long price) {
        return String.format(Locale.ROOT, "%.2f", price / 100.0);
    }
}
