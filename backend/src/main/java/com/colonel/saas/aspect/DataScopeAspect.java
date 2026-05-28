package com.colonel.saas.aspect;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.colonel.saas.annotation.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.UUID;

/**
 * 数据范围过滤切面（Data Scope Aspect）。
 *
 * <h3>架构角色</h3>
 * <p>此切面是 SAAS 平台<strong>行级数据权限</strong>的核心执行组件，通过 AOP 拦截所有标注了
 * {@link com.colonel.saas.annotation.DataScope} 注解的 Mapper 方法，在 SQL 执行前自动向
 * MyBatis-Plus 的查询条件中追加数据范围过滤条件，实现不同角色/用户的查询结果自动隔离。</p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>拦截标注了 {@code @DataScope} 的 Mapper 方法</li>
 *   <li>从方法参数中查找 MyBatis-Plus 的 {@link AbstractWrapper} 查询条件对象</li>
 *   <li>从当前 HTTP 请求的 attribute 中解析用户上下文（userId、deptId、dataScope）</li>
 *   <li>根据数据范围级别向 Wrapper 追加过滤条件：
 *     <ul>
 *       <li><strong>PERSONAL</strong>：追加 {@code WHERE user_field = userId}，只查当前用户的数据</li>
 *       <li><strong>DEPT</strong>：追加 {@code WHERE dept_id = deptId}，只查当前部门的数据</li>
 *       <li><strong>ALL</strong>：不追加任何条件，查询全部数据（管理员权限）</li>
 *     </ul>
 *   </li>
 *   <li>放行原始方法执行，MyBatis-Plus 会将追加后的条件纳入最终 SQL</li>
 * </ol>
 *
 * <h3>与其他组件的关系</h3>
 * <ul>
 *   <li><strong>触发标记</strong>：{@link com.colonel.saas.annotation.DataScope} 注解 — 标注在 Mapper 方法上</li>
 *   <li><strong>范围枚举</strong>：{@link com.colonel.saas.common.enums.DataScope} — 定义 PERSONAL/DEPT/ALL 三级</li>
 *   <li><strong>上下文来源</strong>：前置拦截器或 Filter 负责将 userId、deptId、dataScope 写入 request attribute</li>
 *   <li><strong>业务场景</strong>：订单列表、业绩汇总、达人查询等需要按归属范围过滤的数据查询方法</li>
 * </ul>
 *
 * <h3>边界条件与异常处理</h3>
 * <ul>
 *   <li>方法参数中无 {@link AbstractWrapper} 时直接放行（兼容不含 Wrapper 参数的查询）</li>
 *   <li>请求上下文为空或数据范围为空时直接放行（兼容非 HTTP 请求的调用场景，如定时任务）</li>
 *   <li>PERSONAL 范围下 userId 为 null 时抛出 403 异常，防止未登录用户绕过数据隔离</li>
 *   <li>DEPT 范围下 deptId 为 null 时抛出 403 异常，防止缺少部门归属的用户绕过数据隔离</li>
 *   <li>ALL 范围不检查任何上下文，直接放行</li>
 * </ul>
 *
 * @see com.colonel.saas.annotation.DataScope 标注此注解的 Mapper 方法会被此切面拦截
 * @see com.colonel.saas.common.enums.DataScope 数据范围枚举定义
 */
@Aspect
@Component
public class DataScopeAspect {

