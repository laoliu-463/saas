package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysMenuCreateRequest;
import com.colonel.saas.auth.dto.SysMenuUpdateRequest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SysMenu;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysRoleMenu;
import com.colonel.saas.mapper.SysMenuMapper;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysRoleMenuMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.vo.SysMenuVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DDD-USER-MIGRATION-013（Issue #22）— SysMenuApplication 单元测试。
 *
 * <p>SysMenuService 旁路 DDD 入口，行为 1:1 等价于旧 SysMenuService。
 */
@ExtendWith(MockitoExtension.class)
class SysMenuApplicationTest {

    private static final String ZERO_PARENT = "00000000-0000-0000-0000-000000000000";

    @Mock SysMenuMapper sysMenuMapper;
    @Mock SysRoleMapper sysRoleMapper;
    @Mock SysRoleMenuMapper sysRoleMenuMapper;
    @Mock OperationLogService operationLogService;
    @Mock UserDomainEventPublisher userDomainEventPublisher;

    private SysMenuApplication application;
    private UUID userId;
    private UUID roleId;
    private UUID rootId;
    private UUID childId;

    @BeforeEach
    void setUp() {
        application = new SysMenuApplication(
                sysMenuMapper,
                sysRoleMapper,
                sysRoleMenuMapper,
                operationLogService,
                userDomainEventPublisher);
        userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        roleId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        rootId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        childId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    }

    @Test
    void findAllTreeBuildsSortedRootsAndChildren() {
        SysMenuVO root = menuVo(rootId, ZERO_PARENT, "Root", 2);
        SysMenuVO child = menuVo(childId, rootId.toString(), "Child", 1);
        SysMenuVO orphan = menuVo(UUID.randomUUID(), UUID.randomUUID().toString(), "Orphan", 1);
        when(sysMenuMapper.findAllAsTree(1)).thenReturn(List.of(root, child, orphan));

        List<SysMenuVO> tree = application.findAllTree(1);

        assertThat(tree).extracting(SysMenuVO::getMenuName).containsExactly("Orphan", "Root");
        assertThat(tree.get(1).getChildren()).singleElement().satisfies(node ->
                assertThat(node.getMenuName()).isEqualTo("Child"));
    }

