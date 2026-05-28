package com.colonel.saas.common.enums;

import com.colonel.saas.common.exception.BusinessException;

/**
 * 寄样单状态枚举。
 * <p>
 * 描述寄样单（Sample Request）在全生命周期中的所有可能状态，覆盖从申请、审核、发货、
 * 签收、达人交作业到最终完成或关闭的完整流程。
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）。
 * </p>
 * <p>
 * 状态流转路径：
 * <pre>
 *   PENDING_AUDIT → PENDING_SHIP → SHIPPING → DELIVERED → PENDING_HOMEWORK → COMPLETED
 *                ↘ REJECTED
 *                ↘ CLOSED（任意阶段可关闭）
 * </pre>
 * </p>
 */
public enum SampleStatus {

    /** 待审核：寄样申请已提交，等待团长/渠道审核通过。 */
    PENDING_AUDIT(1, "PENDING_AUDIT"),

    /** 待发货：审核已通过，等待商家或团长安排发货。 */
    PENDING_SHIP(2, "PENDING_SHIP"),

    /** 发货中：物流单号已填写，包裹已交付快递公司，运输途中。 */
    SHIPPING(3, "SHIPPING"),

    /** 已签收：达人已签收包裹，等待达人完成对应作业（如发布带货视频）。 */
    DELIVERED(4, "DELIVERED"),

    /** 待交作业：达人已签收，但尚未完成约定的带货作业任务。 */
    PENDING_HOMEWORK(5, "PENDING_HOMEWORK"),

    /** 已完成：达人已完成带货作业，寄样流程闭环。 */
    COMPLETED(6, "COMPLETED"),

    /** 已驳回：寄样申请未通过审核，被驳回。 */
    REJECTED(7, "REJECTED"),

    /** 已关闭：寄样单因超时、主动取消或其他原因被关闭。 */
    CLOSED(8, "CLOSED");

    /**
     * 数据库持久化状态码，对应数据库字段的整型值。
     */
    private final Integer code;

    /**
     * API 接口状态标识符，用于对外接口返回的状态字符串。
     * 与枚举名称保持一致，便于前后端交互。
     */
    private final String apiStatus;

    SampleStatus(Integer code, String apiStatus) {
        this.code = code;
        this.apiStatus = apiStatus;
    }

    public Integer getCode() {
        return code;
    }

    public String getApiStatus() {
        return apiStatus;
    }

    /**
     * 栆数据库状态码反查枚举实例。
     *
     * @param code 数据库中的整型状态码
     * @return 对应的枚举值
     * @throws com.colonel.saas.common.exception.BusinessException 当状态码无法匹配时抛出参数异常
     */
    public static SampleStatus fromCode(Integer code) {
        for (SampleStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw BusinessException.param("Unknown sample status: " + code);
    }

    /**
     * 根据上游 API 状态字符串反查枚举实例，兼容上游系统的别名映射。
     * <p>
     * 上游兼容映射关系：
     * <ul>
     *   <li>{@code "PENDING_TASK"} → {@link #PENDING_HOMEWORK}（上游旧版用 PENDING_TASK 表示待交作业）</li>
     *   <li>{@code "SHIPPED"} → {@link #SHIPPING}（上游旧版用 SHIPPED 表示发货中）</li>
     *   <li>{@code "FINISHED"} → {@link #COMPLETED}（上游旧版用 FINISHED 表示已完成）</li>
     * </ul>
     * </p>
     *
     * @param status 上游 API 返回的状态字符串
     * @return 对应的枚举值
     */
    public static SampleStatus fromApiStatus(String status) {
        return switch (status) {
            case "PENDING_TASK" -> PENDING_HOMEWORK;
            case "SHIPPED" -> SHIPPING;
            case "FINISHED" -> COMPLETED;
            default -> SampleStatus.valueOf(status);
        };
    }
}
