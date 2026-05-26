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

@Data
@TableName(value = "sample_logistics_trace", autoResultMap = true)
public class SampleLogisticsTrace {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    @TableField("sample_request_id")
    private UUID sampleRequestId;

    @TableField("tracking_no")
    private String trackingNo;

    @TableField("logistics_company")
    private String logisticsCompany;

    @TableField("status_code")
    private String statusCode;

    @TableField("status_name")
    private String statusName;

    @TableField("trace_time")
    private LocalDateTime traceTime;

    @TableField("trace_content")
    private String traceContent;

    @TableField("node_hash")
    private String nodeHash;

    @TableField("location")
    private String location;

    @TableField(value = "raw_payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> rawPayload;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
