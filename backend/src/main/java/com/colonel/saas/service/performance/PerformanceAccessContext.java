package com.colonel.saas.service.performance;

import com.colonel.saas.common.enums.DataScope;

import java.util.List;
import java.util.UUID;

public record PerformanceAccessContext(
        UUID userId,
        UUID deptId,
        DataScope dataScope,
        List<String> roleCodes) {

    public static PerformanceAccessContext of(
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            List<String> roleCodes) {
        return new PerformanceAccessContext(
                userId,
                deptId,
                dataScope == null ? DataScope.PERSONAL : dataScope,
                roleCodes == null ? List.of() : roleCodes);
    }
}
