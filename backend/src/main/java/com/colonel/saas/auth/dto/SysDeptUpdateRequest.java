package com.colonel.saas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 更新部门/业务组请求 DTO。
 *
 * <p>管理员修改组织架构节点信息时提交的请求体。
 * 结构与 {@link SysDeptCreateRequest} 一致，所有字段均为可选（部分校验规则仍生效），
 * 仅更新传入的非空字段。
 *
 * <p>所属业务领域：用户域 / 组织架构管理
 *
 * @see com.colonel.saas.auth.service.SysDeptService#update
 * @see com.colonel.saas.auth.dto.SysDeptCreateRequest
 */
public record SysDeptUpdateRequest(

        /** 父节点 ID（UUID），修改父节点即移动节点位置，需校验是否存在循环引用 */
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

        /** 排序值，数值越小越靠前展示 */
        Integer sortOrder,

        /** 状态：1=启用，0=禁用。禁用后该节点及其成员数据权限将受影响 */
        Integer status,

        /** 备注说明，最长 255 字符 */
        @Size(max = 255) String remark,

        /**
         * 部门类型标识，用于区分部门与业务组。
         * <p>常见值：department（部门）、recruiter_group（招募组）、
         * channel_group（渠道组）、ops_group（运营组）。
         */
        @Size(max = 32) String deptType,

        /** 负责人的用户 ID（UUID），关联 sys_user 表主键 */
        UUID leaderUserId
) {
}
