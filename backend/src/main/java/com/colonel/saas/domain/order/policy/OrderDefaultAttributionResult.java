package com.colonel.saas.domain.order.policy;

import com.colonel.saas.domain.shared.attribution.AttributionSource;
import com.colonel.saas.service.AttributionService;

import java.util.UUID;

/** 订单默认归因结果；渠道和招商是两个互不覆盖的事实维度。 */
public record OrderDefaultAttributionResult(
        UUID defaultChannelUserId,
        UUID channelDeptId,
        UUID defaultRecruiterId,
        String channelAttributionSource,
        String recruiterAttributionSource,
        String attributionStatus,
        String attributionRemark,
        OrderLinkAttributionResolution linkResolution,
        UUID talentId,
        String talentUid,
        String activityId) {

    public static OrderDefaultAttributionResult attributed(
            UUID defaultChannelUserId,
            UUID channelDeptId,
            UUID defaultRecruiterId,
            String channelAttributionSource,
            String recruiterAttributionSource,
            UUID talentId,
            String talentUid,
            String activityId,
            OrderLinkAttributionResolution linkResolution) {
        return new OrderDefaultAttributionResult(
                defaultChannelUserId,
                channelDeptId,
                defaultRecruiterId,
                sourceOrUnattributed(channelAttributionSource),
                sourceOrUnattributed(recruiterAttributionSource),
                AttributionService.STATUS_ATTRIBUTED,
                linkResolution == null ? AttributionService.REASON_ATTRIBUTED : linkResolution.reason(),
                linkResolution,
                talentId,
                talentUid,
                activityId);
    }

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
            String channelAttributionSource,
            String recruiterAttributionSource,
            String remark,
            OrderLinkAttributionResolution linkResolution) {
        return new OrderDefaultAttributionResult(
                null,
                null,
                null,
                sourceOrUnattributed(channelAttributionSource),
                sourceOrUnattributed(recruiterAttributionSource),
                AttributionService.STATUS_UNATTRIBUTED,
                remark,
                linkResolution,
                talentId,
                talentUid,
                activityId);
    }

    private static String sourceOrUnattributed(String source) {
        return source == null || source.isBlank() ? AttributionSource.UNATTRIBUTED : source;
    }

    private static String aggregateStatus(boolean channel, boolean recruiter) {
        if (channel || recruiter) {
            return STATUS_ATTRIBUTED;
        }
        return STATUS_UNATTRIBUTED;
    }
}
