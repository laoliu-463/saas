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
import com.colonel.saas.domain.user.application.AuthorizationVersionApplicationService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.vo.SysMenuVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统菜单管理服务。
 * <p>
 * 负责菜单资源的全生命周期管理，包括菜单的增删改查、
 * 扁平列表到树形结构的构建，以及角色与菜单之间的关联分配。
 * </p>
 *
 * <h3>职责列表</h3>
 * <ul>
 *   <li>菜单树查询：支持全量菜单树（管理员视图）和按用户角色过滤的可见菜单树</li>
 *   <li>菜单 CRUD 操作：新建、更新、删除菜单节点，删除前校验角色引用</li>
 *   <li>角色菜单分配：为角色批量分配菜单权限，变更时发布权限变更事件</li>
 *   <li>树形结构构建：将数据库扁平菜单记录组装为层级树形 VO</li>
 *   <li>操作审计：所有写操作记录到操作日志</li>
 * </ul>
 *
 * <h3>架构角色</h3>
 * <p>
 * 位于 auth（认证授权）领域服务层，服务于用户域的权限模块。
 * 通过 {@link SysRoleMenuMapper} 维护角色-菜单关联关系，
 * 通过 {@link UserDomainEventPublisher} 发布权限变更域事件，
 * 配合 {@link com.colonel.saas.domain.user.PermissionEventHasher} 计算权限哈希用于变更检测。
 * </p>
 *
 * <h3>业务域</h3>
 * <p>用户域 / 权限管理 / 菜单资源管理</p>
 *
 * @see SysRoleService 角色管理服务
 * @see SysUserService 用户管理服务
 */
@Service
public class SysMenuService {

    /** 根级菜单的 parentId 占位值（全零 UUID），表示该菜单为顶层节点 */
    private static final String ZERO_PARENT = "00000000-0000-0000-0000-000000000000";

    /** 菜单数据访问层 */
    private final SysMenuMapper sysMenuMapper;

    /** 角色数据访问层，用于查询角色信息 */
    private final SysRoleMapper sysRoleMapper;

    /** 角色-菜单关联关系数据访问层 */
    private final SysRoleMenuMapper sysRoleMenuMapper;

    /** 操作日志服务，用于记录审计日志 */
    private final OperationLogService operationLogService;

    /** 用户域事件发布器，用于发布权限变更等域事件 */
    private final UserDomainEventPublisher userDomainEventPublisher;

    /** 授权版本服务，用于使受角色权限影响的既有令牌失效 */
    private final AuthorizationVersionApplicationService authorizationVersionService;

    /**
     * 构造注入所有依赖。
     *
     * @param sysMenuMapper          菜单 Mapper
     * @param sysRoleMapper          角色 Mapper
     * @param sysRoleMenuMapper      角色-菜单关联 Mapper
     * @param operationLogService    操作日志服务
     * @param userDomainEventPublisher 用户域事件发布器
     */
    public SysMenuService(
            SysMenuMapper sysMenuMapper,
            SysRoleMapper sysRoleMapper,
            SysRoleMenuMapper sysRoleMenuMapper,
            OperationLogService operationLogService,
            UserDomainEventPublisher userDomainEventPublisher,
            AuthorizationVersionApplicationService authorizationVersionService) {
        this.sysMenuMapper = sysMenuMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.operationLogService = operationLogService;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.authorizationVersionService = authorizationVersionService;
    }

    /**
     * 查询全量菜单树（管理员视图）。
     * <p>
     * 不做角色过滤，返回数据库中所有符合状态条件的菜单，
     * 适用于 ADMIN 角色查看完整菜单结构。
     * </p>
     *
     * <ol>
     *   <li>从数据库查询所有符合 status 条件的扁平菜单列表</li>
     *   <li>调用 {@link #buildTree(List)} 将扁平列表组装为树形结构</li>
     *   <li>返回树形菜单 VO 列表</li>
     * </ol>
     *
     * @param status 菜单状态过滤条件（null 表示不过滤）
     * @return 树形结构的菜单 VO 列表，无数据时返回空列表
     */
    public List<SysMenuVO> findAllTree(Integer status) {
        // 第一步：从数据库获取全部扁平菜单记录
        List<SysMenuVO> list = sysMenuMapper.findAllAsTree(status);
        // 第二步：将扁平列表构建为树形结构并返回
        return buildTree(list);
    }

