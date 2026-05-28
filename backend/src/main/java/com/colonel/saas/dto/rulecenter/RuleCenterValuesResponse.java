package com.colonel.saas.dto.rulecenter;

import java.util.Map;

/**
 * 规则中心配置值响应 DTO。
 * <p>
 * 返回当前生效的所有规则配置项的键值对。
 * 关联业务领域：规则中心（RuleCenter）。
 * </p>
 */
public record RuleCenterValuesResponse(
        /** 配置项键值对映射（key=配置项编码，value=当前生效值） */
        Map<String, String> values) {
}
