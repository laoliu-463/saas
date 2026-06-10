package com.colonel.saas.domain.config.facade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 配置域对外商家侧业务规则（DDD-CONFIG-001）。
 * <p>当前仅聚合 {@code merchant.exclusive.service_fee_ratio}，未来扩展独家商家规则时
 * 直接在本 DTO 中追加字段即可。</p>
 */
@Schema(description = "商家侧业务规则（独家）")
public record ExclusiveRulesDTO(
        @Schema(description = "独家商家服务费比例", example = "0.20")
        BigDecimal merchantServiceFeeRatio
) {
}
