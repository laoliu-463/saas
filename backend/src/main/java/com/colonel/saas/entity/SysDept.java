package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * 部门/组织单元实体。
 * <p>
 * 对应数据库表：{@code sys_dept}，技术上沿用 dept 命名，业务上承载"招商组 / 渠道组 / 运营组"等组织单元。
 * 支持树形层级结构（通过 parentId），用于数据权限范围过滤和组织架构管理。
 * 继承 {@link BaseEntity}，拥有 UUID 主键和审计字段。
 * </p>
 *
 * @see SysUser 系统用户，通过 deptId 关联
 * @see SysRole 系统角色，通过 dataScope 关联部门范围
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    /**
     * 父部门 ID
     * <p>对应数据库列：{@code parent_id}，指向父级组织单元的主键，
     * null 表示顶级部门</p>
     */
    @TableField("parent_id")
    private UUID parentId;

    /**
     * 部门编码
     * <p>对应数据库列：{@code dept_code}，组织单元的唯一业务编码</p>
     */
    @TableField("dept_code")
    private String deptCode;

    /**
     * 部门名称
     * <p>对应数据库列：{@code dept_name}，组织单元的显示名称，
     * 如"招商一组"、"渠道二组"等</p>
     */
    @TableField("dept_name")
    private String deptName;

    /**
     * 组织单元类型
     * <p>对应数据库列：{@code dept_type}，标识组织单元的业务类型，
     * 可选值：department（部门）、recruiter_group（招商组）、channel_group（渠道组）、ops_group（运营组）</p>
     */
    @TableField("dept_type")
    private String deptType = "department";

    /**
     * 组长用户 ID
     * <p>对应数据库列：{@code leader_user_id}，结构化关联 {@link SysUser} 主键。
     * leader 字段保留展示名，此字段用于精确关联</p>
     */
    @TableField("leader_user_id")
    private UUID leaderUserId;

    /**
     * 负责人姓名
     * <p>对应数据库列：{@code leader}，组织单元负责人的显示名称（展示用）</p>
     */
    private String leader;

    /**
     * 联系电话
     * <p>对应数据库列：{@code phone}，组织单元的联系电话</p>
     */
    private String phone;

    /**
     * 联系邮箱
     * <p>对应数据库列：{@code email}，组织单元的联系邮箱</p>
     */
    private String email;

    /**
     * 排序序号
     * <p>对应数据库列：{@code sort_order}，同级部门的展示顺序，数值越小越靠前</p>
     */
    @TableField("sort_order")
    private Integer sortOrder = 0;

    /**
     * 状态
     * <p>1=正常, 0=禁用。控制组织单元是否参与业务操作和数据权限过滤</p>
     */
    private Integer status = 1;

    /**
     * 备注
     * <p>对应数据库列：{@code remark}，组织单元的额外说明信息</p>
     */
    private String remark;
}
