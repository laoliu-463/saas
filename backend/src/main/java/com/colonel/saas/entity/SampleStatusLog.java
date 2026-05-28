package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 寄样申请状态变更日志实体。
 * <p>
 * 对应数据库表：{@code sample_status_log}，记录寄样申请在各状态节点之间的流转历史。
 * 每次状态变更生成一条日志记录，用于状态变更追溯和流程审计。
 * 不继承 BaseEntity，采用手动输入的 UUID 主键。
 * </p>
 *
 * @see SampleRequest 寄样申请主实体
 */
@Data
@TableName("sample_status_log")
public class SampleStatusLog {

    /**
     * 主键 ID
     * <p>手动输入的 UUID 主键</p>
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 寄样申请 ID
     * <p>对应数据库列：{@code request_id}，关联寄样申请主表</p>
     */
    @TableField("request_id")
    private UUID requestId;

    /**
     * 变更前状态
     * <p>对应数据库列：{@code from_status}，状态变更前的寄样申请状态值</p>
     */
    @TableField("from_status")
    private Integer fromStatus;

    /**
     * 变更后状态
     * <p>对应数据库列：{@code to_status}，状态变更后的寄样申请状态值</p>
     */
    @TableField("to_status")
    private Integer toStatus;

    /**
     * 操作人 ID
     * <p>对应数据库列：{@code operator_id}，触发状态变更的系统用户标识</p>
     */
    @TableField("operator_id")
    private UUID operatorId;

    /**
     * 操作时间
     * <p>对应数据库列：{@code operate_time}，状态变更发生的时间</p>
     */
    @TableField("operate_time")
    private LocalDateTime operateTime;

    /**
     * 备注
     * <p>状态变更的补充说明，如驳回原因、审批意见等</p>
     */
    private String remark;
}
