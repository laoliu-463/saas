package com.colonel.saas.domain.user.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.domain.user.port.UserQueryLookup;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 通过现有 sys_user / sys_user_role 查询系统用户分页结果。
 */
@Component
public class SysUserQueryLookupAdapter implements UserQueryLookup {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public SysUserQueryLookupAdapter(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    public IPage<SysUserVO> findPage(
            long pageNo,
            long pageSize,
            SysUserPageRequest request,
            UserQueryFilter filter) {
        Page<SysUserVO> page = new Page<>(pageNo, pageSize);
        QueryWrapper<SysUser> wrapper = buildUserPageWrapper(request, filter);
        return sysUserMapper.findPage(page, request, wrapper);
    }

    @Override
    public Map<UUID, List<UUID>> findRoleIdsByUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinctUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctUserIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRole> relations = sysUserRoleMapper.findByUserIds(distinctUserIds);
        if (relations == null || relations.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UUID>> roleMap = new HashMap<>();
        for (SysUserRole relation : relations) {
            if (relation == null || relation.getUserId() == null || relation.getRoleId() == null) {
                continue;
            }
            roleMap.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(relation.getRoleId());
        }
        return roleMap;
    }

    QueryWrapper<SysUser> buildUserPageWrapper(SysUserPageRequest request, UserQueryFilter filter) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        if (request == null) {
            applyDataScopeFilter(wrapper, filter);
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
        applyDataScopeFilter(wrapper, filter);
        return wrapper;
    }

    void applyDataScopeFilter(QueryWrapper<SysUser> wrapper, UserQueryFilter filter) {
        if (filter == null) {
            return;
        }
        if (filter.userId() != null) {
            wrapper.apply("id = '" + filter.userId() + "'");
        } else if (filter.deptId() != null) {
            wrapper.apply("dept_id = '" + filter.deptId() + "'");
        }
    }

}
