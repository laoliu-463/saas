package com.colonel.saas.service.activity;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.invocation.InvocationOnMock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityAccessServiceTest {

    @Mock
    private ColonelsettlementActivityMapper activityMapper;
    @Mock
    private UserDomainFacade userDomainFacade;

    private ActivityAccessService activityAccessService;

    @BeforeEach
    void setUp() {
        CurrentUserPermissionPolicy oracle = new CurrentUserPermissionPolicy();
        lenient().when(userDomainFacade.hasAnyRole(any(), any(String[].class)))
                .thenAnswer(invocation -> oracle.hasAnyRole(invocation.getArgument(0), expectedRoles(invocation)));
        lenient().when(userDomainFacade.normalizeRoleCodes(any()))
                .thenAnswer(invocation -> oracle.normalizeRoleCodes(invocation.getArgument(0)));
        activityAccessService = new ActivityAccessService(activityMapper, userDomainFacade);
    }

    private static String[] expectedRoles(InvocationOnMock invocation) {
        Object[] arguments = invocation.getArguments();
        if (arguments.length == 2 && arguments[1] instanceof String[] roles) {
            return roles;
        }
        String[] roles = new String[Math.max(0, arguments.length - 1)];
        for (int i = 1; i < arguments.length; i++) {
            roles[i - 1] = (String) arguments[i];
        }
        return roles;
    }

    @Test
    void resolveEffectiveAssignmentFilter_shouldForceMineForRecruiter() {
        assertThat(activityAccessService.resolveEffectiveAssignmentFilter("all", List.of(RoleCodes.BIZ_STAFF)))
                .isEqualTo("mine");
    }

    @Test
    void resolveEffectiveAssignmentFilter_shouldRespectAdminChoice() {
        assertThat(activityAccessService.resolveEffectiveAssignmentFilter("assigned", List.of(RoleCodes.ADMIN)))
                .isEqualTo("assigned");
    }

    @Test
    void normalizeRoleCodes_shouldUseUserDomainPolicy() {
        assertThat(activityAccessService.normalizeRoles("[ADMIN, biz_staff, ADMIN]"))
                .containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF);
    }

    @Test
    void activityAccessService_shouldUseUserDomainFacadeForRoleMatching() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/service/activity/ActivityAccessService.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/service/activity/ActivityAccessService.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .doesNotContain("com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy")
                .doesNotContain("new CurrentUserPermissionPolicy()")
                .doesNotContain("public static Collection<String> normalizeRoleCodes")
                .contains("UserDomainFacade userDomainFacade");
    }

    @Test
    void assertActivityReadable_shouldAllowAdminWithoutLookup() {
        activityAccessService.assertActivityReadable(
                "100018",
                UUID.randomUUID(),
                null,
                List.of(RoleCodes.ADMIN));
    }

    @Test
    void assertActivityReadable_shouldRejectUnassignedActivityForRecruiter() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(activityMapper.selectByActivityId("100018")).thenReturn(null);

        assertThatThrownBy(() -> activityAccessService.assertActivityReadable(
                "100018",
                userId,
                null,
                List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该活动");
    }

    @Test
    void assertActivityReadable_shouldAllowAssignedRecruiter() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId("100018");
        activity.setRecruiterUserId(userId);
        when(activityMapper.selectByActivityId("100018")).thenReturn(activity);

        activityAccessService.assertActivityReadable("100018", userId, null, List.of(RoleCodes.BIZ_LEADER));
    }

    @Test
    void assertActivityReadable_shouldAllowBizLeaderWithMatchingDept() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID deptId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID otherUserId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId("100018");
        activity.setRecruiterUserId(otherUserId); // not assigned to this user
        activity.setRecruiterDeptId(deptId);
        when(activityMapper.selectByActivityId("100018")).thenReturn(activity);

        // biz_leader with matching dept should be allowed
        activityAccessService.assertActivityReadable("100018", userId, deptId, List.of(RoleCodes.BIZ_LEADER));
    }

    @Test
    void assertActivityReadable_shouldRejectBizLeaderWithMismatchedDept() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID deptId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID otherDeptId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId("100018");
        activity.setRecruiterUserId(null);
        activity.setRecruiterDeptId(otherDeptId);
        when(activityMapper.selectByActivityId("100018")).thenReturn(activity);

        // biz_leader with mismatched dept should be denied
        assertThatThrownBy(() -> activityAccessService.assertActivityReadable(
                "100018",
                userId,
                deptId,
                List.of(RoleCodes.BIZ_LEADER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该活动");
    }
}
