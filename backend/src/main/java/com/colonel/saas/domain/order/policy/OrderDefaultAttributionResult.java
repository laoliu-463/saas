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
        String channelAttributionStatus,
        String recruiterAttributionStatus,
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
                statusFor(defaultChannelUserId),
                statusFor(defaultRecruiterId),
                aggregateStatus(defaultChannelUserId, defaultRecruiterId),
                linkResolution == null ? AttributionService.REASON_ATTRIBUTED : linkResolution.reason(),
                linkResolution,
                talentId,
                talentUid,
                activityId);
    }

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
                AttributionService.STATUS_UNATTRIBUTED,
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

    private static String statusFor(UUID userId) {
        return userId == null ? AttributionService.STATUS_UNATTRIBUTED : AttributionService.STATUS_ATTRIBUTED;
    }

    private static String aggregateStatus(UUID channelUserId, UUID recruiterUserId) {
        if (channelUserId != null && recruiterUserId != null) {
            return AttributionService.STATUS_ATTRIBUTED;
        }
        if (channelUserId != null || recruiterUserId != null) {
            return "PARTIAL";
        }
        return AttributionService.STATUS_UNATTRIBUTED;
    }
}
