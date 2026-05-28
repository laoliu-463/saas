package com.colonel.saas.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 系统部门/组织单元树节点展示视图对象。
 * <p>
 * 用于组织架构管理页面的树形结构展示，支持多级嵌套。
 * 对应 {@code sys_dept} 表，通过 {@code children} 字段构建部门和业务组的层级关系。
 * </p>
 * <p>
 * V1 阶段由 {@code sys_dept} 统一承载部门和业务组，通过 {@code deptType} 区分。
 * </p>
 *
 * @see com.colonel.saas.constant.DeptType
 * @see com.colonel.saas.mapper.SysDeptMapper
 */
@Data
public class SysDeptVO {
    /** 组织单元唯一标识 */
    private UUID id;
    /** 父级组织单元 ID，顶级为 null */
    private UUID parentId;
    /** 组织编码 */
    private String deptCode;
    /** 组织名称 */
    private String deptName;
    /**
     * 组织类型：
     * <ul>
     *   <li>{@code department} — 部门</li>
     *   <li>{@code recruiter_group} — 招商组</li>
     *   <li>{@code channel_group} — 渠道组</li>
     *   <li>{@code ops_group} — 运营组</li>
     * </ul>
     */
    private String deptType;
    /** 负责人用户 ID */
    private UUID leaderUserId;
    /** 负责人姓名 */
    private String leader;
    /** 联系电话 */
    private String phone;
    /** 联系邮箱 */
    private String email;
    /** 排序号，值越小越靠前 */
    private Integer sortOrder;
    /** 组织状态：1-启用，0-停用 */
    private Integer status;
    /** 备注 */
    private String remark;
    /** 子组织列表，用于递归渲染树形结构 */
    private List<SysDeptVO> children = new ArrayList<>();
}
