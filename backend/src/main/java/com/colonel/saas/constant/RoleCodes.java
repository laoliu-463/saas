package com.colonel.saas.constant;

public final class RoleCodes {

    private RoleCodes() {
    }

    public static final String ADMIN = "admin";
    public static final String BIZ_LEADER = "biz_leader";
    public static final String BIZ_STAFF = "biz_staff";
    public static final String CHANNEL_LEADER = "channel_leader";
    public static final String CHANNEL_STAFF = "channel_staff";
    public static final String OPS_STAFF = "ops_staff";
    /** 招商组长 — 仅查看活动、分配商品给招商、寄样管理、数据看板，无审核权限 */
    public static final String COLONEL_LEADER = "colonel_leader";
}

