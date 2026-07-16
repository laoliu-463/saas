package com.colonel.saas.domain.order.policy;

import java.util.UUID;

/**
 * 订单默认归因结果（DDD-ORDER-004）。
 *
 * <p>表达订单事实层的默认归属：渠道维度（pick_source → mapping → channel）和
 * 招商维度（商品负责人 / 活动默认负责人）独立计算，分别落渠道归属状态
 * 和招商归属状态。
 *
 * <p>不包含独家/最终归属/提成——这些由业绩域在收到事件后计算。</p>
 */
public record OrderDefaultAttributionResult(
        UUID defaultChannelUserId,
        UUID channelDeptId,
        UUID defaultRecruiterId,
        UUID recruiterDeptId,
        UUID talentId,
        String talentUid,
        String activityId,
        String channelAttributionStatus,
        String recruiterAttributionStatus,
        String attributionStatus,
        String attributionRemark) {

    public static final String CHANNEL_ATTRIBUTED = "CHANNEL_ATTRIBUTED";
    public static final String CHANNEL_UNATTRIBUTED = "CHANNEL_UNATTRIBUTED";
    public static final String RECRUITER_ATTRIBUTED = "RECRUITER_ATTRIBUTED";
    public static final String RECRUITER_UNATTRIBUTED = "RECRUITER_UNATTRIBUTED";
    public static final String STATUS_ATTRIBUTED = "ATTRIBUTED";
    public static final String STATUS_UNATTRIBUTED = "UNATTRIBUTED";

    public static OrderDefaultAttributionResult unattributed(
            UUID talentId,
            String talentUid,
            String activityId,
            UUID defaultRecruiterId,
            String remark) {
        boolean recruiterAttributed = defaultRecruiterId != null;
        boolean channelAttributed = false;
        return new OrderDefaultAttributionResult(
                null,
                null,
                defaultRecruiterId,
                null,
                talentId,
                talentUid,
                activityId,
                channelAttributed ? CHANNEL_ATTRIBUTED : CHANNEL_UNATTRIBUTED,
                recruiterAttributed ? RECRUITER_ATTRIBUTED : RECRUITER_UNATTRIBUTED,
                aggregateStatus(channelAttributed, recruiterAttributed),
                remark);
    }

    public static OrderDefaultAttributionResult attributedChannel(
            UUID defaultChannelUserId,
            UUID channelDeptId,
            UUID talentId,
            String talentUid,
            String activityId,
            UUID defaultRecruiterId,
            String remark) {
        boolean channelAttributed = defaultChannelUserId != null;
        boolean recruiterAttributed = defaultRecruiterId != null;
        return new OrderDefaultAttributionResult(
                defaultChannelUserId,
                channelDeptId,
                defaultRecruiterId,
                null,
                talentId,
                talentUid,
                activityId,
                channelAttributed ? CHANNEL_ATTRIBUTED : CHANNEL_UNATTRIBUTED,
                recruiterAttributed ? RECRUITER_ATTRIBUTED : RECRUITER_UNATTRIBUTED,
                aggregateStatus(channelAttributed, recruiterAttributed),
                remark);
    }

    private static String aggregateStatus(boolean channel, boolean recruiter) {
        if (channel && recruiter) {
            return STATUS_ATTRIBUTED;
        }
        return STATUS_UNATTRIBUTED;
    }
}
