package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.DeptStatsVO;
import com.colonel.saas.vo.SysDeptVO;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 部门与业务组管理服务。
 *
 * <p>负责 sys_dept 表的 CRUD 操作，支持部门树查询、部门成员分页查询、
 * 部门统计信息获取以及部门/组别的创建、更新和删除。
 *
 * <ul>
 *   <li>部门树构建（平铺转树形结构）</li>
 *   <li>按父级查询下级业务组（支持按组别类型筛选）</li>
 *   <li>部门统计（成员数、各类型子组数量）</li>
 *   <li>部门/组别 CRUD，含编码唯一性校验和父级校验</li>
 *   <li>删除前通过 OrgStructureService 校验关联约束</li>
 *   <li>所有变更操作记录审计日志</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 组织架构
 *
 * @see com.colonel.saas.auth.service.OrgStructureService
 * @see com.colonel.saas.entity.SysDept
 */
@Service
public class SysDeptService {

    /** 部门/组别数据访问 */
    private final SysDeptMapper sysDeptMapper;

    /** 审计日志服务 */
    private final OperationLogService operationLogService;

    /** 组织架构归属解析服务，用于删除校验和组长角色校验 */
    private final OrgStructureService orgStructureService;

    /** 用户服务，用于部门成员分页查询 */
    private final SysUserService sysUserService;

    /**
     * 构造注入所有依赖项。
     *
     * @param sysDeptMapper        部门/组别数据访问
     * @param operationLogService  审计日志服务
     * @param orgStructureService  组织架构归属解析服务
     * @param sysUserService       用户服务
     */
    public SysDeptService(
            SysDeptMapper sysDeptMapper,
            OperationLogService operationLogService,
            OrgStructureService orgStructureService,
            SysUserService sysUserService) {
        this.sysDeptMapper = sysDeptMapper;
        this.operationLogService = operationLogService;
        this.orgStructureService = orgStructureService;
        this.sysUserService = sysUserService;
    }

