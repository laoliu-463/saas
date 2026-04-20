package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.PackagePrivate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 系统用户
 * 继承 BaseEntity：UUID 主键 + 审计字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
@PackagePrivate
@TableName("sys_user")
public class SysUser extends com.colonel.saas.common.base.BaseEntity {

    @TableId(type = IdType.AUTO)
    private UUID id;

    private String username;

    private String password;

    private String realName;

    private String phone;

    private String email;

    /** 部门ID（用于 DataScope 过滤） */
    private UUID deptId;

    /** 渠道短码，用于 pick_extra 生成（≤16字符） */
    private String channelCode;

    /** 状态：1=启用, 0=禁用 */
    @TableField("status")
    private Integer status = 1;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted = 0;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private UUID createBy;

    /** 更新人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private UUID updateBy;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;
}