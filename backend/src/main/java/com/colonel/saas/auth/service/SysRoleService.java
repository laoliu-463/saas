package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysRoleCreateRequest;
import com.colonel.saas.auth.dto.SysRoleUpdateRequest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.SysRoleVO;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.List;
import java.util.UUID;

/**
 * 系统角色管理服务。
 * <p>
 * 负责角色资源的全生命周期管理，包括角色的增删改查、
 * 角色编码唯一性校验，以及系统内置角色的保护机制。
 * </p>
 *
 * <h3>职责列表</h3>
 * <ul>
 *   <li>角色分页查询：支持关键字搜索和状态过滤的分页列表</li>
 *   <li>角色 CRUD 操作：新建、更新、删除角色，删除前校验用户引用和系统内置角色保护</li>
 *   <li>角色编码唯一性校验：创建和更新时确保 roleCode 不重复</li>
 *   <li>系统内置角色保护：ADMIN、BIZ_LEADER 等系统预置角色不允许删除</li>
 *   <li>操作审计：所有写操作记录到操作日志</li>
 * </ul>
 *
 * <h3>架构角色</h3>
 * <p>
 * 位于 auth（认证授权）领域服务层，服务于用户域的角色模块。
 * 通过 {@link SysRoleMapper} 管理角色数据，
 * 通过 {@link SysUserRoleMapper} 查询角色-用户关联关系以判断角色是否可删除。
 * </p>
 *
 * <h3>业务域</h3>
 * <p>用户域 / 权限管理 / 角色管理</p>
 *
 * @see SysMenuService 菜单管理服务
 * @see SysUserService 用户管理服务
 */
@Service
public class SysRoleService {

    /**
     * 系统内置角色编码集合。
     * <p>
     * 这些角色是系统预置的，不允许删除，以保证系统基本权限模型的稳定性。
     * 包括：管理员、业务组长、业务专员、渠道组长、渠道专员、运营专员、招商组长。
     * </p>
     */
    private static final Set<String> SYSTEM_ROLE_CODES = Set.of(
            RoleCodes.ADMIN,
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF,
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.CHANNEL_STAFF,
            RoleCodes.OPS_STAFF,
            RoleCodes.COLONEL_LEADER
    );

    /** 角色数据访问层 */
    private final SysRoleMapper sysRoleMapper;

    /** 用户-角色关联关系数据访问层，用于检查角色是否被用户引用 */
    private final SysUserRoleMapper sysUserRoleMapper;

    /** 操作日志服务，用于记录审计日志 */
    private final OperationLogService operationLogService;

