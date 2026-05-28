package com.colonel.saas.vo.sample;

import lombok.Getter;

import java.util.List;

/**
 * 寄样单状态流转规则 VO（Value Object）。
 * <p>
 * 描述寄样单某一状态流转动作的完整规则，包括前置状态、目标状态、权限要求、
 * 必填字段、错误提示信息及对应的 API 端点。前端可基于此 VO 动态渲染操作按钮、
 * 表单校验规则和错误提示。
 * </p>
 * <p>
 * 主要职责：
 * <ul>
 *   <li>定义状态流转动作的元数据（action、label、aliases）</li>
 *   <li>声明流转的前置状态列表和目标状态</li>
 *   <li>声明操作所需的角色和权限</li>
 *   <li>声明必填字段及校验失败的提示信息</li>
 *   <li>提供对应的单条操作端点和批量操作端点</li>
 * </ul>
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）— 状态流转元数据。
 * </p>
 *
 * @see com.colonel.saas.common.enums.SampleStatus 寄样单状态枚举
 */
@Getter
public class SampleStatusTransitionVO {

    /** 状态流转动作标识（如 SHIPPING、REJECTED 等）。 */
    private final String action;

    /** 动作的中文显示标签（如"标记发货"、"驳回"）。 */
    private final String label;

    /**
     * 动作别名列表。
     * <p>
     * 兼容上游旧版接口的别名映射（如 SHIPPED → SHIPPING、FINISHED → COMPLETED）。
     * </p>
     */
    private final List<String> aliases;

    /**
     * 允许执行此动作的前置状态列表。
     * <p>
     * 只有寄样单当前状态在此列表中时，才允许执行该动作。
     * </p>
     */
    private final List<String> fromStatuses;

    /** 执行成功后的目标状态（API 层状态标识）。 */
    private final String toStatus;

    /**
     * 内部目标状态标识。
     * <p>
     * 当 API 层状态标识与内部枚举名称不一致时（如别名场景），此字段记录实际写入数据库的状态。
     * </p>
     */
    private final String internalToStatus;

    /**
     * 允许执行此动作的角色编码列表。
     * <p>
     * 如 ["ADMIN", "CHANNEL"] 表示仅管理员和渠道运营可执行。
     * </p>
     */
    private final List<String> roleCodes;

    /**
     * 操作人类型。
     * <p>
     * 标识执行此动作的主体类型，如 "user"（系统用户）、"system"（系统自动）、"talent"（达人）。
     * </p>
     */
    private final String actorType;

    /**
     * 是否允许用户（前端）直接调用。
     * <p>
     * {@code true} 表示前端用户可主动触发此操作；
     * {@code false} 表示仅系统内部或后台任务可执行。
     * </p>
     */
    private final boolean userCallable;

    /**
     * 执行此动作时的必填字段列表。
     * <p>
     * 如发货动作要求 ["trackingNo"]，驳回动作要求 ["reason"]。
     * 前端据此动态渲染表单和校验逻辑。
     * </p>
     */
    private final List<String> requiredFields;

    /**
     * 必填字段缺失时的错误提示信息模板。
     * <p>
     * 当用户未填写必填字段时返回的提示文案。
     * </p>
     */
    private final String missingFieldMessage;

    /**
     * 状态不满足时的错误提示信息。
     * <p>
     * 当寄样单当前状态不在 {@link #fromStatuses} 中时返回的提示文案。
     * </p>
     */
    private final String invalidStateMessage;

    /**
     * 权限不足时的错误提示信息。
     * <p>
     * 当操作人角色不在 {@link #roleCodes} 中时返回的提示文案。
     * </p>
     */
    private final String forbiddenMessage;

    /**
     * 单条操作的 API 端点路径。
     * <p>
     * 如 "/api/samples/{requestNo}/action"。
     * </p>
     */
    private final String endpoint;

    /**
     * 批量操作的 API 端点路径。
     * <p>
     * 如 "/api/samples/batch/approve"。当 {@link #userCallable} 为 false 时通常为空。
     * </p>
     */
    private final String batchEndpoint;

    /**
     * 流转触发方式。
     * <p>
     * 标识此动作的触发来源，如 "manual"（手动操作）、"event"（事件驱动）、"timer"（定时任务）。
     * </p>
     */
    private final String trigger;

    public SampleStatusTransitionVO(
            String action,
            String label,
            List<String> aliases,
            List<String> fromStatuses,
            String toStatus,
            String internalToStatus,
            List<String> roleCodes,
            String actorType,
            boolean userCallable,
            List<String> requiredFields,
            String missingFieldMessage,
            String invalidStateMessage,
            String forbiddenMessage,
            String endpoint,
            String batchEndpoint,
            String trigger) {
        this.action = action;
        this.label = label;
        this.aliases = aliases;
        this.fromStatuses = fromStatuses;
        this.toStatus = toStatus;
        this.internalToStatus = internalToStatus;
        this.roleCodes = roleCodes;
        this.actorType = actorType;
        this.userCallable = userCallable;
        this.requiredFields = requiredFields;
        this.missingFieldMessage = missingFieldMessage;
        this.invalidStateMessage = invalidStateMessage;
        this.forbiddenMessage = forbiddenMessage;
        this.endpoint = endpoint;
        this.batchEndpoint = batchEndpoint;
        this.trigger = trigger;
    }
}
