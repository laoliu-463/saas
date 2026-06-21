package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.dto.SysUserAssignRolesRequest;
import com.colonel.saas.auth.dto.SysUserCreateRequest;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.dto.SysUserResetPasswordRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.application.SysUserCRUDApplicationA;
import com.colonel.saas.domain.user.application.SysUserCRUDApplicationB;
import com.colonel.saas.domain.user.application.SysUserGroupMembershipApplication;
import com.colonel.saas.domain.user.application.SysUserQueryApplicationService;
import com.colonel.saas.domain.user.application.SysUserRoleAssignmentApplicationService;
import com.colonel.saas.domain.user.application.UserAssignableApplicationService;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
 * 位于 auth（认证授权）兼容服务层，服务于旧 Controller 与现有调用方。
 * 具体用户查询、CRUD、组织成员、负责人校验和角色分配逻辑委托给用户域应用服务。
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

    /** 用户 CRUD DDD 入口 A：getById / create */
    private final SysUserCRUDApplicationA sysUserCRUDApplicationA;

    /** 用户 CRUD DDD 入口 B：update / delete / resetPassword */
    private final SysUserCRUDApplicationB sysUserCRUDApplicationB;

    /** 用户业务组成员 DDD 入口：assignUsersToGroup / removeUsersFromGroup */
    private final SysUserGroupMembershipApplication sysUserGroupMembershipApplication;

    /** 用户查询 DDD 入口：findPage / findDeptMembers */
    private final SysUserQueryApplicationService sysUserQueryApplicationService;

    /** 用户可分配负责人 DDD 入口：findAssignableUsers / assertAssignableUser / assertRecruiterUser */
    private final UserAssignableApplicationService userAssignableApplicationService;

    /** 用户角色分配 DDD 入口：assignRoles */
    private final SysUserRoleAssignmentApplicationService sysUserRoleAssignmentApplicationService;

    /**
     * 构造注入所有依赖。
     *
     * @param sysUserCRUDApplicationA    用户 CRUD DDD 入口 A
     * @param sysUserCRUDApplicationB    用户 CRUD DDD 入口 B
     * @param sysUserGroupMembershipApplication 用户业务组成员 DDD 入口
     * @param sysUserQueryApplicationService 用户查询 DDD 入口
     * @param userAssignableApplicationService 用户可分配负责人 DDD 入口
     * @param sysUserRoleAssignmentApplicationService 用户角色分配 DDD 入口
     */
    public SysUserService(
            SysUserCRUDApplicationA sysUserCRUDApplicationA,
            SysUserCRUDApplicationB sysUserCRUDApplicationB,
            SysUserGroupMembershipApplication sysUserGroupMembershipApplication,
            SysUserQueryApplicationService sysUserQueryApplicationService,
            UserAssignableApplicationService userAssignableApplicationService,
            SysUserRoleAssignmentApplicationService sysUserRoleAssignmentApplicationService) {
        this.sysUserCRUDApplicationA = sysUserCRUDApplicationA;
        this.sysUserCRUDApplicationB = sysUserCRUDApplicationB;
        this.sysUserGroupMembershipApplication = sysUserGroupMembershipApplication;
        this.sysUserQueryApplicationService = sysUserQueryApplicationService;
        this.userAssignableApplicationService = userAssignableApplicationService;
        this.sysUserRoleAssignmentApplicationService = sysUserRoleAssignmentApplicationService;
    }

    /**
     * 分页查询用户列表。
     * <p>
     * 根据请求参数进行分页查询，自动为每个用户填充关联的角色 ID 列表
     * 和组织结构信息（部门名称、业务组名称等）。
     * </p>
     *
     * <p><b>筛选与数据权限（CLAUDE.md 不变量）：</b></p>
     * <ol>
     *   <li>请求字段筛选：keyword（用户名/姓名模糊）、status（账号状态）、deptId/groupId（部门归属）、
     *       roleId/roleCode（拥有指定角色）由用户域查询应用服务组装</li>
     *   <li>数据范围（{@code dataScope}）：PERSONAL → {@code su.id = currentUserId}，
     *       DEPT → {@code su.dept_id = currentDeptId}，ALL → 不追加</li>
     *   <li>本兼容服务只委托，不再直接访问 Mapper / Entity</li>
     * </ol>
     *
     * @param currentUserId 当前操作用户 ID（PERSONAL 范围时必填，其他可空）
     * @param currentDeptId 当前用户所属部门 ID（DEPT 范围时必填，其他可空）
     * @param dataScope     数据权限范围（null 时按 ALL 处理，兼容单元测试与非 HTTP 上下文）
     * @param request       分页查询请求参数（包含页码、每页条数、关键字、状态等过滤条件）
     * @return 分页结果，包含用户 VO 列表和分页元数据
     */
    public IPage<SysUserVO> findPage(
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            SysUserPageRequest request) {
        return sysUserQueryApplicationService.findPage(currentUserId, currentDeptId, dataScope, request);
    }

    /**
     * 3-arg 重载：无部门上下文（DEPT 范围退化为不追加，与 AOP 行为对齐）。
     * t7-system 新增 4-arg 形式后保留 3-arg 兼容 findDeptMembers 等旧调用方。
     */
    public IPage<SysUserVO> findPage(
            UUID currentUserId,
            DataScope dataScope,
            SysUserPageRequest request) {
        return sysUserQueryApplicationService.findPage(currentUserId, dataScope, request);
    }

    /**
     * 查询指定部门/业务组的成员列表。
     * <p>
     * 将部门成员查询请求适配为标准分页查询请求，以 DataScope.ALL 模式查询
     * （部门管理场景不限制数据权限），委托用户域查询应用服务执行。
     * </p>
     *
     * <ol>
     *   <li>将 DeptMemberPageRequest 转换为 SysUserPageRequest</li>
     *   <li>设置部门 ID 作为过滤条件</li>
     *   <li>以 DataScope.ALL 模式委托用户域查询应用服务执行查询</li>
     *   <li>返回分页结果</li>
     * </ol>
     *
     * @param deptId  目标部门/业务组 ID
     * @param request 部门成员分页查询请求参数
     * @return 分页结果，包含成员用户 VO 列表和分页元数据
     */
    public IPage<SysUserVO> findDeptMembers(UUID deptId, DeptMemberPageRequest request) {
        return sysUserQueryApplicationService.findDeptMembers(deptId, request);
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
        return userAssignableApplicationService.findAssignableUsers(keyword, currentRoleCodes, currentDeptId);
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
        userAssignableApplicationService.assertAssignableUser(targetUserId, currentRoleCodes, currentDeptId);
    }

    /**
     * 校验目标用户可作为活动级招商组长（管理员分配活动专用）。
     */
    public void assertRecruiterUser(UUID targetUserId) {
        userAssignableApplicationService.assertRecruiterUser(targetUserId);
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
        return sysUserCRUDApplicationA.getById(id, currentUserId, dataScope);
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
        return sysUserCRUDApplicationA.create(request, currentUserId);
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
        return sysUserCRUDApplicationB.update(id, request, currentUserId, dataScope);
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
        sysUserGroupMembershipApplication.assignUsersToGroup(groupId, userIds, currentUserId);
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
        sysUserGroupMembershipApplication.removeUsersFromGroup(groupId, userIds, currentUserId);
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
        sysUserCRUDApplicationB.delete(id, currentUserId, dataScope);
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
        sysUserCRUDApplicationB.resetPassword(id, request, currentUserId, dataScope);
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
        sysUserRoleAssignmentApplicationService.assignRoles(id, request, currentUserId, dataScope);
    }

}
