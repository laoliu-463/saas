package com.colonel.saas.service.display;

import java.util.UUID;

/**
 * 展示规则操作者上下文，用于记录触发展示规则变更的操作来源。
 *
 * <ul>
 *   <li>区分三种操作来源：系统自动（SYSTEM）、定时任务（JOB）、管理员手动（ADMIN）</li>
 *   <li>在展示规则审计日志中标识操作者身份，便于追溯变更原因</li>
 *   <li>以不可变 record 形式保证上下文在传递过程中的线程安全</li>
 * </ul>
 *
 * <p>在架构中属于展示域（Display Domain）的值对象，与 {@link DisplayRuleService} 配合使用。</p>
 *
 * @see DisplayRuleService
 */
public record DisplayRuleOperatorContext(String operatorType, UUID operatorId) {

    /** 操作者类型：系统自动触发 */
    public static final String TYPE_SYSTEM = "SYSTEM";
    /** 操作者类型：定时任务触发 */
    public static final String TYPE_JOB = "JOB";
    /** 操作者类型：管理员手动操作 */
    public static final String TYPE_ADMIN = "ADMIN";

    /**
     * 创建系统自动操作上下文。
     *
     * <ol>
     *   <li>操作者类型设为 SYSTEM</li>
     *   <li>操作者 ID 设为 null（系统无具体操作人）</li>
     * </ol>
     *
     * @return 系统自动操作上下文实例
     */
    public static DisplayRuleOperatorContext system() {
        return new DisplayRuleOperatorContext(TYPE_SYSTEM, null);
    }

    /**
     * 创建定时任务操作上下文。
     *
     * <ol>
     *   <li>操作者类型设为 JOB</li>
     *   <li>操作者 ID 设为 null（定时任务无具体操作人）</li>
     * </ol>
     *
     * @return 定时任务操作上下文实例
     */
    public static DisplayRuleOperatorContext job() {
        return new DisplayRuleOperatorContext(TYPE_JOB, null);
    }

    /**
     * 创建管理员手动操作上下文。
     *
     * <ol>
     *   <li>操作者类型设为 ADMIN</li>
     *   <li>记录具体管理员的 UUID，用于审计追踪</li>
     * </ol>
     *
     * @param adminId 管理员用户 ID
     * @return 管理员操作上下文实例
     */
    public static DisplayRuleOperatorContext admin(UUID adminId) {
        return new DisplayRuleOperatorContext(TYPE_ADMIN, adminId);
    }
}