    /**
     * 查询部门树结构。
     *
     * <p>加载全部有效部门/组别，通过 parentId 构建父子关系，
     * 返回顶层节点列表，每个节点的 children 字段包含其子节点。
     *
     * @return 树形结构的部门列表（仅包含顶层节点）
     */
    public List<SysDeptVO> findTree() {
        List<SysDept> depts = sysDeptMapper.findAllActive();
        Map<UUID, SysDeptVO> index = new LinkedHashMap<>();
        for (SysDept dept : depts) {
            index.put(dept.getId(), toVO(dept));
        }
        List<SysDeptVO> roots = new ArrayList<>();
        for (SysDeptVO node : index.values()) {
            if (node.getParentId() != null && index.containsKey(node.getParentId())) {
                index.get(node.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * 查询全部有效部门/组别的平铺列表。
     *
     * @return 所有未删除的部门/组别 VO 列表
     */
    public List<SysDeptVO> findAll() {
        return sysDeptMapper.findAllActive().stream().map(this::toVO).toList();
    }

    /**
     * 查询指定部门下的业务组列表，支持按组别类型筛选。
     *
     * @param parentId 父部门 ID
     * @param deptType 组别类型筛选条件（可为 null 表示不过滤）
     * @return 符合条件的业务组 VO 列表
     * @throws BusinessException 父部门不存在时抛出
     */
    public List<SysDeptVO> findGroupsByParent(UUID parentId, String deptType) {
        requireDept(parentId);
        return sysDeptMapper.findByParentId(parentId).stream()
                .filter(dept -> !StringUtils.hasText(deptType)
                        || DeptType.normalize(deptType).equals(DeptType.normalize(dept.getDeptType())))
                .filter(dept -> DeptType.isGroup(dept.getDeptType()))
                .map(this::toVO)
                .toList();
    }

    /**
     * 获取部门统计信息。
     *
     * <p>统计部门下的总成员数、招商组数量和渠道组数量。
     *
     * @param deptId 部门 ID
     * @return 部门统计 VO
     * @throws BusinessException 部门不存在时抛出
     */
    public DeptStatsVO getStats(UUID deptId) {
        requireDept(deptId);
        DeptStatsVO stats = new DeptStatsVO();
        stats.setDeptId(deptId);
        stats.setMemberCount(sysDeptMapper.countMembersUnderDept(deptId));
        stats.setRecruiterGroupCount(sysDeptMapper.countChildGroupsByType(deptId, DeptType.RECRUITER_GROUP));
        stats.setChannelGroupCount(sysDeptMapper.countChildGroupsByType(deptId, DeptType.CHANNEL_GROUP));
        return stats;
    }

    /**
     * 分页查询部门/组别下的成员列表。
     *
     * <p>委托给 SysUserService 的 findDeptMembers 方法执行实际查询。
     *
     * @param deptId   部门/组别 ID
     * @param request  分页查询参数
     * @return 分页结果
     * @throws BusinessException 部门不存在时抛出
     */
    public IPage<SysUserVO> findMembers(UUID deptId, DeptMemberPageRequest request) {
        requireDept(deptId);
        return sysUserService.findDeptMembers(deptId, request);
    }

    /**
     * 根据 ID 查询部门/组别详情。
     *
     * @param id 部门/组别 ID
     * @return 部门/组别 VO
     * @throws BusinessException 记录不存在或已删除时抛出
     */
    public SysDeptVO getById(UUID id) {
        return toVO(requireDept(id));
    }

    /**
     * 创建部门或业务组。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验部门编码唯一性</li>
     *   <li>校验父级部门有效性</li>
     *   <li>解析并校验组织类型</li>
     *   <li>若指定了组长，校验组长角色与组别类型匹配</li>
     *   <li>设置默认排序和状态值</li>
     *   <li>插入数据库</li>
     *   <li>记录审计日志</li>
     * </ol>
     *
     * @param request       创建请求参数
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @return 创建后的部门/组别 VO
     * @throws BusinessException 编码重复、父级无效或组长角色不匹配时抛出
     */
    public SysDeptVO create(SysDeptCreateRequest request, UUID currentUserId) {
        ensureDeptCodeUnique(request.deptCode(), null);
        validateParent(request.parentId(), null);
        SysDept dept = new SysDept();
        dept.setParentId(request.parentId());
        dept.setDeptCode(request.deptCode().trim());
        dept.setDeptName(request.deptName().trim());
        dept.setDeptType(resolveDeptType(request.deptType()));
        dept.setLeaderUserId(request.leaderUserId());
        if (request.leaderUserId() != null) {
            orgStructureService.validateGroupLeader(request.leaderUserId(), dept.getDeptType());
        }
        dept.setLeader(request.leader());
        dept.setPhone(request.phone());
        dept.setEmail(request.email());
        dept.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        dept.setStatus(request.status() == null ? 1 : request.status());
        dept.setRemark(request.remark());
        sysDeptMapper.insert(dept);
        operationLogService.recordSystemAction(
                currentUserId,
                "部门管理",
                "新建部门",
                "POST",
                "SysDept",
                dept.getId() == null ? null : dept.getId().toString(),
                dept.getDeptCode(),
                "新建部门: " + dept.getDeptName()
        );
        return toVO(dept);
    }

    /**
     * 更新部门或业务组信息。
     *
     * <p>处理流程与 {@link #create} 类似，额外校验编码唯一性排除自身 ID。
     *
     * @param id            要更新的部门/组别 ID
     * @param request       更新请求参数
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @return 更新后的部门/组别 VO
     * @throws BusinessException 记录不存在、编码重复或校验失败时抛出
     */
    public SysDeptVO update(UUID id, SysDeptUpdateRequest request, UUID currentUserId) {
        SysDept dept = requireDept(id);
        ensureDeptCodeUnique(request.deptCode(), id);
        validateParent(request.parentId(), id);
        dept.setParentId(request.parentId());
        dept.setDeptCode(request.deptCode().trim());
        dept.setDeptName(request.deptName().trim());
        dept.setDeptType(resolveDeptType(request.deptType()));
        dept.setLeaderUserId(request.leaderUserId());
        if (request.leaderUserId() != null) {
            orgStructureService.validateGroupLeader(request.leaderUserId(), dept.getDeptType());
        }
        dept.setLeader(request.leader());
        dept.setPhone(request.phone());
        dept.setEmail(request.email());
        dept.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        dept.setStatus(request.status() == null ? 1 : request.status());
        dept.setRemark(request.remark());
        sysDeptMapper.updateById(dept);
        operationLogService.recordSystemAction(
                currentUserId,
                "部门管理",
                "更新部门",
                "PUT",
                "SysDept",
                id.toString(),
                dept.getDeptCode(),
                "更新部门: " + dept.getDeptName()
        );
        return toVO(dept);
    }

    /**
     * 删除部门或业务组（软删除）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验部门存在且未被删除</li>
     *   <li>通过 OrgStructureService 校验无关联员工和子组别</li>
     *   <li>执行软删除（设置 deleted 标记）</li>
     *   <li>记录审计日志</li>
     * </ol>
     *
     * @param id            要删除的部门/组别 ID
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @throws BusinessException 记录不存在、下有员工或子组别时抛出
     */
    public void delete(UUID id, UUID currentUserId) {
        SysDept dept = requireDept(id);
        orgStructureService.assertCanDeleteDept(id);
        if (sysDeptMapper.softDeleteById(id) <= 0) {
            throw BusinessException.notFound("部门不存在或已删除");
        }
        operationLogService.recordSystemAction(
                currentUserId,
                "部门管理",
                "删除部门",
                "DELETE",
                "SysDept",
                id.toString(),
                dept.getDeptCode(),
                "删除部门: " + dept.getDeptName()
        );
    }

    /**
     * 校验部门编码唯一性。
     *
     * @param deptCode   部门编码
     * @param excludeId  排除的记录 ID（更新时排除自身），创建时为 null
     * @throws BusinessException 编码为空或已存在时抛出
     */
    private void ensureDeptCodeUnique(String deptCode, UUID excludeId) {
        if (!StringUtils.hasText(deptCode)) {
            throw BusinessException.param("部门编码不能为空");
        }
        sysDeptMapper.findByDeptCode(deptCode.trim()).ifPresent(existing -> {
            if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
                throw BusinessException.duplicate("部门编码已存在: " + deptCode);
            }
        });
    }

    /**
     * 校验父级部门有效性。
     *
     * @param parentId 父部门 ID，可为 null 表示无父级
     * @param selfId   当前记录 ID（防止自引用），创建时为 null
     * @throws BusinessException 父级为自己或父级不存在时抛出
     */
    private void validateParent(UUID parentId, UUID selfId) {
        if (parentId == null) {
            return;
        }
        if (Objects.equals(parentId, selfId)) {
            throw BusinessException.param("上级部门不能选择自己");
        }
        requireDept(parentId);
    }

    /**
     * 查询并校验部门/组别存在且未被删除。
     *
     * @param id 部门/组别 ID
     * @return 有效的 SysDept 实体
     * @throws BusinessException 记录不存在或已删除时抛出
     */
    private SysDept requireDept(UUID id) {
        SysDept dept = sysDeptMapper.selectById(id);
        if (dept == null || Objects.equals(dept.getDeleted(), 1)) {
            throw BusinessException.notFound("部门不存在");
        }
        return dept;
    }

    /**
     * 将 SysDept 实体转换为 VO。
     *
     * @param dept 部门/组别实体
     * @return 对应的 VO 对象
     */
    private SysDeptVO toVO(SysDept dept) {
        SysDeptVO vo = new SysDeptVO();
        vo.setId(dept.getId());
        vo.setParentId(dept.getParentId());
        vo.setDeptCode(dept.getDeptCode());
        vo.setDeptName(dept.getDeptName());
        vo.setDeptType(dept.getDeptType());
        vo.setLeaderUserId(dept.getLeaderUserId());
        vo.setLeader(dept.getLeader());
        vo.setPhone(dept.getPhone());
        vo.setEmail(dept.getEmail());
        vo.setSortOrder(dept.getSortOrder());
        vo.setStatus(dept.getStatus());
        vo.setRemark(dept.getRemark());
        return vo;
    }

    /**
     * 解析并校验组织类型编码。
     *
     * @param deptType 前端传入的组织类型
     * @return 归一化后的组织类型编码
     * @throws BusinessException 类型不合法时抛出
     */
    private String resolveDeptType(String deptType) {
        String normalized = DeptType.normalize(deptType);
        if (!DeptType.isAllowed(normalized)) {
            throw BusinessException.param("组织类型非法: " + deptType);
        }
        return normalized;
    }
}
