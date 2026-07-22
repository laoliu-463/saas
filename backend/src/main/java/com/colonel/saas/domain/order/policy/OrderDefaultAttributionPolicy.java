package com.colonel.saas.domain.order.policy;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.domain.shared.policy.DomainText;

import java.util.UUID;

/**
 * 订单默认归因 Policy（DDD-ORDER-004）。
 *
 * <p>只计算默认渠道（pick_source → mapping → channel）与默认招商（商品负责人 / 活动默认负责人）。
 * 不应用独家达人/独家商家，不计算最终归属、提成或毛利。</p>
 */
public final class OrderDefaultAttributionPolicy {

    public enum UnattributedBucket {
        NONE,
        NO_PICK_SOURCE,
        NO_MAPPING
    }

    public record RecruiterLookup(
            UUID productAssigneeId,
            UUID activityDefaultRecruiterId,
            boolean lookupFailed) {
    }

    private OrderDefaultAttributionPolicy() {
    }

    public static OrderDefaultAttributionResult resolve(
            OrderAttributionInput input,
            PickSourceMapping channelMapping,
            RecruiterLookup recruiterLookup) {
        if (input == null) {
            return OrderDefaultAttributionResult.unattributed(
                    null, null, null, null, AttributionService.REASON_SYNC_FAILED);
        }
        if (!DomainText.hasText(input.productId())) {
            return OrderDefaultAttributionResult.unattributed(
                    input.talentId(),
                    input.talentUid(),
                    input.activityId(),
                    resolveDefaultRecruiter(recruiterLookup),
                    AttributionService.REASON_PRODUCT_NOT_FOUND);
        }

        UUID defaultRecruiterId = resolveDefaultRecruiter(recruiterLookup);

        if (input.hasNativeColonelIdentity() && channelMapping == null) {
            return OrderDefaultAttributionResult.unattributed(
                    input.talentId(),
                    input.talentUid(),
                    input.activityId(),
                    defaultRecruiterId,
                    AttributionService.REASON_MAPPING_NOT_FOUND);
        }

        if (!input.hasNativeColonelIdentity()
                && !DomainText.hasText(input.pickSource())
                && !DomainText.hasText(input.pickExtra())) {
            return OrderDefaultAttributionResult.unattributed(
                    input.talentId(),
                    input.talentUid(),
                    input.activityId(),
                    defaultRecruiterId,
                    AttributionService.REASON_NO_PICK_SOURCE);
        }

        if (channelMapping == null) {
            return OrderDefaultAttributionResult.unattributed(
                    input.talentId(),
                    input.talentUid(),
                    input.activityId(),
                    defaultRecruiterId,
                    AttributionService.REASON_MAPPING_NOT_FOUND);
        }
        if (channelMapping.getUserId() == null) {
            return OrderDefaultAttributionResult.unattributed(
                    input.talentId(),
                    input.talentUid(),
                    input.activityId(),
                    defaultRecruiterId,
                    AttributionService.REASON_CHANNEL_NOT_FOUND);
        }

        String resolvedActivityId = firstNonBlank(
                channelMapping.getActivityId(),
                input.activityId());
        return OrderDefaultAttributionResult.attributedChannel(
                channelMapping.getUserId(),
                channelMapping.getDeptId(),
                input.talentId(),
                input.talentUid(),
                resolvedActivityId,
                defaultRecruiterId,
                AttributionService.REASON_ATTRIBUTED);
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
        order.setUserId(result.defaultChannelUserId());
        order.setDeptId(result.channelDeptId());
        order.setColonelUserId(result.defaultRecruiterId());
        order.setTalentId(result.talentId());
        order.setActivityId(firstNonBlank(result.activityId(), order.getActivityId()));
        order.setAttributionStatus(result.attributionStatus());
        order.setAttributionRemark(result.attributionRemark());
        order.setChannelAttributionStatus(result.channelAttributionStatus());
        order.setRecruiterAttributionStatus(result.recruiterAttributionStatus());
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
        if (order == null) {
            return;
        }
        order.setAttributionStatus(AttributionService.STATUS_UNATTRIBUTED);
    }

    public static boolean isAttributed(String attributionStatus) {
        return AttributionService.STATUS_ATTRIBUTED.equals(attributionStatus);
    }

    public static UnattributedBucket classifyUnattributedRemark(String remark) {
        if (AttributionService.REASON_NO_PICK_SOURCE.equals(remark)) {
            return UnattributedBucket.NO_PICK_SOURCE;
        }
        if (AttributionService.REASON_MAPPING_NOT_FOUND.equals(remark)
                || AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND.equals(remark)) {
            return UnattributedBucket.NO_MAPPING;
        }
        return UnattributedBucket.NONE;
    }

    public static AttributionService.AttributionResult toLegacyResult(OrderDefaultAttributionResult result) {
        if (result == null) {
            return AttributionService.AttributionResult.unattributed(
                    null, null, null, null, AttributionService.REASON_SYNC_FAILED, AttributionService.NativeMappingTrace.none());
        }
        return new AttributionService.AttributionResult(
                result.defaultChannelUserId(),
                result.channelDeptId(),
                result.defaultChannelUserId(),
                result.talentId(),
                result.talentUid(),
                result.activityId(),
                result.defaultRecruiterId(),
                result.attributionStatus(),
                result.attributionRemark(),
                AttributionService.NativeMappingTrace.none());
    }

    private static UUID resolveDefaultRecruiter(RecruiterLookup recruiterLookup) {
        if (recruiterLookup == null || recruiterLookup.lookupFailed()) {
            return null;
        }
        if (recruiterLookup.productAssigneeId() != null) {
            return recruiterLookup.productAssigneeId();
        }
        return recruiterLookup.activityDefaultRecruiterId();
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
