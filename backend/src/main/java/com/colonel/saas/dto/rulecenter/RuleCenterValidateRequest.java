package com.colonel.saas.dto.rulecenter;

import java.util.List;
import java.util.Map;

/**
 * 规则中心校验请求 DTO。
 * <p>
 * 用于在提交规则配置更新前，对配置项的值进行格式和业务规则校验。
 * 关联业务领域：规则中心（RuleCenter）。
 * </p>
 */
public record RuleCenterValidateRequest(
        /** 待校验的配置项键值对映射（key=配置项编码，value=待校验的值） */
        Map<String, String> values) {
}
