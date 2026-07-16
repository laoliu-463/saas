package com.colonel.saas.domain.order.policy;

import com.colonel.saas.domain.order.policy.OrderLinkAttributionResolution.Status;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;
import com.colonel.saas.domain.shared.attribution.AttributionSource;
import com.colonel.saas.domain.shared.policy.DomainText;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.AttributionService;

import java.util.UUID;

/**
 * 订单默认归因 Policy（DDD-ORDER-004）。
 *
 * <p>推广链接归属以创建时固化的 owner type 为准：招商链接只写招商，渠道链接只写渠道。
 * 活动招商仅在招商维度为空时回退；商品负责人不参与默认归因。</p>
 */
public final class OrderDefaultAttributionPolicy {

    public enum UnattributedBucket {
        NONE,
        NO_PICK_SOURCE,
        NO_MAPPING
    }

    public record RecruiterLookup(UUID activityDefaultRecruiterId, boolean lookupFailed) {
    }

    private OrderDefaultAttributionPolicy() {
    }

    public static OrderDefaultAttributionResult resolve(
            OrderAttributionInput input,
            OrderLinkAttributionResolution linkResolution,
            RecruiterLookup recruiterLookup) {
        if (input == null) {
            return OrderDefaultAttributionResult.unattributed(
                    null, null, null,
                    AttributionSource.UNATTRIBUTED,
                    AttributionSource.UNATTRIBUTED,
                    AttributionService.REASON_SYNC_FAILED,
                    linkResolution);
        }

        UUID channelUserId = null;
        UUID channelDeptId = null;
        UUID recruiterId = null;
        String channelSource = channelSource(linkResolution);
        String recruiterSource = AttributionSource.UNATTRIBUTED;

        if (isUniqueOwner(linkResolution, AttributionOwnerType.RECRUITER)) {
            recruiterId = linkResolution.userId();
            recruiterSource = linkResolution.source();
        } else if (isUniqueOwner(linkResolution, AttributionOwnerType.CHANNEL)) {
            channelUserId = linkResolution.userId();
            channelDeptId = linkResolution.deptId();
            channelSource = linkResolution.source();
        }

        if (recruiterId == null && recruiterLookup != null && !recruiterLookup.lookupFailed()
                && recruiterLookup.activityDefaultRecruiterId() != null) {
            recruiterId = recruiterLookup.activityDefaultRecruiterId();
            recruiterSource = AttributionSource.ACTIVITY_OWNER;
        }

        if (channelUserId != null || recruiterId != null) {
            return OrderDefaultAttributionResult.attributed(
                    channelUserId,
                    channelDeptId,
                    recruiterId,
                    channelSource,
                    recruiterSource,
                    input.talentId(),
                    input.talentUid(),
                    input.activityId(),
                    linkResolution);
        }

        return OrderDefaultAttributionResult.unattributed(
                input.talentId(),
                input.talentUid(),
                input.activityId(),
                channelSource,
                recruiterSource,
                unattributedRemark(input, linkResolution),
                linkResolution);
    }

    public static void applyToOrder(
            ColonelsettlementOrder order,
            OrderDefaultAttributionResult result,
            String talentName) {
        if (order == null || result == null) {
            return;
        }
        order.setChannelUserId(result.defaultChannelUserId());
        order.setChannelDeptId(result.channelDeptId());
        // legacy user/dept bridge follows channel only and must not overwrite招商归属。
        order.setUserId(result.defaultChannelUserId());
        order.setDeptId(result.channelDeptId());
        order.setColonelUserId(result.defaultRecruiterId());
        order.setTalentId(result.talentId());
        order.setActivityId(firstNonBlank(result.activityId(), order.getActivityId()));
        order.setChannelAttributionSource(result.channelAttributionSource());
        order.setRecruiterAttributionSource(result.recruiterAttributionSource());
        order.setAttributionStatus(result.attributionStatus());
        order.setAttributionRemark(result.attributionRemark());
        order.setProductTitle(order.getProductName());
        order.setTalentName(talentName);
    }

