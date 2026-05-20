package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import com.colonel.saas.common.typehandler.JsonbListTypeHandler;
import com.colonel.saas.common.typehandler.JsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "talent_profile_sync_log", autoResultMap = true)
public class TalentProfileSyncLog extends BaseEntity {

    @TableField("talent_id")
    private UUID talentId;

    @TableField("input_value")
    private String inputValue;

    @TableField("provider_code")
    private String providerCode;

    @TableField("sync_status")
    private String syncStatus;

    @TableField(value = "fetched_fields", typeHandler = JsonbListTypeHandler.class)
    private List<String> fetchedFields;

    @TableField(value = "unsupported_fields", typeHandler = JsonbListTypeHandler.class)
    private List<String> unsupportedFields;

    @TableField(value = "raw_payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> rawPayload;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
