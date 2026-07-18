package com.colonel.saas.vo.sample;

import java.util.Map;
import java.util.UUID;

/**
 * 合作详情编辑上下文。仅承载当前系统真实可得字段，缺失字段保持 {@code null}。
 */
public record SampleEditContextVO(
        UUID sampleId,
        String talentNickname,
        String talentDouyinNo,
        Long talentFansCount,
        Long talentWindowSales30d,
        UUID productId,
        String productExternalId,
        String productName,
        String shopName,
        String productSpecification,
        Integer quantity,
        Map<String, Object> sampleThreshold,
        String activityId,
        String activityName,
        String remark,
        boolean addressAvailable,
        String recipientName,
        String recipientPhone,
        String recipientAddress,
        Integer version) {
}