    /**
     * 环绕通知：拦截所有标注了 {@link DataScope} 注解的方法，在目标方法执行前追加数据范围过滤条件。
     *
     * <p>执行策略：</p>
     * <ol>
     *   <li>从目标方法的参数列表中查找 MyBatis-Plus 的 {@link AbstractWrapper} 实例</li>
     *   <li>若找不到 Wrapper 或无法解析上下文，直接放行目标方法</li>
     *   <li>根据当前用户的 {@code dataScope} 级别，向 Wrapper 追加对应的等值过滤条件</li>
     *   <li>PERSONAL 级别：使用 {@link DataScope#userField()} 指定的字段名与 userId 匹配</li>
     *   <li>DEPT 级别：使用固定字段 {@code "dept_id"} 与 deptId 匹配</li>
     *   <li>ALL 级别：不做任何过滤，直接放行</li>
     * </ol>
     *
     * @param point    AOP 连接点，携带目标方法的所有信息（方法签名、参数等）
     * @param dataScope 标注在目标方法上的 {@link DataScope} 注解实例，用于获取 userField 等配置
     * @return 目标方法的原始返回值
     * @throws Throwable 目标方法执行过程中抛出的任何异常
     * @throws BusinessException 当 PERSONAL 范围缺少 userId 或 DEPT 范围缺少 deptId 时抛出 403 异常
     */
    @Around("@annotation(dataScope)")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object around(ProceedingJoinPoint point, DataScope dataScope) throws Throwable {
        // 第一步：从目标方法的参数中查找 MyBatis-Plus 的查询条件包装器
        // 若找不到 Wrapper，说明该查询方法不需要条件追加，直接放行
        AbstractWrapper<?, ?, ?> wrapper = findWrapper(point.getArgs());
        if (wrapper == null) {
            return point.proceed();
        }

        // 第二步：从当前 HTTP 请求中解析数据权限上下文（userId、deptId、数据范围级别）
        // 若上下文为空（非 HTTP 调用场景），直接放行，不做数据范围限制
        ScopeContext context = resolveContext();
        if (context == null || context.scope() == null) {
            return point.proceed();
        }
        // 类型擦除后进行强制转换，以便调用 eq() 方法追加查询条件
        AbstractWrapper rawWrapper = (AbstractWrapper) wrapper;

        // 第三步：根据数据范围级别向 Wrapper 追加过滤条件
        com.colonel.saas.common.enums.DataScope scope = context.scope();
        if (scope == com.colonel.saas.common.enums.DataScope.PERSONAL) {
            // 个人范围：仅查询当前用户自己的数据
            // 需要 userId 上下文，若缺失则抛出 403 异常，防止未授权访问
            if (context.userId() == null) {
                throw BusinessException.forbidden("数据权限异常：缺少用户上下文");
            }
            // 使用注解中配置的 userField（默认 "user_id"）作为列名，与当前用户 ID 进行等值匹配
            rawWrapper.eq(dataScope.userField(), context.userId());
        } else if (scope == com.colonel.saas.common.enums.DataScope.DEPT) {
            // 部门范围：查询当前用户所属部门的所有数据
            // 需要 deptId 上下文，若缺失则抛出 403 异常
            if (context.deptId() == null) {
                throw BusinessException.forbidden("数据权限异常：缺少部门上下文");
            }
            // 使用固定的 dept_id 字段与当前部门 ID 进行等值匹配
            rawWrapper.eq("dept_id", context.deptId());
        }
        // ALL 范围：不追加任何过滤条件，查询全部数据（管理员权限）
        // 放行目标方法执行，追加后的 Wrapper 条件会被 MyBatis-Plus 纳入最终 SQL
        return point.proceed();
    }

    /**
     * 从目标方法的参数列表中查找 MyBatis-Plus 的 {@link AbstractWrapper} 实例。
     *
     * <p>Mapper 方法通常将 {@code @Param("ew") Wrapper<T>} 作为查询条件参数传入，
     * 此方法遍历所有参数，返回第一个匹配的 Wrapper 实例。</p>
     *
     * @param args 目标方法的参数数组，由 AOP 框架从 {@link ProceedingJoinPoint#getArgs()} 获取
     * @return 找到的 {@link AbstractWrapper} 实例；若参数为 null 或其中无 Wrapper 类型参数则返回 null
     */
    private AbstractWrapper<?, ?, ?> findWrapper(Object[] args) {
        if (args == null) {
            return null;
        }
        // 遍历所有参数，使用 instanceof 模式匹配（Java 16+）查找 Wrapper 类型的参数
        for (Object arg : args) {
            if (arg instanceof AbstractWrapper<?, ?, ?> wrapper) {
                return wrapper;
            }
        }
        return null;
    }

