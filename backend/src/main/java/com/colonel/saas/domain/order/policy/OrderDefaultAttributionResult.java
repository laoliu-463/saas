package com.colonel.saas.domain.order.policy;

import java.util.UUID;

/**
 * 订单默认归因结果（DDD-ORDER-004）。仅包含默认渠道与默认招商，不含独家/最终归属/提成。
 */
public record OrderDefaultAttributionResult(
        UUID defaultChannelUserId,
        UUID channelDeptId,
        UUID defaultRecruiterId,
        UUID talentId,
        String talentUid,
        String activityId,
        String attributionStatus,
        String attributionRemark) {

    public static OrderDefaultAttributionResult unattributed(
            UUID talentId,
            String talentUid,
            String activityId,
            UUID defaultRecruiterId,
            String remark) {
        return new OrderDefaultAttributionResult(
                null,
                null,
                defaultRecruiterId,
                talentId,
                talentUid,
                activityId,
                com.colonel.saas.service.AttributionService.STATUS_UNATTRIBUTED,
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
        return new OrderDefaultAttributionResult(
                defaultChannelUserId,
                channelDeptId,
                defaultRecruiterId,
                talentId,
                talentUid,
                activityId,
                com.colonel.saas.service.AttributionService.STATUS_ATTRIBUTED,
                remark);
    }
}
