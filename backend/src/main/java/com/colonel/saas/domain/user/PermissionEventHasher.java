package com.colonel.saas.domain.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 角色/菜单权限摘要哈希工具。
 *
 * <p>用于生成角色权限数据的 SHA-256 摘要，避免领域事件（{@link com.colonel.saas.domain.user.event.RolePermissionUpdatedEvent}）
 * 携带完整的 permissions JSON 数据。哈希结果仅用于变更检测（oldHash != newHash 表示权限有变更），
 * 不可逆向还原权限内容。</p>
 *
 * <p>哈希策略：
 * <ol>
 *   <li>菜单 ID 列表：排序后拼接为 {@code "menus:id1,id2,..."} 格式再哈希</li>
 *   <li>完整权限：在菜单哈希基础上拼接 {@code "permissions:{json}"} 后再哈希</li>
 * </ol>
 *
 * <p>所有方法为静态工具方法，不可实例化。</p>
 */
public final class PermissionEventHasher {

    /** JSON 序列化器，用于将 permissions Map 转为字符串。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 工具类，禁止实例化。 */
    private PermissionEventHasher() {
    }

    /**
     * 计算菜单 ID 列表的 SHA-256 哈希。
     *
     * <p>将菜单 ID 排序后拼接为逗号分隔的字符串，以 {@code "menus:"} 为前缀后计算哈希。
     * 空列表返回 {@code sha256("menus:empty")}。</p>
     *
     * @param menuIds 菜单 ID 列表（可为 null 或空）
     * @return SHA-256 十六进制摘要字符串
     */
    public static String hashMenuIds(Collection<UUID> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return sha256Hex("menus:empty");
        }
        String normalized = menuIds.stream()
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.joining(","));
        return sha256Hex("menus:" + normalized);
    }

    /**
     * 计算角色完整权限数据（菜单 + 权限 JSON）的 SHA-256 哈希。
     *
     * <p>先计算菜单 ID 哈希作为基础，再拼接 permissions JSON 后计算最终哈希。
     * 当 permissions 序列化失败时，使用 {@code "permissions:serialize-error"} 占位符。</p>
     *
     * @param permissions 权限数据 Map（可为 null 或空）
     * @param menuIds     菜单 ID 列表（可为 null 或空）
     * @return SHA-256 十六进制摘要字符串
     */
    public static String hashRolePermissions(Map<String, Object> permissions, Collection<UUID> menuIds) {
        String menuPart = hashMenuIds(menuIds);
        if (permissions == null || permissions.isEmpty()) {
            return sha256Hex(menuPart + "|permissions:empty");
        }
        try {
            String json = MAPPER.writeValueAsString(permissions);
            return sha256Hex(menuPart + "|permissions:" + json);
        } catch (JsonProcessingException ex) {
            return sha256Hex(menuPart + "|permissions:serialize-error");
        }
    }

    /**
     * 计算字符串的 SHA-256 十六进制摘要。
     *
     * @param value 待哈希的字符串
     * @return SHA-256 十六进制摘要（小写字母）
     * @throws IllegalStateException 如果 JVM 不支持 SHA-256 算法（正常情况下不会发生）
     */
    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