    /**
     * 构造注入所有依赖。
     *
     * @param sysRoleMapper       角色 Mapper
     * @param sysUserRoleMapper   用户-角色关联 Mapper
     * @param operationLogService 操作日志服务
     */
    public SysRoleService(
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            OperationLogService operationLogService) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.operationLogService = operationLogService;
    }

    /**
     * 分页查询角色列表。
     *
     * @param page    当前页码（从 1 开始）
     * @param size    每页条数
     * @param keyword 搜索关键字（匹配角色编码或名称，null 表示不过滤）
     * @param status  角色状态过滤条件（null 表示不过滤）
     * @return 分页结果，包含角色 VO 列表和分页元数据
     */
    public IPage<SysRoleVO> findPage(long page, long size, String keyword, Integer status) {
        return sysRoleMapper.findPage(new Page<>(page, size), keyword, status);
    }

    /**
     * 根据 ID 查询单个角色详情。
     *
     * @param id 角色 ID
     * @return 角色 VO
     * @throws BusinessException 若角色不存在
     */
    public SysRoleVO getById(UUID id) {
        // 第一步：查询角色（不存在则抛出异常）
        SysRole role = requireRole(id);
        // 第二步：转换为 VO 返回
        return toVO(role);
    }

    /**
     * 查询所有已启用的角色列表。
     * <p>
     * 用于下拉选择等场景，只返回 status = 1 的角色。
     * </p>
     *
     * @return 已启用的角色 VO 列表
     */
    public List<SysRoleVO> findAllEnabled() {
        return sysRoleMapper.findAll(1);
    }

    /**
     * 创建新角色。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验角色编码唯一性</li>
     *   <li>根据请求参数构建角色实体，未指定的字段使用默认值（dataScope 默认 1，status 默认 1）</li>
     *   <li>插入数据库</li>
     *   <li>记录操作审计日志</li>
     *   <li>转换为 VO 返回</li>
     * </ol>
     *
     * @param request       角色创建请求 DTO
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @return 创建后的角色 VO
     * @throws BusinessException 角色编码重复时抛出
     */
    public SysRoleVO create(SysRoleCreateRequest request, UUID currentUserId) {
        // 第一步：校验角色编码唯一性
        ensureRoleCodeUnique(request.roleCode(), null);
        // 第二步：构建角色实体并填充默认值
        SysRole role = new SysRole();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        // 未指定数据范围时默认为 1（仅本人数据）
        role.setDataScope(request.dataScope() == null ? 1 : request.dataScope());
        // 未指定状态时默认为 1（启用）
        role.setStatus(request.status() == null ? 1 : request.status());
        role.setRemark(request.remark());
        // 第三步：持久化到数据库
        sysRoleMapper.insert(role);
        // 第四步：记录操作审计日志
        operationLogService.recordSystemAction(
                currentUserId,
                "角色管理",
                "新建角色",
                "POST",
                "SysRole",
                role.getId() == null ? null : role.getId().toString(),
                role.getRoleCode(),
                "新建角色: " + role.getRoleCode()
        );
        // 第五步：转换为 VO 返回
        return toVO(role);
    }

    /**
     * 更新角色信息。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据 ID 查询原角色，不存在则抛出异常</li>
     *   <li>校验角色编码唯一性（排除自身 ID）</li>
     *   <li>使用请求参数覆盖原角色各字段</li>
     *   <li>持久化更新到数据库</li>
     *   <li>记录操作审计日志</li>
     *   <li>转换为 VO 返回</li>
     * </ol>
     *
     * @param id            要更新的角色 ID
     * @param request       角色更新请求 DTO
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @return 更新后的角色 VO
     * @throws BusinessException 角色不存在或角色编码重复时抛出
     */
    public SysRoleVO update(UUID id, SysRoleUpdateRequest request, UUID currentUserId) {
        // 第一步：查询原角色（不存在则抛出异常）
        SysRole role = requireRole(id);
        // 第二步：校验角色编码唯一性（排除自身）
        ensureRoleCodeUnique(request.roleCode(), id);
        // 第三步：用请求参数覆盖各字段
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setDataScope(request.dataScope());
        role.setStatus(request.status());
        role.setRemark(request.remark());
        // 第四步：持久化更新
        sysRoleMapper.updateById(role);
        // 第五步：记录操作审计日志
        operationLogService.recordSystemAction(
                currentUserId,
                "角色管理",
                "更新角色",
                "PUT",
                "SysRole",
                role.getId() == null ? null : role.getId().toString(),
                role.getRoleCode(),
                "更新角色: " + role.getRoleCode()
        );
        // 第六步：转换为 VO 返回
        return toVO(role);
    }

    /**
     * 删除角色（软删除）。
     *
     * <p>删除前执行两重保护校验：
     * <ol>
     *   <li>根据 ID 查询原角色，不存在则抛出异常</li>
     *   <li>校验角色编码是否为系统内置角色，若是则拒绝删除</li>
     *   <li>校验角色是否仍被用户引用，若存在引用则拒绝删除</li>
     *   <li>执行软删除（设置 deleted 标记）</li>
     *   <li>记录操作审计日志</li>
     * </ol>
     *
     * @param id            要删除的角色 ID
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @throws BusinessException 角色不存在、系统内置角色或仍被用户引用时抛出
     */
    public void delete(UUID id, UUID currentUserId) {
        // 第一步：查询原角色
        SysRole role = requireRole(id);
        // 第二步：系统内置角色不允许删除
        if (SYSTEM_ROLE_CODES.contains(role.getRoleCode())) {
            throw BusinessException.stateInvalid("系统内置角色不允许删除");
        }
        // 第三步：角色仍被用户使用时不允许删除
        if (!sysUserRoleMapper.findByRoleId(id).isEmpty()) {
            throw BusinessException.stateInvalid("角色仍被用户使用，不能删除");
        }
        // 第四步：执行软删除
        sysRoleMapper.softDeleteById(id);
        // 第五步：记录操作审计日志
        operationLogService.recordSystemAction(
                currentUserId,
                "角色管理",
                "删除角色",
                "DELETE",
                "SysRole",
                role.getId() == null ? null : role.getId().toString(),
                role.getRoleCode(),
                "删除角色: " + role.getRoleCode()
        );
    }

    /**
     * 查询并校验角色存在。
     *
     * @param id 角色 ID
     * @return 有效的 SysRole 实体
     * @throws BusinessException 角色不存在时抛出
     */
    private SysRole requireRole(UUID id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw BusinessException.notFound("角色不存在");
        }
        return role;
    }

    /**
     * 校验角色编码唯一性。
     *
     * @param roleCode   角色编码
     * @param currentId  排除的记录 ID（更新时排除自身），创建时为 null
     * @throws BusinessException 编码为空或已存在时抛出
     */
    private void ensureRoleCodeUnique(String roleCode, UUID currentId) {
        if (roleCode == null || roleCode.isBlank()) {
            throw BusinessException.param("角色编码不能为空");
        }
        sysRoleMapper.findByRoleCode(roleCode).ifPresent(exists -> {
            if (currentId == null || !exists.getId().equals(currentId)) {
                throw BusinessException.duplicate("角色编码已存在");
            }
        });
    }

    /**
     * 将角色实体转换为视图对象（VO）。
     *
     * @param role 角色实体
     * @return 角色 VO
     */
    private SysRoleVO toVO(SysRole role) {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDataScope(role.getDataScope());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        return vo;
    }
}
