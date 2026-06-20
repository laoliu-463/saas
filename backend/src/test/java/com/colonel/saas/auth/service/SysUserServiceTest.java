package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.support.AuthTestFixtures;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.application.SysUserCRUDApplicationA;
import com.colonel.saas.domain.user.application.SysUserCRUDApplicationB;
import com.colonel.saas.domain.user.application.SysUserGroupMembershipApplication;
import com.colonel.saas.domain.user.application.SysUserRoleAssignmentApplicationService;
import com.colonel.saas.domain.user.application.UserAssignableApplicationService;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.policy.UserAssignmentPolicy;
import com.colonel.saas.domain.user.policy.UserChannelCodePolicy;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.service.UserPermissionCacheService;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统用户管理服务单元测试（t7-system 重构）。
 *
 * <p>覆盖 4 大场景：</p>
 * <ul>
 *   <li><b>QueryWrapper 组装</b>：keyword/status/deptId/groupId/roleId/roleCode 全部正确拼接到 SQL 段</li>
 *   <li><b>dataScope 行级权限（CLAUDE.md 不变量）</b>：PERSONAL/DEPT/ALL 三档均翻译为 {@link QueryWrapper} 条件；
 *       缺 userId/deptId 时不追加（与 AOP 行为对齐）</li>
 *   <li><b>findPage 端到端</b>：page/pageSize/keyword/status 透传到 mapper，enrichUserList 正确触发，
 *       roleIds 批量填充</li>
 *   <li><b>边界</b>：空 keyword、null status、null deptId 不产生无效 SQL 段</li>
 * </ul>
 *
 * <p>本次重构核心断言：findPage 透传到 mapper 的 QueryWrapper 必须包含 dataScope 条件（CLAUDE.md 不变量要求）。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;
    @Mock
    private OrgStructureService orgStructureService;
    @Mock
    private UserPermissionCacheService userPermissionCacheService;

    private SysUserService sysUserService;

    @BeforeEach
    void setUp() {
        UserAccessPolicy userAccessPolicy = new UserAccessPolicy(userId -> java.util.Optional.ofNullable(sysUserMapper.selectById(userId))
                .map(SysUser::getDeptId));
        UserChannelCodePolicy userChannelCodePolicy = new UserChannelCodePolicy(sysUserMapper::existsByChannelCodeIncludingDeleted);
        SysUserCRUDApplicationA applicationA = new SysUserCRUDApplicationA(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                passwordEncoder,
                operationLogService,
                userDomainEventPublisher,
                orgStructureService,
                userAccessPolicy,
                userChannelCodePolicy);
        SysUserCRUDApplicationB applicationB = new SysUserCRUDApplicationB(
                sysUserMapper,
                sysUserRoleMapper,
                passwordEncoder,
                operationLogService,
                userDomainEventPublisher,
                userPermissionCacheService,
                orgStructureService,
                userAccessPolicy);
        SysUserGroupMembershipApplication groupMembershipApplication = new SysUserGroupMembershipApplication(
                sysUserMapper,
                operationLogService,
                userDomainEventPublisher,
                userPermissionCacheService,
                orgStructureService);
        UserAssignmentPolicy userAssignmentPolicy = new UserAssignmentPolicy(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper);
        UserAssignableApplicationService userAssignableApplicationService = new UserAssignableApplicationService(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                userAssignmentPolicy);
        SysUserRoleAssignmentApplicationService roleAssignmentApplicationService =
                new SysUserRoleAssignmentApplicationService(
                        sysUserMapper,
                        sysRoleMapper,
                        sysUserRoleMapper,
                        operationLogService,
                        userPermissionCacheService,
                        userAccessPolicy);
        sysUserService = new SysUserService(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                passwordEncoder,
                operationLogService,
                userDomainEventPublisher,
                orgStructureService,
                userPermissionCacheService,
                new DataScopePolicy(),
                userAccessPolicy,
                userChannelCodePolicy,
                applicationA,
                applicationB,
                groupMembershipApplication,
                userAssignableApplicationService,
                roleAssignmentApplicationService
        );
    }

    // ========================== QueryWrapper 组装 ==========================

    @Test
    void buildUserPageWrapper_shouldAppendKeywordLikeOnUsernameOrRealName() {
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, "招商", null, null);

        QueryWrapper<SysUser> wrapper = sysUserService.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        // keyword 模糊匹配 username + real_name，参数必须由 MyBatis-Plus 绑定
        assertThat(sql).contains("username LIKE").contains("real_name LIKE");
        assertParameter(wrapper, "%招商%");
        // 关键：不会把 dataScope 注入到 keyword
        assertThat(sql).contains("OR");
    }

    @Test
    void buildUserPageWrapper_shouldAppendStatusEq() {
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, 0, null);

        QueryWrapper<SysUser> wrapper = sysUserService.buildUserPageWrapper(
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

        QueryWrapper<SysUser> wrapper = sysUserService.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        // groupId 精确匹配，参数由 MyBatis-Plus 绑定
        assertThat(sql).contains("dept_id =");
        assertParameter(wrapper, groupId);
        // 不应包含 deptId 的 subquery
        assertThat(sql).doesNotContain("SELECT id FROM sys_dept");
    }

    @Test
    void buildUserPageWrapper_shouldExpandDeptIdToSubqueryWhenGroupIdMissing() {
        UUID deptId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, deptId);

        QueryWrapper<SysUser> wrapper = sysUserService.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        // deptId 同时支持"= deptId"和"IN (子部门)"两路
        assertThat(sql).contains("dept_id =");
        assertParameter(wrapper, deptId);
        assertThat(sql).contains("SELECT id FROM sys_dept WHERE deleted = 0 AND parent_id = " + uuidLiteral(deptId));
    }

    @Test
    void buildUserPageWrapper_shouldAppendExistsForRoleId() {
        UUID roleId = UUID.randomUUID();
        SysUserPageRequest request = new SysUserPageRequest(1, 10, null, null, null, null, roleId, null);

        QueryWrapper<SysUser> wrapper = sysUserService.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("EXISTS (SELECT 1 FROM sys_user_role sur");
        assertThat(sql).contains("sur.role_id =");
        assertParameter(wrapper, roleId);
    }

    @Test
    void buildUserPageWrapper_shouldAppendExistsJoinForRoleCode() {
        SysUserPageRequest request = new SysUserPageRequest(1, 10, null, null, null, null, null, "biz_staff");

        QueryWrapper<SysUser> wrapper = sysUserService.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        assertThat(sql).contains("INNER JOIN sys_role sr");
        assertThat(sql).contains("sr.role_code =");
        assertParameter(wrapper, "biz_staff");
    }

    @Test
    void buildUserPageWrapper_shouldSkipBlankKeywordWithoutProducingLike() {
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, "   ", null, null);

        QueryWrapper<SysUser> wrapper = sysUserService.buildUserPageWrapper(
                UUID.randomUUID(), DataScope.ALL, request);
        String sql = wrapper.getSqlSegment();

        assertThat(sql).doesNotContain("LIKE");
    }

    // ========================== dataScope 行级权限（CLAUDE.md 不变量）==========================

    @Test
    void applyDataScopeFilter_personal_shouldAppendIdEqUserId() {
        UUID userId = UUID.randomUUID();
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        sysUserService.applyDataScopeFilter(wrapper, userId, null, DataScope.PERSONAL);

        assertThat(wrapper.getSqlSegment()).contains("id = " + uuidLiteral(userId));
    }

    @Test
    void applyDataScopeFilter_personal_shouldNotAppendWhenUserIdMissing() {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        sysUserService.applyDataScopeFilter(wrapper, null, null, DataScope.PERSONAL);

        // 缺 userId：拒绝追加（与 DataScopeAspect 一致，由 Controller 抛 403）
        assertThat(wrapper.getSqlSegment()).isNullOrEmpty();
    }

    @Test
    void applyDataScopeFilter_dept_shouldAppendDeptIdEq() {
        UUID deptId = UUID.randomUUID();
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        sysUserService.applyDataScopeFilter(wrapper, UUID.randomUUID(), deptId, DataScope.DEPT);

        assertThat(wrapper.getSqlSegment()).contains("dept_id = " + uuidLiteral(deptId));
    }

    @Test
    void applyDataScopeFilter_dept_shouldNotAppendWhenDeptIdMissing() {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        sysUserService.applyDataScopeFilter(wrapper, UUID.randomUUID(), null, DataScope.DEPT);

        assertThat(wrapper.getSqlSegment()).isNullOrEmpty();
    }

    @Test
    void applyDataScopeFilter_all_shouldNotAppendAnyCondition() {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        sysUserService.applyDataScopeFilter(wrapper, UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);

        assertThat(wrapper.getSqlSegment()).isNullOrEmpty();
    }

    @Test
    void applyDataScopeFilter_nullScope_shouldBehaveLikeAll() {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        sysUserService.applyDataScopeFilter(wrapper, UUID.randomUUID(), UUID.randomUUID(), null);

        assertThat(wrapper.getSqlSegment()).isNullOrEmpty();
    }

    @Test
    void buildUserPageWrapper_personalScope_shouldInjectIdEqInSql() {
        UUID currentUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID(); // 不应出现
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, null);

        QueryWrapper<SysUser> wrapper = sysUserService.buildUserPageWrapper(
                currentUserId, DataScope.PERSONAL, request);
        String sql = wrapper.getSqlSegment();

        // CLAUDE.md 不变量：用户域统一 self/group/all → PERSONAL → id = currentUserId
        assertThat(sql).contains("id = " + uuidLiteral(currentUserId));
        // 不会注入其他用户
        assertThat(sql).doesNotContain("id = " + uuidLiteral(targetUserId));
    }

    @Test
    void buildUserPageWrapper_deptScope_shouldInjectDeptIdEqInSql() {
        UUID currentUserId = UUID.randomUUID();
        UUID currentDeptId = UUID.randomUUID();
        SysUserPageRequest request = AuthTestFixtures.pageRequest(1, 10, null, null, null);

        QueryWrapper<SysUser> wrapper = sysUserService.buildUserPageWrapper(
                currentUserId, currentDeptId, DataScope.DEPT, request);
        String sql = wrapper.getSqlSegment();

        // DEPT → dept_id = currentDeptId
        assertThat(sql).contains("dept_id = " + uuidLiteral(currentDeptId));
    }

    // ========================== findPage 端到端 ==========================

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

        IPage<SysUserVO> result = sysUserService.findPage(currentUserId, DataScope.ALL, request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);

        // 验证 wrapper 透传给 mapper 且含 dataScope 注入段（ALL 时为空）
        ArgumentCaptor<QueryWrapper<SysUser>> captor = QueryCaptor();
        verify(sysUserMapper).findPage(any(Page.class), eq(request), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        // keyword + status + roleCode(EXISTS JOIN) 拼接正确
        assertThat(sql).contains("LIKE");
        assertParameter(captor.getValue(), "%招商%");
        assertThat(sql).contains("status =");
        assertParameter(captor.getValue(), 1);
        assertThat(sql).contains("sr.role_code =");
        assertParameter(captor.getValue(), "biz_staff");
        // ALL 范围不会追加 id/dept_id 条件
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

        sysUserService.findPage(currentUserId, DataScope.PERSONAL, request);

        ArgumentCaptor<QueryWrapper<SysUser>> captor = QueryCaptor();
        verify(sysUserMapper).findPage(any(Page.class), any(SysUserPageRequest.class), captor.capture());
        // PERSONAL 必须注入 id = currentUserId
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

        sysUserService.findPage(currentUserId, currentDeptId, DataScope.DEPT, request);

        ArgumentCaptor<QueryWrapper<SysUser>> captor = QueryCaptor();
        verify(sysUserMapper).findPage(any(Page.class), any(SysUserPageRequest.class), captor.capture());
        // DEPT 必须注入 dept_id = currentDeptId
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

        sysUserService.findPage(UUID.randomUUID(), DataScope.ALL, request);

        // fillRoleIds 触发 findByUserIds
        verify(sysUserRoleMapper).findByUserIds(any());
        // orgStructureService.enrichUserList 触发
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

        sysUserService.findPage(currentUserId, DataScope.PERSONAL, null);

        ArgumentCaptor<QueryWrapper<SysUser>> captor = QueryCaptor();
        verify(sysUserMapper).findPage(any(Page.class), any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        // 没有 request：不应有 LIKE / status / roleCode 等
        assertThat(sql).doesNotContain("LIKE");
        // 但 dataScope 仍生效
        assertThat(sql).contains("id = " + uuidLiteral(currentUserId));
    }

    // ========================== helpers ==========================

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<QueryWrapper<SysUser>> QueryCaptor() {
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
