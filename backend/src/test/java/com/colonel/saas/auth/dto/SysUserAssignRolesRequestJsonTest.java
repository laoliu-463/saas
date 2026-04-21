package com.colonel.saas.auth.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SysUserAssignRolesRequestJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeStringRoleIdsToUuidList() throws Exception {
        UUID roleA = UUID.randomUUID();
        UUID roleB = UUID.randomUUID();
        String json = """
                {
                  "roleIds": ["%s", "%s"]
                }
                """.formatted(roleA, roleB);

        SysUserAssignRolesRequest request = objectMapper.readValue(json, SysUserAssignRolesRequest.class);

        assertThat(request.roleIds()).containsExactly(roleA, roleB);
    }

    @Test
    void shouldSerializeUuidListAsStringArray() throws Exception {
        List<UUID> roleIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        SysUserAssignRolesRequest request = new SysUserAssignRolesRequest(roleIds);

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains(roleIds.get(0).toString()).contains(roleIds.get(1).toString());
    }
}
