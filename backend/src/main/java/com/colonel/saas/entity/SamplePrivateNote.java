package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * 合作单私有备注，仅对备注所属用户可见。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sample_private_note")
public class SamplePrivateNote extends VersionedEntity {

    @TableField("sample_request_id")
    private UUID sampleRequestId;

    @TableField("user_id")
    private UUID userId;

    private String content;
}
