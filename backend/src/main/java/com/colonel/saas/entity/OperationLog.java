package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "operation_log", autoResultMap = true)
public class OperationLog {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID userId;

    private String username;

    private String module;

    private String action;

    private String targetType;

    private String targetId;

    private String targetName;

    private String content;

    private String requestMethod;

    private String requestUrl;

    @TableField(value = "request_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestParams;

    @TableField(value = "request_body", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestBody;

    private String responseCode;

    @TableField(value = "response_body", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> responseBody;

    private String ipAddress;

    private String userAgent;

    private Long durationMs;

    private String errorMessage;

    private LocalDateTime createTime;

    private Integer deleted;
}
