package com.colonel.saas.domain.user.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.support.AuthTestFixtures;
import com.colonel.saas.domain.user.port.UserQueryLookup.UserQueryFilter;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserQueryLookupAdapterTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysUserRoleMapper sysUserRoleMapper;

    private SysUserQueryLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SysUserQueryLookupAdapter(sysUserMapper, sysUserRoleMapper);
    }

    @Test
    void buildUserPageWrapper_shouldAppendKeywordLikeOnUsernameOrRealName() {
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, "招商", null, null);

        QueryWrapper<SysUser> wrapper = adapter.buildUserPageWrapper(request, UserQueryFilter.none());
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("username LIKE").contains("real_name LIKE").contains("OR");
        assertParameter(wrapper, "%招商%");
    }

    @Test
    void buildUserPageWrapper_shouldAppendStatusEq() {
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, 0, null);

        QueryWrapper<SysUser> wrapper = adapter.buildUserPageWrapper(request, UserQueryFilter.none());

        assertThat(wrapper.getSqlSegment()).contains("status =");
        assertParameter(wrapper, 0);
    }

    @Test
    void buildUserPageWrapper_shouldPreferGroupIdOverDeptId() {
        UUID groupId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        SysUserPageRequest request = new SysUserPageRequest(1, 10, null, null, deptId, groupId, null, null);

        QueryWrapper<SysUser> wrapper = adapter.buildUserPageWrapper(request, UserQueryFilter.none());
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("dept_id =").doesNotContain("SELECT id FROM sys_dept");
        assertParameter(wrapper, groupId);
    }

    @Test
    void buildUserPageWrapper_shouldExpandDeptIdToSubqueryWhenGroupIdMissing() {
        UUID deptId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, deptId);

        QueryWrapper<SysUser> wrapper = adapter.buildUserPageWrapper(request, UserQueryFilter.none());
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("dept_id =");
        assertParameter(wrapper, deptId);
        assertThat(sql).contains("SELECT id FROM sys_dept WHERE deleted = 0 AND parent_id = " + uuidLiteral(deptId));
    }

    @Test
    void buildUserPageWrapper_shouldAppendExistsForRoleId() {
        UUID roleId = UUID.randomUUID();
        SysUserPageRequest request = new SysUserPageRequest(1, 10, null, null, null, null, roleId, null);

        QueryWrapper<SysUser> wrapper = adapter.buildUserPageWrapper(request, UserQueryFilter.none());
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("EXISTS (SELECT 1 FROM sys_user_role sur").contains("sur.role_id =");
        assertParameter(wrapper, roleId);
    }

    @Test
    void buildUserPageWrapper_shouldAppendExistsJoinForRoleCode() {
        SysUserPageRequest request = new SysUserPageRequest(1, 10, null, null, null, null, null, "biz_staff");

        QueryWrapper<SysUser> wrapper = adapter.buildUserPageWrapper(request, UserQueryFilter.none());
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("INNER JOIN sys_role sr").contains("sr.role_code =");
        assertParameter(wrapper, "biz_staff");
    }

    @Test
    void buildUserPageWrapper_shouldSkipBlankKeywordWithoutProducingLike() {
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, "   ", null, null);

        QueryWrapper<SysUser> wrapper = adapter.buildUserPageWrapper(request, UserQueryFilter.none());

        assertThat(wrapper.getSqlSegment()).doesNotContain("LIKE");
    }

    @Test
    void applyDataScopeFilter_personal_shouldAppendIdEqUserId() {
        UUID userId = UUID.randomUUID();
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();

        adapter.applyDataScopeFilter(wrapper, UserQueryFilter.user(userId));

        assertThat(wrapper.getSqlSegment()).contains("id = " + uuidLiteral(userId));
    }

    @Test
    void applyDataScopeFilter_dept_shouldAppendDeptIdEq() {
        UUID deptId = UUID.randomUUID();
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();

        adapter.applyDataScopeFilter(wrapper, UserQueryFilter.dept(deptId));

        assertThat(wrapper.getSqlSegment()).contains("dept_id = " + uuidLiteral(deptId));
    }

    @Test
    void applyDataScopeFilter_none_shouldNotAppendAnyCondition() {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();

        adapter.applyDataScopeFilter(wrapper, UserQueryFilter.none());

        assertThat(wrapper.getSqlSegment()).isNullOrEmpty();
    }

    @Test
    void findPage_shouldBuildWrapperAndDelegateToMapper() {
        UUID currentUserId = UUID.randomUUID();
        SysUserPageRequest request = new SysUserPageRequest(
                1, 10, "招商", 1, null, null, null, "biz_staff");
        SysUserVO vo = new SysUserVO();
        vo.setId(UUID.randomUUID());
        Page<SysUserVO> mapperPage = new Page<>(1L, 10L);
        mapperPage.setTotal(1L);
        mapperPage.setRecords(List.of(vo));
        when(sysUserMapper.findPage(any(Page.class), any(SysUserPageRequest.class), any(QueryWrapper.class)))
                .thenReturn(mapperPage);

        IPage<SysUserVO> result = adapter.findPage(1L, 10L, request, UserQueryFilter.user(currentUserId));

        assertThat(result.getTotal()).isEqualTo(1L);
        ArgumentCaptor<QueryWrapper<SysUser>> captor = queryCaptor();
        verify(sysUserMapper).findPage(any(Page.class), eq(request), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("LIKE").contains("status =").contains("sr.role_code =");
        assertThat(sql).contains("id = " + uuidLiteral(currentUserId));
        assertParameter(captor.getValue(), "%招商%");
        assertParameter(captor.getValue(), 1);
        assertParameter(captor.getValue(), "biz_staff");
    }

    @Test
    void findPage_withNullRequest_shouldOnlyApplyDataScope() {
        UUID currentUserId = UUID.randomUUID();
        Page<SysUserVO> mapperPage = new Page<>(1L, 10L);
        mapperPage.setTotal(0L);
        mapperPage.setRecords(List.of());
        when(sysUserMapper.findPage(any(Page.class), eq(null), any(QueryWrapper.class)))
                .thenReturn(mapperPage);

        adapter.findPage(1L, 10L, null, UserQueryFilter.user(currentUserId));

        ArgumentCaptor<QueryWrapper<SysUser>> captor = queryCaptor();
        verify(sysUserMapper).findPage(any(Page.class), eq(null), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).doesNotContain("LIKE").contains("id = " + uuidLiteral(currentUserId));
    }

    @Test
    void findRoleIdsByUserIds_shouldGroupRolesByUser() {
        UUID userId = UUID.randomUUID();
        UUID roleA = UUID.randomUUID();
        UUID roleB = UUID.randomUUID();
        SysUserRole relationA = relation(userId, roleA);
        SysUserRole relationB = relation(userId, roleB);
        when(sysUserRoleMapper.findByUserIds(List.of(userId))).thenReturn(List.of(relationA, relationB));

        Map<UUID, List<UUID>> result = adapter.findRoleIdsByUserIds(Arrays.asList(userId, userId, null));

        assertThat(result).containsEntry(userId, List.of(roleA, roleB));
        verify(sysUserRoleMapper).findByUserIds(List.of(userId));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<QueryWrapper<SysUser>> queryCaptor() {
        return ArgumentCaptor.forClass(QueryWrapper.class);
    }

    private static void assertParameter(QueryWrapper<SysUser> wrapper, Object value) {
        assertThat(wrapper.getParamNameValuePairs().values()).contains(value);
    }

    private static String uuidLiteral(UUID value) {
        return "'" + value + "'";
    }

    private static SysUserRole relation(UUID userId, UUID roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }
}
