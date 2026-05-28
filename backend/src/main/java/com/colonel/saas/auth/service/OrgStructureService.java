package com.colonel.saas.auth.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 组织架构归属解析服务。
 *
 * <p>sys_dept 表同时承载"部门"与"业务组"两级结构，用户仅持久化
 * {@code sys_user.dept_id} 一个外键指向业务组或部门。
 * 本服务负责：
 * <ul>
 *   <li>将 parentDeptId + groupId 的前端入参解析为有效的 effectiveDeptId</li>
 *   <li>将 effectiveDeptId 反向拆分为 parentDeptId 和 groupId 用于前端展示</li>
 *   <li>为用户列表/单个用户填充组织归属字段（部门名称、组名、组类型）</li>
 *   <li>校验组长角色与组别类型的匹配关系</li>
 *   <li>校验部门/组别是否可安全删除（无关联员工、无下级组别）</li>
 *   <li>生成组织变更的审计备注文本</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 组织架构
 *
 * @see com.colonel.saas.constant.DeptType
 * @see com.colonel.saas.vo.SysUserVO
 */
@Service
public class OrgStructureService {

    // ==================== 组长角色校验常量 ====================

    /** 招商组允许的组长角色集合 */
    private static final Set<String> RECRUITER_LEADER_ROLES = Set.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.COLONEL_LEADER,
            RoleCodes.ADMIN);

    /** 渠道组允许的组长角色集合 */
    private static final Set<String> CHANNEL_LEADER_ROLES = Set.of(
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.ADMIN);

    /** 运营组允许的组长角色集合 */
    private static final Set<String> OPS_LEADER_ROLES = Set.of(
            RoleCodes.OPS_STAFF,
            RoleCodes.ADMIN);

    /** 部门负责人允许使用各业务线组长角色 */
    private static final Set<String> DEPARTMENT_LEADER_ROLES = Set.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.COLONEL_LEADER,
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.OPS_STAFF,
            RoleCodes.ADMIN);

    /** 部门/组别数据访问 */
    private final SysDeptMapper sysDeptMapper;

    /** 用户数据访问 */
    private final SysUserMapper sysUserMapper;

    /** 角色数据访问 */
    private final SysRoleMapper sysRoleMapper;

    /** 用户-角色关联数据访问 */
    private final SysUserRoleMapper sysUserRoleMapper;

    /**
     * 构造注入所有依赖项。
     *
     * @param sysDeptMapper      部门/组别数据访问
     * @param sysUserMapper      用户数据访问
     * @param sysRoleMapper      角色数据访问
     * @param sysUserRoleMapper  用户-角色关联数据访问
     */
    public OrgStructureService(
            SysDeptMapper sysDeptMapper,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    /**
     * 组织归属解析结果。
     *
     * @param effectiveDeptId 解析后的有效 dept_id（存入 sys_user.dept_id），可为 null
     * @param parentDeptId    所属部门 ID，可为 null
     * @param groupId         所属业务组 ID，可为 null
     */
    public record ResolvedAssignment(UUID effectiveDeptId, UUID parentDeptId, UUID groupId) {
    }

    /**
     * 组织归属拆分结果，用于前端展示和审计日志。
     *
     * @param parentDeptId   所属部门 ID，可为 null
     * @param groupId        所属业务组 ID，可为 null
     * @param parentDeptName 所属部门名称，可为 null
     * @param groupName      所属业务组名称，可为 null
     * @param groupType      组别类型编码（recruiter_group/channel_group/ops_group），可为 null
     */
    public record SplitAssignment(UUID parentDeptId, UUID groupId, String parentDeptName, String groupName, String groupType) {
    }

    /**
     * 解析前端传入的组织归属参数为有效的 dept_id。
     *
     * <p>处理流程：
     * <ol>
     *   <li>若传入 groupId，校验组别存在且类型为业务组</li>
     *   <li>若同时传入 parentDeptId，校验组别确实属于该部门</li>
     *   <li>以 groupId 作为 effectiveDeptId 返回</li>
     *   <li>若仅传入 parentDeptId，校验其为部门或运营组类型</li>
     *   <li>以 parentDeptId 作为 effectiveDeptId 返回</li>
     *   <li>均未传入时返回全 null 结果</li>
     * </ol>
     *
     * @param parentDeptId 前端传入的部门 ID，可为 null
     * @param groupId      前端传入的业务组 ID，可为 null
     * @return 解析后的组织归属结果
     * @throws BusinessException 组别类型不符、组别不属于部门或部门类型无效时抛出
     */
    public ResolvedAssignment resolveAssignment(UUID parentDeptId, UUID groupId) {
        if (groupId != null) {
            SysDept group = requireActiveDept(groupId);
            if (!DeptType.isGroup(group.getDeptType())) {
                throw BusinessException.param("所属组别必须为招商组/渠道组/运营组");
            }
            if (parentDeptId != null && !Objects.equals(group.getParentId(), parentDeptId)) {
                throw BusinessException.param("所选组别不属于当前部门");
            }
            UUID resolvedParent = group.getParentId() != null ? group.getParentId() : parentDeptId;
            return new ResolvedAssignment(groupId, resolvedParent, groupId);
        }
        if (parentDeptId != null) {
            SysDept dept = requireActiveDept(parentDeptId);
            if (!DeptType.isDepartment(dept.getDeptType()) && !DeptType.OPS_GROUP.equals(DeptType.normalize(dept.getDeptType()))) {
                throw BusinessException.param("所属部门类型无效");
            }
            return new ResolvedAssignment(parentDeptId, parentDeptId, null);
        }
        return new ResolvedAssignment(null, null, null);
    }

    /**
     * 将 effectiveDeptId 反向拆分为部门和组别信息。
     *
     * <p>处理流程：
     * <ol>
     *   <li>effectiveDeptId 为 null 时返回全 null 结果</li>
     *   <li>查询 sys_dept 记录，不存在或已删除时返回全 null 结果</li>
     *   <li>若节点类型为业务组，拆分出 parentDeptId = 父节点 ID，groupId = 当前节点 ID</li>
     *   <li>若节点类型为部门，parentDeptId = 当前节点 ID，groupId = null</li>
     * </ol>
     *
     * @param effectiveDeptId 用户 sys_user.dept_id 的值
     * @return 拆分后的部门和组别信息
     */
    public SplitAssignment splitAssignment(UUID effectiveDeptId) {
        if (effectiveDeptId == null) {
            return new SplitAssignment(null, null, null, null, null);
        }
        SysDept node = sysDeptMapper.selectById(effectiveDeptId);
        if (node == null || Objects.equals(node.getDeleted(), 1)) {
            return new SplitAssignment(null, null, null, null, null);
        }
        if (DeptType.isGroup(node.getDeptType())) {
            SysDept parent = node.getParentId() == null ? null : sysDeptMapper.selectById(node.getParentId());
            return new SplitAssignment(
                    node.getParentId(),
                    node.getId(),
                    parent == null ? null : parent.getDeptName(),
                    node.getDeptName(),
                    DeptType.normalize(node.getDeptType()));
        }
        return new SplitAssignment(
                node.getId(),
                null,
                node.getDeptName(),
                null,
                DeptType.normalize(node.getDeptType()));
    }

    /**
     * 批量为用户列表填充组织归属字段。
     *
     * <p>一次性加载全部部门/角色数据到内存 Map，然后遍历用户列表：
     * 根据 sys_user.dept_id 填充部门名称、组名、组类型等展示字段，
     * 并根据角色 ID 列表填充主角色编码和名称。
     *
     * @param users 需要填充组织字段的用户列表
     */
    public void enrichUserList(List<SysUserVO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Map<UUID, SysDept> deptMap = loadDeptMap();
        Map<UUID, SysRole> roleMap = loadRoleMap();
        for (SysUserVO user : users) {
            applyOrgFields(user, deptMap);
            applyPrimaryRole(user, roleMap);
        }
    }

    /**
     * 为单个用户填充组织归属字段（委托给 {@link #enrichUserList}）。
     *
     * @param user 需要填充组织字段的用户，可为 null
     * @return 填充后的用户对象，输入为 null 时返回 null
     */
    public SysUserVO enrichUser(SysUserVO user) {
        if (user == null) {
            return null;
        }
        enrichUserList(List.of(user));
        return user;
    }

    /**
     * 校验组长角色与组别类型的匹配关系。
     *
     * <p>处理流程：
     * <ol>
     *   <li>leaderUserId 为 null 时跳过校验</li>
     *   <li>查询用户是否存在且未被删除</li>
     *   <li>查询用户的所有角色编码</li>
     *   <li>根据组别类型确定允许的角色集合（招商组/渠道组/运营组各有不同）</li>
     *   <li>若用户角色不在允许集合中，抛出参数异常</li>
     *   <li>校验通过后返回负责人展示名，优先使用真实姓名，其次用户名</li>
     * </ol>
     *
     * @param leaderUserId 组长用户 ID，可为 null 表示不设组长
     * @param groupType    组别类型编码
     * @return 组长展示名；未设置组长时返回 null
     * @throws BusinessException 用户不存在或角色不匹配时抛出
     */
    public String validateGroupLeader(UUID leaderUserId, String groupType) {
        if (leaderUserId == null) {
            return null;
        }
        SysUser leader = sysUserMapper.selectById(leaderUserId);
        if (leader == null || Objects.equals(leader.getDeleted(), 1)) {
            throw BusinessException.param("组长必须是系统内有效用户");
        }
        Set<String> roleCodes = sysUserRoleMapper.findByUserId(leaderUserId).stream()
                .map(SysUserRole::getRoleId)
                .map(sysRoleMapper::selectById)
                .filter(Objects::nonNull)
                .map(SysRole::getRoleCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        String normalizedType = DeptType.normalize(groupType);
        Set<String> allowed = switch (normalizedType) {
            case DeptType.RECRUITER_GROUP -> RECRUITER_LEADER_ROLES;
            case DeptType.CHANNEL_GROUP -> CHANNEL_LEADER_ROLES;
            case DeptType.OPS_GROUP -> OPS_LEADER_ROLES;
            case DeptType.DEPARTMENT -> DEPARTMENT_LEADER_ROLES;
            default -> Set.of(RoleCodes.ADMIN);
        };
        if (roleCodes.stream().noneMatch(allowed::contains)) {
            throw BusinessException.param("组长角色与组别类型不匹配");
        }
        if (StringUtils.hasText(leader.getRealName())) {
            return leader.getRealName();
        }
        if (StringUtils.hasText(leader.getUsername())) {
            return leader.getUsername();
        }
        return leaderUserId.toString();
    }

    /**
     * 校验部门或组别是否可以安全删除。
     *
     * <p>检查两个前置条件：
     * <ol>
     *   <li>该部门/组别下没有直接关联的员工</li>
     *   <li>该部门/组别下没有子组别</li>
     * </ol>
     *
     * @param deptId 要删除的部门/组别 ID
     * @throws BusinessException 下仍有员工或子组别时抛出
     */
    public void assertCanDeleteDept(UUID deptId) {
        long directUsers = sysDeptMapper.countUsersByDeptId(deptId);
        if (directUsers > 0) {
            throw BusinessException.stateInvalid("部门或组别下仍有员工，无法删除");
        }
        long childGroups = sysDeptMapper.countChildGroups(deptId);
        if (childGroups > 0) {
            throw BusinessException.stateInvalid("请先删除下级组别");
        }
    }

    /**
     * 根据用户 dept_id 填充组织归属展示字段。
     *
     * <p>若 dept_id 指向业务组，则设置 groupId/groupName/groupType 及 parentDeptId/parentDeptName；
     * 若指向部门，则仅设置 parentDeptId/parentDeptName。
     *
     * @param user    需要填充的用户 VO
     * @param deptMap 部门/组别 ID 到实体的映射缓存
     */
    private void applyOrgFields(SysUserVO user, Map<UUID, SysDept> deptMap) {
        if (user.getDeptId() == null) {
            return;
        }
        SysDept assigned = deptMap.get(user.getDeptId());
        if (assigned == null) {
            return;
        }
        if (DeptType.isGroup(assigned.getDeptType())) {
            user.setGroupId(assigned.getId());
            user.setGroupName(assigned.getDeptName());
            user.setGroupType(DeptType.normalize(assigned.getDeptType()));
            if (assigned.getParentId() != null) {
                SysDept parent = deptMap.get(assigned.getParentId());
                user.setParentDeptId(assigned.getParentId());
                user.setParentDeptName(parent == null ? null : parent.getDeptName());
            }
        } else {
            user.setParentDeptId(assigned.getId());
            user.setParentDeptName(assigned.getDeptName());
            user.setGroupType(DeptType.normalize(assigned.getDeptType()));
        }
    }

    /**
     * 根据用户角色 ID 列表填充主角色展示字段。
     *
     * <p>取角色列表中的第一个角色作为主角色，设置 roleId/roleCode/roleName。
     * 角色列表为空时不做任何操作。
     *
     * @param user    需要填充的用户 VO
     * @param roleMap 角色 ID 到实体的映射缓存
     */
    private void applyPrimaryRole(SysUserVO user, Map<UUID, SysRole> roleMap) {
        List<UUID> roleIds = user.getRoleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        SysRole role = roleMap.get(roleIds.get(0));
        if (role != null) {
            user.setRoleId(role.getId());
            user.setRoleCode(role.getRoleCode());
            user.setRoleName(role.getRoleName());
        }
    }

    /**
     * 加载全部有效部门/组别数据到内存 Map。
     *
     * @return 部门 ID 到 SysDept 实体的映射
     */
    private Map<UUID, SysDept> loadDeptMap() {
        List<SysDept> depts = sysDeptMapper.findAllActive();
        Map<UUID, SysDept> map = new HashMap<>();
        for (SysDept dept : depts) {
            if (dept.getId() != null) {
                map.put(dept.getId(), dept);
            }
        }
        return map;
    }

    /**
     * 加载全部角色数据到内存 Map。
     *
     * @return 角色 ID 到 SysRole 实体的映射，查询结果为 null 时返回空 Map
     */
    private Map<UUID, SysRole> loadRoleMap() {
        List<SysRole> roles = sysRoleMapper.selectList(null);
        if (roles == null) {
            return Collections.emptyMap();
        }
        Map<UUID, SysRole> map = new HashMap<>();
        for (SysRole role : roles) {
            if (role.getId() != null) {
                map.put(role.getId(), role);
            }
        }
        return map;
    }

    /**
     * 查询并校验部门/组别存在且未被删除。
     *
     * @param id 部门/组别 ID
     * @return 有效的 SysDept 实体
     * @throws BusinessException 记录不存在或已删除时抛出
     */
    private SysDept requireActiveDept(UUID id) {
        SysDept dept = sysDeptMapper.selectById(id);
        if (dept == null || Objects.equals(dept.getDeleted(), 1)) {
            throw BusinessException.notFound("部门或组别不存在");
        }
        return dept;
    }

    /**
     * 生成组织变更的审计备注文本。
     *
     * <p>将变更前后的部门和组别信息格式化为结构化字符串，
     * 用于 OperationLog 的 remark 字段，便于后续审计追溯。
     *
     * @param userId           被变更的用户 ID
     * @param oldEffectiveDeptId 变更前的 effectiveDeptId
     * @param newEffectiveDeptId 变更后的 effectiveDeptId
     * @param operatorId       执行操作的管理员 ID
     * @return 格式化的审计备注文本
     */
    public String formatOrgChangeRemark(
            UUID userId,
            UUID oldEffectiveDeptId,
            UUID newEffectiveDeptId,
            UUID operatorId) {
        SplitAssignment oldSplit = splitAssignment(oldEffectiveDeptId);
        SplitAssignment newSplit = splitAssignment(newEffectiveDeptId);
        return String.format(
                "userId=%s operatorId=%s oldDeptId=%s oldGroupId=%s newDeptId=%s newGroupId=%s",
                userId,
                operatorId,
                oldSplit.parentDeptId(),
                oldSplit.groupId(),
                newSplit.parentDeptId(),
                newSplit.groupId());
    }
}
