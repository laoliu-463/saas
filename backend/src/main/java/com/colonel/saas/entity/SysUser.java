package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 系统用户实体。
 * <p>
 * 对应数据库表：{@code sys_user}，记录平台的操作人员信息。
 * 用户通过 {@link SysUserRole} 关联角色，通过 deptId 关联 {@link SysDept} 部门，
 * 用于数据权限范围过滤（self / group / all）。
 * 继承 {@link com.colonel.saas.common.base.BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see SysDept 部门实体
 * @see SysRole 角色实体
 * @see SysUserRole 用户-角色关联
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends com.colonel.saas.common.base.BaseEntity {

    /**
     * 登录用户名
     * <p>对应数据库列：{@code username}，用户登录系统的唯一用户名</p>
     */
    private String username;

    /**
     * 登录密码
     * <p>对应数据库列：{@code password}，存储加密后的密码哈希值（bcrypt）</p>
     */
    private String password;

    /**
     * 真实姓名
     * <p>对应数据库列：{@code real_name}，用户的真实姓名，用于前端展示和操作审计</p>
     */
    private String realName;

    /**
     * 联系电话
     * <p>对应数据库列：{@code phone}，用户的联系电话</p>
     */
    private String phone;

    /**
     * 联系邮箱
     * <p>对应数据库列：{@code email}，用户的联系邮箱</p>
     */
    private String email;

    /**
     * 所属部门 ID
     * <p>对应数据库列：{@code dept_id}，用户所属的组织单元 ID，
     * 关联 {@link SysDept} 主键，用于 DataScope 数据范围过滤</p>
     */
    private UUID deptId;

    /**
     * 渠道短码
     * <p>对应数据库列：{@code channel_code}，用户的渠道标识短码（不超过 16 字符），
     * 用于 pick_extra 生成，标识推广链接来源渠道</p>
     */
    private String channelCode;

    /**
     * 用户状态
     * <p>对应数据库列：{@code status}，2=待激活（新建用户等待首次登录激活）, 1=正常, 0=已禁用</p>
     */
    @TableField("status")
    private Integer status = 1;

    @TableField(value = "authz_version", updateStrategy = FieldStrategy.NEVER)
    private Long authzVersion = 1L;

    /**
     * 是否强制修改密码
     * <p>对应数据库列：{@code force_password_change}，新建待激活用户默认为 true，
     * 首次登录时强制要求修改密码</p>
     */
    @TableField("force_password_change")
    private Boolean forcePasswordChange = false;

    /**
     * 最后登录时间
     * <p>对应数据库列：{@code last_login_at}，用户最近一次登录系统的时间</p>
     */
    private LocalDateTime lastLoginAt;
}
