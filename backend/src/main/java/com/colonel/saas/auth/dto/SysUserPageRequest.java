package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

@Schema(description = "用户分页查询参数")
public record SysUserPageRequest(
        @Schema(description = "页码，从 1 开始", example = "1")
        @Min(value = 1, message = "页码必须大于等于1")
        Integer page,

        @Schema(description = "每页大小", example = "10")
        @Min(value = 1, message = "每页大小必须大于等于1")
        @Max(value = 100, message = "每页大小不能超过100")
        Integer size,

        @Schema(description = "关键词，匹配用户名/姓名", example = "admin")
        String keyword,

        @Schema(description = "状态：1=启用，0=禁用", example = "1")
        @Min(value = 0, message = "状态值非法")
        @Max(value = 1, message = "状态值非法")
        Integer status,

        @Schema(description = "部门 ID")
        UUID deptId
) {
    public long pageNo() {
        return page == null ? 1L : page;
    }

    public long pageSize() {
        return size == null ? 10L : size;
    }
}
