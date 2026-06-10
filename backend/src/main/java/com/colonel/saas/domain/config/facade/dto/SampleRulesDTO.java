package com.colonel.saas.domain.config.facade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 配置域对外寄样业务规则（DDD-CONFIG-001）。
 * <p>聚合 {@code sample.restrict_days}、{@code sample.restrict_enabled}、超时天数与默认达标标准，
 * 供寄样域、订单域等消费方统一读取，避免散落读单 key。</p>
 */
@Schema(description = "寄样业务规则")
public record SampleRulesDTO(
        @Schema(description = "寄样限制天数", example = "30")
        int restrictDays,

        @Schema(description = "是否启用寄样限制", example = "true")
        boolean restrictEnabled,

        @Schema(description = "超时未交作业天数", example = "7")
        int timeoutHomeworkDays,

        @Schema(description = "超时未发货天数", example = "5")
        int timeoutPendingShipDays,

        @Schema(description = "默认达标标准（30天销量/最低等级）")
        SampleDefaultStandardDTO defaultStandard
) {
}
