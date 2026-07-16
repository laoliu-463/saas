package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * 达人投诉附件元数据；文件内容由 storageKey 指向的存储服务管理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_complaint_attachment")
public class TalentComplaintAttachment extends BaseEntity {

    @TableField("complaint_id")
    private UUID complaintId;

    @TableField("storage_key")
    private String storageKey;

    @TableField("original_name")
    private String originalName;

    @TableField("content_type")
    private String contentType;

    @TableField("file_size")
    private Long fileSize;

    private String sha256;
}
