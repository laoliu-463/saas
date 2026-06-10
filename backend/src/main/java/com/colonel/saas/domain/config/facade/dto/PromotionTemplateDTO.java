package com.colonel.saas.domain.config.facade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 配置域对外推广文案与转链模板（DDD-CONFIG-001）。
 * <p>对应 {@code promotion.copy_brief_template} 与 {@code promotion.pick_extra_rule}。</p>
 */
@Schema(description = "推广文案与转链模板")
public record PromotionTemplateDTO(
        @Schema(description = "推广文案模板")
        String copyBriefTemplate,

        @Schema(description = "转链额外规则格式模板", example = "channel_{channel_code}")
        String pickExtraFormat,

        @Schema(description = "转链额外规则编码方式", example = "none")
        String pickExtraEncode
) {
}
