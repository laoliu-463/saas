package com.colonel.saas.domain.user.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.port.UserQueryLookup;
import com.colonel.saas.domain.user.port.UserQueryLookup.UserQueryFilter;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
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

    private final UserQueryLookup userQueryLookup;
    private final OrgStructureService orgStructureService;
    private final DataScopePolicy dataScopePolicy;

    public SysUserQueryApplicationService(
            UserQueryLookup userQueryLookup,
            OrgStructureService orgStructureService,
            DataScopePolicy dataScopePolicy) {
        this.userQueryLookup = userQueryLookup;
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
        UserQueryFilter filter = resolveFilter(currentUserId, currentDeptId, dataScope);
        IPage<SysUserVO> result = userQueryLookup.findPage(pageNo, pageSize, request, filter);
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

    private UserQueryFilter resolveFilter(UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        DataScopePolicy.Decision decision = dataScopePolicy.decide(currentUserId, currentDeptId, dataScope);
        return switch (decision) {
            case FILTER_USER -> UserQueryFilter.user(currentUserId);
            case FILTER_DEPT -> UserQueryFilter.dept(currentDeptId);
            case NO_FILTER -> UserQueryFilter.none();
        };
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
        Map<UUID, List<UUID>> roleMap = userQueryLookup.findRoleIdsByUserIds(userIds);
        for (SysUserVO user : users) {
            user.setRoleIds(roleMap.getOrDefault(user.getId(), Collections.emptyList()));
        }
    }
}
