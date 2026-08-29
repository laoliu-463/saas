package com.colonel.saas.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductSnapshot;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** 从活动商品快照提取抖音活动商品申请 ID。 */
final class ProductAuditApplyIdResolver {

    private ProductAuditApplyIdResolver() {
    }

    static List<Long> resolve(ProductSnapshot snapshot, ObjectMapper objectMapper) {
        Map<String, Object> payload = parse(snapshot, objectMapper);
        Object rawApplyId = payload.get("apply_id");
        if (rawApplyId == null) {
            rawApplyId = payload.get("applyId");
        }
        String applyId = rawApplyId == null ? null : String.valueOf(rawApplyId).trim();
        if (!StringUtils.hasText(applyId)) {
            throw BusinessException.dataNotReady("活动商品快照缺少抖音申请 ID，请先重新同步活动商品");
        }
        try {
            return List.of(Long.parseLong(applyId));
        } catch (NumberFormatException ex) {
            throw BusinessException.stateInvalid("商品上游申请 ID 无效：" + applyId);
        }
    }

    private static Map<String, Object> parse(ProductSnapshot snapshot, ObjectMapper objectMapper) {
        if (snapshot == null || !StringUtils.hasText(snapshot.getRawPayload())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(snapshot.getRawPayload(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
