package com.colonel.saas.gateway.douyin.test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 测试环境 Mock 活动与商品的生成工具类。
 * <p>
 * 为 {@link TestDouyinActivityGateway} 和 {@link TestDouyinProductGateway} 提供统一的
 * Mock 数据生成逻辑，确保测试环境中活动列表与商品列表在状态、时间、价格等维度上保持一致。
 * </p>
 *
 * <ul>
 *   <li><b>活动状态生成</b>：根据 activityId 的种子值（seed）循环分配 6 种抖店活动状态（未上线、报名未开始、报名中、推广未开始、推广中、报名结束）</li>
 *   <li><b>商品状态生成</b>：根据商品自身排名生成联盟状态，不读取活动状态</li>
 *   <li><b>时间口径</b>：活动与推广的起止日期基于当前日期相对计算，确保本地联调时数据始终处于合理的时间窗口内</li>
 *   <li><b>工具方法</b>：提供价格格式化、游标解析、安全的 Long 转换等公共工具方法</li>
 * </ul>
 *
 * <p>架构角色：包级私有工具类，仅在 {@code com.colonel.saas.gateway.douyin.test} 包内使用，
 * 不对外暴露。所属领域：商品域 / 活动域（测试适配器层）。</p>
 *
 * @see TestDouyinActivityGateway
 * @see TestDouyinProductGateway
 */
final class TestMockActivityProductSupport {

    /** 活动状态循环序列：未上线(1) -> 报名未开始(2) -> 报名中(3) -> 推广未开始(4) -> 推广中(5) -> 报名结束(7) */
    private static final int[] ACTIVITY_STATUS_CYCLE = {1, 2, 3, 4, 5, 7};

    /** 日期格式化器，输出 ISO 本地日期格式（如 2026-05-27） */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 私有构造函数，防止实例化工具类 */
    private TestMockActivityProductSupport() {
    }

    /**
     * 根据活动种子值解析 Mock 活动状态。
     * <p>处理流程：</p>
     * <ol>
     *   <li>如果 activitySeed 在 [100001, 100036] 范围内，按 (activitySeed - 100001) % 6 映射到状态循环数组</li>
     *   <li>对于演示活动列表范围外的 activityId，默认返回状态 5（推广中），便于本地联调时商品库展示</li>
     * </ol>
     *
     * @param activitySeed 活动 ID，作为确定性随机种子
     * @return 活动状态码（1=未上线, 2=报名未开始, 3=报名中, 4=推广未开始, 5=推广中, 7=报名结束）
     */
    static int resolveMockActivityStatus(long activitySeed) {
        if (activitySeed >= 100001L && activitySeed <= 100036L) {
            int index = (int) (activitySeed - 100001L);
            return ACTIVITY_STATUS_CYCLE[Math.floorMod(index, ACTIVITY_STATUS_CYCLE.length)];
        }
        // 演示活动列表外的 activityId 默认按「推广中」生成商品，便于本地联调商品库展示。
        return 5;
    }

    /**
     * 根据商品排名解析 Mock 商品在联盟中的状态。
     * <p>处理流程：</p>
     * <ol>
     *   <li>仅使用商品排名构造稳定分布</li>
     *   <li>同一活动下可同时出现待审核、推广中、申请未通过、合作已终止、合作已到期</li>
     * </ol>
     *
     * @param activitySeed 活动 ID 种子值，仅用于保持方法签名兼容
     * @param rank         商品在列表中的排名（从 1 开始）
     * @return 商品状态码（0=待审核, 1=推广中, 2=申请未通过, 3=合作已终止, 6=合作已到期）
     */
    static int resolveMockProductStatus(long activitySeed, int rank) {
        return switch (Math.floorMod(rank, 5)) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            default -> 6;
        };
    }

    /**
     * 获取 Mock 活动开始日期：当前日期前推 14 天。
     *
     * @return 格式化的日期字符串（如 "2026-05-13"）
     */
    static String mockActivityStartDate() {
        return LocalDate.now().minusDays(14).format(DATE_FORMAT);
    }

    /**
     * 获取 Mock 活动结束日期：当前日期后推 45 天。
     *
     * @return 格式化的日期字符串（如 "2026-07-11"）
     */
    static String mockActivityEndDate() {
        return LocalDate.now().plusDays(45).format(DATE_FORMAT);
    }

    /**
     * 获取 Mock 推广开始日期：当前日期前推 7 天。
     *
     * @return 格式化的日期字符串（如 "2026-05-20"）
     */
    static String mockPromotionStartDate() {
        return LocalDate.now().minusDays(7).format(DATE_FORMAT);
    }

    /**
     * 获取 Mock 推广结束日期：当前日期后推 30 天。
     *
     * @return 格式化的日期字符串（如 "2026-06-26"）
     */
    static String mockPromotionEndDate() {
        return LocalDate.now().plusDays(30).format(DATE_FORMAT);
    }

    /**
     * 将商品状态码转换为中文状态文本。
     *
     * @param status 商品状态码（0=待审核, 1=推广中, 2=申请未通过, 3=合作已终止, 6=合作已到期；4 为历史兼容，按合作已终止展示）
     * @return 对应的中文状态描述，未知状态返回 "未知状态"
     */
    static String productStatusText(int status) {
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "推广中";
            case 2 -> "申请未通过";
            case 3 -> "合作已终止";
            case 4 -> "合作已终止";
            case 6 -> "合作已到期";
            default -> "未知状态";
        };
    }

    /**
     * 安全地将字符串转换为 long 值。
     * <p>当输入为 null、空白字符串或解析失败时，返回默认值。</p>
     *
     * @param value        待转换的字符串
     * @param defaultValue 转换失败时的默认值
     * @return 解析后的 long 值，或默认值
     */
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

    /**
     * 解析游标字符串为非负整数索引。
     * <p>当输入为 null、空白字符串或解析失败时，返回 0（即从头开始）。</p>
     *
     * @param cursor 游标字符串（通常由上次分页结果的 nextCursor 返回）
     * @return 解析后的非负整数索引，解析失败返回 0
     */
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

    /**
     * 将分为单位的价格转换为元为单位的格式化字符串。
     * <p>例如：9900 分 -> "99.00" 元</p>
     *
     * @param price 以分为单位的价格（long 类型）
     * @return 格式化后的价格字符串，保留两位小数
     */
    static String formatPriceText(long price) {
        return String.format(Locale.ROOT, "%.2f", price / 100.0);
    }
}
