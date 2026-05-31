package com.colonel.saas.constant;

import java.util.Set;

/**
 * sys_dept.dept_type 业务分类：招商组 / 渠道组 / 运营（行政）组。
 */
public final class DeptTypes {

    public static final String RECRUITER = "recruiter";
    public static final String CHANNEL = "channel";
    public static final String DEPT = "dept";

    public static final Set<String> ALL = Set.of(RECRUITER, CHANNEL, DEPT);

    private DeptTypes() {
    }

    public static boolean isValid(String deptType) {
        return deptType != null && ALL.contains(deptType.trim().toLowerCase());
    }

    public static String normalize(String deptType) {
        if (deptType == null || deptType.isBlank()) {
            return null;
        }
        return deptType.trim().toLowerCase();
    }
}