    /**
     * 根据用户 ID 查询该用户可见的菜单树。
     * <p>
     * 内部先解析用户关联的所有角色 ID，再委托 {@link #findUserTree} 过滤菜单。
     * 适用于根据用户身份自动确定角色后查询菜单的场景。
     * </p>
     *
     * <ol>
     *   <li>通过 SysRoleMapper 查询该用户关联的所有角色</li>
     *   <li>提取角色 ID 列表</li>
     *   <li>委托 findUserTree 方法基于角色过滤菜单并构建树</li>
     * </ol>
     *
     * @param userId 用户 ID
     * @param status 菜单状态过滤条件（null 表示不过滤）
     * @return 该用户可见的树形菜单 VO 列表
     */
    public List<SysMenuVO> findUserTreeByUserId(UUID userId, Integer status) {
        // 第一步：查询用户关联的角色并提取角色 ID 列表
        List<UUID> roleIds = sysRoleMapper.findByUserId(userId).stream()
                .map(SysRole::getId)
                .collect(Collectors.toList());
        // 第二步：委托 findUserTree 过滤菜单并构建树
        return findUserTree(userId, roleIds, status);
    }

    /**
     * 查询指定用户基于角色列表可见的菜单树。
     * <p>
     * 将所有角色关联的菜单 ID 合并去重，然后从全量菜单中过滤出可见部分，
     * 最终构建为树形结构返回。
     * </p>
     *
     * <ol>
     *   <li>校验角色列表是否为空，为空则直接返回空列表</li>
     *   <li>遍历每个角色，收集其关联的所有菜单 ID 到去重集合中</li>
     *   <li>若没有匹配的菜单 ID，返回空列表</li>
     *   <li>查询全量菜单列表，按菜单 ID 集合过滤</li>
     *   <li>将过滤后的扁平列表构建为树形结构</li>
     * </ol>
     *
     * @param userId  当前用户 ID（保留参数，供后续扩展使用）
     * @param roleIds 用户关联的角色 ID 列表
     * @param status  菜单状态过滤条件（null 表示不过滤）
     * @return 用户可见的树形菜单 VO 列表，无数据时返回空列表
     */
    public List<SysMenuVO> findUserTree(UUID userId, List<UUID> roleIds, Integer status) {
        // 第一步：校验角色列表
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 第二步：收集所有角色关联的菜单 ID（去重，保留插入顺序）
        Set<UUID> menuIds = new LinkedHashSet<>();
        for (UUID roleId : roleIds) {
            menuIds.addAll(sysRoleMenuMapper.findMenuIdsByRoleId(roleId));
        }
        // 第三步：无菜单 ID 时直接返回
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 第四步：查询全量菜单并按菜单 ID 过滤
        List<SysMenuVO> all = sysMenuMapper.findAllAsTree(status);
        List<SysMenuVO> filtered = all.stream()
                .filter(v -> menuIds.contains(v.getId()))
                .collect(Collectors.toList());
        // 第五步：构建树形结构并返回
        return buildTree(filtered);
    }

    /**
     * 根据角色 ID 查询其关联的菜单 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 该角色关联的菜单 ID 列表
     */
    public List<UUID> getMenuIdsByRoleId(UUID roleId) {
        return sysRoleMenuMapper.findMenuIdsByRoleId(roleId);
    }

