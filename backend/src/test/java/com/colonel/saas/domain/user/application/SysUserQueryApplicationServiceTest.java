package com.colonel.saas.domain.user.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.auth.support.AuthTestFixtures;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserQueryApplicationServiceTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysUserRoleMapper sysUserRoleMapper;
    @Mock private OrgStructureService orgStructureService;

    private SysUserQueryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SysUserQueryApplicationService(
                sysUserMapper,
                sysUserRoleMapper,
                orgStructureService,
                new DataScopePolicy());
    }

    @Test
    void buildUserPageWrapper_shouldAppendKeywordLikeOnUsernameOrRealName() {
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, "招商", null, null);

        QueryWrapper<SysUser> wrapper = service.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("username LIKE").contains("real_name LIKE").contains("OR");
        assertParameter(wrapper, "%招商%");
    }

    @Test
    void buildUserPageWrapper_shouldAppendStatusEq() {
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, 0, null);

        QueryWrapper<SysUser> wrapper = service.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("status =");
        assertParameter(wrapper, 0);
    }

    @Test
    void buildUserPageWrapper_shouldPreferGroupIdOverDeptId() {
        UUID groupId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        SysUserPageRequest request = new SysUserPageRequest(1, 10, null, null, deptId, groupId, null, null);

        QueryWrapper<SysUser> wrapper = service.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("dept_id =").doesNotContain("SELECT id FROM sys_dept");
        assertParameter(wrapper, groupId);
    }

    @Test
    void buildUserPageWrapper_shouldExpandDeptIdToSubqueryWhenGroupIdMissing() {
        UUID deptId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, deptId);

        QueryWrapper<SysUser> wrapper = service.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("dept_id =");
        assertParameter(wrapper, deptId);
        assertThat(sql).contains("SELECT id FROM sys_dept WHERE deleted = 0 AND parent_id = " + uuidLiteral(deptId));
    }

    @Test
    void buildUserPageWrapper_shouldAppendExistsForRoleId() {
        UUID roleId = UUID.randomUUID();
        SysUserPageRequest request = new SysUserPageRequest(1, 10, null, null, null, null, roleId, null);

        QueryWrapper<SysUser> wrapper = service.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("EXISTS (SELECT 1 FROM sys_user_role sur").contains("sur.role_id =");
        assertParameter(wrapper, roleId);
    }

    @Test
    void buildUserPageWrapper_shouldAppendExistsJoinForRoleCode() {
        SysUserPageRequest request = new SysUserPageRequest(1, 10, null, null, null, null, null, "biz_staff");

        QueryWrapper<SysUser> wrapper = service.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("INNER JOIN sys_role sr").contains("sr.role_code =");
        assertParameter(wrapper, "biz_staff");
    }

    @Test
    void buildUserPageWrapper_shouldSkipBlankKeywordWithoutProducingLike() {
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, "   ", null, null);

        QueryWrapper<SysUser> wrapper = service.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);

        assertThat(wrapper.getSqlSegment()).doesNotContain("LIKE");
    }

    @Test
    void applyDataScopeFilter_personal_shouldAppendIdEqUserId() {
        UUID userId = UUID.randomUUID();
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();

        service.applyDataScopeFilter(wrapper, userId, null, DataScope.PERSONAL);

        assertThat(wrapper.getSqlSegment()).contains("id = " + uuidLiteral(userId));
    }

    @Test
    void applyDataScopeFilter_personal_shouldNotAppendWhenUserIdMissing() {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();

        service.applyDataScopeFilter(wrapper, null, null, DataScope.PERSONAL);

        assertThat(wrapper.getSqlSegment()).isNullOrEmpty();
    }

    @Test
    void applyDataScopeFilter_dept_shouldAppendDeptIdEq() {
        UUID deptId = UUID.randomUUID();
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();

        service.applyDataScopeFilter(wrapper, UUID.randomUUID(), deptId, DataScope.DEPT);

        assertThat(wrapper.getSqlSegment()).contains("dept_id = " + uuidLiteral(deptId));
    }

    @Test
    void applyDataScopeFilter_dept_shouldNotAppendWhenDeptIdMissing() {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();

        service.applyDataScopeFilter(wrapper, UUID.randomUUID(), null, DataScope.DEPT);

        assertThat(wrapper.getSqlSegment()).isNullOrEmpty();
    }

    @Test
    void applyDataScopeFilter_all_shouldNotAppendAnyCondition() {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();

        service.applyDataScopeFilter(wrapper, UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);

        assertThat(wrapper.getSqlSegment()).isNullOrEmpty();
    }

    @Test
    void applyDataScopeFilter_nullScope_shouldBehaveLikeAll() {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();

        service.applyDataScopeFilter(wrapper, UUID.randomUUID(), UUID.randomUUID(), null);

        assertThat(wrapper.getSqlSegment()).isNullOrEmpty();
    }

    @Test
    void buildUserPageWrapper_personalScope_shouldInjectIdEqInSql() {
        UUID currentUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, null);

        QueryWrapper<SysUser> wrapper = service.buildUserPageWrapper(
                currentUserId, DataScope.PERSONAL, request);
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("id = " + uuidLiteral(currentUserId));
        assertThat(sql).doesNotContain("id = " + uuidLiteral(targetUserId));
    }

    @Test
    void buildUserPageWrapper_deptScope_shouldInjectDeptIdEqInSql() {
        UUID currentUserId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, null);

        QueryWrapper<SysUser> wrapper = service.buildUserPageWrapper(
                currentUserId, currentDeptId, DataScope.DEPT, request);

        assertThat(wrapper.getSqlSegment()).contains("dept_id = " + uuidLiteral(currentDeptId));
    }

    @Test
    void findPage_shouldBuildWrapperWithDataScopeAndDelegateToMapper() {
        UUID currentUserId = UUID.randomUUID();
        SysUserPageRequest request = new SysUserPageRequest(
                1, 10, "招商", 1, null, null, null, "biz_staff");
        SysUserVO vo = new SysUserVO();
        vo.setId(UUID.randomUUID());
        vo.setUsername("bizstaff");
        Page<SysUserVO> mapperPage = new Page<>(1L, 10L);
        mapperPage.setTotal(1L);
        mapperPage.setRecords(List.of(vo));
        when(sysUserMapper.findPage(any(Page.class), any(SysUserPageRequest.class), any(QueryWrapper.class)))
                .thenReturn(mapperPage);
        when(sysUserRoleMapper.findByUserIds(any())).thenReturn(List.of());

        IPage<SysUserVO> result = service.findPage(currentUserId, DataScope.ALL, request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        ArgumentCaptor<QueryWrapper<SysUser>> captor = queryCaptor();
        verify(sysUserMapper).findPage(any(Page.class), eq(request), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("LIKE").contains("status =").contains("sr.role_code =");
        assertParameter(captor.getValue(), "%招商%");
        assertParameter(captor.getValue(), 1);
        assertParameter(captor.getValue(), "biz_staff");
        assertThat(sql).doesNotContain("id = " + currentUserId);
        assertThat(sql).doesNotContain("dept_id = ");
    }

    @Test
    void findPage_personalScope_shouldInjectSelfIdFilter() {
        UUID currentUserId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, null);
        Page<SysUserVO> mapperPage = new Page<>(1L, 10L);
        mapperPage.setTotal(0L);
        mapperPage.setRecords(List.of());
        when(sysUserMapper.findPage(any(Page.class), any(SysUserPageRequest.class), any(QueryWrapper.class)))
                .thenReturn(mapperPage);

        service.findPage(currentUserId, DataScope.PERSONAL, request);

        ArgumentCaptor<QueryWrapper<SysUser>> captor = queryCaptor();
        verify(sysUserMapper).findPage(any(Page.class), any(SysUserPageRequest.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("id = " + uuidLiteral(currentUserId));
    }

    @Test
    void findPage_deptScope_shouldInjectDeptIdFilter() {
        UUID currentUserId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, null);
        Page<SysUserVO> mapperPage = new Page<>(1L, 10L);
        mapperPage.setTotal(0L);
        mapperPage.setRecords(List.of());
        when(sysUserMapper.findPage(any(Page.class), any(SysUserPageRequest.class), any(QueryWrapper.class)))
                .thenReturn(mapperPage);

        service.findPage(currentUserId, currentDeptId, DataScope.DEPT, request);

        ArgumentCaptor<QueryWrapper<SysUser>> captor = queryCaptor();
        verify(sysUserMapper).findPage(any(Page.class), any(SysUserPageRequest.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("dept_id = " + uuidLiteral(currentDeptId));
    }

    @Test
    void findPage_shouldFillRoleIdsAndEnrichOrgStructure() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, null);
        SysUserVO vo = new SysUserVO();
        vo.setId(userId);
        Page<SysUserVO> mapperPage = new Page<>(1L, 10L);
        mapperPage.setTotal(1L);
        mapperPage.setRecords(List.of(vo));
        when(sysUserMapper.findPage(any(Page.class), any(SysUserPageRequest.class), any(QueryWrapper.class)))
                .thenReturn(mapperPage);
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        when(sysUserRoleMapper.findByUserIds(any())).thenReturn(List.of(relation));

        service.findPage(UUID.randomUUID(), DataScope.ALL, request);

        verify(sysUserRoleMapper).findByUserIds(any());
        verify(orgStructureService).enrichUserList(any());
    }

    @Test
    void findPage_withNullRequest_shouldOnlyApplyDataScope() {
        UUID currentUserId = UUID.randomUUID();
        Page<SysUserVO> mapperPage = new Page<>(1L, 10L);
        mapperPage.setTotal(0L);
        mapperPage.setRecords(List.of());
        when(sysUserMapper.findPage(any(Page.class), any(), any(QueryWrapper.class)))
                .thenReturn(mapperPage);

        service.findPage(currentUserId, DataScope.PERSONAL, null);

        ArgumentCaptor<QueryWrapper<SysUser>> captor = queryCaptor();
        verify(sysUserMapper).findPage(any(Page.class), any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).doesNotContain("LIKE").contains("id = " + uuidLiteral(currentUserId));
    }

    @Test
    void findDeptMembers_shouldAdaptDeptRequestToUserPageQuery() {
        UUID deptId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        DeptMemberPageRequest request = new DeptMemberPageRequest(
                2, 30, "组员", 1, groupId, roleId, "biz_staff");
        Page<SysUserVO> mapperPage = new Page<>(2L, 30L);
        mapperPage.setTotal(0L);
        mapperPage.setRecords(List.of());
        when(sysUserMapper.findPage(any(Page.class), any(SysUserPageRequest.class), any(QueryWrapper.class)))
                .thenReturn(mapperPage);

        service.findDeptMembers(deptId, request);

        ArgumentCaptor<SysUserPageRequest> requestCaptor = ArgumentCaptor.forClass(SysUserPageRequest.class);
        verify(sysUserMapper).findPage(any(Page.class), requestCaptor.capture(), any(QueryWrapper.class));
        SysUserPageRequest adapted = requestCaptor.getValue();
        assertThat(adapted.pageNo()).isEqualTo(2);
        assertThat(adapted.pageSize()).isEqualTo(30);
        assertThat(adapted.keyword()).isEqualTo("组员");
        assertThat(adapted.status()).isEqualTo(1);
        assertThat(adapted.deptId()).isEqualTo(deptId);
        assertThat(adapted.groupId()).isEqualTo(groupId);
        assertThat(adapted.roleId()).isEqualTo(roleId);
        assertThat(adapted.roleCode()).isEqualTo("biz_staff");
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<QueryWrapper<SysUser>> queryCaptor() {
        return ArgumentCaptor.forClass(QueryWrapper.class);
    }

    private static String uuidLiteral(UUID value) {
        return "'" + value + "'";
    }

    private static void assertParameter(QueryWrapper<SysUser> wrapper, Object expected) {
        assertThat(wrapper.getParamNameValuePairs().values())
                .as("QueryWrapper parameter values")
                .contains(expected);
    }
}
