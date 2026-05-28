package com.colonel.saas.common.enums;

import com.colonel.saas.common.exception.BusinessException;

/**
 * 数据范围枚举，定义用户可访问的数据边界。
 *
 * <p>用于实现行级数据权限控制。系统根据用户角色分配的数据范围，
 * 在查询时自动追加过滤条件，确保用户只能访问其权限范围内的数据。</p>
 *
 * <h3>权限层级</h3>
 * <ul>
 *   <li>{@link #PERSONAL}（code=1）— 个人数据：仅能看到自己创建或负责的记录</li>
 *   <li>{@link #DEPT}（code=2）— 部门数据：能看到本部门所有成员的记录</li>
 *   <li>{@link #ALL}（code=3）— 全部数据：能看到系统中所有记录（管理员级别）</li>
 * </ul>
 *
 * <h3>实现机制</h3>
 * <p>数据范围通常在用户-角色关联表中配置，查询时通过 MyBatis 拦截器
 * 或手动在 SQL 中追加 {@code WHERE create_by = #{currentUserId}}（个人）
 * 或 {@code WHERE dept_id = #{currentDeptId}}（部门）来实现。</p>
 *
 * @see com.colonel.saas.common.result.ResultCode#FORBIDDEN 无权限时的状态码
 */
public enum DataScope {
    /** 个人数据范围：仅能看到自己创建或负责的记录 */
    PERSONAL(1),
    /** 部门数据范围：能看到本部门所有成员的记录 */
    DEPT(2),
    /** 全部数据范围：能看到系统中所有记录（管理员级别） */
    ALL(3);

    /** 数据范围的数字编码，存储在数据库中 */
    private final int code;

    DataScope(int code) {
        this.code = code;
    }

    /**
     * 获取数据范围的数字编码。
     *
     * @return 数据范围编码（1=个人, 2=部门, 3=全部）
     */
    public int getCode() {
        return code;
    }

    /**
     * 根据数字编码查找对应的数据范围枚举实例。
     *
     * @param code 数据范围数字编码
     * @return 匹配的枚举实例
     * @throws BusinessException 当编码不在有效范围内（1-3）时抛出参数错误异常
     */
    public static DataScope fromCode(int code) {
        for (DataScope scope : values()) {
            if (scope.code == code) {
                return scope;
            }
        }
        throw BusinessException.param("非法数据范围: " + code);
    }
}
