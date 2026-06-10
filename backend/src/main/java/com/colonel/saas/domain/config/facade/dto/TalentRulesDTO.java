package com.colonel.saas.domain.config.facade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 配置域对外达人侧业务规则（DDD-CONFIG-001）。
 * <p>聚合 {@code talent.protection_days}、{@code talent.exclusive.service_fee_ratio}、
 * {@code talent.exclusive.monthly_samples}。</p>
 */
@Schema(description = "达人侧业务规则")
public record TalentRulesDTO(
        @Schema(description = "认领保护天数", example = "7")
        int protectionDays,

        @Schema(description = "独家服务费比例阈值", example = "0.70")
        BigDecimal exclusiveRatioThreshold,

        @Schema(description = "独家月度寄样数量", example = "10")
        int exclusiveMonthlySamples
) {
}
