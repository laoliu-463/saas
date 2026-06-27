package com.colonel.saas.domain.product.policy;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.domain.shared.policy.DomainText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品人工审核的状态和日志语义。
 */
public class ProductAuditDecisionPolicy {

    public AuditDecision resolve(boolean approved, String reason, Map<String, Object> supplement) {
        if (approved) {
            Map<String, Object> normalizedSupplement = ProductAuditSupplementPayload.normalize(supplement);
            requireApprovalSupplement(normalizedSupplement);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("approved", true);
            payload.put("reason", reason);
            payload.put("eventLabel", "审核通过并加入商品库");
            payload.put("selectedToLibrary", true);
            payload.put("libraryVisible", true);
            payload.put("supplement", normalizedSupplement);
            return new AuditDecision(
                    true,
                    ProductBizStatus.APPROVED,
                    "AUDIT",
                    "审核通过，已加入商品库",
                    2,
                    null,
                    true,
                    null,
                    null,
                    normalizedSupplement,
                    payload);
        }

        String rejectionReason = DomainText.trimToNull(reason);
        if (!DomainText.hasText(rejectionReason)) {
            throw BusinessException.stateInvalid("审核拒绝时必须填写原因");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approved", false);
        payload.put("reason", rejectionReason);
        payload.put("eventLabel", "审核拒绝");
        return new AuditDecision(
                false,
                ProductBizStatus.REJECTED,
                "AUDIT",
                "审核拒绝",
                3,
                rejectionReason,
                false,
                ProductDisplayStatus.HIDDEN.name(),
                "审核拒绝",
                Map.of(),
                payload);
    }

    private void requireApprovalSupplement(Map<String, Object> normalized) {
        List<String> missing = new ArrayList<>();
        requireText(normalized, "exclusivePriceRemark", "专属价说明", missing);
        requireText(normalized, "shippingInfo", "发货信息", missing);
        requireList(normalized, "sellingPoints", "商品卖点", missing);
        requireText(normalized, "promotionScript", "推广话术", missing);
        if (!normalized.containsKey("supportsAds")) {
            missing.add("是否支持投流");
        }
        requireText(normalized, "rewardRemark", "奖励说明", missing);
        requireText(normalized, "participationRequirements", "参与要求", missing);
        requireText(normalized, "campaignTimeRemark", "活动时间", missing);
        requireList(normalized, "materialFiles", "手卡素材", missing);
        if (!missing.isEmpty()) {
            throw BusinessException.stateInvalid("审核通过前请补充：" + String.join("、", missing));
        }
    }

    private void requireText(Map<String, Object> payload, String key, String label, List<String> missing) {
        if (!DomainText.hasText(ProductAuditSupplementPayload.readString(payload, key))) {
            missing.add(label);
        }
    }

    private void requireList(Map<String, Object> payload, String key, String label, List<String> missing) {
        if (ProductAuditSupplementPayload.readStringList(payload, key).isEmpty()) {
            missing.add(label);
        }
    }

    public record AuditDecision(
            boolean approved,
            ProductBizStatus targetStatus,
            String operationType,
            String operationRemark,
            int auditStatus,
            String auditRemark,
            boolean selectedToLibrary,
            String displayStatus,
            String hiddenReason,
            Map<String, Object> normalizedSupplement,
            Map<String, Object> payload
    ) {
        public AuditDecision {
            normalizedSupplement = Collections.unmodifiableMap(new LinkedHashMap<>(normalizedSupplement));
            payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }
}
