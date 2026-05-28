package com.colonel.saas.security;

/**
 * 待激活用户访问控制策略，定义处于"待激活"（pendingActivation）状态的用户允许访问的接口白名单。
 *
 * <p>架构角色：
 * <ul>
 *   <li>作为安全层的策略组件，为 {@link JwtAuthenticationFilter} 提供受限访问判断逻辑。</li>
 *   <li>新用户首次登录后处于待激活状态（通常由管理员创建账号后分配临时密码），
 *       此时仅允许访问有限接口：查看个人信息、修改密码、退出登录。
 *       修改密码后用户状态变更为正常激活状态。</li>
 * </ul>
 *
 * <p>白名单规则：
 * <ul>
 *   <li>{@code GET /users/current} — 获取当前用户信息（前端需要展示用户名等信息以便用户操作）</li>
 *   <li>{@code PUT /users/current/password} — 修改密码（激活操作的核心步骤）</li>
 *   <li>{@code POST /auth/logout} — 退出登录（允许用户放弃激活退出系统）</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>使用纯静态工具类（{@code final} 类 + 私有构造函数），无状态、无副作用。</li>
 *   <li>入参经过归一化处理（trim + 大小写），避免因格式差异误判。</li>
 *   <li>任何参数为 null 或空字符串时直接返回 {@code false}（保守策略，拒绝未知请求）。</li>
 * </ul>
 *
 * <p>与其他组件的关系：
 * <ul>
 *   <li>{@link JwtAuthenticationFilter} — 在 Token 中检测到 {@code pendingActivation=true} 时，
 *       调用本类的 {@link #isAllowed} 方法判断是否放行</li>
 *   <li>{@link com.colonel.saas.auth.service.AuthService} — 负责用户登录、密码修改和激活状态管理</li>
 * </ul>
 */
public final class PendingActivationAccessPolicy {

    /** 私有构造函数，防止实例化纯静态工具类 */
    private PendingActivationAccessPolicy() {
    }

    /**
     * 判断指定的 HTTP 方法和路径是否对"待激活"用户开放。
     *
     * <p>白名单规则（仅以下三个接口允许访问）：
     * <ol>
     *   <li>{@code GET /users/current} — 获取当前用户信息</li>
     *   <li>{@code PUT /users/current/password} — 修改密码</li>
     *   <li>{@code POST /auth/logout} — 退出登录</li>
     * </ol>
     *
     * @param method HTTP 方法（如 GET、POST、PUT），会自动 trim 并转大写
     * @param path   请求路径（如 /users/current），会自动 trim
     * @return {@code true} 允许访问，{@code false} 禁止访问
     */
    public static boolean isAllowed(String method, String path) {
        // 保守策略：参数为 null 或空时一律拒绝
        if (method == null || path == null || path.isBlank()) {
            return false;
        }
        // 归一化处理：去除首尾空格，方法名统一为大写
        String normalizedMethod = method.trim().toUpperCase();
        String normalizedPath = path.trim();
        // 白名单规则 1：允许查看当前用户信息
        if ("GET".equals(normalizedMethod) && "/users/current".equals(normalizedPath)) {
            return true;
        }
        // 白名单规则 2：允许修改密码（激活的核心操作）
        if ("PUT".equals(normalizedMethod) && "/users/current/password".equals(normalizedPath)) {
            return true;
        }
        // 白名单规则 3：允许退出登录
        return "POST".equals(normalizedMethod) && "/auth/logout".equals(normalizedPath);
    }
}
