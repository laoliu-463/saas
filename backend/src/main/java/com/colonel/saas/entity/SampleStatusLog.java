package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("sample_status_log")
public class SampleStatusLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    @TableField("request_id")
    private UUID requestId;

    @TableField("from_status")
    private Integer fromStatus;

    @TableField("to_status")
    private Integer toStatus;

    @TableField("operator_id")
    private UUID operatorId;

    @TableField("operate_time")
    private LocalDateTime operateTime;

    private String remark;
}
