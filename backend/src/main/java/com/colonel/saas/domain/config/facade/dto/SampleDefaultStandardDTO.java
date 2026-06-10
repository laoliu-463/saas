package com.colonel.saas.domain.config.facade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 配置域对外寄样默认达标标准（DDD-CONFIG-001）。
 * <p>对应 {@code sample.default_standard} JSON 中的两个常用字段，{@code raw} 保留完整 JSON 供
 * 消费方做额外解析。</p>
 */
@Schema(description = "寄样默认达标标准")
public record SampleDefaultStandardDTO(
        @Schema(description = "30天最低销量")
        Long min30DaySales,

        @Schema(description = "最低等级（已归一化）")
        String minLevel,

        @Schema(description = "原始 JSON Map")
        java.util.Map<String, Object> raw
) {
}
