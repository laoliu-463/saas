package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.PackagePrivate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 系统部门
 * 继承 BaseEntity：UUID 主键 + 审计字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
@PackagePrivate
@TableName("sys_department")
public class SysDepartment extends com.colonel.saas.common.base.BaseEntity {

    @TableId(type = IdType.AUTO)
    private UUID id;

    /** 父部门ID */
    private UUID parentId;

    /** 部门名称 */
    private String deptName;

    /** 部门类型：business=招商组, channel=渠道组 */
    @TableField("dept_type")
    private String deptType;

    /** 部门负责人ID */
    @TableField("leader_user_id")
    private UUID leaderUserId;

    /** 排序 */
    @TableField("sort_order")
    private Integer sortOrder = 0;

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
}