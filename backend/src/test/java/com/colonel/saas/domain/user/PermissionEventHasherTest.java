package com.colonel.saas.domain.user;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionEventHasherTest {

    @Test
    void hashMenuIds_shouldBeStableForSameInput() {
        UUID a = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID b = UUID.fromString("22222222-2222-2222-2222-222222222222");

        String hash1 = PermissionEventHasher.hashMenuIds(List.of(b, a));
        String hash2 = PermissionEventHasher.hashMenuIds(List.of(a, b));

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashRolePermissions_shouldChangeWhenMenusChange() {
        UUID menuId = UUID.randomUUID();
        String before = PermissionEventHasher.hashRolePermissions(Map.of(), List.of(menuId));
        String after = PermissionEventHasher.hashRolePermissions(Map.of(), List.of());

        assertThat(before).isNotEqualTo(after);
    }
}
