package com.colonel.saas.domain.config.facade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 配置域对外佣金比例（DDD-CONFIG-001）。
 * <p>对应 system_config 中的 {@code commission.business_default_ratio}（招商/招募端）与
 * {@code commission.channel_default_ratio}（渠道端）。</p>
 */
@Schema(description = "佣金默认比例（招商/渠道）")
public record CommissionRatesDTO(
        @Schema(description = "招商端默认佣金比例", example = "0.05")
        BigDecimal businessRatio,

        @Schema(description = "渠道端默认佣金比例", example = "0.10")
        BigDecimal channelRatio
) {
}
