package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysUserAssignRolesRequest;
import com.colonel.saas.auth.dto.SysUserCreateRequest;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.dto.SysUserResetPasswordRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.service.OrgStructureService.ResolvedAssignment;
import com.colonel.saas.auth.service.OrgStructureService.SplitAssignment;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.service.UserPermissionCacheService;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 系统用户管理服务。
 * <p>
 * 负责用户资源的全生命周期管理，包括用户的增删改查、
 * 角色分配、组织归属管理、可分配负责人解析，以及密码重置等操作。
 * </p>
 *
 * <h3>职责列表</h3>
 * <ul>
 *   <li>用户分页查询：支持关键字搜索、部门/角色过滤的分页列表，自动填充角色 ID 和组织信息</li>
 *   <li>部门成员查询：按部门 ID 查询所属成员，委托分页查询实现</li>
 *   <li>可分配负责人解析：根据当前用户角色和部门，解析可被分配的用户范围</li>
 *   <li>可分配用户校验：校验目标用户是否符合当前操作者的分配规则</li>
 *   <li>用户 CRUD 操作：新建、更新、删除用户，包含用户名唯一性校验和数据权限校验</li>
 *   <li>组织归属管理：将用户批量加入/移出业务组，变更时发布组织变更域事件</li>
 *   <li>密码重置：为指定用户重置密码，BCrypt 加密存储</li>
 *   <li>角色分配：为用户全量替换角色关联，变更时刷新权限缓存</li>
 *   <li>渠道编码生成：为新用户生成唯一渠道编码（基于用户名规范化 + 随机后缀防碰撞）</li>
 *   <li>单一管理员保护：系统全局只允许一个 ADMIN 角色用户</li>
 *   <li>操作审计：所有写操作记录到操作日志</li>
 *   <li>域事件发布：用户创建、禁用、组织变更时发布对应域事件</li>
 * </ul>
 *
 * <h3>架构角色</h3>
 * <p>
 * 位于 auth（认证授权）领域服务层，服务于用户域的核心用户管理模块。
 * 通过 {@link SysUserMapper} 管理用户数据，
 * 通过 {@link SysUserRoleMapper} 维护用户-角色关联关系，
 * 通过 {@link OrgStructureService} 解析组织结构和分配关系，
 * 通过 {@link UserDomainEventPublisher} 发布用户生命周期域事件，
 * 通过 {@link UserPermissionCacheService} 管理权限缓存刷新。
 * </p>
 *
 * <h3>业务域</h3>
 * <p>用户域 / 用户管理</p>
 *
 * @see SysRoleService 角色管理服务
 * @see SysMenuService 菜单管理服务
 * @see OrgStructureService 组织结构服务
 */
@Service
public class SysUserService {

    /** 渠道编码最大长度限制 */
    private static final int MAX_CHANNEL_CODE_LEN = 16;

