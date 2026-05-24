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
 * 角色/菜单权限摘要，避免领域事件携带整包 permissions JSON。
 */
public final class PermissionEventHasher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PermissionEventHasher() {
    }

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
