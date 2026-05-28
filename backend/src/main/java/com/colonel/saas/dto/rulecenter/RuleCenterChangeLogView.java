package com.colonel.saas.dto.rulecenter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 规则中心变更日志视图 DTO。
 * <p>
 * 表示单条规则配置的变更历史记录，包含配置项信息、变更动作、新旧值、
 * 操作来源、变更原因和操作人信息。
 * 关联业务领域：规则中心（RuleCenter）。
 * </p>
 */
public record RuleCenterChangeLogView(
        /** 变更日志 ID */
        UUID id,
        /** 关联的事件 ID */
        UUID eventId,
        /** 配置项编码 */
        String configKey,
        /** 变更动作（如 create、update、delete） */
        String changeAction,
        /** 变更前的值 */
        String oldValue,
        /** 变更后的值 */
        String newValue,
        /** 变更来源（如 ui、api、reconcile） */
        String source,
        /** 变更原因说明 */
        String changeReason,
        /** 操作人用户 ID */
        UUID operatorId,
        /** 配置项版本号 */
        Integer configVersion,
        /** 变更时间 */
        LocalDateTime changedAt) {
}
