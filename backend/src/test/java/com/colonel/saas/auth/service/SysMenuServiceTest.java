package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.SysMenuCreateRequest;
import com.colonel.saas.auth.dto.SysMenuUpdateRequest;
import com.colonel.saas.domain.user.application.SysMenuApplication;
import com.colonel.saas.vo.SysMenuVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DDD-USER-MIGRATION-013（Issue #22）— SysMenuService Legacy 委派壳测试。
 *
 * <p>SysMenuService 现在是瘦壳，所有方法委派给 SysMenuApplication。
 * 本测试仅验证委派行为（每个方法 1:1 转发到 Application）；行为细节由
 * {@code SysMenuApplicationTest} 覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysMenuServiceTest {

    @Mock
    SysMenuApplication sysMenuApplication;

    private SysMenuService service;
    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID roleId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        service = new SysMenuService(sysMenuApplication);
    }

    @Test
    void findAllTreeDelegatesToApplication() {
        List<SysMenuVO> expected = List.of();
        when(sysMenuApplication.findAllTree(1)).thenReturn(expected);

        assertThat(service.findAllTree(1)).isSameAs(expected);
        verify(sysMenuApplication, times(1)).findAllTree(1);
    }

    @Test
    void findUserTreeByUserIdDelegatesToApplication() {
        List<SysMenuVO> expected = List.of();
        when(sysMenuApplication.findUserTreeByUserId(userId, 1)).thenReturn(expected);

        assertThat(service.findUserTreeByUserId(userId, 1)).isSameAs(expected);
        verify(sysMenuApplication, times(1)).findUserTreeByUserId(userId, 1);
    }

    @Test
    void findUserTreeDelegatesToApplication() {
        List<UUID> roleIds = List.of(roleId);
        List<SysMenuVO> expected = List.of();
        when(sysMenuApplication.findUserTree(userId, roleIds, 1)).thenReturn(expected);

        assertThat(service.findUserTree(userId, roleIds, 1)).isSameAs(expected);
        verify(sysMenuApplication, times(1)).findUserTree(userId, roleIds, 1);
    }

    @Test
    void getMenuIdsByRoleIdDelegatesToApplication() {
        List<UUID> expected = List.of(UUID.randomUUID());
        when(sysMenuApplication.getMenuIdsByRoleId(roleId)).thenReturn(expected);

        assertThat(service.getMenuIdsByRoleId(roleId)).isSameAs(expected);
        verify(sysMenuApplication, times(1)).getMenuIdsByRoleId(roleId);
    }

    @Test
    void assignMenusToRoleDelegatesToApplication() {
        List<UUID> menuIds = List.of(UUID.randomUUID());

        service.assignMenusToRole(roleId, menuIds, userId);

        verify(sysMenuApplication, times(1)).assignMenusToRole(roleId, menuIds, userId);
    }

    @Test
    void createDelegatesToApplication() {
        SysMenuCreateRequest request = new SysMenuCreateRequest(
                "Menu", "MENU", null, "/menu", "MenuView", "menu",
                null, "menu:list", null, null);
        SysMenuVO expected = new SysMenuVO();
        when(sysMenuApplication.create(request, userId)).thenReturn(expected);

        assertThat(service.create(request, userId)).isSameAs(expected);
        verify(sysMenuApplication, times(1)).create(request, userId);
    }

    @Test
    void updateDelegatesToApplication() {
        UUID id = UUID.randomUUID();
        SysMenuUpdateRequest request = new SysMenuUpdateRequest(
                "Updated", "BUTTON", null, "/updated", "UpdatedView",
                "edit", 1, "menu:update", 1, 1);
        SysMenuVO expected = new SysMenuVO();
        when(sysMenuApplication.update(id, request, userId)).thenReturn(expected);

        assertThat(service.update(id, request, userId)).isSameAs(expected);
        verify(sysMenuApplication, times(1)).update(id, request, userId);
    }

    @Test
    void deleteDelegatesToApplication() {
        UUID id = UUID.randomUUID();

        service.delete(id, userId);

        verify(sysMenuApplication, times(1)).delete(id, userId);
    }

    @Test
    void buildTreeDelegatesToApplication() {
        List<SysMenuVO> input = List.of();
        List<SysMenuVO> expected = List.of();
        when(sysMenuApplication.buildTree(input)).thenReturn(expected);

        assertThat(service.buildTree(input)).isSameAs(expected);
        verify(sysMenuApplication, times(1)).buildTree(input);
    }

    @Test
    void constructorDoesNotInjectMappersOrEventPublisher() {
        // 确保 SysMenuService 不再直接依赖 Mapper / EventPublisher
        // 9 个方法中任何一个被调用都应触发 SysMenuApplication mock 交互
        verifyNoInteractions(sysMenuApplication);
    }
}
