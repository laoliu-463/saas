package com.colonel.saas.auth.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖 auth 模块所有 DTO 类的 getter/setter、builder 和 JSON 序列化。
 */
class AuthDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("LoginRequest")
    class LoginRequestTest {

        @Test
        void getterSetter() {
            LoginRequest req = new LoginRequest();
            req.setUsername("admin");
            req.setPassword("pass123");

            assertThat(req.getUsername()).isEqualTo("admin");
            assertThat(req.getPassword()).isEqualTo("pass123");
        }

        @Test
        void jsonRoundTrip() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setUsername("admin");
            req.setPassword("pass123");

            String json = objectMapper.writeValueAsString(req);
            LoginRequest deserialized = objectMapper.readValue(json, LoginRequest.class);

            assertThat(deserialized.getUsername()).isEqualTo("admin");
            assertThat(deserialized.getPassword()).isEqualTo("pass123");
        }
    }

    @Nested
    @DisplayName("LoginResponse")
    class LoginResponseTest {

        @Test
        void builder() {
            UUID userId = UUID.randomUUID();
            UUID deptId = UUID.randomUUID();

            LoginResponse resp = LoginResponse.builder()
                    .token("jwt_token")
                    .tokenType("Bearer")
                    .expiresIn(7200L)
                    .userId(userId)
                    .deptId(deptId)
                    .dataScope(3)
                    .roleCodes(List.of("admin"))
                    .username("admin")
                    .realName("管理员")
                    .refreshToken("refresh_jwt")
                    .refreshExpiresIn(604800L)
                    .accessTokenExpiresIn(7200L)
                    .build();

            assertThat(resp.getToken()).isEqualTo("jwt_token");
            assertThat(resp.getTokenType()).isEqualTo("Bearer");
            assertThat(resp.getExpiresIn()).isEqualTo(7200L);
            assertThat(resp.getUserId()).isEqualTo(userId);
            assertThat(resp.getDeptId()).isEqualTo(deptId);
            assertThat(resp.getDataScope()).isEqualTo(3);
            assertThat(resp.getRoleCodes()).containsExactly("admin");
            assertThat(resp.getUsername()).isEqualTo("admin");
            assertThat(resp.getRealName()).isEqualTo("管理员");
            assertThat(resp.getRefreshToken()).isEqualTo("refresh_jwt");
            assertThat(resp.getRefreshExpiresIn()).isEqualTo(604800L);
            assertThat(resp.getAccessTokenExpiresIn()).isEqualTo(7200L);
        }

        @Test
        void jsonSerialization() throws Exception {
            LoginResponse resp = LoginResponse.builder()
                    .token("jwt")
                    .tokenType("Bearer")
                    .userId(UUID.randomUUID())
                    .build();

            String json = objectMapper.writeValueAsString(resp);

            assertThat(json).contains("\"token\":\"jwt\"");
            assertThat(json).contains("\"tokenType\":\"Bearer\"");
        }
    }

    @Nested
    @DisplayName("RefreshRequest")
    class RefreshRequestTest {

        @Test
        void getterSetter() {
            RefreshRequest req = new RefreshRequest();
            req.setRefreshToken("refresh_jwt_token");

            assertThat(req.getRefreshToken()).isEqualTo("refresh_jwt_token");
        }
    }

    @Nested
    @DisplayName("RefreshResponse")
    class RefreshResponseTest {

        @Test
        void builder() {
            RefreshResponse resp = RefreshResponse.builder()
                    .accessToken("new_access")
                    .accessTokenExpiresIn(7200L)
                    .refreshToken("same_refresh")
                    .refreshExpiresIn(604800L)
                    .build();

            assertThat(resp.getAccessToken()).isEqualTo("new_access");
            assertThat(resp.getAccessTokenExpiresIn()).isEqualTo(7200L);
            assertThat(resp.getRefreshToken()).isEqualTo("same_refresh");
            assertThat(resp.getRefreshExpiresIn()).isEqualTo(604800L);
        }
    }

    @Nested
    @DisplayName("LogoutRequest")
    class LogoutRequestTest {

        @Test
        void getterSetter() {
            LogoutRequest req = new LogoutRequest();
            req.setAccessToken("access_token");
            req.setRefreshToken("refresh_token");

            assertThat(req.getAccessToken()).isEqualTo("access_token");
            assertThat(req.getRefreshToken()).isEqualTo("refresh_token");
        }

        @Test
        void accessToken_isOptional() {
            LogoutRequest req = new LogoutRequest();
            req.setRefreshToken("refresh_token");

            assertThat(req.getAccessToken()).isNull();
            assertThat(req.getRefreshToken()).isEqualTo("refresh_token");
        }
    }

    @Nested
    @DisplayName("SysRoleCreateRequest (record)")
    class SysRoleCreateRequestTest {

        @Test
        void recordAccessors() {
            var req = new SysRoleCreateRequest("admin", "管理员", 3, 1, "系统管理员");

            assertThat(req.roleCode()).isEqualTo("admin");
            assertThat(req.roleName()).isEqualTo("管理员");
            assertThat(req.dataScope()).isEqualTo(3);
            assertThat(req.status()).isEqualTo(1);
            assertThat(req.remark()).isEqualTo("系统管理员");
        }

        @Test
        void jsonRoundTrip() throws Exception {
            var req = new SysRoleCreateRequest("admin", "管理员", 3, 1, "备注");

            String json = objectMapper.writeValueAsString(req);
            var deserialized = objectMapper.readValue(json, SysRoleCreateRequest.class);

            assertThat(deserialized.roleCode()).isEqualTo("admin");
            assertThat(deserialized.dataScope()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("SysRoleUpdateRequest (record)")
    class SysRoleUpdateRequestTest {

        @Test
        void recordAccessors() {
            var req = new SysRoleUpdateRequest("biz_staff", "招商专员", 1, 1, "备注");

            assertThat(req.roleCode()).isEqualTo("biz_staff");
            assertThat(req.roleName()).isEqualTo("招商专员");
        }
    }

    @Nested
    @DisplayName("SysUserCreateRequest (record)")
    class SysUserCreateRequestTest {

        @Test
        void recordAccessors() {
            UUID deptId = UUID.randomUUID();
            List<UUID> roleIds = List.of(UUID.randomUUID(), UUID.randomUUID());

            var req = new SysUserCreateRequest(
                    "zhangsan", "Passw0rd!", "张三",
                    "13800138000", "zhangsan@test.com", null, null, deptId, roleIds);

            assertThat(req.username()).isEqualTo("zhangsan");
            assertThat(req.password()).isEqualTo("Passw0rd!");
            assertThat(req.realName()).isEqualTo("张三");
            assertThat(req.phone()).isEqualTo("13800138000");
            assertThat(req.email()).isEqualTo("zhangsan@test.com");
            assertThat(req.deptId()).isEqualTo(deptId);
            assertThat(req.roleIds()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("SysUserPageRequest (record)")
    class SysUserPageRequestTest {

        @Test
        void recordAccessors() {
            var req = new SysUserPageRequest(2, 20, "admin", 1, null, null, null, null);

            assertThat(req.page()).isEqualTo(2);
            assertThat(req.size()).isEqualTo(20);
            assertThat(req.keyword()).isEqualTo("admin");
        }

        @Test
        void pageNo_default() {
            var req = new SysUserPageRequest(null, null, null, null, null, null, null, null);
            assertThat(req.pageNo()).isEqualTo(1L);
            assertThat(req.pageSize()).isEqualTo(10L);
        }

        @Test
        void pageNo_fromPage() {
            var req = new SysUserPageRequest(5, 20, null, null, null, null, null, null);
            assertThat(req.pageNo()).isEqualTo(5L);
            assertThat(req.pageSize()).isEqualTo(20L);
        }
    }

    @Nested
    @DisplayName("SysUserUpdateRequest (record)")
    class SysUserUpdateRequestTest {

        @Test
        void recordAccessors() {
            var req = new SysUserUpdateRequest("张三", "13800138000", "test@test.com", 1, null, null, null);

            assertThat(req.realName()).isEqualTo("张三");
            assertThat(req.phone()).isEqualTo("13800138000");
            assertThat(req.email()).isEqualTo("test@test.com");
            assertThat(req.status()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("SysUserResetPasswordRequest (record)")
    class SysUserResetPasswordRequestTest {

        @Test
        void recordAccessors() {
            var req = new SysUserResetPasswordRequest("NewPassw0rd!");
            assertThat(req.newPassword()).isEqualTo("NewPassw0rd!");
        }
    }

    @Nested
    @DisplayName("SysUserAssignRolesRequest (record)")
    class SysUserAssignRolesRequestTest {

        @Test
        void recordAccessors() {
            List<UUID> roleIds = List.of(UUID.randomUUID());
            var req = new SysUserAssignRolesRequest(roleIds);
            assertThat(req.roleIds()).hasSize(1);
        }
    }
}
