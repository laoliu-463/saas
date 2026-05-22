package com.colonel.saas.controller;

import com.colonel.saas.auth.dto.SysMenuCreateRequest;
import com.colonel.saas.auth.dto.SysMenuUpdateRequest;
import com.colonel.saas.auth.service.SysMenuService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.vo.SysMenuVO;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SysMenuControllerTest {

    @Mock
    private SysMenuService sysMenuService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID menuId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SysMenuController(sysMenuService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void userTree_shouldReturnCurrentUserMenuTree() throws Exception {
        SysMenuVO menu = new SysMenuVO();
        menu.setId(menuId);
        menu.setMenuName("工作台");
        when(sysMenuService.findUserTreeByUserId(userId, 1)).thenReturn(List.of(menu));

        mockMvc.perform(get("/menus/tree")
                        .param("status", "1")
                        .requestAttr("userId", userId)
                        .requestAttr("deptId", UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.PERSONAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].menuName").value("工作台"));
    }

    @Test
    void allTree_shouldReturnAllMenus() throws Exception {
        SysMenuVO menu = new SysMenuVO();
        menu.setId(menuId);
        menu.setMenuName("系统管理");
        when(sysMenuService.findAllTree(null)).thenReturn(List.of(menu));

        mockMvc.perform(get("/menus/all")
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].menuName").value("系统管理"));
    }

    @Test
    void create_shouldPassCreator() throws Exception {
        SysMenuCreateRequest request = new SysMenuCreateRequest(
                "商品库", "MENU", null, "/products", "product", "box", 10, "product:list", 1, 1);
        SysMenuVO menu = new SysMenuVO();
        menu.setId(menuId);
        menu.setMenuName("商品库");
        when(sysMenuService.create(any(SysMenuCreateRequest.class), eq(userId))).thenReturn(menu);

        mockMvc.perform(post("/menus")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuName").value("商品库"));
    }

    @Test
    void update_shouldPassIdAndOperator() throws Exception {
        SysMenuUpdateRequest request = new SysMenuUpdateRequest(
                "商品中心", "MENU", null, "/products", "product", "box", 20, "product:list", 1, 1);
        SysMenuVO menu = new SysMenuVO();
        menu.setId(menuId);
        menu.setMenuName("商品中心");
        when(sysMenuService.update(eq(menuId), any(SysMenuUpdateRequest.class), eq(userId))).thenReturn(menu);

        mockMvc.perform(put("/menus/{id}", menuId)
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuName").value("商品中心"));
    }

    @Test
    void delete_shouldInvokeService() throws Exception {
        doNothing().when(sysMenuService).delete(menuId, userId);

        mockMvc.perform(delete("/menus/{id}", menuId)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sysMenuService).delete(menuId, userId);
    }
}