    /**
     * 可分配的业务角色编码集合。
     * <p>
     * 用于负责人分配场景，限定哪些角色的用户可被选为负责人。
     * 包括：业务组长、业务专员、渠道组长、渠道专员。
     * </p>
     */
    private static final Set<String> ASSIGNABLE_BIZ_ROLE_CODES = Set.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF,
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.CHANNEL_STAFF
    );

    /** 用户数据访问层 */
    private final SysUserMapper sysUserMapper;

    /** 角色数据访问层，用于查询角色信息和批量查询 */
    private final SysRoleMapper sysRoleMapper;

    /** 用户-角色关联关系数据访问层 */
    private final SysUserRoleMapper sysUserRoleMapper;

    /** 密码编码器（BCrypt），用于加密存储用户密码 */
    private final PasswordEncoder passwordEncoder;

    /** 操作日志服务，用于记录审计日志 */
    private final OperationLogService operationLogService;

    /** 用户域事件发布器，用于发布用户创建、禁用、组织变更等域事件 */
    private final UserDomainEventPublisher userDomainEventPublisher;

    /** 组织结构服务，用于解析组织归属和分配关系 */
    private final OrgStructureService orgStructureService;

    /** 用户权限缓存服务，用于在角色或组织变更时刷新缓存 */
    private final UserPermissionCacheService userPermissionCacheService;

    /**
     * 构造注入所有依赖。
     *
     * @param sysUserMapper              用户 Mapper
     * @param sysRoleMapper              角色 Mapper
     * @param sysUserRoleMapper          用户-角色关联 Mapper
     * @param passwordEncoder            密码编码器（BCrypt）
     * @param operationLogService        操作日志服务
     * @param userDomainEventPublisher   用户域事件发布器
     * @param orgStructureService        组织结构服务
     * @param userPermissionCacheService 用户权限缓存服务
     */
    public SysUserService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            PasswordEncoder passwordEncoder,
            OperationLogService operationLogService,
            UserDomainEventPublisher userDomainEventPublisher,
            OrgStructureService orgStructureService,
            UserPermissionCacheService userPermissionCacheService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.orgStructureService = orgStructureService;
        this.userPermissionCacheService = userPermissionCacheService;
    }

    /**
     * 分页查询用户列表。
     * <p>
     * 根据请求参数进行分页查询，自动为每个用户填充关联的角色 ID 列表
     * 和组织结构信息（部门名称、业务组名称等）。
     * </p>
     *
     * <ol>
     *   <li>根据分页参数和查询条件构建 MyBatis-Plus 分页对象</li>
     *   <li>调用 Mapper 执行分页查询</li>
     *   <li>批量填充每个用户关联的角色 ID 列表</li>
     *   <li>通过组织结构服务为用户列表补充部门/业务组名称等展示字段</li>
     *   <li>返回分页结果</li>
     * </ol>
     *
     * @param currentUserId 当前操作用户 ID（保留参数，供后续扩展使用）
     * @param dataScope     数据权限范围（保留参数，供后续扩展使用）
     * @param request       分页查询请求参数（包含页码、每页条数、关键字、状态等过滤条件）
     * @return 分页结果，包含用户 VO 列表和分页元数据
     */
    public IPage<SysUserVO> findPage(
            UUID currentUserId,
            DataScope dataScope,
            SysUserPageRequest request) {
        // 第一步：构建分页对象
        Page<SysUserVO> page = new Page<>(request.pageNo(), request.pageSize());
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        // 第二步：执行分页查询
        IPage<SysUserVO> result = sysUserMapper.findPage(page, request, wrapper);
        // 第三步：批量填充角色 ID 列表
        fillRoleIds(result.getRecords());
        // 第四步：补充组织结构展示信息
        orgStructureService.enrichUserList(result.getRecords());
        return result;
    }

    /**
     * 查询指定部门/业务组的成员列表。
     * <p>
     * 将部门成员查询请求适配为标准分页查询请求，以 DataScope.ALL 模式查询
     * （部门管理场景不限制数据权限），委托 {@link #findPage} 执行。
     * </p>
     *
     * <ol>
     *   <li>将 DeptMemberPageRequest 转换为 SysUserPageRequest</li>
     *   <li>设置部门 ID 作为过滤条件</li>
     *   <li>以 DataScope.ALL 模式委托 findPage 执行查询</li>
     *   <li>返回分页结果</li>
     * </ol>
     *
     * @param deptId  目标部门/业务组 ID
     * @param request 部门成员分页查询请求参数
     * @return 分页结果，包含成员用户 VO 列表和分页元数据
     */
    public IPage<SysUserVO> findDeptMembers(UUID deptId, DeptMemberPageRequest request) {
        SysUserPageRequest pageRequest = new SysUserPageRequest(
                (int) request.pageNo(),
                (int) request.pageSize(),
                request.keyword(),
                request.status(),
                deptId,
                request.groupId(),
                request.roleId(),
                request.roleCode());
        return findPage(null, DataScope.ALL, pageRequest);
    }

    /**
     * 查询当前操作者可分配的用户列表（用于负责人下拉选择）。
     * <p>
     * 根据当前用户的角色和部门信息，解析可分配范围（角色过滤 + 部门过滤），
     * 返回符合条件的已启用用户列表，最多返回 20 条。
     * </p>
     *
     * <ol>
     *   <li>构建查询条件：已删除 = 0、状态 = 1、按姓名和用户名排序、限制 20 条</li>
     *   <li>若提供了关键字，则按用户名或真实姓名模糊匹配</li>
     *   <li>查询候选用户列表，为空则直接返回</li>
     *   <li>解析当前用户的可分配范围（角色 + 部门 + 是否允许跨部门）</li>
     *   <li>批量查询候选用户的角色关联关系和角色详情</li>
     *   <li>按部门过滤（非跨部门模式下只保留同部门用户）</li>
     *   <li>按角色过滤（只保留拥有可分配角色的用户）</li>
     *   <li>转换为 VO 列表返回</li>
     * </ol>
     *
     * @param keyword         搜索关键字（匹配用户名或真实姓名，null 表示不过滤）
     * @param currentRoleCodes 当前用户的角色编码列表（用于解析可分配范围）
     * @param currentDeptId   当前用户的部门 ID（用于部门范围过滤）
     * @return 可分配的用户 VO 列表，无数据时返回空列表
     */
    public List<SysUserVO> findAssignableUsers(String keyword, List<String> currentRoleCodes, UUID currentDeptId) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .eq("status", 1)
                .orderByAsc("real_name")
                .orderByAsc("username")
                .last("limit 20");
        if (keyword != null && !keyword.trim().isEmpty()) {
            String safeKeyword = keyword.trim();
            wrapper.and(query -> query.like("username", safeKeyword).or().like("real_name", safeKeyword));
        }

        List<SysUser> users = sysUserMapper.selectList(wrapper);
        if (users.isEmpty()) {
            return Collections.emptyList();
        }
        AssignableScope scope = resolveAssignableScope(currentRoleCodes, currentDeptId);
        Set<String> allowedRoleCodes = scope.allowedRoleCodes();
        if (allowedRoleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<UUID, List<SysUserRole>> relationMap = users.stream()
                .collect(Collectors.toMap(
                        SysUser::getId,
                        user -> sysUserRoleMapper.findByUserId(user.getId())
                ));
        Set<UUID> roleIds = relationMap.values().stream()
                .flatMap(List::stream)
                .map(SysUserRole::getRoleId)
                .filter(roleId -> roleId != null)
                .collect(Collectors.toSet());
        Map<UUID, SysRole> roleMap = roleIds.isEmpty()
                ? Collections.emptyMap()
                : sysRoleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, role -> role));

        return users.stream()
                .filter(user -> scope.deptId() == null || scope.allowCrossDept() || Objects.equals(scope.deptId(), user.getDeptId()))
                .filter(user -> matchesAssignableRole(user.getId(), relationMap, roleMap, allowedRoleCodes))
                .map(this::toVO)
                .toList();
    }

    /**
     * 校验目标用户是否可被当前操作者分配为负责人。
     * <p>
     * 用于商品分配等业务场景中的负责人校验，确保目标用户满足：
     * 角色在可分配范围内、部门在允许范围内（非跨部门场景）。
     * </p>
     *
     * <ol>
     *   <li>校验目标用户 ID 非空</li>
     *   <li>解析当前用户的可分配范围（角色 + 部门 + 是否允许跨部门）</li>
     *   <li>若可分配角色为空，抛出状态异常（当前角色不允许分配负责人）</li>
     *   <li>查询目标用户，校验部门归属（非跨部门模式下必须同部门）</li>
     *   <li>查询目标用户的角色关联关系，校验角色匹配（必须拥有可分配角色之一）</li>
     * </ol>
     *
     * @param targetUserId    目标用户 ID
     * @param currentRoleCodes 当前用户的角色编码列表
     * @param currentDeptId   当前用户的部门 ID
     * @throws BusinessException 目标用户 ID 为空、角色不允许分配、跨部门访问或角色不匹配时抛出
     */
    public void assertAssignableUser(UUID targetUserId, List<String> currentRoleCodes, UUID currentDeptId) {
        if (targetUserId == null) {
            throw BusinessException.param("负责人不能为空");
        }
        AssignableScope scope = resolveAssignableScope(currentRoleCodes, currentDeptId);
        if (scope.allowedRoleCodes().isEmpty()) {
            throw BusinessException.stateInvalid("当前角色不允许分配负责人");
        }
        SysUser targetUser = requireUser(targetUserId);
        if (scope.deptId() != null && !scope.allowCrossDept() && !Objects.equals(scope.deptId(), targetUser.getDeptId())) {
            throw BusinessException.forbidden("只能分配给本组招商下属");
        }

        List<SysUserRole> relations = sysUserRoleMapper.findByUserId(targetUserId);
        if (relations == null || relations.isEmpty()) {
            throw BusinessException.stateInvalid("目标负责人未配置可分配角色");
        }
        Set<UUID> roleIds = relations.stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SysRole> roleMap = roleIds.isEmpty()
                ? Collections.emptyMap()
                : sysRoleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, role -> role));
        if (!matchesAssignableRole(targetUserId, Map.of(targetUserId, relations), roleMap, scope.allowedRoleCodes())) {
            throw BusinessException.forbidden("只能分配给符合规则的招商下属");
        }
    }

    /**
     * 根据 ID 查询单个用户详情。
     *
     * <ol>
     *   <li>根据 ID 查询用户，不存在则抛出异常</li>
     *   <li>校验当前操作者是否有权访问该用户（基于数据权限范围）</li>
     *   <li>转换为 VO 并通过组织结构服务补充部门/业务组名称</li>
     *   <li>返回用户 VO</li>
     * </ol>
     *
     * @param id            用户 ID
     * @param currentUserId 当前操作用户 ID
     * @param dataScope     当前操作者的数据权限范围
     * @return 用户 VO（含组织结构展示信息）
     * @throws BusinessException 用户不存在或无权访问时抛出
     */
    public SysUserVO getById(UUID id, UUID currentUserId, DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        return orgStructureService.enrichUser(toVO(user));
    }

    /**
     * 创建新用户。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验用户名唯一性，已存在则抛出重复异常</li>
     *   <li>规范化并校验角色 ID 列表（去重、存在性检查、启用状态检查、单一管理员保护）</li>
     *   <li>构建用户实体：生成 UUID、设置用户名、BCrypt 加密密码、真实姓名、手机、邮箱</li>
     *   <li>解析组织归属（支持 parentDeptId + groupId 新模式或 deptId 旧模式）</li>
     *   <li>设置初始状态为"待激活"、强制修改密码标记为 true、生成唯一渠道编码</li>
     *   <li>持久化用户到数据库</li>
     *   <li>全量替换用户-角色关联</li>
     *   <li>记录操作审计日志</li>
     *   <li>发布用户创建域事件（含主角色和组织归属信息）</li>
     *   <li>转换为 VO 并补充组织结构信息返回</li>
     * </ol>
     *
     * @param request       用户创建请求 DTO
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @return 创建后的用户 VO（含组织结构展示信息）
     * @throws BusinessException 用户名重复、角色校验失败或渠道编码生成失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public SysUserVO create(SysUserCreateRequest request, UUID currentUserId) {
        sysUserMapper.findByUsername(request.username()).ifPresent(existing -> {
            throw BusinessException.duplicate("用户名已存在");
        });

        List<UUID> roleIds = normalizeRoleIds(request.roleIds());
        validateRoleIds(roleIds, null);

        SysUser user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        ResolvedAssignment assignment = resolveAssignment(
                request.parentDeptId(),
                request.groupId(),
                request.deptId());
        user.setDeptId(assignment.effectiveDeptId());
        user.setStatus(SysUserStatus.PENDING_ACTIVATION);
        user.setForcePasswordChange(true);
        user.setChannelCode(generateUniqueChannelCode(request.username()));
        sysUserMapper.insert(user);

        replaceUserRoles(user.getId(), roleIds);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "新建用户",
                "POST",
                "SysUser",
                user.getId() == null ? null : user.getId().toString(),
                user.getUsername(),
                "新建用户: " + user.getUsername()
        );
        SysRole primaryRole = resolvePrimaryRole(roleIds);
        userDomainEventPublisher.publishUserCreated(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                primaryRole == null ? null : primaryRole.getId(),
                primaryRole == null ? null : primaryRole.getRoleCode(),
                user.getDeptId(),
                user.getDeptId(),
                user.getStatus(),
                currentUserId);
        return orgStructureService.enrichUser(toVO(user));
    }

    /**
     * 更新用户信息。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据 ID 查询原用户，不存在则抛出异常</li>
     *   <li>校验当前操作者是否有权访问该用户（基于数据权限范围）</li>
     *   <li>记录变更前的状态和部门 ID（用于后续判断是否需要发布域事件）</li>
     *   <li>用请求参数覆盖真实姓名、手机、邮箱等字段</li>
     *   <li>若请求中指定了状态，则更新状态</li>
     *   <li>若请求中指定了组织归属参数（parentDeptId / groupId / deptId），则解析并更新部门 ID</li>
     *   <li>持久化更新到数据库</li>
     *   <li>若组织归属发生变化，记录组织变更审计日志并发布域事件</li>
     *   <li>记录操作审计日志</li>
     *   <li>若用户从非禁用变为禁用状态，发布用户禁用域事件</li>
     *   <li>刷新用户权限缓存（含数据范围缓存）</li>
     *   <li>转换为 VO 并补充组织结构信息返回</li>
     * </ol>
     *
     * @param id            要更新的用户 ID
     * @param request       用户更新请求 DTO
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @param dataScope     当前操作者的数据权限范围
     * @return 更新后的用户 VO（含组织结构展示信息）
     * @throws BusinessException 用户不存在或无权访问时抛出
     */
    public SysUserVO update(
            UUID id,
            SysUserUpdateRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);

        Integer previousStatus = user.getStatus();
        UUID previousDeptId = user.getDeptId();

        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.parentDeptId() != null || request.groupId() != null || request.deptId() != null) {
            ResolvedAssignment assignment = resolveAssignment(
                    request.parentDeptId(),
                    request.groupId(),
                    request.deptId());
            user.setDeptId(assignment.effectiveDeptId());
        }
        sysUserMapper.updateById(user);
        recordOrgChangeIfNeeded(user, previousDeptId, user.getDeptId(), currentUserId);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "更新用户",
                "PUT",
                "SysUser",
                user.getId() == null ? null : user.getId().toString(),
                user.getUsername(),
                "更新用户: " + user.getUsername()
        );
        if (becameDisabled(previousStatus, user.getStatus())) {
            userDomainEventPublisher.publishUserDisabled(
                    user.getId(),
                    previousStatus,
                    user.getStatus(),
                    currentUserId);
        }
        userPermissionCacheService.invalidateUser(user.getId());
        userPermissionCacheService.invalidateDataScopeForGroupChange(previousDeptId, user.getDeptId());
        return orgStructureService.enrichUser(toVO(user));
    }

    /**
     * 批量将用户加入指定业务组。
     * <p>
     * 采用事务保证原子性，逐个用户更新组织归属并发布变更事件。
     * </p>
     *
     * <ol>
     *   <li>通过组织结构服务解析目标业务组的有效部门 ID</li>
     *   <li>遍历每个用户 ID，查询用户实体</li>
     *   <li>记录变更前的部门 ID</li>
     *   <li>更新用户的部门 ID 为目标业务组的有效部门 ID</li>
     *   <li>持久化更新到数据库</li>
     *   <li>若组织归属发生变化，记录组织变更审计日志并发布域事件</li>
     *   <li>刷新用户权限缓存</li>
     * </ol>
     *
     * @param groupId       目标业务组 ID
     * @param userIds       要加入的用户 ID 列表
     * @param currentUserId 当前操作用户 ID（用于审计日志和域事件）
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignUsersToGroup(UUID groupId, List<UUID> userIds, UUID currentUserId) {
        ResolvedAssignment groupAssignment = orgStructureService.resolveAssignment(null, groupId);
        for (UUID targetUserId : userIds) {
            SysUser user = requireUser(targetUserId);
            UUID previousDeptId = user.getDeptId();
            user.setDeptId(groupAssignment.effectiveDeptId());
            sysUserMapper.updateById(user);
            recordOrgChangeIfNeeded(user, previousDeptId, user.getDeptId(), currentUserId);
            userPermissionCacheService.invalidateUser(user.getId());
            userPermissionCacheService.invalidateDataScopeForGroupChange(previousDeptId, user.getDeptId());
        }
    }

    /**
     * 批量将用户从指定业务组移除。
     * <p>
     * 仅当用户的当前部门 ID 等于目标业务组 ID 时才执行移除操作，
     * 移除后将部门 ID 置为 null。
     * </p>
     *
     * <ol>
     *   <li>遍历每个用户 ID，查询用户实体</li>
     *   <li>校验用户的当前部门 ID 是否等于目标业务组 ID，不匹配则跳过</li>
     *   <li>记录变更前的部门 ID</li>
     *   <li>将用户的部门 ID 置为 null</li>
     *   <li>持久化更新到数据库</li>
     *   <li>记录组织变更审计日志并发布域事件</li>
     *   <li>刷新用户权限缓存</li>
     * </ol>
     *
     * @param groupId       目标业务组 ID
     * @param userIds       要移除的用户 ID 列表
     * @param currentUserId 当前操作用户 ID（用于审计日志和域事件）
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeUsersFromGroup(UUID groupId, List<UUID> userIds, UUID currentUserId) {
        for (UUID targetUserId : userIds) {
            SysUser user = requireUser(targetUserId);
            if (!Objects.equals(user.getDeptId(), groupId)) {
                continue;
            }
            UUID previousDeptId = user.getDeptId();
            user.setDeptId(null);
            sysUserMapper.updateById(user);
            recordOrgChangeIfNeeded(user, previousDeptId, null, currentUserId);
            userPermissionCacheService.invalidateUser(user.getId());
            userPermissionCacheService.invalidateDataScopeForGroupChange(previousDeptId, null);
        }
    }

    /**
     * 删除用户（软删除）。
     * <p>
     * 删除前执行保护校验：不允许删除当前登录用户自身。
     * 同时物理删除该用户的所有角色关联记录。
     * </p>
     *
     * <ol>
     *   <li>校验目标用户 ID 不等于当前操作者 ID（防止自我删除）</li>
     *   <li>根据 ID 查询用户，不存在则抛出异常</li>
     *   <li>校验当前操作者是否有权访问该用户（基于数据权限范围）</li>
     *   <li>物理删除该用户的所有角色关联记录</li>
     *   <li>执行软删除（设置 deleted 标记）</li>
     *   <li>记录操作审计日志</li>
     * </ol>
     *
     * @param id            要删除的用户 ID
     * @param currentUserId 当前操作用户 ID（用于审计日志和自我删除保护）
     * @param dataScope     当前操作者的数据权限范围
     * @throws BusinessException 试图删除自身、用户不存在或无权访问时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID currentUserId, DataScope dataScope) {
        if (id.equals(currentUserId)) {
            throw BusinessException.stateInvalid("不能删除当前登录用户");
        }
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        sysUserRoleMapper.deleteByUserIdPhysical(id);
        sysUserMapper.softDeleteById(id);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "删除用户",
                "DELETE",
                "SysUser",
                user.getId() == null ? null : user.getId().toString(),
                user.getUsername(),
                "删除用户: " + user.getUsername()
        );
    }

    /**
     * 重置用户密码。
     * <p>
     * 将指定用户的密码更新为新密码（BCrypt 加密存储），操作前校验数据权限。
     * </p>
     *
     * <ol>
     *   <li>根据 ID 查询用户，不存在则抛出异常</li>
     *   <li>校验当前操作者是否有权访问该用户（基于数据权限范围）</li>
     *   <li>构建更新对象，设置 ID、新密码（BCrypt 加密）和强制改密标记</li>
     *   <li>持久化更新到数据库</li>
     *   <li>记录操作审计日志</li>
     * </ol>
     *
     * @param id            目标用户 ID
     * @param request       密码重置请求 DTO（包含新密码）
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @param dataScope     当前操作者的数据权限范围
     * @throws BusinessException 用户不存在或无权访问时抛出
     */
    public void resetPassword(
            UUID id,
            SysUserResetPasswordRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(request.newPassword()));
        update.setForcePasswordChange(true);
        sysUserMapper.updateById(update);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "重置密码",
                "PUT",
                "SysUser",
                user.getId() == null ? null : user.getId().toString(),
                user.getUsername(),
                "重置用户密码: " + user.getUsername()
        );
    }

    /**
     * 为用户分配角色（全量替换模式）。
     * <p>
     * 采用"先删除旧关联、再插入新关联"的策略，替换用户的所有角色关联。
     * 变更后刷新相关权限缓存。
     * </p>
     *
     * <ol>
     *   <li>根据 ID 查询用户，不存在则抛出异常</li>
     *   <li>校验当前操作者是否有权访问该用户（基于数据权限范围）</li>
     *   <li>规范化并校验角色 ID 列表（去重、存在性检查、启用状态检查、单一管理员保护）</li>
     *   <li>全量替换用户-角色关联</li>
     *   <li>刷新用户权限缓存和相关角色的缓存</li>
     *   <li>记录操作审计日志</li>
     * </ol>
     *
     * @param id            目标用户 ID
     * @param request       角色分配请求 DTO（包含角色 ID 列表）
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @param dataScope     当前操作者的数据权限范围
     * @throws BusinessException 用户不存在、无权访问或角色校验失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(
            UUID id,
            SysUserAssignRolesRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        List<UUID> roleIds = normalizeRoleIds(request.roleIds());
        validateRoleIds(roleIds, id);
        replaceUserRoles(id, roleIds);
        userPermissionCacheService.invalidateUser(id);
        for (UUID roleId : roleIds) {
            userPermissionCacheService.invalidateRole(roleId);
        }
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "分配角色",
                "PUT",
                "SysUser",
                user.getId() == null ? null : user.getId().toString(),
                user.getUsername(),
                "更新用户角色: " + user.getUsername()
        );
    }

    /**
     * 查询并校验用户存在。
     *
     * @param id 用户 ID
     * @return 有效的 SysUser 实体
     * @throws BusinessException 用户不存在时抛出
     */
    private SysUser requireUser(UUID id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    /**
     * 校验当前操作者是否有权访问目标用户。
     * <p>
     * 基于数据权限范围进行访问控制：
     * PERSONAL 模式下只能访问自身，DEPT 模式只能访问同部门用户，ALL 模式允许访问任意用户。
     * </p>
     *
     * @param user          目标用户实体
     * @param currentUserId 当前操作者 ID
     * @param dataScope     数据权限范围
     * @throws BusinessException 数据权限为空或访问超出范围用户时抛出
     */
    private void assertCanAccess(SysUser user, UUID currentUserId, DataScope dataScope) {
        if (dataScope == null) {
            throw BusinessException.forbidden("无法确认数据权限，拒绝访问");
        }
        // PERSONAL 模式下只能访问自身
        if (dataScope == DataScope.PERSONAL && !user.getId().equals(currentUserId)) {
            throw BusinessException.forbidden("无权访问该用户");
        }
        if (dataScope == DataScope.DEPT) {
            SysUser currentUser = currentUserId == null ? null : sysUserMapper.selectById(currentUserId);
            if (currentUser == null || currentUser.getDeptId() == null || user.getDeptId() == null
                    || !currentUser.getDeptId().equals(user.getDeptId())) {
                throw BusinessException.forbidden("无权访问该部门外用户");
            }
        }
    }

    /**
     * 规范化角色 ID 列表：去重并过滤 null 值。
     *
     * @param roleIds 原始角色 ID 列表
     * @return 去重后的角色 ID 列表（保持插入顺序），null 或空输入返回空列表
     */
    private List<UUID> normalizeRoleIds(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> distinct = new LinkedHashSet<>();
        for (UUID roleId : roleIds) {
            if (roleId != null) {
                distinct.add(roleId);
            }
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 校验角色 ID 列表的有效性。
     * <p>
     * 校验内容包括：角色是否存在、角色是否已启用、是否违反单一管理员约束。
     * </p>
     *
     * @param roleIds      去重后的角色 ID 列表
     * @param targetUserId 目标用户 ID（更新/分配时传入，创建时为 null），用于单一管理员校验
     * @throws BusinessException 角色不存在、已禁用或违反单一管理员约束时抛出
     */
    private void validateRoleIds(List<UUID> roleIds, UUID targetUserId) {
        if (roleIds.isEmpty()) {
            return;
        }
        // 第一步：校验角色存在性
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw BusinessException.notFound("角色不存在或已删除");
        }
        // 第二步：校验角色是否已禁用
        boolean hasDisabledRole = roles.stream()
                .anyMatch(role -> role.getStatus() == null || role.getStatus() != 1);
        if (hasDisabledRole) {
            throw BusinessException.stateInvalid("不能分配已禁用角色");
        }
        // 第三步：单一管理员保护
        assertSingleAdminUser(roles, targetUserId);
    }

    /**
     * 校验单一管理员约束：系统全局只允许一个未删除的 ADMIN 角色用户。
     * <p>
     * 若待分配的角色列表中包含 ADMIN 角色，且目标用户当前不是管理员，
     * 则检查数据库中是否已存在其他未删除的管理员用户，存在则拒绝。
     * </p>
     *
     * @param roles         待分配的角色列表
     * @param targetUserId  目标用户 ID（更新/分配时传入，创建时为 null）
     * @throws BusinessException 已存在管理员且目标用户不是现有管理员时抛出
     */
    private void assertSingleAdminUser(List<SysRole> roles, UUID targetUserId) {
        // 第一步：检查待分配角色中是否包含 ADMIN
        SysRole adminRole = roles.stream()
                .filter(role -> RoleCodes.ADMIN.equals(role.getRoleCode()))
                .findFirst()
                .orElse(null);
        if (adminRole == null || adminRole.getId() == null) {
            return;
        }
        // 第二步：若目标用户已经是管理员，允许重新分配（不抛异常）
        if (targetUserId != null) {
            boolean targetAlreadyAdmin = sysUserRoleMapper.findByUserId(targetUserId).stream()
                    .anyMatch(relation -> adminRole.getId().equals(relation.getRoleId()));
            if (targetAlreadyAdmin) {
                return;
            }
        }
        // 第三步：检查数据库中是否已存在其他未删除的管理员
        List<UUID> adminUserIds = sysUserRoleMapper.findByRoleId(adminRole.getId()).stream()
                .map(SysUserRole::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (adminUserIds.isEmpty()) {
            return;
        }
        boolean hasExistingAdmin = sysUserMapper.selectBatchIds(adminUserIds).stream()
                .filter(Objects::nonNull)
                .anyMatch(user -> user.getDeleted() == null || user.getDeleted() == 0);
        if (hasExistingAdmin) {
            throw BusinessException.duplicate("管理员账号已存在，不能新增或转配第二个管理员");
        }
    }

    /**
     * 全量替换用户的角色关联（先删后插）。
     *
     * @param userId  用户 ID
     * @param roleIds 新的角色 ID 列表
     */
    private void replaceUserRoles(UUID userId, List<UUID> roleIds) {
        // 第一步：物理删除旧的角色关联
        sysUserRoleMapper.deleteByUserIdPhysical(userId);
        // 第二步：插入新的角色关联
        for (UUID roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setId(UUID.randomUUID());
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            sysUserRoleMapper.insert(relation);
        }
    }

    /**
     * 将用户实体转换为视图对象（VO）。
     * <p>
     * 同时查询该用户关联的角色 ID 列表填充到 VO 中。
     * </p>
     *
     * @param user 用户实体
     * @return 用户 VO
     */
    private SysUserVO toVO(SysUser user) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setDeptId(user.getDeptId());
        vo.setStatus(user.getStatus());
        vo.setForcePasswordChange(user.getForcePasswordChange());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreateTime(user.getCreateTime());
        // 查询并填充角色 ID 列表
        List<UUID> roleIds = sysUserRoleMapper.findByUserId(user.getId()).stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        vo.setRoleIds(roleIds);
        return vo;
    }

    /**
     * 批量填充用户列表中每个用户的角色 ID 列表。
     * <p>
     * 使用批量查询（findByUserIds）避免 N+1 问题，
     * 然后按用户 ID 分组映射到对应的 VO 列表。
     * </p>
     *
     * @param users 用户 VO 列表
     */
    private void fillRoleIds(List<SysUserVO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        // 第一步：提取所有用户 ID（去重）
        List<UUID> userIds = users.stream()
                .map(SysUserVO::getId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        // 第二步：批量查询用户-角色关联，按用户 ID 分组
        Map<UUID, List<UUID>> roleMap = new HashMap<>();
        for (SysUserRole relation : sysUserRoleMapper.findByUserIds(userIds)) {
            roleMap.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(relation.getRoleId());
        }
        // 第三步：填充每个 VO 的角色 ID 列表
        for (SysUserVO user : users) {
            user.setRoleIds(roleMap.getOrDefault(user.getId(), Collections.emptyList()));
        }
    }

    /**
     * 解析当前用户的可分配范围。
     * <p>
     * 根据角色编码确定可分配的目标角色集合、部门范围和跨部门权限：
     * <ul>
     *   <li>ADMIN：可分配所有业务角色，不限部门，允许跨部门</li>
     *   <li>BIZ_LEADER：只能分配 BIZ_STAFF，限同部门</li>
     *   <li>CHANNEL_LEADER：只能分配 CHANNEL_STAFF，限同部门</li>
     *   <li>COLONEL_LEADER：可分配 BIZ_STAFF（招商专员），限同部门</li>
     *   <li>其他角色：返回空范围（不允许分配负责人）</li>
     * </ul>
     * </p>
     *
     * @param currentRoleCodes 当前用户的角色编码列表
     * @param currentDeptId    当前用户的部门 ID
     * @return 可分配范围描述对象
     */
    private AssignableScope resolveAssignableScope(List<String> currentRoleCodes, UUID currentDeptId) {
        if (currentRoleCodes == null || currentRoleCodes.isEmpty()) {
            return AssignableScope.empty();
        }
        LinkedHashSet<String> normalized = currentRoleCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // ADMIN：可分配所有业务角色，不限部门
        if (normalized.contains(RoleCodes.ADMIN)) {
            return new AssignableScope(ASSIGNABLE_BIZ_ROLE_CODES, null, true);
        }
        // 业务组长：只能分配业务专员，限同部门
        if (normalized.contains(RoleCodes.BIZ_LEADER)) {
            return new AssignableScope(Set.of(RoleCodes.BIZ_STAFF), currentDeptId, false);
        }
        // 渠道组长：只能分配渠道专员，限同部门
        if (normalized.contains(RoleCodes.CHANNEL_LEADER)) {
            return new AssignableScope(Set.of(RoleCodes.CHANNEL_STAFF), currentDeptId, false);
        }
        // 招商组长：可分配招商专员（BIZ_STAFF），限同部门
        if (normalized.contains(RoleCodes.COLONEL_LEADER)) {
            return new AssignableScope(Set.of(RoleCodes.BIZ_STAFF), currentDeptId, false);
        }
        return AssignableScope.empty();
    }

    /**
     * 判断用户是否拥有可分配角色中的任意一个。
     * <p>
     * 遍历用户的角色关联关系，跳过已禁用或不存在的角色，
     * 只要有一个角色编码在允许列表中即返回 true。
     * </p>
     *
     * @param userId           用户 ID
     * @param relationMap      用户 ID -> 角色关联列表映射
     * @param roleMap          角色 ID -> 角色实体映射
     * @param allowedRoleCodes 允许的角色编码集合
     * @return 用户拥有可分配角色时返回 true
     */
    private boolean matchesAssignableRole(
            UUID userId,
            Map<UUID, List<SysUserRole>> relationMap,
            Map<UUID, SysRole> roleMap,
            Set<String> allowedRoleCodes) {
        List<SysUserRole> relations = relationMap.getOrDefault(userId, Collections.emptyList());
        if (relations.isEmpty()) {
            return false;
        }
        for (SysUserRole relation : relations) {
            SysRole role = roleMap.get(relation.getRoleId());
            // 跳过已禁用或不存在的角色
            if (role == null || role.getStatus() == null || role.getStatus() != 1) {
                continue;
            }
            if (allowedRoleCodes.contains(role.getRoleCode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断用户是否从非禁用状态变为禁用状态。
     *
     * @param previousStatus 变更前的用户状态
     * @param newStatus      变更后的用户状态
     * @return 若新状态为禁用且原状态不是禁用则返回 true
     */
    private boolean becameDisabled(Integer previousStatus, Integer newStatus) {
        if (newStatus == null || newStatus != SysUserStatus.DISABLED) {
            return false;
        }
        return previousStatus == null || previousStatus != SysUserStatus.DISABLED;
    }

    /**
     * 判断部门 ID 是否发生变化。
     *
     * @param previousDeptId 变更前的部门 ID
     * @param newDeptId      变更后的部门 ID
     * @return 部门 ID 不相等时返回 true
     */
    private boolean deptChanged(UUID previousDeptId, UUID newDeptId) {
        return !Objects.equals(previousDeptId, newDeptId);
    }

    /**
     * 从角色 ID 列表中解析主角色（取第一个）。
     * <p>
     * 用于用户创建事件中携带主角色信息。
     * </p>
     *
     * @param roleIds 角色 ID 列表
     * @return 第一个角色实体，列表为空或查询结果为空时返回 null
     */
    private SysRole resolvePrimaryRole(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return null;
        }
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        return roles.get(0);
    }

    /**
     * 可分配范围描述。
     * <p>
     * 封装负责人分配场景中的权限约束信息。
     * </p>
     *
     * @param allowedRoleCodes 允许分配的目标角色编码集合
     * @param deptId           允许的部门 ID（null 表示不限部门）
     * @param allowCrossDept   是否允许跨部门分配
     */
    private record AssignableScope(Set<String> allowedRoleCodes, UUID deptId, boolean allowCrossDept) {
        /**
         * 创建空的可分配范围（不允许任何分配）。
         *
         * @return 空的 AssignableScope 实例
         */
        private static AssignableScope empty() {
            return new AssignableScope(Collections.emptySet(), null, false);
        }
    }

    /**
     * 生成唯一的渠道编码。
     * <p>
     * 基于用户名规范化后生成渠道编码，若发生冲突则追加随机后缀重试（最多 8 次），
     * 超过重试次数则抛出冲突异常。
     * </p>
     *
     * <ol>
     *   <li>调用 normalizeChannelCode 将用户名转为小写、仅保留字母数字下划线，并截断至最大长度</li>
     *   <li>若规范化结果为空，则使用默认值 "user"</li>
     *   <li>若该编码不存在冲突，直接返回</li>
     *   <li>否则循环最多 8 次：截取 base 前缀 + 4 位随机 UUID 后缀，校验冲突</li>
     *   <li>超过重试次数仍未找到唯一编码，抛出 BusinessException.conflict</li>
     * </ol>
     *
     * @param username 用户名（用于生成渠道编码的基础字符串）
     * @return 唯一的渠道编码
     * @throws BusinessException 8 次重试后仍无法生成唯一编码时抛出
     */
    private String generateUniqueChannelCode(String username) {
        String base = normalizeChannelCode(username);
        if (base.isBlank()) {
            base = "user";
        }
        if (!channelCodeExists(base)) {
            return base;
        }
        for (int i = 0; i < 8; i++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
            int maxBaseLen = MAX_CHANNEL_CODE_LEN - suffix.length();
            String candidate = (base.length() > maxBaseLen ? base.substring(0, maxBaseLen) : base) + suffix;
            if (!channelCodeExists(candidate)) {
                return candidate;
            }
        }
        throw BusinessException.conflict("生成用户渠道编码失败，请重试");
    }

    /**
     * 将用户名规范化为合法的渠道编码格式。
     * <p>
     * 规则：转小写、去除首尾空格、仅保留字母数字下划线，截断至最大长度 {@link #MAX_CHANNEL_CODE_LEN}。
     * </p>
     *
     * @param username 原始用户名（null 视为空字符串）
     * @return 规范化后的渠道编码字符串
     */
    private String normalizeChannelCode(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (normalized.length() > MAX_CHANNEL_CODE_LEN) {
            return normalized.substring(0, MAX_CHANNEL_CODE_LEN);
        }
        return normalized;
    }

    /**
     * 检查渠道编码是否已存在（包含已软删除的记录）。
     * <p>
     * 即使用户已被软删除，其渠道编码也不允许重复使用，以保证编码全局唯一。
     * </p>
     *
     * @param channelCode 待校验的渠道编码
     * @return 编码已存在时返回 true
     */
    private boolean channelCodeExists(String channelCode) {
        return sysUserMapper.existsByChannelCodeIncludingDeleted(channelCode);
    }

    /**
     * 解析组织归属分配参数，兼容新旧两种入参方式。
     * <p>
     * 优先使用新的参数组合（parentDeptId + groupId）；
     * 若两者均为空但 legacyDeptId 不为空，则从旧版 deptId 拆解出父部门和小组；
     * 三者均为空时返回全 null 的空分配结果。
     * </p>
     *
     * <ol>
     *   <li>若 parentDeptId 或 groupId 不为空，使用 OrgStructureService.resolveAssignment 解析</li>
     *   <li>否则若 legacyDeptId 不为空，使用 OrgStructureService.splitAssignment 拆解旧版 ID</li>
     *   <li>否则返回 parentDeptId=null, deptId=null, groupId=null 的空结果</li>
     * </ol>
     *
     * @param parentDeptId 新版父部门 ID（可为 null）
     * @param groupId      新版小组 ID（可为 null）
     * @param legacyDeptId 旧版部门 ID（兼容字段，可为 null）
     * @return 解析后的组织归属分配结果
     */
    private ResolvedAssignment resolveAssignment(UUID parentDeptId, UUID groupId, UUID legacyDeptId) {
        if (parentDeptId != null || groupId != null) {
            return orgStructureService.resolveAssignment(parentDeptId, groupId);
        }
        if (legacyDeptId != null) {
            SplitAssignment split = orgStructureService.splitAssignment(legacyDeptId);
            return new ResolvedAssignment(
                    legacyDeptId,
                    split.parentDeptId() != null ? split.parentDeptId() : legacyDeptId,
                    split.groupId());
        }
        return new ResolvedAssignment(null, null, null);
    }

    /**
     * 记录组织归属变更（若发生变更）。
     * <p>
     * 当用户的生效部门 ID 发生变化时，记录操作审计日志并发布用户小组变更域事件，
     * 通知下游系统（如业绩归属缓存刷新）进行响应。
     * </p>
     *
     * <ol>
     *   <li>调用 deptChanged 判断部门 ID 是否发生变化，未变化则直接返回</li>
     *   <li>拆解新旧部门 ID 为父部门和小组信息</li>
     *   <li>记录操作审计日志（包含变更详情）</li>
     *   <li>发布 UserGroupChanged 域事件</li>
     * </ol>
     *
     * @param user                  用户实体（用于审计日志中的用户信息）
     * @param previousEffectiveDeptId 变更前的生效部门 ID
     * @param newEffectiveDeptId      变更后的生效部门 ID
     * @param operatorId            操作者 ID（用于审计日志和事件）
     */
    private void recordOrgChangeIfNeeded(
            SysUser user,
            UUID previousEffectiveDeptId,
            UUID newEffectiveDeptId,
            UUID operatorId) {
        if (!deptChanged(previousEffectiveDeptId, newEffectiveDeptId)) {
            return;
        }
        SplitAssignment oldSplit = orgStructureService.splitAssignment(previousEffectiveDeptId);
        SplitAssignment newSplit = orgStructureService.splitAssignment(newEffectiveDeptId);
        operationLogService.recordSystemAction(
                operatorId,
                "用户管理",
                "组织归属变更",
                "PUT",
                "SysUser",
                user.getId() == null ? null : user.getId().toString(),
                user.getUsername(),
                orgStructureService.formatOrgChangeRemark(
                        user.getId(),
                        previousEffectiveDeptId,
                        newEffectiveDeptId,
                        operatorId));
        userDomainEventPublisher.publishUserGroupChanged(
                user.getId(),
                oldSplit.groupId(),
                newSplit.groupId(),
                oldSplit.parentDeptId(),
                newSplit.parentDeptId(),
                operatorId);
    }
}
