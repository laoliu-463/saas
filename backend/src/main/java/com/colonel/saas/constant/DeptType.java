package com.colonel.saas.constant;

import java.util.Set;

/**
 * sys_dept 组织单元类型。V1 不单独建 groups 表，由 sys_dept 统一承载部门与业务组。
 */
public final class DeptType {

    public static final String DEPARTMENT = "department";
    public static final String RECRUITER_GROUP = "recruiter_group";
    public static final String CHANNEL_GROUP = "channel_group";
    public static final String OPS_GROUP = "ops_group";

    /** 历史迁移默认值，写入时应规范化为 {@link #DEPARTMENT} 或具体业务组类型。 */
    public static final String LEGACY_BUSINESS = "BUSINESS";

    private static final Set<String> ALLOWED = Set.of(
            DEPARTMENT,
            RECRUITER_GROUP,
            CHANNEL_GROUP,
            OPS_GROUP,
            LEGACY_BUSINESS
    );

    private DeptType() {
    }

    public static boolean isAllowed(String deptType) {
        return deptType != null && ALLOWED.contains(deptType.trim());
    }

    public static String normalize(String deptType) {
        if (deptType == null || deptType.isBlank()) {
            return DEPARTMENT;
        }
        String normalized = deptType.trim();
        if (LEGACY_BUSINESS.equalsIgnoreCase(normalized)) {
            return DEPARTMENT;
        }
        return normalized;
    }

    public static boolean isGroup(String deptType) {
        String normalized = normalize(deptType);
        return RECRUITER_GROUP.equals(normalized)
                || CHANNEL_GROUP.equals(normalized)
                || OPS_GROUP.equals(normalized);
    }

    public static boolean isDepartment(String deptType) {
        return DEPARTMENT.equals(normalize(deptType));
    }
}