    /** @deprecated SLIM-ORDER-002 legacy bridge; prefer {@link #applyToOrder(ColonelsettlementOrder, OrderDefaultAttributionResult, String)}. */
    @Deprecated
    public static void applyAttributionResult(
            ColonelsettlementOrder order,
            AttributionService.AttributionResult result,
            String fallbackActivityId,
            String talentName) {
        if (order == null || result == null) {
            return;
        }
        order.setChannelUserId(result.channelUserId());
        order.setChannelDeptId(result.deptId());
        order.setUserId(result.userId());
        order.setDeptId(result.deptId());
        order.setColonelUserId(result.colonelUserId());
        order.setTalentId(result.talentId());
        order.setActivityId(firstNonBlank(result.activityId(), fallbackActivityId));
        order.setAttributionStatus(result.attributionStatus());
        order.setAttributionRemark(result.attributionRemark());
        order.setProductTitle(order.getProductName());
        order.setTalentName(talentName);
    }

    public static void applyInitialUnattributedStatus(ColonelsettlementOrder order) {
        if (order != null) {
            order.setAttributionStatus(AttributionService.STATUS_UNATTRIBUTED);
        }
    }

    public static boolean isAttributed(String attributionStatus) {
        return AttributionService.STATUS_ATTRIBUTED.equals(attributionStatus);
    }

    public static UnattributedBucket classifyUnattributedRemark(String remark) {
        if (AttributionService.REASON_NO_PICK_SOURCE.equals(remark)) {
            return UnattributedBucket.NO_PICK_SOURCE;
        }
        if (AttributionService.REASON_MAPPING_NOT_FOUND.equals(remark)
                || AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND.equals(remark)
                || "MAPPING_NOT_FOUND".equals(remark)) {
            return UnattributedBucket.NO_MAPPING;
        }
        return UnattributedBucket.NONE;
    }

    public static AttributionService.AttributionResult toLegacyResult(OrderDefaultAttributionResult result) {
        if (result == null) {
            return AttributionService.AttributionResult.unattributed(
                    null, null, null, null, AttributionService.REASON_SYNC_FAILED, AttributionService.NativeMappingTrace.none());
        }
        if (isAttributed(result.attributionStatus())) {
            return AttributionService.AttributionResult.attributed(
                    result.defaultChannelUserId(),
                    result.channelDeptId(),
                    result.defaultChannelUserId(),
                    result.talentId(),
                    result.talentUid(),
                    result.activityId(),
                    result.defaultRecruiterId(),
                    result.attributionRemark(),
                    AttributionService.NativeMappingTrace.none());
        }
        return AttributionService.AttributionResult.unattributed(
                result.talentId(),
                result.talentUid(),
                result.activityId(),
                result.defaultRecruiterId(),
                result.attributionRemark(),
                AttributionService.NativeMappingTrace.none());
    }

    private static boolean isUniqueOwner(
            OrderLinkAttributionResolution resolution,
            AttributionOwnerType ownerType) {
        return resolution != null
                && resolution.status() == Status.UNIQUE
                && resolution.ownerType() == ownerType
                && resolution.userId() != null;
    }

    private static String channelSource(OrderLinkAttributionResolution resolution) {
        if (resolution != null && resolution.status() == Status.AMBIGUOUS) {
            return AttributionSource.AMBIGUOUS;
        }
        if (isUniqueOwner(resolution, AttributionOwnerType.CHANNEL)) {
            return resolution.source();
        }
        return AttributionSource.UNATTRIBUTED;
    }

    private static String unattributedRemark(
            OrderAttributionInput input,
            OrderLinkAttributionResolution resolution) {
        if (resolution != null && DomainText.hasText(resolution.reason())) {
            return resolution.reason();
        }
        if (!DomainText.hasText(input.pickSource())
                && !DomainText.hasText(input.pickExtra())
                && !DomainText.hasText(input.colonelBuyinId())
                && !DomainText.hasText(input.secondColonelBuyinId())) {
            return AttributionService.REASON_NO_PICK_SOURCE;
        }
        return AttributionService.REASON_MAPPING_NOT_FOUND;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (DomainText.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
