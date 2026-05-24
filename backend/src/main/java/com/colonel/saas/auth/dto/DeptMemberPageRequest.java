package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

@Schema(description = "部门成员分页查询")
public record DeptMemberPageRequest(
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer size,
        String keyword,
        @Min(0) @Max(2) Integer status,
        UUID groupId,
        UUID roleId,
        String roleCode
) {
    public long pageNo() {
        return page == null ? 1L : page;
    }

    public long pageSize() {
        return size == null ? 20L : size;
    }
}
