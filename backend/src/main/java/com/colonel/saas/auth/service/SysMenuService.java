package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.SysMenuCreateRequest;
import com.colonel.saas.auth.dto.SysMenuUpdateRequest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SysMenu;
import com.colonel.saas.entity.SysRoleMenu;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.mapper.SysMenuMapper;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysRoleMenuMapper;
import com.colonel.saas.domain.user.PermissionEventHasher;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.vo.SysMenuVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SysMenuService {

    private static final String ZERO_PARENT = "00000000-0000-0000-0000-000000000000";

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final OperationLogService operationLogService;
    private final UserDomainEventPublisher userDomainEventPublisher;

    public SysMenuService(
            SysMenuMapper sysMenuMapper,
            SysRoleMapper sysRoleMapper,
            SysRoleMenuMapper sysRoleMenuMapper,
            OperationLogService operationLogService,
            UserDomainEventPublisher userDomainEventPublisher) {
        this.sysMenuMapper = sysMenuMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.operationLogService = operationLogService;
        this.userDomainEventPublisher = userDomainEventPublisher;
    }

    /**
     * 查询全量菜单树（ADMIN 用）
     */
    public List<SysMenuVO> findAllTree(Integer status) {
        List<SysMenuVO> list = sysMenuMapper.findAllAsTree(status);
        return buildTree(list);
    }

    /**
     * 根据用户ID查询可见菜单树（内部解析角色）
     */
    public List<SysMenuVO> findUserTreeByUserId(UUID userId, Integer status) {
        List<UUID> roleIds = sysRoleMapper.findByUserId(userId).stream()
                .map(SysRole::getId)
                .collect(Collectors.toList());
        return findUserTree(userId, roleIds, status);
    }

    /**
     * 查询当前用户可见菜单树（根据角色关联）
     */
    public List<SysMenuVO> findUserTree(UUID userId, List<UUID> roleIds, Integer status) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> menuIds = new LinkedHashSet<>();
        for (UUID roleId : roleIds) {
            menuIds.addAll(sysRoleMenuMapper.findMenuIdsByRoleId(roleId));
        }
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysMenuVO> all = sysMenuMapper.findAllAsTree(status);
        List<SysMenuVO> filtered = all.stream()
                .filter(v -> menuIds.contains(v.getId()))
                .collect(Collectors.toList());
        return buildTree(filtered);
    }

    /**
     * 根据角色ID查询菜单ID列表
     */
    public List<UUID> getMenuIdsByRoleId(UUID roleId) {
        return sysRoleMenuMapper.findMenuIdsByRoleId(roleId);
    }

    /**
     * 为角色分配菜单（先删后插）
     */
    @Transactional
    public void assignMenusToRole(UUID roleId, List<UUID> menuIds, UUID currentUserId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw BusinessException.notFound("角色不存在");
        }
        List<UUID> oldMenuIds = sysRoleMenuMapper.findMenuIdsByRoleId(roleId);
        String oldHash = PermissionEventHasher.hashRolePermissions(role.getPermissions(), oldMenuIds);

        sysRoleMenuMapper.deleteByRoleId(roleId);
        int menuCount = 0;
        if (menuIds != null && !menuIds.isEmpty()) {
            menuCount = menuIds.size();
            for (UUID menuId : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                sysRoleMenuMapper.insert(rm);
            }
        }
        String newHash = PermissionEventHasher.hashRolePermissions(role.getPermissions(), menuIds);

        operationLogService.recordSystemAction(
                currentUserId,
                "角色菜单管理",
                "分配角色菜单",
                "PUT",
                "SysRoleMenu",
                roleId.toString(),
                null,
                "分配角色菜单: roleId=" + roleId + ", menuCount=" + menuCount
        );
        if (!Objects.equals(oldHash, newHash)) {
            userDomainEventPublisher.publishRolePermissionUpdated(
                    roleId,
                    role.getRoleCode(),
                    oldHash,
                    newHash,
                    currentUserId);
        }
    }

    /**
     * 新增菜单
     */
    @Transactional
    public SysMenuVO create(SysMenuCreateRequest request, UUID currentUserId) {
        SysMenu menu = new SysMenu();
        menu.setMenuName(request.menuName());
        menu.setMenuType(request.menuType());
        menu.setParentId(request.parentId() != null ? request.parentId() : ZERO_PARENT);
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setIcon(request.icon());
        menu.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        menu.setPermissionCode(request.permissionCode());
        menu.setVisible(request.visible() != null ? request.visible() : 1);
        menu.setStatus(request.status() != null ? request.status() : 1);
        sysMenuMapper.insert(menu);
        operationLogService.recordSystemAction(
                currentUserId,
                "菜单管理",
                "新建菜单",
                "POST",
                "SysMenu",
                menu.getId() == null ? null : menu.getId().toString(),
                menu.getMenuName(),
                "新建菜单: " + menu.getMenuName()
        );
        return toVO(menu);
    }

    /**
     * 更新菜单
     */
    @Transactional
    public SysMenuVO update(UUID id, SysMenuUpdateRequest request, UUID currentUserId) {
        SysMenu menu = requireMenu(id);
        menu.setMenuName(request.menuName());
        menu.setMenuType(request.menuType());
        menu.setParentId(request.parentId() != null ? request.parentId() : ZERO_PARENT);
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setIcon(request.icon());
        menu.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        menu.setPermissionCode(request.permissionCode());
        menu.setVisible(request.visible() != null ? request.visible() : 1);
        menu.setStatus(request.status() != null ? request.status() : 1);
        sysMenuMapper.updateById(menu);
        operationLogService.recordSystemAction(
                currentUserId,
                "菜单管理",
                "更新菜单",
                "PUT",
                "SysMenu",
                menu.getId() == null ? null : menu.getId().toString(),
                menu.getMenuName(),
                "更新菜单: " + menu.getMenuName()
        );
        return toVO(menu);
    }

    /**
     * 删除菜单
     */
    @Transactional
    public void delete(UUID id, UUID currentUserId) {
        SysMenu menu = requireMenu(id);
        int refCount = sysRoleMenuMapper.countByMenuIds(List.of(id));
        if (refCount > 0) {
            throw BusinessException.stateInvalid("菜单仍被角色引用，不能删除");
        }
        sysMenuMapper.deleteById(id);
        operationLogService.recordSystemAction(
                currentUserId,
                "菜单管理",
                "删除菜单",
                "DELETE",
                "SysMenu",
                menu.getId() == null ? null : menu.getId().toString(),
                menu.getMenuName(),
                "删除菜单: " + menu.getMenuName()
        );
    }

    private SysMenu requireMenu(UUID id) {
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null) {
            throw BusinessException.notFound("菜单不存在");
        }
        return menu;
    }

    /**
     * 将扁平列表构建为树形结构
     */
    public List<SysMenuVO> buildTree(List<SysMenuVO> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, SysMenuVO> map = new LinkedHashMap<>();
        for (SysMenuVO vo : list) {
            vo.setChildren(new ArrayList<>());
            map.put(vo.getId().toString(), vo);
        }
        List<SysMenuVO> roots = new ArrayList<>();
        for (SysMenuVO vo : list) {
            String pid = vo.getParentId();
            if (pid == null || ZERO_PARENT.equals(pid)) {
                roots.add(vo);
            } else {
                SysMenuVO parent = map.get(pid);
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    roots.add(vo);
                }
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<SysMenuVO> nodes) {
        nodes.sort(Comparator.comparingInt(SysMenuVO::getSortOrder));
        for (SysMenuVO node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortTree(node.getChildren());
            }
        }
    }

    private SysMenuVO toVO(SysMenu menu) {
        SysMenuVO vo = new SysMenuVO();
        vo.setId(menu.getId());
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setParentId(menu.getParentId());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setIcon(menu.getIcon());
        vo.setSortOrder(menu.getSortOrder());
        vo.setPermissionCode(menu.getPermissionCode());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        return vo;
    }
}
