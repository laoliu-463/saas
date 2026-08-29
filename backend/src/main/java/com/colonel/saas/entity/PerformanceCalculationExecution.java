package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 业绩计算事件的可恢复执行台账。
 *
 * <p>事件键唯一，成功后的同一事件不会再次重复计算；失败事件保留错误和下次重试时间，
 * 供受控重试任务恢复，而不是依赖进程内异步日志。</p>
 */
@Data
@TableName(value = "performance_calculation_execution", autoResultMap = true)
public class PerformanceCalculationExecution {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("event_key")
    private String eventKey;

    @TableField("event_type")
    private String eventType;

    @TableField("order_id")
    private String orderId;

    @TableField("order_version")
    private Integer orderVersion;

    /**
     * 事件处理所需的最小业务输入快照（例如退款金额、退款单号与发生时间）。
     * 重试必须基于该快照恢复原事件语义，不能只重算订单主记录。
     */
    @TableField(value = "event_payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> eventPayload;

    /** RUNNING / SUCCEEDED / FAILED。 */
    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("last_error")
    private String lastError;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
