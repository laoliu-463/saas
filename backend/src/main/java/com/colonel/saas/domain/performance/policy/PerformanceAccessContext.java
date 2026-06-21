package com.colonel.saas.domain.performance.policy;

import com.colonel.saas.common.enums.DataScope;

import java.util.List;
import java.util.UUID;

/**
 * 业绩数据访问上下文，封装当前用户的身份与权限信息，用于数据范围过滤。
 *
 * <ul>
 *   <li>携带用户 ID、部门 ID、数据范围（PERSONAL / DEPT / ALL）和角色编码列表</li>
 *   <li>与 {@link PerformanceAccessScope} 配合，决定当前用户可查看哪些业绩记录</li>
 *   <li>在业绩查询、导出、重算等场景中作为权限判断的入参传递</li>
 * </ul>
 *
 * <p>在架构中属于业绩域（Performance Domain）的权限值对象，由 Controller 层从
 * {@code SecurityContextHolder} 构建后注入到 Service 层。</p>
 *
 * @param userId    当前登录用户 ID
 * @param deptId    当前用户所属部门 ID
 * @param dataScope 数据范围枚举（PERSONAL：仅个人 / DEPT：部门级 / ALL：全部）
 * @param roleCodes 当前用户拥有的角色编码列表
 *
 * @see PerformanceAccessScope
 */
public record PerformanceAccessContext(
        UUID userId,
        UUID deptId,
        DataScope dataScope,
        List<String> roleCodes) {

    /**
     * 工厂方法：构建业绩访问上下文，对 null 参数提供安全默认值。
     *
     * <ol>
     *   <li>第一步：校验 dataScope，若为 null 则默认为 PERSONAL（最小权限原则）</li>
     *   <li>第二步：校验 roleCodes，若为 null 则默认为空列表（无角色）</li>
     *   <li>第三步：组装不可变 record 实例并返回</li>
     * </ol>
     *
     * @param userId    当前用户 ID
     * @param deptId    当前用户部门 ID
     * @param dataScope 数据范围枚举，null 时默认 PERSONAL
     * @param roleCodes 角色编码列表，null 时默认空列表
     * @return 构建好的访问上下文实例
     */
    public static PerformanceAccessContext of(
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            List<String> roleCodes) {
        return new PerformanceAccessContext(
                userId,
                deptId,
                // 第一步：dataScope 为 null 时默认最小权限 PERSONAL
                dataScope == null ? DataScope.PERSONAL : dataScope,
                // 第二步：roleCodes 为 null 时默认空列表
                roleCodes == null ? List.of() : roleCodes);
    }
}