    /**
     * 从当前 HTTP 请求的 attribute 中解析数据权限上下文。
     *
     * <p>上下文数据（userId、deptId、dataScope）由前置拦截器或 Filter 在请求进入时写入
     * request attribute。此方法从 attribute 中提取并组装为 {@link ScopeContext} 记录。</p>
     *
     * <p>若当前请求不在 HTTP 环境中（如单元测试、定时任务调用），则返回 null，
     * 表示不做数据范围限制。</p>
     *
     * @return 包含 userId、deptId、dataScope 的上下文对象；非 HTTP 环境下返回 null
     */
    private ScopeContext resolveContext() {
        // 从 Spring 的 RequestContextHolder 获取当前请求的属性
        // 若不在 HTTP 请求上下文中（如异步任务、测试环境），直接返回 null
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        HttpServletRequest request = servletAttrs.getRequest();

        // 从 request attribute 中提取用户 ID、部门 ID 和数据范围
        // 这些值由上游的认证拦截器/Filter 在请求鉴权后写入
        UUID userId = asUuid(request.getAttribute("userId"));
        UUID deptId = asUuid(request.getAttribute("deptId"));
        com.colonel.saas.common.enums.DataScope scope = asScope(request.getAttribute("dataScope"));
        return new ScopeContext(userId, deptId, scope);
    }

    /**
     * 将 request attribute 中的原始值安全地转换为 {@link UUID} 类型。
     *
     * <p>支持两种输入形式：</p>
     * <ul>
     *   <li>已经是 {@link UUID} 类型，直接返回</li>
     *   <li>是 {@link String} 类型，通过 {@link UUID#fromString(String)} 转换</li>
     * </ul>
     *
     * @param raw request attribute 中存储的原始值，可能为 null、UUID 实例或 UUID 字符串
     * @return 转换后的 UUID；若输入为 null、空白字符串或不合法的 UUID 格式则返回 null
     */
    private UUID asUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        // 若已经是 UUID 类型，直接返回，避免不必要的转换
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        String text = raw.toString();
        if (text.isBlank()) {
            return null;
        }
        // 将字符串解析为 UUID；若格式非法会抛出 IllegalArgumentException，由调用方决定处理策略
        return UUID.fromString(text);
    }

    /**
     * 将 request attribute 中的原始值安全地转换为 {@link com.colonel.saas.common.enums.DataScope} 枚举。
     *
     * <p>支持多种输入形式，按优先级依次尝试：</p>
     * <ol>
     *   <li>已经是 {@code DataScope} 枚举实例，直接返回</li>
     *   <li>是数值类型（Integer 等），通过 {@code fromCode()} 按编码查找</li>
     *   <li>是字符串，先尝试 {@code valueOf()} 按名称匹配，失败后尝试按数值编码查找</li>
     * </ol>
     *
     * @param raw request attribute 中存储的原始值，可能为 null、枚举实例、数字或字符串
     * @return 转换后的 {@link com.colonel.saas.common.enums.DataScope} 枚举值；
     *         若输入为 null、空白字符串或无法识别的值则返回 null
     */
    private com.colonel.saas.common.enums.DataScope asScope(Object raw) {
        if (raw == null) {
            return null;
        }
        // 已经是枚举类型，直接返回
        if (raw instanceof com.colonel.saas.common.enums.DataScope scope) {
            return scope;
        }
        // 数值类型：通过编码查找枚举值（如 1 -> PERSONAL, 2 -> DEPT, 3 -> ALL）
        if (raw instanceof Number number) {
            return com.colonel.saas.common.enums.DataScope.fromCode(number.intValue());
        }
        String text = Objects.toString(raw, "").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            // 优先按枚举名称匹配（忽略大小写），如 "personal" -> PERSONAL
            return com.colonel.saas.common.enums.DataScope.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // 名称匹配失败，尝试将字符串解析为整数编码后按编码查找
            // 若编码也非法（如 "abc"），此处会抛出 NumberFormatException，由上层处理
            return com.colonel.saas.common.enums.DataScope.fromCode(Integer.parseInt(text));
        }
    }

    /**
     * 数据权限上下文记录，封装从 HTTP 请求中解析出的用户权限信息。
     *
     * <p>使用 Java record（Java 16+）作为不可变值对象，一次构建后不可修改。</p>
     *
     * @param userId 当前登录用户的 ID（UUID 格式），PERSONAL 范围的过滤依据
     * @param deptId 当前用户所属部门的 ID（UUID 格式），DEPT 范围的过滤依据
     * @param scope  数据范围级别枚举，决定追加哪种过滤条件（PERSONAL / DEPT / ALL）
     */
    private record ScopeContext(UUID userId, UUID deptId, com.colonel.saas.common.enums.DataScope scope) {
    }
}
