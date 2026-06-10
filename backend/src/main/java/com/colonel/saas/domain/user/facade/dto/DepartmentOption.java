package com.colonel.saas.domain.user.facade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 用户域对外部门/组织单元选项（DDD-USER-001）。
 * <p>不暴露 {@code SysDept} 实体，仅供 Facade 消费方做下拉或范围解析。</p>
 */
@Schema(description = "部门/组织单元选项")
public record DepartmentOption(
        @Schema(description = "部门ID")
        UUID id,

        @Schema(description = "部门编码")
        String deptCode,

        @Schema(description = "部门名称")
        String deptName,

        @Schema(description = "组织类型")
        String deptType
) {
}
