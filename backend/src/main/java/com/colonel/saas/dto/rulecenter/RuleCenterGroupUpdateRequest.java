package com.colonel.saas.dto.rulecenter;

import java.util.Map;

/**
 * 规则中心分组更新请求 DTO。
 * <p>
 * 用于按规则分组批量更新配置项的值，需提供键值对映射和变更原因说明。
 * 关联业务领域：规则中心（RuleCenter）。
 * </p>
 */
public record RuleCenterGroupUpdateRequest(
        /** 待更新的配置项键值对映射（key=配置项编码，value=新值） */
        Map<String, String> values,
        /** 变更原因说明（用于审计追溯） */
        String changeReason) {
}