    /**
     * 为角色分配菜单权限（先删后插模式）。
     * <p>
     * 采用"先删除旧关联、再插入新关联"的策略实现全量替换，
     * 变更前后通过权限哈希比对判断是否需要发布权限变更事件。
     * </p>
     *
     * <ol>
     *   <li>校验角色是否存在，不存在则抛出 BusinessException</li>
     *   <li>记录变更前的菜单 ID 列表，并计算变更前的权限哈希</li>
     *   <li>删除该角色的所有旧菜单关联记录</li>
     *   <li>遍历新菜单 ID 列表，逐条插入新的角色-菜单关联记录</li>
     *   <li>计算变更后的权限哈希</li>
     *   <li>记录操作审计日志</li>
     *   <li>若权限哈希发生变化，发布角色权限变更域事件（通知缓存刷新等下游消费者）</li>
     * </ol>
     *
     * @param roleId        角色 ID
     * @param menuIds       要分配的菜单 ID 列表（null 或空表示清空该角色的菜单权限）
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @throws BusinessException 若角色不存在
     */
    @Transactional
    public void assignMenusToRole(UUID roleId, List<UUID> menuIds, UUID currentUserId) {
        // 第一步：校验角色存在性
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw BusinessException.notFound("角色不存在");
        }
        // 第二步：记录变更前状态，计算旧权限哈希
        List<UUID> oldMenuIds = sysRoleMenuMapper.findMenuIdsByRoleId(roleId);
        String oldHash = PermissionEventHasher.hashRolePermissions(role.getPermissions(), oldMenuIds);

        // 第三步：删除该角色的所有旧菜单关联
        sysRoleMenuMapper.deleteByRoleId(roleId);
        // 第四步：插入新的角色-菜单关联
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
        // 第五步：计算变更后权限哈希
        String newHash = PermissionEventHasher.hashRolePermissions(role.getPermissions(), menuIds);

