package com.colonel.saas.vo.sample;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 寄样单状态变更日志 VO（Value Object）。
 * <p>
 * 用于展示寄样单的单条状态变更历史记录，支持前端时间线组件的数据渲染。
 * 每条记录描述一次状态流转事件，包括操作人、时间和原因。
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）— 状态变更日志。
 * </p>
 *
 * @see com.colonel.saas.common.enums.SampleStatus 寄样单状态枚举
 */
@Data
public class StatusLogVO {

    /** 日志记录唯一标识（UUID）。 */
    private UUID id;

    /**
     * 变更前的状态。
     * <p>
     * 对应 {@link com.colonel.saas.common.enums.SampleStatus} 的 API 状态值。
     * 首次创建时可能为 null（表示初始化）。
     * </p>
     */
    private String fromStatus;

    /**
     * 变更后的状态。
     * <p>
     * 对应 {@link com.colonel.saas.common.enums.SampleStatus} 的 API 状态值。
     * </p>
     */
    private String toStatus;

    /** 操作人姓名，系统自动操作时为"系统"。 */
    private String operatorName;

    /** 操作时间。 */
    private LocalDateTime operateTime;

    /**
     * 操作备注。
     * <p>
     * 驳回原因、关闭原因等场景下由操作人填写的说明信息。
     * </p>
     */
    private String remark;
}
