package com.colonel.saas.security;

/**
 * 待激活用户仅允许访问当前用户信息与改密接口。
 */
public final class PendingActivationAccessPolicy {

    private PendingActivationAccessPolicy() {
    }

    public static boolean isAllowed(String method, String path) {
        if (method == null || path == null || path.isBlank()) {
            return false;
        }
        String normalizedMethod = method.trim().toUpperCase();
        String normalizedPath = path.trim();
        if ("GET".equals(normalizedMethod) && "/users/current".equals(normalizedPath)) {
            return true;
        }
        if ("PUT".equals(normalizedMethod) && "/users/current/password".equals(normalizedPath)) {
            return true;
        }
        return "POST".equals(normalizedMethod) && "/auth/logout".equals(normalizedPath);
    }
}
