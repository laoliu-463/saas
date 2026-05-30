package com.colonel.saas.domain;

/**
 * 抖店活动状态码与文本的相互转换工具。
 * <p>
 * 状态码定义（与抖音精选联盟 API 一致）：
 * <ul>
 *   <li>1 = 未上线</li>
 *   <li>2 = 报名未开始</li>
 *   <li>3 = 报名中</li>
 *   <li>4 = 推广未开始</li>
 *   <li>5 = 推广中</li>
 *   <li>7 = 报名结束</li>
 * </ul>
 */
public final class ActivityStatusResolver {

    /** 未上线 */
    public static final int STATUS_NOT_ONLINE = 1;
    /** 报名未开始 */
    public static final int STATUS_SIGNUP_NOT_STARTED = 2;
    /** 报名中 */
    public static final int STATUS_SIGNUP_IN_PROGRESS = 3;
    /** 推广未开始 */
    public static final int STATUS_PROMOTION_NOT_STARTED = 4;
    /** 推广中 */
    public static final int STATUS_PROMOTING = 5;
    /** 报名结束 */
    public static final int STATUS_SIGNUP_ENDED = 7;

    private ActivityStatusResolver() {
    }

    /**
     * 将活动状态码转为中文描述。
     *
     * @param status 活动状态码
     * @return 中文状态描述，未知状态返回 "任意状态"
     */
    public static String toText(int status) {
        return switch (status) {
            case STATUS_NOT_ONLINE -> "未上线";
            case STATUS_SIGNUP_NOT_STARTED -> "报名未开始";
            case STATUS_SIGNUP_IN_PROGRESS -> "报名中";
            case STATUS_PROMOTION_NOT_STARTED -> "推广未开始";
            case STATUS_PROMOTING -> "推广中";
            case STATUS_SIGNUP_ENDED -> "报名结束";
            default -> "任意状态";
        };
    }

    /**
     * 判断状态文本是否表示推广中。
     *
     * @param statusText 状态文本
     * @return true 表示推广中
     */
    public static boolean isPromotingText(String statusText) {
        return statusText != null && statusText.contains("推广中");
    }
}
