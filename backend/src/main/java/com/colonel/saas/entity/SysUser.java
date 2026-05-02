package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 系统用户
 * 继承 BaseEntity：UUID 主键 + 审计字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends com.colonel.saas.common.base.BaseEntity {

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

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;
}