    @Test
    void findUserTreeByUserIdResolvesRolesAndFiltersMenus() {
        SysRole role = new SysRole();
        role.setId(roleId);
        when(sysRoleMapper.findByUserId(userId)).thenReturn(List.of(role));
        when(sysRoleMenuMapper.findMenuIdsByRoleId(roleId)).thenReturn(List.of(rootId, childId));
        when(sysMenuMapper.findAllAsTree(1)).thenReturn(List.of(
                menuVo(rootId, ZERO_PARENT, "Root", 1),
                menuVo(childId, rootId.toString(), "Child", 1),
                menuVo(UUID.randomUUID(), ZERO_PARENT, "Hidden", 1)
        ));

        List<SysMenuVO> tree = application.findUserTreeByUserId(userId, 1);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getMenuName()).isEqualTo("Root");
        assertThat(tree.get(0).getChildren()).singleElement()
                .extracting(SysMenuVO::getMenuName)
                .isEqualTo("Child");
    }

    @Test
    void findUserTreeReturnsEmptyWhenRoleOrMenuIdsMissing() {
        assertThat(application.findUserTree(userId, null, 1)).isEmpty();
        assertThat(application.findUserTree(userId, List.of(), 1)).isEmpty();

        when(sysRoleMenuMapper.findMenuIdsByRoleId(roleId)).thenReturn(List.of());
        assertThat(application.findUserTree(userId, List.of(roleId), 1)).isEmpty();
    }

    @Test
    void assignMenusToRoleDeletesExistingAndInsertsNewRows() {
        UUID menuA = UUID.randomUUID();
        UUID menuB = UUID.randomUUID();
        ArgumentCaptor<SysRoleMenu> captor = ArgumentCaptor.forClass(SysRoleMenu.class);
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setRoleCode("biz_staff");
        when(sysRoleMapper.selectById(roleId)).thenReturn(role);
        when(sysRoleMenuMapper.findMenuIdsByRoleId(roleId)).thenReturn(List.of());

        application.assignMenusToRole(roleId, List.of(menuA, menuB), userId);

        verify(sysRoleMenuMapper).deleteByRoleId(roleId);
        verify(sysRoleMenuMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysRoleMenu::getMenuId).containsExactly(menuA, menuB);
        verify(operationLogService).recordSystemAction(
                userId,
                "角色菜单管理",
                "分配角色菜单",
                "PUT",
                "SysRoleMenu",
                roleId.toString(),
                null,
                "分配角色菜单: roleId=" + roleId + ", menuCount=2"
        );
        verify(userDomainEventPublisher).publishRolePermissionUpdated(
                eq(roleId),
                eq("biz_staff"),
                any(),
                any(),
                eq(userId));
    }

    @Test
    void createAndUpdateApplyDefaultsAndRecordOperationLogs() {
        SysMenuCreateRequest createRequest = new SysMenuCreateRequest(
                "Menu", "MENU", null, "/menu", "MenuView", "menu",
                null, "menu:list", null, null
        );

        SysMenuVO created = application.create(createRequest, userId);

        assertThat(created.getMenuName()).isEqualTo("Menu");
        assertThat(created.getParentId()).isEqualTo(ZERO_PARENT);
        assertThat(created.getSortOrder()).isZero();
        assertThat(created.getVisible()).isEqualTo(1);
        assertThat(created.getStatus()).isEqualTo(1);
        verify(sysMenuMapper).insert(any(SysMenu.class));

        SysMenu existing = menu(rootId, "Old");
        when(sysMenuMapper.selectById(rootId)).thenReturn(existing);
        SysMenuUpdateRequest updateRequest = new SysMenuUpdateRequest(
                "Updated", "BUTTON", childId.toString(), "/updated", "UpdatedView",
                "edit", 9, "menu:update", 0, 0
        );

        SysMenuVO updated = application.update(rootId, updateRequest, userId);

        assertThat(updated.getMenuName()).isEqualTo("Updated");
        assertThat(updated.getMenuType()).isEqualTo("BUTTON");
        assertThat(updated.getParentId()).isEqualTo(childId.toString());
        assertThat(updated.getSortOrder()).isEqualTo(9);
        assertThat(updated.getVisible()).isZero();
        assertThat(updated.getStatus()).isZero();
        verify(sysMenuMapper).updateById(existing);
        verify(operationLogService).recordSystemAction(
                userId,
                "菜单管理",
                "更新菜单",
                "PUT",
                "SysMenu",
                rootId.toString(),
                "Updated",
                "更新菜单: Updated"
        );
    }

    @Test
    void deleteRejectsMissingOrReferencedMenusAndDeletesUnreferencedMenu() {
        UUID missingId = UUID.randomUUID();
        when(sysMenuMapper.selectById(missingId)).thenReturn(null);
        assertThatThrownBy(() -> application.delete(missingId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("菜单不存在");

        SysMenu referenced = menu(rootId, "Referenced");
        when(sysMenuMapper.selectById(rootId)).thenReturn(referenced);
        when(sysRoleMenuMapper.countByMenuIds(List.of(rootId))).thenReturn(1);
        assertThatThrownBy(() -> application.delete(rootId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("菜单仍被角色引用");
        verify(sysMenuMapper, never()).deleteById(rootId);

        when(sysRoleMenuMapper.countByMenuIds(List.of(rootId))).thenReturn(0);
        application.delete(rootId, userId);

        verify(sysMenuMapper).deleteById(rootId);
        verify(operationLogService).recordSystemAction(
                userId,
                "菜单管理",
                "删除菜单",
                "DELETE",
                "SysMenu",
                rootId.toString(),
                "Referenced",
                "删除菜单: Referenced"
        );
    }

    @Test
    void getMenuIdsByRoleIdDelegatesToMapperAndBuildTreeHandlesEmptyInput() {
        when(sysRoleMenuMapper.findMenuIdsByRoleId(roleId)).thenReturn(List.of(rootId));

        assertThat(application.getMenuIdsByRoleId(roleId)).containsExactly(rootId);
        assertThat(application.buildTree(null)).isEmpty();
        assertThat(application.buildTree(List.of())).isEmpty();
    }

    private SysMenuVO menuVo(UUID id, String parentId, String name, int sortOrder) {
        SysMenuVO vo = new SysMenuVO();
        vo.setId(id);
        vo.setParentId(parentId);
        vo.setMenuName(name);
        vo.setSortOrder(sortOrder);
        return vo;
    }

    private SysMenu menu(UUID id, String name) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setMenuName(name);
        menu.setMenuType("MENU");
        menu.setParentId(ZERO_PARENT);
        menu.setPath("/" + name.toLowerCase(java.util.Locale.ROOT));
        menu.setComponent(name + "View");
        menu.setIcon("menu");
        menu.setSortOrder(1);
        menu.setPermissionCode("menu:" + name.toLowerCase(java.util.Locale.ROOT));
        menu.setVisible(1);
        menu.setStatus(1);
        return menu;
    }
}
