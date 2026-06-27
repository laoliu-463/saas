package com.colonel.saas.domain.product.policy;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.shared.policy.DomainText;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 商品操作日志与状态操作语义策略。
 */
public class ProductOperationDecisionPolicy {

    private static final Set<String> DECISION_LEVELS = Set.of("MAIN", "SECONDARY", "PAUSE", "DROP");

    public OperationDecision libraryEntry(String productTitle) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventLabel", "加入商品库");
        payload.put("productTitle", DomainText.hasText(productTitle) ? productTitle : "活动商品");
        return new OperationDecision(null, "LIBRARY_ENTRY", "上游状态为推广中，已加入商品库", payload);
    }

    public OperationDecision bindActivity(String boundActivityId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("boundActivityId", boundActivityId);
        payload.put("eventLabel", "商品活动绑定已更新");
        return new OperationDecision(null, "BIND_ACTIVITY", "绑定活动成功", payload);
    }

    public OperationDecision assignProduct(
            UUID assigneeId,
            String assigneeName,
            UUID operatorId,
            String operatorName) {
        Map<String, Object> payload = assigneePayload(
                assigneeId,
                assigneeName,
                operatorId,
                operatorName,
                "商品已分配给招商组长");
        return new OperationDecision(ProductBizStatus.ASSIGNED, "ASSIGN", "分配招商成功", payload);
    }

    public OperationDecision assignAuditOwner(
            ProductBizStatus currentStatus,
            UUID assigneeId,
            String assigneeName,
            UUID operatorId,
            String operatorName) {
        if (currentStatus != ProductBizStatus.PENDING_AUDIT) {
            throw BusinessException.stateInvalid("仅待审核商品可分配审核人");
        }
        Map<String, Object> payload = assigneePayload(
                assigneeId,
                assigneeName,
                operatorId,
                operatorName,
                "商品已分配给审核负责人");
        return new OperationDecision(currentStatus, "ASSIGN_AUDIT", "分配审核人成功", payload);
    }

    public OperationDecision progressDecision(
            String decisionLevel,
            String reason,
            UUID operatorId,
            String operatorName) {
        String normalizedLevel = normalizeDecisionLevel(decisionLevel);
        String normalizedReason = DomainText.trimToNull(reason);
        if (!DomainText.hasText(normalizedReason)) {
            throw BusinessException.param("推进判断原因不能为空");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decisionLevel", normalizedLevel);
        payload.put("decisionLabel", decisionLabel(normalizedLevel));
        payload.put("operatorId", operatorId);
        payload.put("operatorName", operatorName);
        payload.put("eventLabel", "商品推进判断已更新");
        return new OperationDecision(null, "DECISION", normalizedReason, payload);
    }

    public String normalizeDecisionLevel(String decisionLevel) {
        if (!DomainText.hasText(decisionLevel)) {
            throw BusinessException.param("推进判断不能为空");
        }
        String normalized = decisionLevel.trim().toUpperCase(Locale.ROOT);
        if (!DECISION_LEVELS.contains(normalized)) {
            throw BusinessException.param("未知推进判断：" + decisionLevel);
        }
        return normalized;
    }

    public String decisionLabel(String decisionLevel) {
        if (decisionLevel == null) {
            return null;
        }
        return switch (decisionLevel) {
            case "MAIN" -> "主推";
            case "SECONDARY" -> "次推";
            case "PAUSE" -> "暂缓";
            case "DROP" -> "放弃";
            default -> decisionLevel;
        };
    }

    private Map<String, Object> assigneePayload(
            UUID assigneeId,
            String assigneeName,
            UUID operatorId,
            String operatorName,
            String eventLabel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assigneeId", assigneeId);
        payload.put("assigneeName", assigneeName);
        payload.put("operatorId", operatorId);
        payload.put("operatorName", operatorName);
        payload.put("eventLabel", eventLabel);
        return payload;
    }

    public record OperationDecision(
            ProductBizStatus targetStatus,
            String operationType,
            String operationRemark,
            Map<String, Object> payload) {
        public OperationDecision {
            payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }
}
