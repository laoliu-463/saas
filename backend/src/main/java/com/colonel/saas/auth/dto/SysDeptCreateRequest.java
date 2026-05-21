package com.colonel.saas.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SysDeptCreateRequest(
        UUID parentId,
        @NotBlank @Size(max = 50) String deptCode,
        @NotBlank @Size(max = 100) String deptName,
        @Size(max = 100) String leader,
        @Size(max = 20) String phone,
        @Size(max = 100) String email,
        Integer sortOrder,
        Integer status,
        @Size(max = 255) String remark
) {
}
