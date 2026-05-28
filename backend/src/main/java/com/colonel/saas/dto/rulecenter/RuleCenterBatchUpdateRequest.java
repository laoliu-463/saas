package com.colonel.saas.dto.rulecenter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 规则中心批量更新请求 DTO。
 * <p>
 * 用于一次性批量更新多个规则配置项的值，需提供键值对映射和变更原因说明。
 * 关联业务领域：规则中心（RuleCenter）。
 * </p>
 */
public record RuleCenterBatchUpdateRequest(
        /** 待更新的配置项键值对映射（key=配置项编码，value=新值） */
        Map<String, String> values,
        /** 变更原因说明（用于审计追溯） */
        String changeReason) {
}
