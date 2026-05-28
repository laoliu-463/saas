package com.colonel.saas.dto.rulecenter;

import java.util.List;
import java.util.UUID;

/**
 * 规则中心更新响应 DTO。
 * <p>
 * 返回规则配置更新操作的结果，包含事件 ID、变更的配置项列表和警告信息。
 * 关联业务领域：规则中心（RuleCenter）。
 * </p>
 */
public record RuleCenterUpdateResponse(
        /** 本次更新生成的事件 ID（用于追踪消费状态） */
        UUID eventId,
        /** 本次变更的配置项编码列表 */
        List<String> changedKeys,
        /** 更新过程中的警告信息列表 */
        List<String> warnings) {
}
