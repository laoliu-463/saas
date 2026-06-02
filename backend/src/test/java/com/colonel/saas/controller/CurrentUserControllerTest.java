package com.colonel.saas.controller;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.user.CheckPermissionResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.service.UserDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CurrentUserControllerTest {

    @Mock
    private UserDomainService userDomainService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID userId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        CurrentUserController controller = new CurrentUserController(userDomainService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void currentUser_returnsUserPermissionsAndScope() throws Exception {
        when(userDomainService.getCurrentUser(userId, deptId, DataScope.DEPT, List.of(RoleCodes.CHANNEL_LEADER)))
                .thenReturn(new CurrentUserResponse(
                        userId,
                        "channel_leader",
                        "渠道组长",
                        deptId,
                        2,
                        "group",
                        List.of(RoleCodes.CHANNEL_LEADER),
                        Map.of("operations", Map.of("talent", List.of("claim", "tag"))),
                        1,
                        false
                ));

        mockMvc.perform(get("/users/current")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.DEPT)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_LEADER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.username").value("channel_leader"))
                .andExpect(jsonPath("$.data.dataScope").value(2))
                .andExpect(jsonPath("$.data.dataScopeName").value("group"))
                .andExpect(jsonPath("$.data.roleCodes[0]").value(RoleCodes.CHANNEL_LEADER))
                .andExpect(jsonPath("$.data.permissions.operations.talent[0]").value("claim"));
    }

    @Test
    void changePassword_usesCurrentUserOnly() throws Exception {
        doNothing().when(userDomainService).changePassword(eq(userId), any());

        mockMvc.perform(put("/users/current/password")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldPassword", "old-pass",
                                "newPassword", "new-pass-123"
                        ))))
                .andExpect(status().isOk());

        verify(userDomainService).changePassword(eq(userId), any());
    }

    @Test
    void dataScope_returnsResolvedUserIdsForCurrentUser() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(userDomainService.getUserDataScope(userId, deptId, DataScope.DEPT))
                .thenReturn(new UserDataScopeResponse("group", 2, List.of(userId, memberId)));

        mockMvc.perform(get("/users/current/data-scope")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", deptId)
                        .requestAttr("dataScope", DataScope.DEPT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("group"))
                .andExpect(jsonPath("$.data.code").value(2))
                .andExpect(jsonPath("$.data.userIds[0]").value(userId.toString()))
                .andExpect(jsonPath("$.data.userIds[1]").value(memberId.toString()));
    }

    @Test
    void checkPermission_returnsAllowedFlag() throws Exception {
        when(userDomainService.checkPermission(eq(userId), eq(List.of(RoleCodes.BIZ_STAFF)), any()))
                .thenReturn(new CheckPermissionResponse("product", "audit", true));

        mockMvc.perform(post("/users/current/permissions/check")
                        .requestAttr("userId", userId)
                        .requestAttr("roleCodes", List.of(RoleCodes.BIZ_STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "resource", "product",
                                "action", "audit"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resource").value("product"))
                .andExpect(jsonPath("$.data.action").value("audit"))
                .andExpect(jsonPath("$.data.allowed").value(true));
    }

    // ========================== 空数据 ==========================

    @Test
    void currentUser_returnsEmptyPermissionsAndDataScopeWhenServiceReturnsEmpty() throws Exception {
        when(userDomainService.getCurrentUser(userId, null, null, List.of()))
                .thenReturn(new CurrentUserResponse(
                        userId,
                        "ops_staff",
                        "运营",
                        null,
                        1,
                        "self",
                        List.of(),
                        Map.of(),
                        1,
                        false
                ));

        mockMvc.perform(get("/users/current")
                        .requestAttr("userId", userId)
                        .requestAttr("roleCodes", List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("ops_staff"))
                .andExpect(jsonPath("$.data.dataScopeName").value("self"))
                .andExpect(jsonPath("$.data.roleCodes").isArray())
                .andExpect(jsonPath("$.data.roleCodes").isEmpty())
                .andExpect(jsonPath("$.data.permissions").isMap());
    }

    @Test
    void dataScope_returnsEmptyUserIdsForAllScope() throws Exception {
        when(userDomainService.getUserDataScope(userId, null, DataScope.ALL))
                .thenReturn(new UserDataScopeResponse("all", 3, List.of()));

        mockMvc.perform(get("/users/current/data-scope")
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("all"))
                .andExpect(jsonPath("$.data.code").value(3))
                .andExpect(jsonPath("$.data.userIds").isArray())
                .andExpect(jsonPath("$.data.userIds").isEmpty());
    }

    @Test
    void checkPermission_returnsNotAllowedFlag() throws Exception {
        when(userDomainService.checkPermission(eq(userId), eq(List.of(RoleCodes.CHANNEL_STAFF)), any()))
                .thenReturn(new CheckPermissionResponse("product", "audit", false));

        mockMvc.perform(post("/users/current/permissions/check")
                        .requestAttr("userId", userId)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "resource", "product",
                                "action", "audit"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(false));
    }

    // ========================== 鉴权失败 ==========================
    // standaloneSetup 不激活 @RequireRoles 切面，因此本测试只能验证
    // controller 不会因缺失 request attribute 而把 service 误调。
    // 真实鉴权失败的端到端验证见 ColonelActivityControllerTest 中 403 相关测试。

    @Test
    void currentUser_shouldPassNullAttributesWhenNotProvided() throws Exception {
        when(userDomainService.getCurrentUser(userId, null, null, List.of()))
                .thenReturn(new CurrentUserResponse(
                        userId, "x", "X", null, 1, "self", List.of(), Map.of(), 1, false));

        // 不传 deptId / dataScope / roleCodes
        mockMvc.perform(get("/users/current")
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()));

        // controller 必须显式回退到空集合（避免 NPE 传到 service）
        verify(userDomainService).getCurrentUser(userId, null, null, List.of());
    }

    @Test
    void dataScope_shouldPassNullsWhenAttributesMissing() throws Exception {
        when(userDomainService.getUserDataScope(userId, null, null))
                .thenReturn(new UserDataScopeResponse("self", 1, List.of(userId)));

        mockMvc.perform(get("/users/current/data-scope")
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("self"));

        verify(userDomainService).getUserDataScope(userId, null, null);
    }
}
