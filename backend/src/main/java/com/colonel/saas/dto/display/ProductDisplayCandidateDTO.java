package com.colonel.saas.dto.display;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品展示候选 DTO。
 * <p>
 * 表示一个商品在精选联盟中的展示候选信息，包括活动关联、佣金费率、广告支持状态、
 * 展示状态以及展示时间记录。用于展示管理页面的候选列表展示。
 * 关联业务领域：展示域（Display）。
 * </p>
 */
public record ProductDisplayCandidateDTO(
        /** 展示关系 ID */
        UUID relationId,
        /** 活动 ID */
        String activityId,
        /** 活动名称 */
        String activityName,
        /** 合作伙伴名称 */
        String partnerName,
        /** 招募人名称 */
        String recruiterName,
        /** 佣金费率 */
        BigDecimal commissionRate,
        /** 服务费率 */
        BigDecimal serviceFeeRate,
        /** 是否支持广告投放 */
        Boolean supportsAds,
        /** 当前展示状态 */
        String displayStatus,
        /** 隐藏原因（如果被隐藏） */
        String hiddenReason,
        /** 首次展示时间 */
        LocalDateTime firstDisplayedAt,
        /** 最近展示时间 */
        LocalDateTime lastDisplayedAt) {
}
