package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户-角色关联实体。
 * <p>
 * 对应数据库表：{@code sys_user_role}，记录用户与角色的多对多关联关系。
 * 不继承 BaseEntity（无审计字段，仅用于关联）。
 * 关联 {@link SysUser}（用户）和 {@link SysRole}（角色）。
 * </p>
 *
 * @see SysUser 用户实体
 * @see SysRole 角色实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_user_role")
public class SysUserRole {

    /**
     * 主键 ID
     * <p>自动生成的 UUID 主键</p>
     */
    @TableId(type = IdType.AUTO)
    private UUID id;

    /**
     * 用户 ID
     * <p>对应数据库列：{@code user_id}，关联 {@link SysUser} 主键</p>
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 角色 ID
     * <p>对应数据库列：{@code role_id}，关联 {@link SysRole} 主键</p>
     */
    @TableField("role_id")
    private UUID roleId;

    /**
     * 创建时间
     * <p>对应数据库列：{@code create_time}，记录创建时自动填充</p>
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 逻辑删除标记
     * <p>0=正常, 1=已删除。使用 {@link TableLogic} 实现逻辑删除，
     * 删除时实际执行 UPDATE 语句设置 deleted=1</p>
     */
    @TableLogic
    private Integer deleted = 0;
}
