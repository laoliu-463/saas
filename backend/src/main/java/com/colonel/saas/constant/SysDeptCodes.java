package com.colonel.saas.constant;

import java.util.Set;

/** 初始化种子部门编码，禁止删除。 */
public final class SysDeptCodes {

    public static final String BIZ = "BIZ";
    public static final String CHANNEL = "CHANNEL";
    public static final String OPS = "OPS";

    public static final Set<String> SEED_CODES = Set.of(BIZ, CHANNEL, OPS);

    private SysDeptCodes() {
    }

    public static boolean isSeedCode(String deptCode) {
        return deptCode != null && SEED_CODES.contains(deptCode.trim().toUpperCase());
    }
}
