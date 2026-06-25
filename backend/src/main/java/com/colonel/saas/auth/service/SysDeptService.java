package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.domain.user.application.OrgUnitDirectoryApplicationService;
import com.colonel.saas.domain.user.application.OrgUnitWriteApplicationService;
import com.colonel.saas.vo.DeptStatsVO;
import com.colonel.saas.vo.SysDeptVO;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
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
 *   <li>删除前通过用户域组织校验策略校验关联约束</li>
 *   <li>所有变更操作记录审计日志</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 组织架构
 *
 * @see com.colonel.saas.domain.user.application.OrgUnitDirectoryApplicationService
 * @see com.colonel.saas.domain.user.application.OrgUnitWriteApplicationService
 */
@Service
public class SysDeptService {

    /** 组织单元目录查询应用服务 */
    private final OrgUnitDirectoryApplicationService orgUnitDirectoryApplicationService;

    /** 组织单元写应用服务 */
    private final OrgUnitWriteApplicationService orgUnitWriteApplicationService;

    /**
     * 构造注入所有依赖项。
     *
     * @param orgUnitDirectoryApplicationService 组织单元目录查询应用服务
     * @param orgUnitWriteApplicationService 组织单元写应用服务
     */
    public SysDeptService(
            OrgUnitDirectoryApplicationService orgUnitDirectoryApplicationService,
            OrgUnitWriteApplicationService orgUnitWriteApplicationService) {
        this.orgUnitDirectoryApplicationService = orgUnitDirectoryApplicationService;
        this.orgUnitWriteApplicationService = orgUnitWriteApplicationService;
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
        return orgUnitDirectoryApplicationService.findTree();
    }

    /**
     * 查询全部有效部门/组别的平铺列表。
     *
     * @return 所有未删除的部门/组别 VO 列表
     */
    public List<SysDeptVO> findAll() {
        return orgUnitDirectoryApplicationService.findAll();
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
        return orgUnitDirectoryApplicationService.findGroupsByParent(parentId, deptType);
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
        return orgUnitDirectoryApplicationService.getStats(deptId);
    }

    /**
     * 分页查询部门/组别下的成员列表。
     *
     * <p>委托给用户域组织单元目录应用服务执行实际查询。
     *
     * @param deptId   部门/组别 ID
     * @param request  分页查询参数
     * @return 分页结果
     * @throws BusinessException 部门不存在时抛出
     */
    public IPage<SysUserVO> findMembers(UUID deptId, DeptMemberPageRequest request) {
        return orgUnitDirectoryApplicationService.findMembers(deptId, request);
    }

    /**
     * 根据 ID 查询部门/组别详情。
     *
     * @param id 部门/组别 ID
     * @return 部门/组别 VO
     * @throws BusinessException 记录不存在或已删除时抛出
     */
    public SysDeptVO getById(UUID id) {
        return orgUnitDirectoryApplicationService.getById(id);
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
        return orgUnitWriteApplicationService.create(request, currentUserId);
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
    public SysDeptVO update(UUID id, SysDeptUpdateRequest request, UUID currentUserId, Collection<?> roleCodes) {
        return orgUnitWriteApplicationService.update(id, request, currentUserId, roleCodes);
    }

    /**
     * 删除部门或业务组（软删除）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验部门存在且未被删除</li>
     *   <li>通过用户域组织校验策略校验无关联员工和子组别</li>
     *   <li>执行软删除（设置 deleted 标记）</li>
     *   <li>记录审计日志</li>
     * </ol>
     *
     * @param id            要删除的部门/组别 ID
     * @param currentUserId 当前操作用户 ID（用于审计日志）
     * @throws BusinessException 记录不存在、下有员工或子组别时抛出
     */
    public void delete(UUID id, UUID currentUserId, Collection<?> roleCodes) {
        orgUnitWriteApplicationService.delete(id, currentUserId, roleCodes);
    }
}
