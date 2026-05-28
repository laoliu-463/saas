package com.colonel.saas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 创建部门/业务组请求 DTO。
 *
 * <p>管理员创建组织架构节点（部门或业务组）时提交的请求体。
 * sys_dept 表同时承担部门和业务组两种角色，通过 deptType 字段区分。
 *
 * <p>校验规则：
 * <ul>
 *   <li>deptCode 不能为空且最长 50 字符，同一父节点下不可重复</li>
 *   <li>deptName 不能为空且最长 100 字符</li>
 *   <li>其他可选字段均有长度限制</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 组织架构管理
 *
 * @see com.colonel.saas.auth.service.SysDeptService#create
 * @see com.colonel.saas.auth.dto.SysDeptUpdateRequest
 */
public record SysDeptCreateRequest(

        /** 父节点 ID（UUID），为空或全零时表示创建顶级节点 */
        UUID parentId,

        /** 部门/组编码，同一父节点下必须唯一。校验：@NotBlank，最长 50 字符 */
        @NotBlank @Size(max = 50) String deptCode,

        /** 部门/组名称，用于前端展示和搜索。校验：@NotBlank，最长 100 字符 */
        @NotBlank @Size(max = 100) String deptName,

        /** 负责人姓名，仅做文本展示，最长 100 字符 */
        @Size(max = 100) String leader,

        /** 负责人联系电话，最长 20 字符 */
        @Size(max = 20) String phone,

        /** 负责人邮箱，最长 100 字符 */
        @Size(max = 100) String email,

        /** 排序值，数值越小越靠前展示，为空时默认为 0 */
        Integer sortOrder,

        /** 状态：1=启用，0=禁用。为空时默认启用 */
        Integer status,

        /** 备注说明，最长 255 字符 */
        @Size(max = 255) String remark,

        /**
         * 部门类型标识，用于区分部门与业务组。
         * <p>常见值：department（部门）、recruiter_group（招募组）、
         * channel_group（渠道组）、ops_group（运营组）。
         * <p>校验：最长 32 字符。
         */
        @Size(max = 32) String deptType,

        /** 负责人的用户 ID（UUID），关联 sys_user 表主键，用于权限校验和数据归属 */
        UUID leaderUserId
) {
}
