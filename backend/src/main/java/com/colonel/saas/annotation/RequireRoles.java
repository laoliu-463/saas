package com.colonel.saas.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限控制注解，用于声明接口或类所需的角色编码。
 *
 * <p>架构角色：此注解是接口级角色鉴权的核心标记，配合 {@link com.colonel.saas.aspect.RoleGuardAspect} 切面使用。
 * 当 Controller 方法或类被此注解标注后，AOP 切面会在方法执行前检查当前请求携带的角色信息是否满足要求，
 * 不满足则抛出 {@link com.colonel.saas.common.exception.ForbiddenException}。</p>
 *
 * <h3>作用范围</h3>
 * <ul>
 *   <li><strong>方法级别</strong>：仅对该 Controller 方法生效，优先级高于类级别注解</li>
 *   <li><strong>类级别</strong>：对该 Controller 中所有方法生效，作为默认角色要求</li>
 * </ul>
 *
 * <h3>鉴权逻辑</h3>
 * <ol>
 *   <li>若方法或类上均无此注解（或 value 为空数组），则放行，不进行角色校验</li>
 *   <li>当前用户角色中包含 {@link com.colonel.saas.constant.RoleCodes#ADMIN}（管理员）时，直接放行</li>
 *   <li>当前用户的任一角色与注解声明的角色匹配时放行</li>
 *   <li>以上均不满足时，抛出 403 无权限异常</li>
 * </ol>
 *
 * <h3>角色编码规范</h3>
 * <p>角色编码定义在 {@link com.colonel.saas.constant.RoleCodes} 中，比较时忽略大小写和前后空格。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 方法级别 — 仅允许运营角色访问
 * @RequireRoles(RoleCodes.OPERATOR)
 * @GetMapping("/talents")
 * public ApiResponse<List<Talent>> listTalents() { ... }
 *
 * // 类级别 — 整个 Controller 要求运营或管理员角色
 * @RequireRoles({RoleCodes.OPERATOR, RoleCodes.MANAGER})
 * @RestController
 * @RequestMapping("/ops")
 * public class OpsController { ... }
 * }</pre>
 *
 * @see com.colonel.saas.aspect.RoleGuardAspect
 * @see com.colonel.saas.constant.RoleCodes
 * @see com.colonel.saas.common.exception.ForbiddenException
 */
// 同时允许标注在类和方法上：方法级注解优先级高于类级，实现灵活的层级覆盖机制
@Target({ElementType.TYPE, ElementType.METHOD})
// 运行时保留注解，使得 AOP 切面（RoleGuardAspect）能够通过反射读取注解信息
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRoles {

    /**
     * 允许访问的角色编码列表。
     *
     * <p>当前用户的任一角色与列表中任意一项匹配即视为通过鉴权。
     * 角色编码定义在 {@link com.colonel.saas.constant.RoleCodes} 中。</p>
     *
     * <p>若为空数组或未设置，表示不进行角色校验，直接放行。</p>
     *
     * @return 允许的角色编码数组，不能为空（但可以是空数组）
     */
    String[] value();
}

