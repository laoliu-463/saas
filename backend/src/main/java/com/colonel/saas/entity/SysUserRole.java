package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.PackagePrivate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户-角色关联表
 * 不继承 BaseEntity（无审计字段，仅用于关联）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@PackagePrivate
@TableName("sys_user_role")
public class SysUserRole {

    @TableId(type = IdType.AUTO)
    private UUID id;

    /** 用户ID */
    @TableField("user_id")
    private UUID userId;

    /** 角色ID */
    @TableField("role_id")
    private UUID roleId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted = 0;
}