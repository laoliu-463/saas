package com.colonel.saas.domain.user.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.auth.support.AuthTestFixtures;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.port.UserQueryLookup;
import com.colonel.saas.domain.user.port.UserQueryLookup.UserQueryFilter;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserQueryApplicationServiceTest {

    @Mock private UserQueryLookup userQueryLookup;
    @Mock private OrgStructureService orgStructureService;

    private SysUserQueryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SysUserQueryApplicationService(
                userQueryLookup,
                orgStructureService,
                new DataScopePolicy());
    }

    @Test
    void findPage_allScope_shouldDelegateWithoutDataScopeFilter() {
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, "招商", 1, null);
        Page<SysUserVO> page = pageWithUser(UUID.randomUUID());
        when(userQueryLookup.findPage(eq(1L), eq(10L), eq(request), any(UserQueryFilter.class)))
                .thenReturn(page);
        when(userQueryLookup.findRoleIdsByUserIds(any())).thenReturn(Map.of());

        IPage<SysUserVO> result = service.findPage(UUID.randomUUID(), DataScope.ALL, request);

        assertThat(result.getTotal()).isEqualTo(1L);
        ArgumentCaptor<UserQueryFilter> filterCaptor = ArgumentCaptor.forClass(UserQueryFilter.class);
        verify(userQueryLookup).findPage(eq(1L), eq(10L), eq(request), filterCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(UserQueryFilter.none());
        verify(orgStructureService).enrichUserList(result.getRecords());
    }

    @Test
    void findPage_personalScope_shouldPassUserFilter() {
        UUID currentUserId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, null);
        Page<SysUserVO> page = emptyPage(1L, 10L);
        when(userQueryLookup.findPage(eq(1L), eq(10L), eq(request), any(UserQueryFilter.class)))
                .thenReturn(page);

        service.findPage(currentUserId, DataScope.PERSONAL, request);

        ArgumentCaptor<UserQueryFilter> filterCaptor = ArgumentCaptor.forClass(UserQueryFilter.class);
        verify(userQueryLookup).findPage(eq(1L), eq(10L), eq(request), filterCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(UserQueryFilter.user(currentUserId));
    }

    @Test
    void findPage_deptScope_shouldPassDeptFilter() {
        UUID currentUserId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, null);
        Page<SysUserVO> page = emptyPage(1L, 10L);
        when(userQueryLookup.findPage(eq(1L), eq(10L), eq(request), any(UserQueryFilter.class)))
                .thenReturn(page);

        service.findPage(currentUserId, currentDeptId, DataScope.DEPT, request);

        ArgumentCaptor<UserQueryFilter> filterCaptor = ArgumentCaptor.forClass(UserQueryFilter.class);
        verify(userQueryLookup).findPage(eq(1L), eq(10L), eq(request), filterCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(UserQueryFilter.dept(currentDeptId));
    }

    @Test
    void findPage_shouldFillRoleIdsAndEnrichOrgStructure() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, null);
        Page<SysUserVO> page = pageWithUser(userId);
        when(userQueryLookup.findPage(eq(1L), eq(10L), eq(request), any(UserQueryFilter.class)))
                .thenReturn(page);
        when(userQueryLookup.findRoleIdsByUserIds(List.of(userId))).thenReturn(Map.of(userId, List.of(roleId)));

        IPage<SysUserVO> result = service.findPage(UUID.randomUUID(), DataScope.ALL, request);

        assertThat(result.getRecords().get(0).getRoleIds()).containsExactly(roleId);
        verify(userQueryLookup).findRoleIdsByUserIds(List.of(userId));
        verify(orgStructureService).enrichUserList(result.getRecords());
    }

    @Test
    void findPage_withNullRequest_shouldUseDefaultPageAndOnlyApplyDataScope() {
        UUID currentUserId = UUID.randomUUID();
        Page<SysUserVO> page = emptyPage(1L, 10L);
        when(userQueryLookup.findPage(eq(1L), eq(10L), eq(null), any(UserQueryFilter.class)))
                .thenReturn(page);

        service.findPage(currentUserId, DataScope.PERSONAL, null);

        ArgumentCaptor<UserQueryFilter> filterCaptor = ArgumentCaptor.forClass(UserQueryFilter.class);
        verify(userQueryLookup).findPage(eq(1L), eq(10L), eq(null), filterCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(UserQueryFilter.user(currentUserId));
    }

    @Test
    void findPage_shouldSkipRoleLookupWhenPageHasNoUsers() {
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, null);
        Page<SysUserVO> page = emptyPage(1L, 10L);
        when(userQueryLookup.findPage(eq(1L), eq(10L), eq(request), any(UserQueryFilter.class)))
                .thenReturn(page);

        service.findPage(UUID.randomUUID(), DataScope.ALL, request);

        verify(userQueryLookup, never()).findRoleIdsByUserIds(any());
        verify(orgStructureService).enrichUserList(page.getRecords());
    }

    @Test
    void findDeptMembers_shouldAdaptDeptRequestToUserPageQuery() {
        UUID deptId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        DeptMemberPageRequest request = new DeptMemberPageRequest(
                2, 30, "组员", 1, groupId, roleId, "biz_staff");
        Page<SysUserVO> page = emptyPage(2L, 30L);
        when(userQueryLookup.findPage(eq(2L), eq(30L), any(SysUserPageRequest.class), any(UserQueryFilter.class)))
                .thenReturn(page);

        service.findDeptMembers(deptId, request);

        ArgumentCaptor<SysUserPageRequest> requestCaptor = ArgumentCaptor.forClass(SysUserPageRequest.class);
        ArgumentCaptor<UserQueryFilter> filterCaptor = ArgumentCaptor.forClass(UserQueryFilter.class);
        verify(userQueryLookup).findPage(eq(2L), eq(30L), requestCaptor.capture(), filterCaptor.capture());
        SysUserPageRequest adapted = requestCaptor.getValue();
        assertThat(adapted.pageNo()).isEqualTo(2);
        assertThat(adapted.pageSize()).isEqualTo(30);
        assertThat(adapted.keyword()).isEqualTo("组员");
        assertThat(adapted.status()).isEqualTo(1);
        assertThat(adapted.deptId()).isEqualTo(deptId);
        assertThat(adapted.groupId()).isEqualTo(groupId);
        assertThat(adapted.roleId()).isEqualTo(roleId);
        assertThat(adapted.roleCode()).isEqualTo("biz_staff");
        assertThat(filterCaptor.getValue()).isEqualTo(UserQueryFilter.none());
    }

    private static Page<SysUserVO> pageWithUser(UUID userId) {
        SysUserVO vo = new SysUserVO();
        vo.setId(userId);
        Page<SysUserVO> page = new Page<>(1L, 10L);
        page.setTotal(1L);
        page.setRecords(List.of(vo));
        return page;
    }

    private static Page<SysUserVO> emptyPage(long pageNo, long pageSize) {
        Page<SysUserVO> page = new Page<>(pageNo, pageSize);
        page.setTotal(0L);
        page.setRecords(List.of());
        return page;
    }
}
