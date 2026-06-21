package com.colonel.saas.domain.user.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 系统用户查询应用服务。
 *
 * <p>承接旧 {@code SysUserService.findPage/findDeptMembers} 读路径，保持分页筛选、
 * dataScope 显式注入、角色 ID 批量填充和组织展示补充语义不变。</p>
 */
@Service
public class SysUserQueryApplicationService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final OrgStructureService orgStructureService;
    private final DataScopePolicy dataScopePolicy;

    public SysUserQueryApplicationService(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            OrgStructureService orgStructureService,
            DataScopePolicy dataScopePolicy) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.orgStructureService = orgStructureService;
        this.dataScopePolicy = dataScopePolicy;
    }

    public IPage<SysUserVO> findPage(
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            SysUserPageRequest request) {
        long pageNo = request == null ? 1L : request.pageNo();
        long pageSize = request == null ? 10L : request.pageSize();
        Page<SysUserVO> page = new Page<>(pageNo, pageSize);
        QueryWrapper<SysUser> wrapper = buildUserPageWrapper(currentUserId, currentDeptId, dataScope, request);
        IPage<SysUserVO> result = sysUserMapper.findPage(page, request, wrapper);
        fillRoleIds(result.getRecords());
        orgStructureService.enrichUserList(result.getRecords());
        return result;
    }

    public IPage<SysUserVO> findPage(
            UUID currentUserId,
            DataScope dataScope,
            SysUserPageRequest request) {
        return findPage(currentUserId, null, dataScope, request);
    }

    QueryWrapper<SysUser> buildUserPageWrapper(
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            SysUserPageRequest request) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        if (request == null) {
            applyDataScopeFilter(wrapper, currentUserId, currentDeptId, dataScope);
            return wrapper;
        }
        if (request.keyword() != null && !request.keyword().isBlank()) {
            String safe = request.keyword().trim();
            wrapper.and(q -> q.like("username", safe)
                    .or().like("real_name", safe));
        }
        if (request.status() != null) {
            wrapper.eq("status", request.status());
        }
        if (request.groupId() != null) {
            wrapper.eq("dept_id", request.groupId());
        } else if (request.deptId() != null) {
            UUID parentDeptId = request.deptId();
            wrapper.and(q -> q.eq("dept_id", parentDeptId)
                    .or().inSql("dept_id",
                            "SELECT id FROM sys_dept WHERE deleted = 0 AND parent_id = '" + parentDeptId + "'"));
        }
        if (request.roleId() != null) {
            wrapper.exists("SELECT 1 FROM sys_user_role sur WHERE sur.user_id = su.id AND sur.role_id = {0}",
                    request.roleId());
        } else if (request.roleCode() != null && !request.roleCode().isBlank()) {
            String code = request.roleCode().trim();
            wrapper.exists(
                    "SELECT 1 FROM sys_user_role sur INNER JOIN sys_role sr ON sr.id = sur.role_id"
                            + " AND sr.deleted = 0 WHERE sur.user_id = su.id AND sr.role_code = {0}",
                    code);
        }
        applyDataScopeFilter(wrapper, currentUserId, currentDeptId, dataScope);
        return wrapper;
    }

    QueryWrapper<SysUser> buildUserPageWrapper(
            UUID currentUserId,
            DataScope dataScope,
            SysUserPageRequest request) {
        return buildUserPageWrapper(currentUserId, null, dataScope, request);
    }

    void applyDataScopeFilter(
            QueryWrapper<SysUser> wrapper,
            UUID currentUserId,
            DataScope dataScope) {
        applyDataScopeFilter(wrapper, currentUserId, null, dataScope);
    }

    void applyDataScopeFilter(
            QueryWrapper<SysUser> wrapper,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope) {
        DataScopePolicy.Decision decision = dataScopePolicy.decide(currentUserId, currentDeptId, dataScope);
        switch (decision) {
            case FILTER_USER -> wrapper.apply("id = '" + currentUserId + "'");
            case FILTER_DEPT -> wrapper.apply("dept_id = '" + currentDeptId + "'");
            case NO_FILTER -> {
                // no-op
            }
        }
    }

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

    private void fillRoleIds(List<SysUserVO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<UUID> userIds = users.stream()
                .map(SysUserVO::getId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Map<UUID, List<UUID>> roleMap = new HashMap<>();
        for (SysUserRole relation : sysUserRoleMapper.findByUserIds(userIds)) {
            roleMap.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(relation.getRoleId());
        }
        for (SysUserVO user : users) {
            user.setRoleIds(roleMap.getOrDefault(user.getId(), Collections.emptyList()));
        }
    }
}
