package com.colonel.saas.dto.rulecenter;

import java.util.List;

/**
 * 规则中心校验响应 DTO。
 * <p>
 * 返回规则配置项的校验结果，包含校验是否通过、错误列表和警告列表。
 * 关联业务领域：规则中心（RuleCenter）。
 * </p>
 */
public record RuleCenterValidateResponse(
        /** 校验是否全部通过 */
        boolean valid,
        /** 校验错误信息列表 */
        List<String> errors,
        /** 校验警告信息列表（不影响通过，但需关注） */
        List<String> warnings) {
}
