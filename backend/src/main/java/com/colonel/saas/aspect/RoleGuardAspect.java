package com.colonel.saas.aspect;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.RoleCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 角色守卫切面（Role Guard Aspect）。
 *
 * <h3>架构角色</h3>
 * <p>此切面是 SAAS 平台<strong>接口级角色鉴权</strong>的核心执行组件，通过 AOP 拦截所有 Controller
 * 层方法，在方法执行前检查当前请求携带的角色信息是否满足 {@link com.colonel.saas.annotation.RequireRoles}
 * 注解声明的角色要求。不满足则抛出 {@link com.colonel.saas.common.exception.ForbiddenException}（403）。</p>
 *
 * <h3>鉴权流程</h3>
 * <ol>
 *   <li>解析目标方法或类上的 {@link RequireRoles} 注解（方法级优先于类级）</li>
 *   <li>若注解不存在或角色列表为空，直接放行（不进行角色校验）</li>
 *   <li>从当前 HTTP 请求的 attribute 中提取当前用户的角色编码集合</li>
 *   <li>若当前用户包含 {@link com.colonel.saas.constant.RoleCodes#ADMIN}（管理员），直接放行</li>
 *   <li>若当前用户的任一角色与注解声明的角色匹配，放行</li>
 *   <li>以上均不满足，抛出 403 无权限异常</li>
 * </ol>
 *
 * <h3>与其他组件的关系</h3>
 * <ul>
 *   <li><strong>触发标记</strong>：{@link com.colonel.saas.annotation.RequireRoles} 注解 — 标注在 Controller 类或方法上</li>
 *   <li><strong>角色编码</strong>：{@link com.colonel.saas.constant.RoleCodes} — 定义系统中的角色编码常量</li>
 *   <li><strong>异常</strong>：{@link com.colonel.saas.common.exception.ForbiddenException} — 鉴权失败时抛出</li>
 *   <li><strong>上下文来源</strong>：前置拦截器或 Filter 负责将 roleCodes 写入 request attribute</li>
 * </ul>
 *
 * <h3>拦截范围</h3>
 * <p>使用 {@code @Around("execution(* com.colonel.saas.controller..*(..))")} 切点表达式，
 * 拦截 {@code com.colonel.saas.controller} 包及其所有子包下的任意类的任意方法。
 * 这确保了所有 Controller 端点都受角色鉴权保护，无需逐个添加切点。</p>
 *
 * <h3>角色编码比较规则</h3>
 * <p>所有角色编码在比较前统一进行 {@code trim().toLowerCase(Locale.ROOT)} 规范化处理，
 * 以实现大小写不敏感和去除前后空格的健壮匹配。</p>
 *
 * @see com.colonel.saas.annotation.RequireRoles 标注此注解的 Controller 方法/类会被此切面鉴权
 * @see com.colonel.saas.constant.RoleCodes 系统角色编码常量定义
 * @see com.colonel.saas.common.exception.ForbiddenException 鉴权失败时抛出的异常
 */
@Aspect
@Component
public class RoleGuardAspect {

    /**
     * 环绕通知：拦截所有 Controller 方法，在执行前进行角色鉴权。
     *
     * <p>此方法是整个角色守卫的入口，通过切点表达式匹配所有 Controller 层的公开方法。
     * 鉴权通过后才允许目标方法执行，否则抛出 {@link ForbiddenException}。</p>
     *
     * @param point AOP 连接点，携带目标 Controller 方法的全部信息（方法签名、参数、目标类等）
     * @return 目标方法的原始返回值（鉴权通过时）
     * @throws Throwable 目标方法执行过程中抛出的任何异常
     * @throws ForbiddenException 当当前用户不满足注解声明的角色要求时抛出（HTTP 403）
     */
    @Around("execution(* com.colonel.saas.controller..*(..))")
    public Object guard(ProceedingJoinPoint point) throws Throwable {
        // 第一步：解析目标方法或类上的 @RequireRoles 注解
        // 方法级注解优先于类级注解（详见 resolveRequireRoles 方法）
        RequireRoles requireRoles = resolveRequireRoles(point);
        // 若注解不存在或角色列表为空，表示不进行角色校验，直接放行
        if (requireRoles == null || requireRoles.value().length == 0) {
            return point.proceed();
        }

        // 第二步：从当前 HTTP 请求中提取用户的角色编码集合
        Set<String> currentRoles = resolveCurrentRoles();
        // 管理员角色拥有最高权限，直接放行，无需匹配具体角色列表
        if (currentRoles.contains(RoleCodes.ADMIN)) {
            return point.proceed();
        }

        // 第三步：遍历注解声明的角色列表，检查当前用户是否拥有匹配的角色
        // 只要有一个角色匹配即视为通过鉴权
        for (String role : requireRoles.value()) {
            if (currentRoles.contains(normalize(role))) {
                return point.proceed();
            }
        }

        // 所有角色均不匹配，抛出 403 无权限异常
        throw new ForbiddenException("无权限访问该接口");
    }

    /**
     * 解析目标方法上的 {@link RequireRoles} 注解，遵循"方法级优先于类级"的策略。
     *
     * <p>解析优先级：</p>
     * <ol>
     *   <li>优先查找方法上直接标注的 {@code @RequireRoles} 注解</li>
     *   <li>若方法上无此注解，则查找目标 Controller 类上标注的 {@code @RequireRoles} 注解</li>
     *   <li>若两者均无，返回 null（表示不进行角色校验）</li>
     * </ol>
     *
     * <p>这种设计允许在类级别设置默认角色要求，同时在特定方法上通过更具体的角色要求进行覆盖。</p>
     *
     * @param point AOP 连接点，用于获取目标方法签名和目标类
     * @return 解析到的 {@link RequireRoles} 注解实例；若方法和类上均无此注解则返回 null
     */
    private RequireRoles resolveRequireRoles(ProceedingJoinPoint point) {
        // 通过 MethodSignature 获取目标方法的 Method 对象
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        // 优先查找方法级别的注解
        RequireRoles methodAnnotation = method.getAnnotation(RequireRoles.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        // 方法上无注解，回退到类级别
        Class<?> targetClass = point.getTarget().getClass();
        return targetClass.getAnnotation(RequireRoles.class);
    }

    /**
     * 从当前 HTTP 请求中解析当前用户的角色编码集合。
     *
     * <p>角色编码由前置拦截器或 Filter 在请求鉴权后写入 request attribute（key 为 {@code "roleCodes"}）。
     * 支持两种存储形式：</p>
     * <ul>
     *   <li><strong>Collection 类型</strong>（如 {@code List<String>}）：逐个规范化后加入集合</li>
     *   <li><strong>String 类型</strong>（如 {@code "OPERATOR,VIEWER"}）：按逗号拆分后逐个规范化后加入集合</li>
     * </ul>
     *
     * @return 当前用户的角色编码集合（全部已规范化为小写）；非 HTTP 环境或无角色信息时返回空集合（永不返回 null）
     */
    private Set<String> resolveCurrentRoles() {
        // 从 Spring 的 RequestContextHolder 获取当前请求的属性
        // 若不在 HTTP 请求上下文中，返回空集合
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttrs)) {
            return Set.of();
        }
        HttpServletRequest request = servletAttrs.getRequest();
        // 从 request attribute 中获取 roleCodes，由上游认证拦截器写入
        Object raw = request.getAttribute("roleCodes");
        if (raw == null) {
            return Set.of();
        }

        Set<String> roles = new HashSet<>();
        // 处理 Collection 类型的角色编码（如 List<String>、Set<String>）
        if (raw instanceof Collection<?> collection) {
            for (Object value : collection) {
                String normalized = normalize(Objects.toString(value, ""));
                if (!normalized.isBlank()) {
                    roles.add(normalized);
                }
            }
            return roles;
        }
        // 处理逗号分隔的字符串类型角色编码（如 "OPERATOR,VIEWER"）
        String text = Objects.toString(raw, "");
        if (text.isBlank()) {
            return Set.of();
        }
        Arrays.stream(text.split(","))
                .map(this::normalize)
                .filter(item -> !item.isBlank())
                .forEach(roles::add);
        return roles;
    }

    /**
     * 规范化角色编码字符串，实现大小写不敏感的健壮匹配。
     *
     * <p>对角色编码执行以下规范化操作：</p>
     * <ol>
     *   <li>null 值转为空字符串</li>
     *   <li>去除前后空白字符（{@code trim()}）</li>
     *   <li>统一转为小写（{@code toLowerCase(Locale.ROOT)}，使用 ROOT Locale 避免特定语言环境的大小写异常）</li>
     * </ol>
     *
     * @param roleCode 原始角色编码，可能为 null 或包含前后空格/大小写混用
     * @return 规范化后的小写角色编码字符串；输入为 null 时返回空字符串
     */
    private String normalize(String roleCode) {
        return roleCode == null ? "" : roleCode.trim().toLowerCase(Locale.ROOT);
    }
}