        // 第六步：记录操作审计日志
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
        // 第七步：权限哈希变化时发布域事件，并在旧序列完成后递增授权版本
        if (!Objects.equals(oldHash, newHash)) {
            userDomainEventPublisher.publishRolePermissionUpdated(
                    roleId,
                    role.getRoleCode(),
                    oldHash,
                    newHash,
                    currentUserId);
            authorizationVersionService.incrementUsersByRole(
                    roleId,
                    "ROLE_MENU_PERMISSIONS_UPDATED",
                    currentUserId);
        }
    }

    /**
     * 新增菜单节点。
     *
     * <ol>
     *   <li>根据请求参数构建菜单实体，未指定的字段使用默认值</li>
     *   <li>若未指定 parentId，则默认为根节点（ZERO_PARENT）</li>
     *   <li>插入数据库</li>
     *   <li>记录操作审计日志</li>
     *   <li>转换为 VO 返回</li>
     * </ol>
     *
     * @param request       菜单创建请求 DTO
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @return 新创建的菜单 VO
     */
    @Transactional
    public SysMenuVO create(SysMenuCreateRequest request, UUID currentUserId) {
        // 第一步：构建菜单实体并填充默认值
        SysMenu menu = new SysMenu();
        menu.setMenuName(request.menuName());
        menu.setMenuType(request.menuType());
        // 未指定父级菜单时默认为根节点
        menu.setParentId(request.parentId() != null ? request.parentId() : ZERO_PARENT);
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setIcon(request.icon());
        menu.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        menu.setPermissionCode(request.permissionCode());
        menu.setVisible(request.visible() != null ? request.visible() : 1);
        menu.setStatus(request.status() != null ? request.status() : 1);
        // 第二步：持久化到数据库
        sysMenuMapper.insert(menu);
        // 第三步：记录操作审计日志
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
        // 第四步：转换为 VO 返回
        return toVO(menu);
    }

    /**
     * 更新菜单节点信息。
     *
     * <ol>
     *   <li>根据 ID 查询原菜单，不存在则抛出异常</li>
     *   <li>使用请求参数覆盖原菜单各字段（未指定的字段取默认值）</li>
     *   <li>持久化更新到数据库</li>
     *   <li>记录操作审计日志</li>
     *   <li>转换为 VO 返回</li>
     * </ol>
     *
     * @param id            要更新的菜单 ID
     * @param request       菜单更新请求 DTO
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @return 更新后的菜单 VO
     * @throws BusinessException 若菜单不存在
     */
    @Transactional
    public SysMenuVO update(UUID id, SysMenuUpdateRequest request, UUID currentUserId) {
        // 第一步：查询原菜单（不存在则抛出异常）
        SysMenu menu = requireMenu(id);
        // 第二步：用请求参数覆盖各字段
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
        // 第三步：持久化更新
        sysMenuMapper.updateById(menu);
        // 第四步：记录操作审计日志
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
        // 第五步：转换为 VO 返回
        return toVO(menu);
    }

    /**
     * 删除菜单节点。
     * <p>
     * 删除前校验该菜单是否仍被角色引用，若存在引用则拒绝删除以保证数据完整性。
     * </p>
     *
     * <ol>
     *   <li>根据 ID 查询原菜单，不存在则抛出异常</li>
     *   <li>查询该菜单被角色引用的数量</li>
     *   <li>若引用数大于 0，抛出状态异常阻止删除</li>
     *   <li>从数据库删除该菜单记录</li>
     *   <li>记录操作审计日志</li>
     * </ol>
     *
     * @param id            要删除的菜单 ID
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @throws BusinessException 若菜单不存在或仍被角色引用
     */
    @Transactional
    public void delete(UUID id, UUID currentUserId) {
        // 第一步：查询原菜单
        SysMenu menu = requireMenu(id);
        // 第二步：校验是否仍被角色引用
        int refCount = sysRoleMenuMapper.countByMenuIds(List.of(id));
        if (refCount > 0) {
            // 第三步：存在引用，拒绝删除
            throw BusinessException.stateInvalid("菜单仍被角色引用，不能删除");
        }
        // 第四步：从数据库删除
        sysMenuMapper.deleteById(id);
        // 第五步：记录操作审计日志
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

    /**
     * 根据 ID 查询菜单，不存在时抛出异常。
     * <p>内部辅助方法，用于在写操作前确保目标菜单存在。</p>
     *
     * @param id 菜单 ID
     * @return 菜单实体
     * @throws BusinessException 若菜单不存在
     */
    private SysMenu requireMenu(UUID id) {
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null) {
            throw BusinessException.notFound("菜单不存在");
        }
        return menu;
    }

    /**
     * 将扁平菜单列表构建为树形结构。
     * <p>
     * 通过 parentId 关系将扁平列表组装为父子嵌套的树形结构，
     * 并按 sortOrder 升序排列各层级节点。
     * </p>
     *
     * <ol>
     *   <li>校验列表是否为空，为空则返回空列表</li>
     *   <li>将所有菜单 VO 按 ID 建立映射，并初始化 children 列表</li>
     *   <li>遍历每个节点，根据 parentId 找到父节点并挂载为子节点</li>
     *   <li>无父节点或父节点为根标识（ZERO_PARENT）的节点作为根节点收集</li>
     *   <li>父节点未找到的节点也作为根节点兜底处理</li>
     *   <li>递归对整棵树按 sortOrder 升序排列</li>
     * </ol>
     *
     * @param list 扁平菜单 VO 列表
     * @return 树形结构的根节点列表
     */
    public List<SysMenuVO> buildTree(List<SysMenuVO> list) {
        // 第一步：校验空列表
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        // 第二步：建立 ID -> VO 映射，初始化 children 列表
        Map<String, SysMenuVO> map = new LinkedHashMap<>();
        for (SysMenuVO vo : list) {
            vo.setChildren(new ArrayList<>());
            map.put(vo.getId().toString(), vo);
        }
        // 第三步：遍历节点，按 parentId 关系组装父子层级
        List<SysMenuVO> roots = new ArrayList<>();
        for (SysMenuVO vo : list) {
            String pid = vo.getParentId();
            if (pid == null || ZERO_PARENT.equals(pid)) {
                // 根节点（无父级或 parentId 为全零标识）
                roots.add(vo);
            } else {
                SysMenuVO parent = map.get(pid);
                if (parent != null) {
                    // 挂载到父节点的 children 列表
                    parent.getChildren().add(vo);
                } else {
                    // 父节点未找到，兜底作为根节点
                    roots.add(vo);
                }
            }
        }
        // 第四步：递归排序
        sortTree(roots);
        return roots;
    }

    /**
     * 递归对树节点按 sortOrder 升序排列。
     *
     * @param nodes 当前层级的节点列表
     */
    private void sortTree(List<SysMenuVO> nodes) {
        // 按 sortOrder 升序排列当前层级
        nodes.sort(Comparator.comparingInt(SysMenuVO::getSortOrder));
        // 递归排列子节点
        for (SysMenuVO node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortTree(node.getChildren());
            }
        }
    }

    /**
     * 将菜单实体转换为视图对象（VO）。
     *
     * @param menu 菜单实体
     * @return 菜单 VO
     */
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
