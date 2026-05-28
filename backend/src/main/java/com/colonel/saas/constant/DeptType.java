package com.colonel.saas.constant;

import java.util.Set;

/**
 * 组织单元类型常量类。
 * <p>
 * 对应 {@code sys_dept} 表中的 {@code dept_type} 字段。V1 阶段不单独建业务组表，
 * 由 {@code sys_dept} 统一承载部门与业务组两种组织形态，通过 {@code dept_type} 字段区分。
 * </p>
 * <p>
 * 组织类型包括：
 * <ul>
 *   <li>{@code department} — 部门，如"技术部"、"业务部"</li>
 *   <li>{@code recruiter_group} — 招商组，负责达人招募和招商对接</li>
 *   <li>{@code channel_group} — 渠道组，负责渠道资源管理和拓展</li>
 *   <li>{@code ops_group} — 运营组，负责日常运营和数据分析</li>
 *   <li>{@code BUSINESS} — 历史遗留类型，写入时应规范化为具体类型</li>
 * </ul>
 * </p>
 *
 * @see com.colonel.saas.mapper.SysDeptMapper
 */
public final class DeptType {

    /** 防止实例化 */
    private DeptType() {
    }

    /** 部门类型 — 顶层组织单元 */
    public static final String DEPARTMENT = "department";

    /** 招商组 — 负责达人招募和招商对接的业务组 */
    public static final String RECRUITER_GROUP = "recruiter_group";

    /** 渠道组 — 负责渠道资源管理和拓展的业务组 */
    public static final String CHANNEL_GROUP = "channel_group";

    /** 运营组 — 负责日常运营和数据分析的业务组 */
    public static final String OPS_GROUP = "ops_group";

    /**
     * 历史迁移默认值。
     * <p>早期版本未区分部门与业务组，统一使用 "BUSINESS" 类型。写入时应规范化为 {@link #DEPARTMENT} 或具体业务组类型。</p>
     */
    public static final String LEGACY_BUSINESS = "BUSINESS";

    /** 允许的组织类型集合，用于输入校验 */
    private static final Set<String> ALLOWED = Set.of(
            DEPARTMENT,
            RECRUITER_GROUP,
            CHANNEL_GROUP,
            OPS_GROUP,
            LEGACY_BUSINESS
    );

    /**
     * 判断给定的组织类型是否合法。
     *
     * @param deptType 待校验的组织类型字符串
     * @return {@code true} 表示合法，{@code false} 表示非法或为空
     */
    public static boolean isAllowed(String deptType) {
        return deptType != null && ALLOWED.contains(deptType.trim());
    }

    /**
     * 规范化组织类型。
     * <p>
     * 将输入字符串标准化为合法的组织类型：
     * <ul>
     *   <li>null 或空白字符串 → 默认为 {@link #DEPARTMENT}</li>
     *   <li>{@code "BUSINESS"}（不区分大小写）→ 归一为 {@link #DEPARTMENT}</li>
     *   <li>其他合法类型 → 去除首尾空格后返回</li>
     * </ul>
     * </p>
     *
     * @param deptType 原始组织类型字符串
     * @return 规范化后的组织类型，不会返回 null
     */
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

    /**
     * 判断给定的组织类型是否为业务组（招商组、渠道组、运营组）。
     * <p>会先进行规范化处理，因此历史遗留的 "BUSINESS" 类型不会被识别为业务组。</p>
     *
     * @param deptType 组织类型字符串
     * @return {@code true} 表示是业务组类型
     */
    public static boolean isGroup(String deptType) {
        String normalized = normalize(deptType);
        return RECRUITER_GROUP.equals(normalized)
                || CHANNEL_GROUP.equals(normalized)
                || OPS_GROUP.equals(normalized);
    }

    /**
     * 判断给定的组织类型是否为部门。
     * <p>会先进行规范化处理，历史遗留的 "BUSINESS" 类型也会被识别为部门。</p>
     *
     * @param deptType 组织类型字符串
     * @return {@code true} 表示是部门类型
     */
    public static boolean isDepartment(String deptType) {
        return DEPARTMENT.equals(normalize(deptType));
    }
}
