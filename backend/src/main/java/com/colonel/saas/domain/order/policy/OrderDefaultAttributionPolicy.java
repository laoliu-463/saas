package com.colonel.saas.domain.order.policy;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.AttributionService.AttributionResult;
import org.springframework.util.StringUtils;

/**
 * 订单默认归因 Policy（DDD-ORDER-004 / SLIM-ORDER-002）。
 *
 * <p>只负责把 {@link AttributionResult} 写入订单实体及同步统计分类，
 * 不执行跨域 Mapper 查询（仍由 {@link AttributionService} 承担）。</p>
 */
public final class OrderDefaultAttributionPolicy {

    public enum UnattributedBucket {
        NONE,
        NO_PICK_SOURCE,
        NO_MAPPING
    }

    private OrderDefaultAttributionPolicy() {
    }

    public static void applyInitialUnattributedStatus(ColonelsettlementOrder order) {
        if (order == null) {
            return;
        }
        order.setAttributionStatus(AttributionService.STATUS_UNATTRIBUTED);
    }

    public static void applyAttributionResult(
            ColonelsettlementOrder order,
            AttributionResult result,
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
