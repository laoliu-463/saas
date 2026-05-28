package com.colonel.saas.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据范围过滤注解，用于在 Mapper 层自动追加数据权限条件。
 *
 * <p>架构角色：此注解是数据权限体系的核心标记，配合 {@link com.colonel.saas.aspect.DataScopeAspect} 切面使用。
 * 当 Mapper 方法被此注解标注后，AOP 切面会在 SQL 执行前自动向 MyBatis-Plus 的查询条件
 * （{@link com.baomidou.mybatisplus.core.conditions.AbstractWrapper}）中追加数据范围过滤条件，
 * 实现行级数据隔离。</p>
 *
 * <h3>数据范围级别</h3>
 * <ul>
 *   <li><strong>PERSONAL（个人）</strong>：仅查询当前用户自己的数据，按 {@link #userField()} 配置的字段过滤</li>
 *   <li><strong>DEPT（部门）</strong>：查询当前用户所属部门的所有数据，按 dept_id 字段过滤</li>
 *   <li><strong>ALL（全部）</strong>：不做额外过滤，查询全部数据（通常仅管理员拥有此权限）</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <p>适用于需要根据用户角色和数据权限自动限制查询范围的 Mapper 方法，例如：</p>
 * <ul>
 *   <li>订单列表查询 — 销售人员只能看到自己的订单</li>
 *   <li>业绩汇总查询 — 部门经理只能看到本部门的业绩</li>
 *   <li>达人列表查询 — 按归属范围过滤达人数据</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @DataScope
 * List<Order> selectOrderList(@Param("ew") Wrapper<Order> wrapper);
 *
 * @DataScope(userField = "creator_id")
 * List<Talent> selectTalentList(@Param("ew") Wrapper<Talent> wrapper);
 * }</pre>
 *
 * @see com.colonel.saas.aspect.DataScopeAspect
 * @see com.colonel.saas.common.enums.DataScope
 */
// 仅允许标注在方法上（不能标注在类、字段等位置），确保精确到单个 Mapper 方法的数据范围控制
@Target(ElementType.METHOD)
// 运行时保留注解，使得 AOP 切面（DataScopeAspect）能够通过反射读取注解信息
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * PERSONAL 范围对应的用户字段名。
     *
     * <p>当数据范围为 PERSONAL 时，切面会使用此字段名作为查询条件的列名，
     * 与当前登录用户的 userId 进行等值匹配。</p>
     *
     * <p>默认值为 {@code "user_id"}，适用于大多数场景。
     * 如果目标表的用户关联字段名不同（如 {@code "creator_id"}、{@code "owner_id"}），
     * 需要在注解中显式指定。</p>
     *
     * @return 用户字段名称，默认 "user_id"
     */
    String userField() default "user_id";
}
